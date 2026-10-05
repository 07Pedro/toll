package com.petr.toll.rules

import java.time.Duration
import java.time.LocalDate

/** How the daily limit shrinks each week. */
sealed class Taper {
    /** Take [amount] off the limit every week. */
    data class Subtract(val amount: Duration) : Taper()

    /** Multiply the limit by [factor] every week. */
    data class Multiply(val factor: Double) : Taper()
}

/**
 * Everything Petr can change in Toll's settings, plus the start date.
 * Defaults are the values Petr approved on 2026-10-04.
 */
data class TollSettings(
    /** Week 1 starts on this day; the taper counts weeks from here. */
    val startDate: LocalDate,
    val weekdayStartLimit: Duration = Duration.ofHours(3),
    val weekendStartLimit: Duration = Duration.ofHours(5),
    val floor: Duration = Duration.ofMinutes(45),
    val taper: Taper = Taper.Subtract(Duration.ofMinutes(20)),
    /** A Toll day runs from this hour to the same hour the next morning. */
    val dayStartHour: Int = 4,
    val quickPassesPerDay: Int = 3,
    val quickPassLength: Duration = Duration.ofMinutes(3),
)

/** Fixed product rules. Not settings: changing these is a code change. */
object Rules {
    /** Leaving Instagram for less than this doesn't end a visit. */
    val VISIT_GRACE: Duration = Duration.ofSeconds(30)

    /** A visit starting this soon after the last visit with paid time is a reflex visit. */
    val REFLEX_WINDOW: Duration = Duration.ofMinutes(10)

    /** Paid time one bought pass covers when over the limit. */
    val PAID_PASS: Duration = Duration.ofMinutes(10)

    val HOLD_BASE: Duration = Duration.ofMinutes(1)
    val HOLD_CAP: Duration = Duration.ofMinutes(30)

    /** Being off the dot longer than this resets the hold to the start. */
    val HOLD_RESET_AFTER: Duration = Duration.ofSeconds(10)

    /** Paid time between "Still here?" screens, from 75% of the limit up. */
    val STILL_HERE_EVERY: Duration = Duration.ofMinutes(5)

    /** The quick pass countdown turns red for this long at the end. */
    val QUICK_PASS_WARNING: Duration = Duration.ofSeconds(30)

    /** Loosening a setting takes effect after this long. */
    val LOOSENING_DELAY: Duration = Duration.ofHours(24)

    /** A request to turn Toll off takes effect after this long. */
    val TURN_OFF_DELAY: Duration = Duration.ofHours(48)
}
