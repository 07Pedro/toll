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
import com.petr.toll.rules.Decision
import com.petr.toll.rules.ScreenKind
import com.petr.toll.rules.TollEvent
import com.petr.toll.rules.TollSettings
import com.petr.toll.service.TollAccessibilityService
import com.petr.toll.ui.home.HomeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * The app's single source of truth, shared by the accessibility service and the home screen. The logic lives in
 * [TollCore] (plain Kotlin, JVM-tested); this class adds the Android parts: one background thread for every change,
 * scheduled ticks, the home screen's [StateFlow], and launching Instagram for a quick pass.
 *
 * Until Petr presses Start, Toll only watches: no events reach the engine and nothing is charged.
 */
class TollRepository private constructor(context: Context) {
    private val app = context.applicationContext
    private val clock = TollClock(System::currentTimeMillis, SystemClock::elapsedRealtime)
    private val core = TollCore(ZoneId.systemDefault(), clock::now, app.filesDir)
    private val probeOffFlag = File(app.filesDir, "probe_off")

    private val thread = HandlerThread("toll-session").apply { start() }
    private val handler = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())
    private val tick = Runnable { sendNow(TollEvent.Tick(clock.now())) }

    private val homeState = MutableStateFlow(core.home(serviceOn()))
    val home: StateFlow<HomeState> = homeState

    private val testModeState = MutableStateFlow(!probeOffFlag.exists())

    /** Test mode shows the Phase 0 probe panel over Instagram (live screen label, dumps). On until switched off. */
    val testMode: StateFlow<Boolean> = testModeState

    /** Called on the session thread after every event while enforcing. The service renders overlays from it. */
    @Volatile
    var onDecision: ((Decision) -> Unit)? = null

    init {
        if (core.enforcing) handler.post(tick)
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
        handler.post {
            if (!core.start()) return@post
            Log.i(TAG, "started: week 1 begins ${core.stored.settings.startDate}")
            sendNow(TollEvent.Tick(clock.now()))
        }
    }

    /** Starts a quick pass and opens Instagram, if one is left. */
    fun quickPass() {
        handler.post {
            val open = core.quickPass()
            afterChange()
            if (open) main.post(::openInstagram)
        }
    }

    /** Tightening applies now; loosening waits 24 hours (rules.Commitments). */
    fun saveSettings(desired: TollSettings) {
        handler.post {
            core.saveSettings(desired)
            if (core.enforcing) sendNow(TollEvent.Tick(clock.now())) else afterChange()
        }
    }

    /** Re-checks things Toll isn't told about, such as the accessibility switch. Call from onResume. */
    fun refresh() {
        handler.post { if (core.enforcing) sendNow(TollEvent.Tick(clock.now())) else afterChange() }
    }

    fun setTestMode(on: Boolean) {
        handler.post {
            if (on) probeOffFlag.delete() else probeOffFlag.writeText("off")
            testModeState.value = on
        }
    }

    fun now(): Instant = clock.now()

    private fun send(event: (Instant) -> TollEvent) {
        handler.post { sendNow(event(clock.now())) }
    }

    private fun sendNow(event: TollEvent) {
        core.send(event) ?: return
        afterChange()
    }

    /** Publishes the new state, tells the service, and schedules the next tick. Session thread only. */
    private fun afterChange() {
        homeState.value = core.home(serviceOn())
        core.decision?.takeIf { core.enforcing }?.let { onDecision?.invoke(it) }
        handler.removeCallbacks(tick)
        val due = core.nextWakeUp() ?: return
        handler.postDelayed(tick, Duration.between(clock.now(), due).toMillis().coerceAtLeast(0) + TICK_SLACK_MS)
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
        private const val TICK_SLACK_MS = 50L

        @Volatile
        private var instance: TollRepository? = null

        fun get(context: Context): TollRepository =
            instance ?: synchronized(this) { instance ?: TollRepository(context).also { instance = it } }
    }
}
