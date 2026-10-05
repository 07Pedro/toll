package com.petr.toll.rules

import java.time.Duration
import java.time.Instant

/** Some of the editable settings. Null means "not part of this change". */
data class SettingsPatch(
    val weekdayStartLimit: Duration? = null,
    val weekendStartLimit: Duration? = null,
    val floor: Duration? = null,
    val taper: Taper? = null,
    val quickPassesPerDay: Int? = null,
    val quickPassLength: Duration? = null,
) {
    val isEmpty: Boolean get() = this == SettingsPatch()

    fun applyTo(s: TollSettings): TollSettings = s.copy(
        weekdayStartLimit = weekdayStartLimit ?: s.weekdayStartLimit,
        weekendStartLimit = weekendStartLimit ?: s.weekendStartLimit,
        floor = floor ?: s.floor,
        taper = taper ?: s.taper,
        quickPassesPerDay = quickPassesPerDay ?: s.quickPassesPerDay,
        quickPassLength = quickPassLength ?: s.quickPassLength,
    )
}

/** A loosening change waiting out its 24 hours. */
data class PendingChange(val requestedAt: Instant, val effectiveAt: Instant, val patch: SettingsPatch)

/** Settings to use now, and the changes still waiting. */
data class ChangePlan(val settings: TollSettings, val pending: List<PendingChange>)

/**
 * Tightening applies at once; loosening waits [Rules.LOOSENING_DELAY].
 * Each field is judged on its own, so one save can do both.
 */
object Commitments {
    /**
     * Petr saved [desired] in Settings while [current] was in force and [pending] was waiting.
     * Only the editable fields of [desired] are read; its start date and day start hour are ignored.
     */
    fun plan(current: TollSettings, pending: List<PendingChange>, desired: TollSettings, now: Instant): ChangePlan {
        val acc = FIELDS.fold(Acc(SettingsPatch(), SettingsPatch(), pending)) { a, f -> f.plan(current, desired, a) }
        val kept = acc.pending.filterNot { it.patch.isEmpty }
        val added = if (acc.loosen.isEmpty) emptyList() else listOf(PendingChange(now, now + Rules.LOOSENING_DELAY, acc.loosen))
        return ChangePlan(acc.tighten.applyTo(current), kept + added)
    }

    /** Folds every pending change that's due by [now] into [base]. */
    fun mature(base: TollSettings, pending: List<PendingChange>, now: Instant): ChangePlan {
        val (due, waiting) = pending.partition { it.effectiveAt <= now }
        return ChangePlan(due.sortedBy { it.effectiveAt }.fold(base) { s, p -> p.patch.applyTo(s) }, waiting)
    }

    fun turnOffAt(requestedAt: Instant): Instant = requestedAt + Rules.TURN_OFF_DELAY

    /** Looser if, in any of the next two years of weeks, it would give a higher limit. */
    fun taperIsLooser(current: TollSettings, new: Taper): Boolean =
        (1..104).any { week ->
            listOf(current.weekdayStartLimit, current.weekendStartLimit).any { start ->
                Limits.limitForWeek(start, week, new, current.floor) > Limits.limitForWeek(start, week, current.taper, current.floor)
            }
        }
}

private data class Acc(val tighten: SettingsPatch, val loosen: SettingsPatch, val pending: List<PendingChange>)

private class Field<T : Any>(
    val read: (TollSettings) -> T,
    val readPatch: (SettingsPatch) -> T?,
    val write: (SettingsPatch, T?) -> SettingsPatch,
    val isLooser: (TollSettings, T) -> Boolean,
) {
    fun plan(current: TollSettings, desired: TollSettings, acc: Acc): Acc {
        val new = read(desired)
        if (new == read(current)) return acc
        // A new value for this field replaces whatever was waiting for it...
        val cleared = acc.pending.map { it.copy(patch = write(it.patch, null)) }
        if (!isLooser(current, new)) return acc.copy(tighten = write(acc.tighten, new), pending = cleared)
        // ...unless it's the same value already waiting, which keeps its timer.
        val waiting = acc.pending.lastOrNull { readPatch(it.patch) != null }?.let { readPatch(it.patch) }
        if (waiting == new) return acc
        return acc.copy(loosen = write(acc.loosen, new), pending = cleared)
    }
}

private val FIELDS: List<Field<*>> = listOf(
    Field<Duration>({ it.weekdayStartLimit }, { it.weekdayStartLimit }, { p, v -> p.copy(weekdayStartLimit = v) }, { c, v -> v > c.weekdayStartLimit }),
    Field<Duration>({ it.weekendStartLimit }, { it.weekendStartLimit }, { p, v -> p.copy(weekendStartLimit = v) }, { c, v -> v > c.weekendStartLimit }),
    Field<Duration>({ it.floor }, { it.floor }, { p, v -> p.copy(floor = v) }, { c, v -> v > c.floor }),
    Field<Taper>({ it.taper }, { it.taper }, { p, v -> p.copy(taper = v) }, { c, v -> Commitments.taperIsLooser(c, v) }),
    Field<Int>({ it.quickPassesPerDay }, { it.quickPassesPerDay }, { p, v -> p.copy(quickPassesPerDay = v) }, { c, v -> v > c.quickPassesPerDay }),
    Field<Duration>({ it.quickPassLength }, { it.quickPassLength }, { p, v -> p.copy(quickPassLength = v) }, { c, v -> v > c.quickPassLength }),
)
