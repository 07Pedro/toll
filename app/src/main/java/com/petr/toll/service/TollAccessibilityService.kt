package com.petr.toll.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Path
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.petr.toll.classifier.Access
import com.petr.toll.classifier.Classification
import com.petr.toll.classifier.Screen
import com.petr.toll.classifier.ScreenClassifier
import com.petr.toll.classifier.ScreenSnapshot
import com.petr.toll.classifier.Signatures
import com.petr.toll.probe.DumpFormat
import com.petr.toll.probe.DumpStore
import com.petr.toll.probe.NavResult
import com.petr.toll.probe.Navigator
import com.petr.toll.probe.PREFETCH
import com.petr.toll.probe.ProbeOverlay
import com.petr.toll.probe.SnapshotReader
import com.petr.toll.rules.Decision
import com.petr.toll.rules.ScreenKind
import com.petr.toll.session.FrontAppTimer
import com.petr.toll.session.SessionTracker
import com.petr.toll.session.TollRepository
import com.petr.toll.ui.overlay.TollOverlays
import android.os.PowerManager
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Toll's eyes and hands. On every Instagram change it reads the screen (a few direct view-ID lookups), turns it into
 * a [ScreenKind] and tells [TollRepository]; each resulting decision is drawn by [TollOverlays] while Instagram is in
 * front. Until Petr presses Start the repository ignores everything, so Toll only watches.
 *
 * Test mode adds the Phase 0 probe panel (live screen label, dumps, shortcut tests).
 * Logcat: `adb logcat -s TollProbe TollSession`.
 */
class TollAccessibilityService : AccessibilityService(), TollOverlays.Actions, ProbeOverlay.Actions {

    private val main = Handler(Looper.getMainLooper())
    private val worker = HandlerThread("toll-reader").apply { start() }
    private val workerHandler = Handler(worker.looper)
    private val tracker = SessionTracker() // worker thread only
    private val duolingo = FrontAppTimer(DUOLINGO, DUOLINGO_TASK) // worker thread only
    private val earnCheck = Runnable { scheduleTick() }
    @Volatile private var screenOn = true

    private lateinit var repo: TollRepository
    private lateinit var classifier: ScreenClassifier
    private lateinit var navigator: Navigator
    private lateinit var overlays: TollOverlays
    private lateinit var probe: ProbeOverlay
    private lateinit var dumps: DumpStore

    @Volatile private var instagramWindowClass: String? = null
    @Volatile private var tollWindowClass: String? = null
    @Volatile private var lastClassification: Classification? = null
    @Volatile private var lastDecision: Decision? = null
    @Volatile private var lastTick = 0L
    @Volatile private var dumpRequestedAt = 0L
    @Volatile private var probeHiddenUntil = 0L
    private val tickPending = AtomicBoolean(false)
    private var lastLogLine: String? = null // worker thread only
    private var instagramInFront = false // main thread only
    private var lastSaveAt = 0L // main thread only

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenOn = false
                    repo.screenOff()
                    workerHandler.post {
                        tracker.reset()
                        earnFrom(null)
                    }
                    showInstagramFront(false)
                }
                Intent.ACTION_SCREEN_ON -> {
                    screenOn = true
                    repo.screenOn()
                    scheduleTick()
                }
            }
        }
    }

    override fun onServiceConnected() {
        val signatures = Signatures.parse(assets.open("signatures.json").bufferedReader().use { it.readText() })
        classifier = ScreenClassifier(signatures)
        navigator = Navigator(signatures.navigation)
        repo = TollRepository.get(this)
        overlays = TollOverlays(this, this)
        probe = ProbeOverlay(this, this)
        dumps = DumpStore(this)
        repo.onDecision = { decision ->
            lastDecision = decision
            main.post { if (instagramInFront) overlays.render(decision) else overlays.hideAll() }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
        screenOn = getSystemService(PowerManager::class.java).isInteractive
        repo.refresh()
        Log.i(TAG, "connected: ${signatures.rules.size} rules, Instagram ${instagramVersion(this)}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString()
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (pkg in INSTAGRAM_PACKAGES) instagramWindowClass = event.className?.toString()
            if (pkg == packageName) tollWindowClass = event.className?.toString()
        }
        if (pkg in INSTAGRAM_PACKAGES && event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) logScroll(event)
        scheduleTick()
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        if (::repo.isInitialized) {
            repo.onDecision = null
            runCatching { unregisterReceiver(screenReceiver) }
            overlays.release()
            probe.hide()
        }
        main.removeCallbacksAndMessages(null)
        workerHandler.removeCallbacksAndMessages(null)
        worker.quitSafely()
        return super.onUnbind(intent)
    }

    /** Read the screen at most every [MIN_INTERVAL_MS]; Instagram fires content events constantly. */
    private fun scheduleTick() {
        if (!tickPending.compareAndSet(false, true)) return
        val wait = (lastTick + MIN_INTERVAL_MS - SystemClock.uptimeMillis()).coerceAtLeast(0)
        workerHandler.postDelayed(::tick, wait)
    }

    /** Worker thread. */
    private fun tick() {
        tickPending.set(false)
        val started = SystemClock.uptimeMillis()
        lastTick = started
        val root = frontAppRoot()
        val pkg = root?.packageName?.toString()
        earnFrom(pkg)
        if (root != null && pkg in INSTAGRAM_PACKAGES) {
            readInstagram(root, started)
            return
        }
        if (dumpDue(started)) saveRequestedDump(null, null, started)
        // Toll's typing challenge is part of the visit; anything else means Instagram was left.
        val challenge = pkg == packageName && tollWindowClass?.endsWith(CHALLENGE_ACTIVITY) == true
        (if (challenge) tracker.tollChallenge() else tracker.outside())?.let(repo::screen)
        main.post { showInstagramFront(false) }
    }

    private fun readInstagram(root: AccessibilityNodeInfo, started: Long) {
        val windowClass = instagramWindowClass
        val query = LiveScreenQuery(root, windowClass)
        val classification = classifier.classify(query)
        val itemKey = classifier.itemKey(query)
        tracker.instagram(classification.screen, itemKey)?.let(repo::screen)
        val kind = tracker.current
        val took = SystemClock.uptimeMillis() - started
        lastClassification = classification
        // The full tree is only read for a requested dump; classification needs just a few direct lookups.
        if (dumpDue(started)) saveRequestedDump(SnapshotReader.read(root, windowClass), classification, started)

        val access = when (kind) {
            ScreenKind.FREE -> Access.FREE
            ScreenKind.PAID -> Access.PAID
            else -> Access.UNKNOWN
        }
        val viewer = classification.screen == Screen.REELS_VIEWER || classification.screen == Screen.POST
        val headline = "${classification.screen.label} · $access" + if (viewer && access == Access.FREE) " (opened on purpose)" else ""
        val details = buildString {
            append("rule ").append(classification.ruleId ?: "none")
            append(" · tab ").append(classification.selectedTab ?: "-")
            append("\nwin ").append(windowClass?.substringAfterLast('.') ?: "-")
            append(" · item ").append(itemKey ?: "-")
            append("\nread ").append(took).append(" ms · ").append(query.lookups).append(" lookups")
        }
        main.post {
            showInstagramFront(true)
            if (repo.testMode.value && SystemClock.uptimeMillis() >= probeHiddenUntil) {
                probe.show()
                probe.render(headline, access, details)
            } else if (!repo.testMode.value) {
                probe.hide()
            }
        }

        val line = "screen=${classification.screen} kind=$kind rule=${classification.ruleId} " +
            "tab=${classification.selectedTab} win=$windowClass item=$itemKey"
        if (line != lastLogLine) {
            Log.i(TAG, "$line read=${took}ms/${query.lookups}")
            lastLogLine = line
        } else if (took > SLOW_TICK_MS) {
            Log.d(TAG, "slow tick: $took ms, ${query.lookups} lookups")
        }
    }

    /**
     * Worker thread. Earn time: every 5 minutes with Duolingo in front and the screen on earns a task. A check is
     * scheduled for when the current stretch would complete, in case Duolingo sends no events meanwhile.
     */
    private fun earnFrom(front: String?) {
        repeat(duolingo.update(front, screenOn, repo.now())) {
            Log.i(TAG, "earned: duolingo")
            repo.earn(EARN_DUOLINGO)
        }
        workerHandler.removeCallbacks(earnCheck)
        duolingo.dueAt()?.let { due ->
            workerHandler.postDelayed(earnCheck, Duration.between(repo.now(), due).toMillis().coerceAtLeast(0) + 100)
        }
    }

    /** Main thread. Toll's windows only ever cover Instagram. */
    private fun showInstagramFront(front: Boolean) {
        if (front == instagramInFront) return
        instagramInFront = front
        if (front) {
            lastDecision?.let(overlays::render)
        } else {
            overlays.hideAll()
            probe.hide()
        }
    }

    /** The app window in front (Instagram, Toll's challenge, the launcher…), or null (e.g. the notification shade). */
    private fun frontAppRoot(): AccessibilityNodeInfo? {
        val apps = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        val front = apps.firstOrNull { it.isActive } ?: apps.firstOrNull { it.isFocused }
        return front?.getRoot(PREFETCH) ?: getRootInActiveWindow(PREFETCH)
    }

    // The gate's buttons (TollOverlays.Actions).

    override fun onFreeMessages() = navigate("free messages", retry = true) { navigator.openDms(it) }

    override fun onFreeStories() = navigate("free stories", retry = true) { navigator.openFirstStory(it) }

    override fun onLeave() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    override fun onTollPaid() = repo.tollPaid()

    override fun onStillHereDismissed() = repo.stillHereDismissed()

    // The probe panel's buttons (ProbeOverlay.Actions), test mode only.

    override fun onOpenDms() = navigate("open DMs", retry = true) { navigator.openDms(it) }

    override fun onOpenFirstStory() = navigate("open first story", retry = true) { navigator.openFirstStory(it) }

    /**
     * Runs a free shortcut. If the target isn't on screen, first gets there (feed: scroll to the top for the stories
     * bar; elsewhere: Back) and retries once. If Instagram refuses the accessibility click, taps like a finger.
     */
    private fun navigate(action: String, retry: Boolean, block: (AccessibilityNodeInfo) -> NavResult) {
        val root = frontAppRoot()?.takeIf { it.packageName?.toString() in INSTAGRAM_PACKAGES }
            ?: return report(action, "Instagram isn't in front")
        when (val result = block(root)) {
            is NavResult.Done -> report(action, result.message)
            is NavResult.NotHere -> if (retry) {
                val onFeed = lastClassification?.screen == Screen.FEED
                Log.i(TAG, "$action: ${result.message}; ${if (onFeed) "scrolling the feed up" else "going back"} and retrying")
                if (!onFeed || !navigator.scrollFeedToTop(root)) performGlobalAction(GLOBAL_ACTION_BACK)
                main.postDelayed({ navigate(action, retry = false, block) }, RETRY_DELAY_MS)
            } else {
                report(action, "${result.message}. Go back to the feed and try again.")
            }
            is NavResult.Tap -> tap(action, result)
        }
    }

    /**
     * Taps like a finger. The gate stays visible but lets the touch through; the probe panel, which may sit on the
     * target, is removed for a moment.
     */
    private fun tap(action: String, target: NavResult.Tap) {
        probeHiddenUntil = SystemClock.uptimeMillis() + TAP_DELAY_MS + TAP_MS + 300
        probe.hide()
        overlays.touchThrough(true)
        val path = Path().apply { moveTo(target.x, target.y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, TAP_DELAY_MS, TAP_MS))
            .build()
        val callback = object : GestureResultCallback() {
            override fun onCompleted(description: GestureDescription?) = afterTap(action, "Opened ${target.what}")
            override fun onCancelled(description: GestureDescription?) = afterTap(action, "Tap on ${target.what} was cancelled")
        }
        if (!dispatchGesture(gesture, callback, main)) afterTap(action, "Couldn't tap ${target.what}")
    }

    private fun afterTap(action: String, message: String) {
        overlays.touchThrough(false)
        probeHiddenUntil = 0L
        if (repo.testMode.value && instagramInFront) probe.show()
        report(action, message)
    }

    private fun report(action: String, message: String) {
        Log.i(TAG, "$action: $message")
        if (repo.testMode.value) probe.setStatus(message)
    }

    // Probe: dumps and skips (test mode).

    /**
     * Asks the worker for a dump of the screen as it is after the tap. Reusing the last read saved stale screens when
     * reading Instagram stalled (about 5.7 s in the first walkthrough, during which every save got the DM inbox).
     */
    override fun onDump() {
        if (!acceptSaveTap()) return
        dumpRequestedAt = SystemClock.uptimeMillis()
        probe.setStatus("Saving…")
        scheduleTick()
    }

    /** Records a skipped walkthrough step, so the step count advances without a fake dump. */
    override fun onSkip() {
        if (!acceptSaveTap()) return
        thread(name = "toll-skip") {
            val step = dumps.count() + 1
            val file = dumps.saveSkip(step)
            Log.i(TAG, "step $step skipped: ${file.name}")
            main.post { probe.setStatus("Skipped step $step") }
        }
    }

    /** One save or skip per [SAVE_DEBOUNCE_MS], so a double tap can't record two steps. */
    private fun acceptSaveTap(): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - lastSaveAt < SAVE_DEBOUNCE_MS) return false
        lastSaveAt = now
        return true
    }

    private fun dumpDue(started: Long): Boolean = dumpRequestedAt.let { it != 0L && started >= it }

    /** Worker thread: saves the dump requested by [onDump] once a read that started after the tap exists. */
    private fun saveRequestedDump(snapshot: ScreenSnapshot?, classification: Classification?, started: Long) {
        val requested = dumpRequestedAt
        if (requested == 0L || started < requested) return
        dumpRequestedAt = 0L
        val message = if (snapshot == null || classification == null) {
            "Not saved: Instagram isn't in front"
        } else {
            runCatching {
                val savedAt = LocalDateTime.now().format(DumpStore.SAVED_AT)
                val file = dumps.save(DumpFormat.create(snapshot, classification, instagramVersion(this), savedAt))
                Log.i(TAG, "dump saved: ${file.name} (${started - requested} ms after the tap)")
                "Saved ${file.name}"
            }.getOrElse { error ->
                Log.e(TAG, "dump failed", error)
                "Dump failed: ${error.message}"
            }
        }
        main.post { probe.setStatus(message) }
    }

    /** Scroll events tell us whether a pager swipe is visible to accessibility; only logged. */
    private fun logScroll(event: AccessibilityEvent) {
        val screen = lastClassification?.screen ?: return
        if (screen != Screen.REELS_VIEWER && screen != Screen.POST && screen != Screen.STORY) return
        val id = event.source?.viewIdResourceName?.substringAfter(":id/")
        Log.d(TAG, "scroll screen=$screen id=$id dx=${event.scrollDeltaX} dy=${event.scrollDeltaY} " +
            "from=${event.fromIndex} to=${event.toIndex} count=${event.itemCount}")
    }

    companion object {
        const val TAG = "TollProbe"
        private const val CHALLENGE_ACTIVITY = ".ui.ChallengeActivity"
        private const val MIN_INTERVAL_MS = 150L
        private const val SLOW_TICK_MS = 100L
        private const val SAVE_DEBOUNCE_MS = 700L
        private const val RETRY_DELAY_MS = 800L
        private const val TAP_DELAY_MS = 100L // lets the window manager apply touch-through before the finger lands
        private const val TAP_MS = 50L
        val INSTAGRAM_PACKAGES = setOf("com.instagram.android", "com.instagram.lite")
        private const val DUOLINGO = "com.duolingo"
        private const val EARN_DUOLINGO = "duolingo"
        private val DUOLINGO_TASK: Duration = Duration.ofMinutes(5)

        fun instagramVersion(context: Context): String? = try {
            context.packageManager
                .getPackageInfo("com.instagram.android", PackageManager.PackageInfoFlags.of(0))
                .versionName
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }
}
