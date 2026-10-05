package com.petr.toll.ui.home

import com.petr.toll.rules.Decision
import com.petr.toll.rules.PendingChange
import com.petr.toll.rules.TollSettings
import java.time.Duration
import java.time.LocalDate

/** One finished or ongoing Toll day, for the history chart. */
data class DaySummary(
    val day: LocalDate,
    val paid: Duration,
    val limit: Duration,
    val opens: Int,
)

/** Everything the home screen shows. Filled by the repository; the screen only reads it. */
data class HomeState(
    /** Toll's accessibility service is switched on. */
    val serviceOn: Boolean,
    /** Petr has pressed Start: Toll counts and charges. Before that it only watches. */
    val enforcing: Boolean,
    /** Today so far, or null before Toll has seen Instagram today. */
    val today: Decision?,
    /** Today's Toll day, so the screen can label things even when [today] is null. */
    val todayDate: LocalDate,
    val settings: TollSettings,
    /** Oldest first, up to the last 14 days including today. Days without Instagram may be missing. */
    val history: List<DaySummary>,
    val pending: List<PendingChange>,
)
