package com.petr.toll.rules

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** What's in front of Petr, as the accessibility service sees it. */
enum class ScreenKind {
    /** Feed, Reels tab, Explore, profiles, swiping onward from a reel opened from a chat. */
    PAID,

    /** Chats, a single post or reel opened from a chat, stories. */
    FREE,

    /** An Instagram screen Toll can't recognise: counted as paid, never gated. */
    UNKNOWN,

    /** Instagram isn't in front. */
    OUTSIDE,
}

/** Everything the service tells the engine. Times come from the caller; the engine never reads a clock. */
sealed class TollEvent {
    abstract val at: Instant

    data class Screen(override val at: Instant, val kind: ScreenKind) : TollEvent()
    data class ScreenOff(override val at: Instant) : TollEvent()
    data class ScreenOn(override val at: Instant) : TollEvent()

    /** Nothing happened; just move time forward. Send one at [Decision.nextChangeAt]. */
    data class Tick(override val at: Instant) : TollEvent()

    /** Petr completed the challenge for the gate on screen. */
    data class TollPaid(override val at: Instant) : TollEvent()
    data class StillHereDismissed(override val at: Instant) : TollEvent()

    /** Petr tapped Quick pass in Toll. Ignored if none are left or one is already running. */
    data class QuickPassStarted(override val at: Instant) : TollEvent()
}

/** One continuous stay in Instagram. */
data class Visit(
    val start: Instant,
    val reflex: Boolean,
    /** Started during a quick pass, or a quick pass was started during it. Ignored for reflex and opens. */
    val quickPass: Boolean,
    /** Counted as an open: reached a paid screen outside a quick pass. */
    val counted: Boolean = false,
    /** The entry price for this visit has been paid (or was free). */
    val entrySettled: Boolean = false,
    /** Paid time left on a bought pass (over the limit). */
    val passBudget: Duration? = null,
    val paid: Duration = Duration.ZERO,
    val sinceStillHere: Duration = Duration.ZERO,
    val stillHereDue: Boolean = false,
)

/** All engine state. Plain data, so the service can persist it and rebuild it by replaying events. */
data class EngineState(
    /** Time of the last event. Null before the first one. */
    val clock: Instant? = null,
    val day: LocalDate? = null,
    val paidToday: Duration = Duration.ZERO,
    val opensToday: Int = 0,
    val passesBoughtToday: Int = 0,
    val quickPassesUsedToday: Int = 0,
    val screenOn: Boolean = true,
    val screen: ScreenKind = ScreenKind.OUTSIDE,
    /** When Instagram last left the front, while the visit's grace period runs. */
    val outsideSince: Instant? = null,
    val visit: Visit? = null,
    /** End of the last visit (not a quick pass one) that had paid time. */
    val lastPaidVisitEnd: Instant? = null,
    val quickPassUntil: Instant? = null,
)

data class Gate(
    val price: Price,
    val reflex: Boolean,
    /** What it would cost to leave and come back within the reflex window. */
    val ifBackSoon: Price,
)

/** What the service should show right now. */
data class Decision(
    val at: Instant,
    val day: LocalDate,
    val limit: Duration,
    val paidToday: Duration,
    val tier: Tier,
    val opensToday: Int,
    /** Non-null: cover Instagram with the gate. */
    val gate: Gate?,
    val stillHere: Boolean,
    val greyscale: Boolean,
    /** Show the timer dial: paid and unrecognised screens, from 50% of the limit, not during a quick pass. */
    val showTimer: Boolean,
    /** Non-null while a quick pass runs: show this countdown instead of the timer. */
    val quickPassLeft: Duration?,
    val quickPassesLeft: Int,
    /** Paid time left on the current bought pass, if any. */
    val passLeft: Duration?,
    /** The next time this decision can change without a new event. Schedule a [TollEvent.Tick] for it. */
    val nextChangeAt: Instant?,
)

/**
 * Toll's rules as a pure state machine: `state = reduce(state, event, settings)`, then
 * `decide(state, settings)`. No Android code and no clock reads, so it runs in JVM tests.
 */
class TollEngine(private val zone: ZoneId) {

    fun reduce(state: EngineState, event: TollEvent, settings: TollSettings): EngineState {
        var s = state
        val clock = s.clock
        if (clock == null || s.day == null) s = s.copy(clock = event.at, day = tollDay(event.at, settings))
        // Out-of-order events are treated as happening now.
        val at = maxOf(event.at, s.clock!!)
        s = advance(s, at, settings)
        s = handle(s, event, at, settings)
        return settle(s, at, settings)
    }

    fun replay(events: Iterable<TollEvent>, settings: TollSettings, from: EngineState = EngineState()): EngineState =
        events.fold(from) { s, e -> reduce(s, e, settings) }

    fun decide(state: EngineState, settings: TollSettings): Decision {
        val at = state.clock ?: error("decide() needs at least one event first")
        val day = state.day!!
        val limit = Limits.limitFor(day, settings)
        val tier = Tier.of(state.paidToday, limit)
        val quickPass = quickPassActive(state, at)
        val visit = state.visit
        val gate = if (visit != null && gateNeeded(state, at, settings)) {
            val price = priceNow(state, settings)
            Gate(price, reflex = !visit.entrySettled && visit.reflex, ifBackSoon = ifBackSoon(state, price, settings))
        } else null
        val inside = inside(state)
        return Decision(
            at = at,
            day = day,
            limit = limit,
            paidToday = state.paidToday,
            tier = tier,
            opensToday = state.opensToday,
            gate = gate,
            stillHere = gate == null && stillHereShowing(state, at),
            greyscale = inside && !quickPass && tier >= Tier.GREY,
            showTimer = inside && !quickPass && tier >= Tier.TYPING &&
                (state.screen == ScreenKind.PAID || state.screen == ScreenKind.UNKNOWN),
            quickPassLeft = state.quickPassUntil?.takeIf { quickPass }?.let { Duration.between(at, it) },
            quickPassesLeft = (settings.quickPassesPerDay - state.quickPassesUsedToday).coerceAtLeast(0),
            passLeft = visit?.passBudget,
            nextChangeAt = nextStop(state, at, settings),
        )
    }

    // ---- time passing ----

    /** Moves time forward to [to], stopping wherever a rule could change what's on screen. */
    private fun advance(state: EngineState, to: Instant, settings: TollSettings): EngineState {
        var s = state
        var now = s.clock!!
        while (now < to) {
            val stop = nextStop(s, now, settings)
            val next = if (stop != null && stop < to) stop else to
            s = accrue(s, now, next, settings).copy(clock = next)
            now = next
            s = settle(s, now, settings)
        }
        return s
    }

    private fun nextStop(s: EngineState, now: Instant, settings: TollSettings): Instant? {
        val stops = mutableListOf(Limits.dayStart(s.day!!.plusDays(1), zone, settings.dayStartHour))
        s.outsideSince?.let { stops += it + Rules.VISIT_GRACE }
        s.quickPassUntil?.let { stops += it }
        val visit = s.visit
        if (visit != null && accruing(s, now, settings)) {
            val limit = Limits.limitFor(s.day, settings)
            for (tier in listOf(Tier.TYPING, Tier.GREY, Tier.OVER)) {
                stops += now + (tier.threshold(limit) - s.paidToday)
            }
            visit.passBudget?.let { stops += now + it }
            if (countsTowardStillHere(s, now, settings)) {
                stops += now + (Rules.STILL_HERE_EVERY - visit.sinceStillHere)
            }
        }
        return stops.filter { it > now }.minOrNull()
    }

    private fun accrue(s: EngineState, from: Instant, to: Instant, settings: TollSettings): EngineState {
        val visit = s.visit
        if (visit == null || !accruing(s, from, settings)) return s
        val d = Duration.between(from, to)
        val stillHereCounting = countsTowardStillHere(s, from, settings)
        return s.copy(
            paidToday = s.paidToday + d,
            visit = visit.copy(
                paid = visit.paid + d,
                passBudget = visit.passBudget?.let { maxOf(it - d, Duration.ZERO) },
                sinceStillHere = if (stillHereCounting) visit.sinceStillHere + d else visit.sinceStillHere,
            ),
        )
    }

    /** Applies everything that follows from the state at [at]: day rollover, visit end, opens, free entry. */
    private fun settle(state: EngineState, at: Instant, settings: TollSettings): EngineState {
        var s = state
        val today = tollDay(at, settings)
        if (today != s.day) {
            s = s.copy(day = today, paidToday = Duration.ZERO, opensToday = 0, passesBoughtToday = 0, quickPassesUsedToday = 0)
        }
        val outsideSince = s.outsideSince
        if (outsideSince != null && at >= outsideSince + Rules.VISIT_GRACE) s = endVisit(s, outsideSince)
        val quickPassUntil = s.quickPassUntil
        if (quickPassUntil != null && at >= quickPassUntil) s = s.copy(quickPassUntil = null)

        var visit = s.visit ?: return s
        if (!inside(s)) return s
        if (s.screen == ScreenKind.PAID && !visit.counted && !visit.quickPass) {
            visit = visit.copy(counted = true)
            s = s.copy(opensToday = s.opensToday + 1)
        }
        if (!visit.stillHereDue && visit.sinceStillHere >= Rules.STILL_HERE_EVERY) visit = visit.copy(stillHereDue = true)
        s = s.copy(visit = visit)
        if (s.screen == ScreenKind.PAID && !visit.entrySettled && !quickPassActive(s, at) &&
            entryPrice(s, settings) == Price.None
        ) {
            s = s.copy(visit = visit.copy(entrySettled = true))
        }
        return s
    }

    // ---- events ----

    private fun handle(s: EngineState, e: TollEvent, at: Instant, settings: TollSettings): EngineState = when (e) {
        is TollEvent.Screen -> onScreen(s, e.kind, at)
        is TollEvent.ScreenOff -> endVisit(s, s.outsideSince ?: at).copy(screenOn = false, screen = ScreenKind.OUTSIDE)
        is TollEvent.ScreenOn -> s.copy(screenOn = true)
        is TollEvent.Tick -> s
        is TollEvent.TollPaid -> onPaid(s, at, settings)
        is TollEvent.StillHereDismissed ->
            s.visit?.let { s.copy(visit = it.copy(stillHereDue = false, sinceStillHere = Duration.ZERO)) } ?: s
        is TollEvent.QuickPassStarted -> onQuickPass(s, at, settings)
    }

    private fun onScreen(s: EngineState, kind: ScreenKind, at: Instant): EngineState {
        if (kind == ScreenKind.OUTSIDE) {
            if (s.visit == null || s.screen == ScreenKind.OUTSIDE) return s.copy(screen = kind, screenOn = true)
            return s.copy(screen = kind, screenOn = true, outsideSince = at)
        }
        val visit = s.visit ?: run {
            val quickPass = quickPassActive(s, at)
            val lastEnd = s.lastPaidVisitEnd
            val reflex = !quickPass && lastEnd != null && Duration.between(lastEnd, at) < Rules.REFLEX_WINDOW
            Visit(start = at, reflex = reflex, quickPass = quickPass)
        }
        return s.copy(screen = kind, screenOn = true, outsideSince = null, visit = visit)
    }

    private fun onPaid(s: EngineState, at: Instant, settings: TollSettings): EngineState {
        val visit = s.visit
        if (visit == null || !gateNeeded(s, at, settings)) return s
        val settled = visit.copy(entrySettled = true, stillHereDue = false, sinceStillHere = Duration.ZERO)
        return when (priceNow(s, settings)) {
            is Price.QrAndHold -> s.copy(
                passesBoughtToday = s.passesBoughtToday + 1,
                visit = settled.copy(passBudget = Rules.PAID_PASS),
            )
            else -> s.copy(visit = settled)
        }
    }

    private fun onQuickPass(s: EngineState, at: Instant, settings: TollSettings): EngineState {
        if (quickPassActive(s, at) || s.quickPassesUsedToday >= settings.quickPassesPerDay) return s
        return s.copy(
            quickPassUntil = at + settings.quickPassLength,
            quickPassesUsedToday = s.quickPassesUsedToday + 1,
            visit = s.visit?.copy(quickPass = true, reflex = false),
        )
    }

    private fun endVisit(s: EngineState, endAt: Instant): EngineState {
        val visit = s.visit ?: return s.copy(outsideSince = null)
        val hadPaidTime = !visit.quickPass && visit.paid > Duration.ZERO
        return s.copy(
            visit = null,
            outsideSince = null,
            lastPaidVisitEnd = if (hadPaidTime) endAt else s.lastPaidVisitEnd,
        )
    }

    // ---- rules ----

    private fun tollDay(at: Instant, settings: TollSettings): LocalDate = Limits.tollDay(at, zone, settings.dayStartHour)

    private fun tier(s: EngineState, settings: TollSettings): Tier = Tier.of(s.paidToday, Limits.limitFor(s.day!!, settings))

    private fun quickPassActive(s: EngineState, at: Instant): Boolean = s.quickPassUntil?.let { at < it } ?: false

    private fun inside(s: EngineState): Boolean = s.screenOn && s.visit != null && s.screen != ScreenKind.OUTSIDE

    private fun gateNeeded(s: EngineState, at: Instant, settings: TollSettings): Boolean {
        val visit = s.visit ?: return false
        if (!inside(s) || s.screen != ScreenKind.PAID || quickPassActive(s, at)) return false
        if (!visit.entrySettled) return true
        return tier(s, settings) == Tier.OVER && (visit.passBudget ?: Duration.ZERO) <= Duration.ZERO
    }

    private fun stillHereShowing(s: EngineState, at: Instant): Boolean =
        inside(s) && s.screen == ScreenKind.PAID && s.visit?.stillHereDue == true && !quickPassActive(s, at)

    /** Paid time runs on paid screens nothing covers, and on unrecognised screens always. */
    private fun accruing(s: EngineState, at: Instant, settings: TollSettings): Boolean {
        if (!inside(s)) return false
        return when (s.screen) {
            ScreenKind.UNKNOWN -> true
            ScreenKind.PAID -> !gateNeeded(s, at, settings) && !stillHereShowing(s, at)
            else -> false
        }
    }

    private fun countsTowardStillHere(s: EngineState, at: Instant, settings: TollSettings): Boolean =
        s.screen == ScreenKind.PAID && !quickPassActive(s, at) && tier(s, settings) >= Tier.GREY

    private fun entryPrice(s: EngineState, settings: TollSettings): Price =
        Prices.entry(
            tier = tier(s, settings),
            reflex = s.visit?.reflex == true,
            openNumber = maxOf(s.opensToday, 1),
            paidToday = s.paidToday,
            nextPassNumber = s.passesBoughtToday + 1,
        )

    private fun priceNow(s: EngineState, settings: TollSettings): Price =
        if (s.visit?.entrySettled == true) Price.QrAndHold(Prices.holdFor(s.passesBoughtToday + 1, reflex = false))
        else entryPrice(s, settings)

    private fun ifBackSoon(s: EngineState, price: Price, settings: TollSettings): Price {
        val passesAfter = s.passesBoughtToday + if (price is Price.QrAndHold) 1 else 0
        return Prices.entry(tier(s, settings), reflex = true, openNumber = s.opensToday + 1, paidToday = s.paidToday, nextPassNumber = passesAfter + 1)
    }
}
