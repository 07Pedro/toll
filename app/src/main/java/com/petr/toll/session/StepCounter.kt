package com.petr.toll.session

import kotlinx.serialization.Serializable
import java.time.LocalDate

/** Saved between restarts in `files/steps.json`. */
@Serializable
data class StepState(
    /** Toll day these steps belong to (ISO date). */
    val day: String? = null,
    /** Last raw reading of the step counter (steps since the phone booted). */
    val lastTotal: Long? = null,
    val stepsToday: Long = 0,
    /** Tasks already rewarded today. */
    val credited: Int = 0,
)

/**
 * Turns Android's step counter (a running total since boot) into earn-time tasks: every [goal] steps in a Toll day
 * completes one. A total lower than the last one means the phone rebooted, so the new total counts as fresh steps.
 * Pure; the caller persists [state].
 */
class StepCounter(private val goal: Int, var state: StepState = StepState()) {

    /** Returns how many tasks this reading completed (usually 0 or 1). */
    fun onReading(total: Long, day: LocalDate): Int {
        val current = if (state.day == day.toString()) state else StepState(day = day.toString(), lastTotal = state.lastTotal)
        val last = current.lastTotal
        val delta = when {
            last == null -> 0L
            total >= last -> total - last
            else -> total // rebooted
        }
        val steps = current.stepsToday + delta
        val due = ((steps / goal).toInt() - current.credited).coerceAtLeast(0)
        state = current.copy(lastTotal = total, stepsToday = steps, credited = current.credited + due)
        return due
    }

    /** Steps toward the next task today. */
    fun progress(day: LocalDate): Int = if (state.day == day.toString()) (state.stepsToday % goal).toInt() else 0
}
