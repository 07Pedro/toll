package com.petr.toll.session

import com.petr.toll.rules.Commitments
import com.petr.toll.rules.Decision
import com.petr.toll.rules.Limits
import com.petr.toll.rules.ScreenKind
import com.petr.toll.rules.TollEngine
import com.petr.toll.rules.TollEvent
import com.petr.toll.rules.TollSettings
import com.petr.toll.ui.home.DaySummary
import com.petr.toll.ui.home.EarnState
import com.petr.toll.ui.home.HomeState
import com.petr.toll.ui.home.TurnOffState
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Everything [TollRepository] does that isn't Android: settings, the Start switch, the engine session, replay after a
 * restart, earn time from Duolingo and steps, and the home screen's data. Plain Kotlin over files, so JVM tests cover
 * startup and restarts.
 * Not thread-safe: the repository calls it from one thread.
 *
 * Until [start], Toll only watches: [send] ignores every event and nothing is logged or charged. The same holds after a
 * turn-off has taken effect, until [turnBackOn].
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
    private val stepsFile = File(dir, "steps.json")
    private val turnOffFile = File(dir, "turn_off_requested")
    private val offFile = File(dir, "off")

    var stored: StoredSettings = settingsStore.load() ?: firstRunSettings()
        private set
    /** When Petr asked to turn Toll off, while the 24 h wait runs. */
    var turnOffRequestedAt: Instant? = readInstant(turnOffFile)
        private set

    /** When the turn-off took effect; Toll stays off until [turnBackOn]. */
    var offSince: Instant? = readInstant(offFile)
        private set

    /** Started and not turned off: Toll counts, charges and guards its settings pages. */
    var enforcing: Boolean = enforcingFlag.exists() && offSince == null
        private set
    private val session = TollSession(engine, stored.settings) { day, event -> events.append(day, event) }

    // Earn time. Push-ups report themselves through send(TimeEarned).
    private val duolingo = FrontAppTimer(DUOLINGO, DUOLINGO_GOAL)
    private val stepCounter = StepCounter(STEPS_GOAL, loadSteps())
    private var front: String? = null
    private var screenOn = true

    /** Summaries of finished days, built once by replaying their logs. */
    private val pastDays = HashMap<LocalDate, DaySummary>()

    val decision: Decision? get() = session.decision

    init {
        if (enforcing) {
            val today = today()
            session.restore(events.read(today.minusDays(1)) + events.read(today))
        }
        checkTurnOff(now()) // the phone may have been off when the 24 h ran out
    }

    /** Week 1 starts today; from now on Toll counts and charges. Returns false if it had already started. */
    fun start(): Boolean {
        if (enforcingFlag.exists()) return false
        stored = stored.copy(settings = stored.settings.copy(startDate = today()))
        settingsStore.save(stored)
        session.updateSettings(stored.settings)
        enforcingFlag.parentFile?.mkdirs()
        enforcingFlag.writeText(now().toString())
        enforcing = true
        return true
    }

    /**
     * Feeds one event to the engine. Null (and nothing logged) until Toll is started. Every event also brings the
     * Duolingo timer up to date, so a scheduled tick completes a stretch on time.
     */
    fun send(event: TollEvent): Decision? {
        if (event is TollEvent.ScreenOff) screenOn = false
        if (event is TollEvent.ScreenOn) screenOn = true
        checkTurnOff(event.at)
        if (!enforcing) return null
        matureSettings(event.at)
        var decision = session.send(event)
        repeat(duolingo.update(front, screenOn, event.at)) {
            decision = session.send(TollEvent.TimeEarned(event.at, DUOLINGO_TASK))
        }
        return decision
    }

    /** Any app came to the front (null: none known). Feeds the Duolingo timer. */
    fun frontApp(packageName: String?): Decision? {
        front = packageName
        return send(TollEvent.Tick(now()))
    }

    /** A step counter reading (steps since boot). Every 1,000 steps in a Toll day earns a task. */
    fun steps(totalSinceBoot: Long): Decision? {
        if (!enforcing) return null
        val due = stepCounter.onReading(totalSinceBoot, today())
        stepsFile.writeText(json.encodeToString(StepState.serializer(), stepCounter.state))
        var decision = session.decision
        repeat(due) { decision = send(TollEvent.TimeEarned(now(), STEPS_TASK)) }
        return decision
    }

    /** Petr asks to turn Toll off: everything keeps working for 24 h, then Toll stops. False if not possible now. */
    fun requestTurnOff(): Boolean {
        if (!enforcing || turnOffRequestedAt != null) return false
        val at = now()
        turnOffFile.writeText(at.toString())
        turnOffRequestedAt = at
        return true
    }

    fun cancelTurnOff(): Boolean {
        if (turnOffRequestedAt == null) return false
        turnOffFile.delete()
        turnOffRequestedAt = null
        return true
    }

    /** Back on at once, with the same plan and start date. False if Toll isn't off. */
    fun turnBackOn(): Boolean {
        if (offSince == null) return false
        offFile.delete()
        offSince = null
        enforcing = enforcingFlag.exists()
        return enforcing
    }

    /** Switches Toll off once the 24 h after a request are over. True if it switched just now. */
    private fun checkTurnOff(at: Instant): Boolean {
        val requested = turnOffRequestedAt ?: return false
        val due = Commitments.turnOffAt(requested)
        if (at < due) return false
        // Close any visit at the moment Toll stops, so the log ends cleanly.
        if (enforcing) session.send(TollEvent.Screen(due, ScreenKind.OUTSIDE))
        offFile.writeText(due.toString())
        offSince = due
        turnOffFile.delete()
        turnOffRequestedAt = null
        enforcing = false
        return true
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
    fun nextWakeUp(): Instant? = listOfNotNull(
        session.decision?.nextChangeAt?.takeIf { enforcing },
        duolingo.dueAt()?.takeIf { enforcing },
        turnOffRequestedAt?.let(Commitments::turnOffAt),
        stored.pending.minOfOrNull { it.effectiveAt },
    ).minOrNull()

    fun home(serviceOn: Boolean, stepsAllowed: Boolean): HomeState {
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
            earn = EarnState(
                stepsAllowed = stepsAllowed,
                steps = if (enforcing) stepCounter.progress(today) else 0,
                stepsGoal = STEPS_GOAL,
                duolingo = if (enforcing) duolingo.progress(now()) else Duration.ZERO,
                duolingoGoal = DUOLINGO_GOAL,
            ),
            turnOff = turnOffState(),
        )
    }

    fun turnOffState(): TurnOffState {
        val off = offSince
        val requested = turnOffRequestedAt
        return when {
            off != null -> TurnOffState.Off(off)
            requested != null -> TurnOffState.Requested(requested, Commitments.turnOffAt(requested))
            else -> TurnOffState.Running
        }
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

    private fun readInstant(file: File): Instant? =
        runCatching { Instant.parse(file.readText().trim()) }.getOrNull()

    private fun loadSteps(): StepState =
        runCatching { json.decodeFromString(StepState.serializer(), stepsFile.readText()) }.getOrNull() ?: StepState()

    /** Defaults, with week 1 provisionally starting today; [start] moves it to the real start day. */
    private fun firstRunSettings(): StoredSettings {
        val defaults = TollSettings(startDate = LocalDate.EPOCH)
        return StoredSettings(defaults.copy(startDate = Limits.tollDay(now(), zone, defaults.dayStartHour)), emptyList())
    }

    private companion object {
        const val HISTORY_DAYS = 14L
        const val DUOLINGO = "com.duolingo"
        const val DUOLINGO_TASK = "duolingo"
        val DUOLINGO_GOAL: Duration = Duration.ofMinutes(5)
        const val STEPS_TASK = "steps"
        const val STEPS_GOAL = 1_000
        val json = Json { ignoreUnknownKeys = true }
    }
}
