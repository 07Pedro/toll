package com.petr.toll.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
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
import com.petr.toll.classifier.OriginTracker
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
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Phase 0 probe: watches Instagram, shows the live classification on a floating label, logs every change and saves
 * redacted screen dumps. Nothing is blocked or counted yet.
 * Logcat: `adb logcat -s TollProbe`.
 */
class TollAccessibilityService : AccessibilityService(), ProbeOverlay.Actions {

    private val handler = Handler(Looper.getMainLooper())
    private val worker = HandlerThread("toll-probe").apply { start() }
    private val workerHandler = Handler(worker.looper)
    private val origin = OriginTracker()
    private lateinit var classifier: ScreenClassifier
    private lateinit var navigator: Navigator
    private lateinit var overlay: ProbeOverlay
    private lateinit var dumps: DumpStore

    @Volatile private var instagramWindowClass: String? = null
    @Volatile private var lastSnapshot: ScreenSnapshot? = null
    @Volatile private var lastClassification: Classification? = null
    @Volatile private var lastTick = 0L
    @Volatile private var dumpRequestedAt = 0L
    @Volatile private var overlayHiddenUntil = 0L
    private val tickPending = AtomicBoolean(false)
    private var lastLogLine: String? = null // worker thread only
    private var lastSaveAt = 0L // main thread only

    override fun onServiceConnected() {
        val signatures = Signatures.parse(assets.open("signatures.json").bufferedReader().use { it.readText() })
        classifier = ScreenClassifier(signatures)
        navigator = Navigator(signatures.navigation)
        overlay = ProbeOverlay(this, this)
        dumps = DumpStore(this)
        Log.i(TAG, "connected: ${signatures.rules.size} rules, Instagram ${instagramVersion(this)}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val fromInstagram = event.packageName?.toString() in INSTAGRAM_PACKAGES
        if (fromInstagram && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            instagramWindowClass = event.className?.toString()
        }
        if (fromInstagram && event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) logScroll(event)
        scheduleTick()
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        handler.removeCallbacksAndMessages(null)
        workerHandler.removeCallbacksAndMessages(null)
        worker.quitSafely()
        if (::overlay.isInitialized) overlay.hide()
        return super.onUnbind(intent)
    }

    /** Classify at most every [MIN_INTERVAL_MS]; Instagram fires content events constantly. */
    private fun scheduleTick() {
        if (!tickPending.compareAndSet(false, true)) return
        val wait = (lastTick + MIN_INTERVAL_MS - SystemClock.uptimeMillis()).coerceAtLeast(0)
        workerHandler.postDelayed(::tick, wait)
    }

    /**
     * Runs on the worker thread. Reading Instagram's tree is hundreds of IPC calls; on the main thread it blocked taps
     * on the label, which then arrived in a burst (four dumps within 9 ms in the first walkthrough).
     */
    private fun tick() {
        tickPending.set(false)
        val started = SystemClock.uptimeMillis()
        lastTick = started
        val root = instagramRoot()
        if (root == null) {
            saveRequestedDump(null, null, started)
            handler.post { overlay.hide() }
            return
        }
        val snapshot = SnapshotReader.read(root, instagramWindowClass)
        val classification = classifier.classify(snapshot)
        val itemKey = classifier.itemKey(snapshot)
        val access = origin.update(classification.screen, itemKey)
        lastSnapshot = snapshot
        lastClassification = classification
        saveRequestedDump(snapshot, classification, started)

        val viewer = classification.screen == Screen.REELS_VIEWER || classification.screen == Screen.POST
        val suffix = if (viewer && access == Access.FREE) " (from DM)" else ""
        val headline = "${classification.screen.label} · $access$suffix"
        val details = buildString {
            append("rule ").append(classification.ruleId ?: "none")
            append(" · tab ").append(classification.selectedTab ?: "-")
            append("\nwin ").append(snapshot.windowClass?.substringAfterLast('.') ?: "-")
            append(" · item ").append(itemKey ?: "-")
        }
        handler.post {
            if (SystemClock.uptimeMillis() < overlayHiddenUntil) return@post
            overlay.show()
            overlay.render(headline, access, details)
        }

        val line = "screen=${classification.screen} access=$access rule=${classification.ruleId} " +
            "tab=${classification.selectedTab} win=${snapshot.windowClass} item=$itemKey"
        if (line != lastLogLine) {
            Log.i(TAG, line)
            lastLogLine = line
        }
        val took = SystemClock.uptimeMillis() - started
        if (took > SLOW_TICK_MS) Log.d(TAG, "slow tick: $took ms, ${snapshot.root.walk().count()} nodes")
    }

    /** Instagram's window if it's the app in front, else null (home screen, other apps, notification shade). */
    private fun instagramRoot(): AccessibilityNodeInfo? {
        val apps = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        val front = apps.firstOrNull { it.isActive } ?: apps.firstOrNull { it.isFocused }
        val root = front?.getRoot(PREFETCH) ?: getRootInActiveWindow(PREFETCH)
        return root?.takeIf { it.packageName?.toString() in INSTAGRAM_PACKAGES }
    }

    /** Scroll events tell us whether a pager swipe is visible to accessibility; the probe only logs them. */
    private fun logScroll(event: AccessibilityEvent) {
        val screen = lastClassification?.screen ?: return
        if (screen != Screen.REELS_VIEWER && screen != Screen.POST && screen != Screen.STORY) return
        val id = event.source?.viewIdResourceName?.substringAfter(":id/")
        Log.d(TAG, "scroll screen=$screen id=$id dx=${event.scrollDeltaX} dy=${event.scrollDeltaY} " +
            "from=${event.fromIndex} to=${event.toIndex} count=${event.itemCount}")
    }

    /**
     * Asks the worker for a dump of the screen as it is after the tap. Reusing the last snapshot saved stale screens
     * when reading Instagram stalled (about 5.7 s in the first walkthrough, during which every save got the DM inbox).
     */
    override fun onDump() {
        if (!acceptSaveTap()) return
        dumpRequestedAt = SystemClock.uptimeMillis()
        overlay.setStatus("Saving…")
        scheduleTick()
    }

    /** Worker thread: saves the dump requested by [onDump] once a snapshot taken after the tap exists. */
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
        handler.post { overlay.setStatus(message) }
    }

    /** Records a skipped walkthrough step, so the step count advances without a fake dump. */
    override fun onSkip() {
        if (!acceptSaveTap()) return
        thread(name = "toll-skip") {
            val step = dumps.count() + 1
            val file = dumps.saveSkip(step)
            Log.i(TAG, "step $step skipped: ${file.name}")
            handler.post { overlay.setStatus("Skipped step $step") }
        }
    }

    /** One save or skip per [SAVE_DEBOUNCE_MS], so a double tap can't record two steps. */
    private fun acceptSaveTap(): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - lastSaveAt < SAVE_DEBOUNCE_MS) return false
        lastSaveAt = now
        return true
    }

    override fun onOpenDms() = navigate("open DMs", retryAfterBack = true) { navigator.openDms(it) }

    override fun onOpenFirstStory() = navigate("open first story", retryAfterBack = true) { navigator.openFirstStory(it) }

    /**
     * Runs a free shortcut. If the target isn't on screen (e.g. pressed inside a story), goes Back once and retries;
     * if Instagram refuses the accessibility click, taps the target like a finger.
     */
    private fun navigate(action: String, retryAfterBack: Boolean, block: (AccessibilityNodeInfo) -> NavResult) {
        val root = instagramRoot()
        if (root == null) return report(action, "Instagram isn't in front")
        when (val result = block(root)) {
            is NavResult.Done -> report(action, result.message)
            is NavResult.NotHere -> if (retryAfterBack) {
                Log.i(TAG, "$action: ${result.message}; going back and retrying")
                performGlobalAction(GLOBAL_ACTION_BACK)
                handler.postDelayed({ navigate(action, retryAfterBack = false, block) }, RETRY_AFTER_BACK_MS)
            } else {
                report(action, "${result.message}. Go back to the feed and try again.")
            }
            is NavResult.Tap -> tap(action, result)
        }
    }

    /** Taps like a finger. The panel is removed first: it may cover the target and would catch the tap itself. */
    private fun tap(action: String, target: NavResult.Tap) {
        overlayHiddenUntil = SystemClock.uptimeMillis() + TAP_DELAY_MS + TAP_MS + 300
        overlay.hide()
        val path = Path().apply { moveTo(target.x, target.y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, TAP_DELAY_MS, TAP_MS))
            .build()
        val callback = object : GestureResultCallback() {
            override fun onCompleted(description: GestureDescription?) = report(action, "Opened ${target.what}")
            override fun onCancelled(description: GestureDescription?) = report(action, "Tap on ${target.what} was cancelled")
        }
        if (!dispatchGesture(gesture, callback, handler)) report(action, "Couldn't tap ${target.what}")
    }

    private fun report(action: String, message: String) {
        Log.i(TAG, "$action: $message")
        if (overlayHiddenUntil != 0L) {
            overlayHiddenUntil = 0L
            overlay.show() // it was removed for a tap
        }
        overlay.setStatus(message)
    }

    companion object {
        const val TAG = "TollProbe"
        private const val MIN_INTERVAL_MS = 300L
        private const val SLOW_TICK_MS = 150L
        private const val SAVE_DEBOUNCE_MS = 700L
        private const val RETRY_AFTER_BACK_MS = 800L
        private const val TAP_DELAY_MS = 100L // lets the window manager remove the panel before the finger lands
        private const val TAP_MS = 50L
        val INSTAGRAM_PACKAGES = setOf("com.instagram.android", "com.instagram.lite")

        fun instagramVersion(context: Context): String? = try {
            context.packageManager
                .getPackageInfo("com.instagram.android", PackageManager.PackageInfoFlags.of(0))
                .versionName
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }
}
