package com.petr.toll.session

import com.petr.toll.rules.Commitments
import com.petr.toll.rules.Decision
import com.petr.toll.rules.Limits
import com.petr.toll.rules.TollEngine
import com.petr.toll.rules.TollEvent
import com.petr.toll.rules.TollSettings
import com.petr.toll.ui.home.DaySummary
import com.petr.toll.ui.home.HomeState
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Everything [TollRepository] does that isn't Android: settings, the Start switch, the engine session, replay after a
 * restart, and the home screen's data. Plain Kotlin over files, so JVM tests cover startup and restarts.
 * Not thread-safe: the repository calls it from one thread.
 *
 * Until [start], Toll only watches: [send] ignores every event and nothing is logged or charged.
 */
class TollCore(
    private val zone: ZoneId,
    private val now: () -> Instant,
    dir: File,
) {
    private val engine = TollEngine(zone)
    private val events = EventStore(File(dir, "events"))
    private val settingsStore = SettingsStore(File(dir, "settings.json"))
    private val enforcingFlag = File(dir, "enforcing")

    var stored: StoredSettings = settingsStore.load() ?: firstRunSettings()
        private set
    var enforcing: Boolean = enforcingFlag.exists()
        private set
    private val session = TollSession(engine, stored.settings) { day, event -> events.append(day, event) }

    /** Summaries of finished days, built once by replaying their logs. */
    private val pastDays = HashMap<LocalDate, DaySummary>()

    val decision: Decision? get() = session.decision

    init {
        if (enforcing) {
            val today = today()
            session.restore(events.read(today.minusDays(1)) + events.read(today))
        }
    }

    /** Week 1 starts today; from now on Toll counts and charges. Returns false if it had already started. */
    fun start(): Boolean {
        if (enforcing) return false
        stored = stored.copy(settings = stored.settings.copy(startDate = today()))
        settingsStore.save(stored)
        session.updateSettings(stored.settings)
        enforcingFlag.parentFile?.mkdirs()
        enforcingFlag.writeText(now().toString())
        enforcing = true
        return true
    }

    /** Feeds one event to the engine. Null (and nothing logged) until Toll is started. */
    fun send(event: TollEvent): Decision? {
        if (!enforcing) return null
        matureSettings(event.at)
        return session.send(event)
    }

    /** Starts a quick pass. True when a pass is running afterwards (just started or already running): open Instagram. */
    fun quickPass(): Boolean = send(TollEvent.QuickPassStarted(now()))?.quickPassLeft != null

    /** Tightening applies now; loosening waits 24 hours (rules.Commitments). */
    fun saveSettings(desired: TollSettings) {
        val plan = Commitments.plan(stored.settings, stored.pending, desired, now())
        stored = StoredSettings(plan.settings, plan.pending)
        settingsStore.save(stored)
        session.updateSettings(stored.settings)
    }

    /** When something changes next without a new event: the engine's next change or a pending setting's 24 h. */
    fun nextWakeUp(): Instant? =
        listOfNotNull(session.decision?.nextChangeAt?.takeIf { enforcing }, stored.pending.minOfOrNull { it.effectiveAt }).minOrNull()

    fun home(serviceOn: Boolean): HomeState {
        val today = today()
        val todayDecision = session.decision?.takeIf { enforcing && it.day == today }
        return HomeState(
            serviceOn = serviceOn,
            enforcing = enforcing,
            today = todayDecision,
            todayDate = today,
            settings = stored.settings,
            history = history(today, todayDecision),
            pending = stored.pending,
        )
    }

    private fun matureSettings(at: Instant) {
        if (stored.pending.none { it.effectiveAt <= at }) return
        val plan = Commitments.mature(stored.settings, stored.pending, at)
        stored = StoredSettings(plan.settings, plan.pending)
        settingsStore.save(stored)
        session.updateSettings(stored.settings)
    }

    private fun history(today: LocalDate, decision: Decision?): List<DaySummary> {
        val days = events.days().filter { it > today.minusDays(HISTORY_DAYS) && it < today }
        val past = days.map { day -> pastDays.getOrPut(day) { summarise(day) } }
        val current = decision?.let { DaySummary(today, it.paidToday, it.limit, it.opensToday) }
        return past + listOfNotNull(current)
    }

    private fun summarise(day: LocalDate): DaySummary {
        val state = engine.replay(events.read(day), stored.settings)
        return DaySummary(day, state.paidToday, Limits.limitFor(day, stored.settings), state.opensToday)
    }

    private fun today(): LocalDate = Limits.tollDay(now(), zone, stored.settings.dayStartHour)

    /** Defaults, with week 1 provisionally starting today; [start] moves it to the real start day. */
    private fun firstRunSettings(): StoredSettings {
        val defaults = TollSettings(startDate = LocalDate.EPOCH)
        return StoredSettings(defaults.copy(startDate = Limits.tollDay(now(), zone, defaults.dayStartHour)), emptyList())
    }

    private companion object {
        const val HISTORY_DAYS = 14L
    }
}
