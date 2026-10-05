package com.petr.toll.session

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import com.petr.toll.rules.Commitments
import com.petr.toll.rules.Decision
import com.petr.toll.rules.EngineState
import com.petr.toll.rules.Limits
import com.petr.toll.rules.ScreenKind
import com.petr.toll.rules.TollEngine
import com.petr.toll.rules.TollEvent
import com.petr.toll.rules.TollSettings
import com.petr.toll.service.TollAccessibilityService
import com.petr.toll.ui.home.DaySummary
import com.petr.toll.ui.home.HomeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The app's single source of truth: the rules engine's state, the event log, settings and the home screen's data.
 * The accessibility service sends what it sees; the home screen reads [home]. Every change runs on one background
 * thread, so callers never block and the engine is never touched concurrently.
 *
 * Until Petr presses Start, Toll only watches: no events reach the engine and nothing is charged.
 */
class TollRepository private constructor(context: Context) {
    private val app = context.applicationContext
    private val zone: ZoneId = ZoneId.systemDefault()
    private val engine = TollEngine(zone)
    private val clock = TollClock(System::currentTimeMillis, SystemClock::elapsedRealtime)
    private val events = EventStore(File(app.filesDir, "events"))
    private val settingsStore = SettingsStore(File(app.filesDir, "settings.json"))
    private val enforcingFlag = File(app.filesDir, "enforcing")
    private val probeOffFlag = File(app.filesDir, "probe_off")

    private val thread = HandlerThread("toll-session").apply { start() }
    private val handler = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())
    private val tick = Runnable { sendNow(TollEvent.Tick(clock.now())) }

    private var stored: StoredSettings = settingsStore.load() ?: firstRunSettings()
    private var enforcing: Boolean = enforcingFlag.exists()
    private val session = TollSession(engine, stored.settings) { day, event -> events.append(day, event) }

    /** Summaries of finished days, built once by replaying their logs. */
    private val pastDays = HashMap<LocalDate, DaySummary>()

    private val homeState: MutableStateFlow<HomeState>
    val home: StateFlow<HomeState>

    private val testModeState = MutableStateFlow(!probeOffFlag.exists())

    /** Test mode shows the Phase 0 probe panel over Instagram (live screen label, dumps). On until switched off. */
    val testMode: StateFlow<Boolean> = testModeState

    fun setTestMode(on: Boolean) {
        handler.post {
            if (on) probeOffFlag.delete() else probeOffFlag.writeText("off")
            testModeState.value = on
        }
    }

    /** Called on the session thread after every event while enforcing. The service renders overlays from it. */
    @Volatile
    var onDecision: ((Decision) -> Unit)? = null

    init {
        if (enforcing) {
            val today = today()
            session.restore(events.read(today.minusDays(1)) + events.read(today))
        }
        homeState = MutableStateFlow(buildHome())
        home = homeState
        if (enforcing) handler.post(tick)
    }

    // What the service sees. All thread-safe; they queue onto the session thread.

    fun screen(kind: ScreenKind) = send { TollEvent.Screen(it, kind) }
    fun screenOff() = send { TollEvent.ScreenOff(it) }
    fun screenOn() = send { TollEvent.ScreenOn(it) }
    fun tollPaid() = send { TollEvent.TollPaid(it) }
    fun stillHereDismissed() = send { TollEvent.StillHereDismissed(it) }

    // What the home screen does.

    /** Week 1 starts today; from now on Toll counts and charges. */
    fun start() {
        handler.post { startNow() }
    }

    private fun startNow() {
        if (enforcing) return
        stored = stored.copy(settings = stored.settings.copy(startDate = today()))
        settingsStore.save(stored)
        session.updateSettings(stored.settings)
        enforcingFlag.writeText(clock.now().toString())
        enforcing = true
        Log.i(TAG, "started: week 1 begins ${stored.settings.startDate}")
        sendNow(TollEvent.Tick(clock.now()))
    }

    /** Starts a quick pass and opens Instagram, if one is left. */
    fun quickPass() {
        handler.post { quickPassNow() }
    }

    private fun quickPassNow() {
        if (!enforcing) return
        // A pass is running after the event (just started, or already running): open Instagram. None left: stay.
        val decision = sendNow(TollEvent.QuickPassStarted(clock.now()))
        if (decision?.quickPassLeft != null) main.post(::openInstagram)
    }

    /** Tightening applies now; loosening waits 24 hours (rules.Commitments). */
    fun saveSettings(desired: TollSettings) {
        handler.post { saveSettingsNow(desired) }
    }

    private fun saveSettingsNow(desired: TollSettings) {
        val plan = Commitments.plan(stored.settings, stored.pending, desired, clock.now())
        stored = StoredSettings(plan.settings, plan.pending)
        settingsStore.save(stored)
        session.updateSettings(stored.settings)
        if (enforcing) sendNow(TollEvent.Tick(clock.now())) else publish()
    }

    /** Re-checks things Toll isn't told about, such as the accessibility switch. Call from onResume. */
    fun refresh() {
        handler.post { if (enforcing) sendNow(TollEvent.Tick(clock.now())) else publish() }
    }

    fun now(): Instant = clock.now()

    private fun send(event: (Instant) -> TollEvent) {
        handler.post { if (enforcing) sendNow(event(clock.now())) }
    }

    private fun sendNow(event: TollEvent): Decision? {
        if (!enforcing) return null
        matureSettings(event.at)
        val decision = session.send(event)
        scheduleTick(decision)
        publish()
        onDecision?.invoke(decision)
        return decision
    }

    private fun matureSettings(now: Instant) {
        if (stored.pending.none { it.effectiveAt <= now }) return
        val plan = Commitments.mature(stored.settings, stored.pending, now)
        stored = StoredSettings(plan.settings, plan.pending)
        settingsStore.save(stored)
        session.updateSettings(stored.settings)
    }

    private fun scheduleTick(decision: Decision) {
        handler.removeCallbacks(tick)
        val due = listOfNotNull(decision.nextChangeAt, stored.pending.minOfOrNull { it.effectiveAt }).minOrNull() ?: return
        val delay = Duration.between(clock.now(), due).toMillis().coerceAtLeast(0) + TICK_SLACK_MS
        handler.postDelayed(tick, delay)
    }

    private fun publish() {
        homeState.value = buildHome()
    }

    private fun buildHome(): HomeState {
        val today = today()
        val decision = session.decision?.takeIf { enforcing && it.day == today }
        return HomeState(
            serviceOn = serviceOn(),
            enforcing = enforcing,
            today = decision,
            todayDate = today,
            settings = stored.settings,
            history = history(today, decision),
            pending = stored.pending,
        )
    }

    private fun history(today: LocalDate, decision: Decision?): List<DaySummary> {
        val days = events.days().filter { it > today.minusDays(HISTORY_DAYS) && it < today }
        val past = days.map { day -> pastDays.getOrPut(day) { summarise(day) } }
        val now = decision?.let { DaySummary(today, it.paidToday, it.limit, it.opensToday) }
        return past + listOfNotNull(now)
    }

    private fun summarise(day: LocalDate): DaySummary {
        val state: EngineState = engine.replay(events.read(day), stored.settings)
        return DaySummary(day, state.paidToday, Limits.limitFor(day, stored.settings), state.opensToday)
    }

    private fun today(): LocalDate = Limits.tollDay(clock.now(), zone, stored.settings.dayStartHour)

    /** Defaults, with week 1 provisionally starting today; [start] moves it to the real start day. */
    private fun firstRunSettings(): StoredSettings {
        val defaults = TollSettings(startDate = LocalDate.EPOCH)
        val today = Limits.tollDay(clock.now(), zone, defaults.dayStartHour)
        return StoredSettings(defaults.copy(startDate = today), emptyList())
    }

    private fun serviceOn(): Boolean {
        val ours = ComponentName(app, TollAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == ours }
    }

    private fun openInstagram() {
        val intent = app.packageManager.getLaunchIntentForPackage(INSTAGRAM) ?: return
        app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    companion object {
        private const val TAG = "TollSession"
        private const val INSTAGRAM = "com.instagram.android"
        private const val HISTORY_DAYS = 14L
        private const val TICK_SLACK_MS = 50L

        @Volatile
        private var instance: TollRepository? = null

        fun get(context: Context): TollRepository =
            instance ?: synchronized(this) { instance ?: TollRepository(context).also { instance = it } }
    }
}
