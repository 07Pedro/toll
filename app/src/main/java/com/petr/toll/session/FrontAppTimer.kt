package com.petr.toll.session

import java.time.Duration
import java.time.Instant

/**
 * Counts time with one app in front and the screen on, for earn-time tasks such as "about 5 minutes of Duolingo".
 * Time accumulates across visits; each full [needed] completes one task. Pure: the caller passes what's in front and
 * when, and schedules a check at [dueAt] so a long, uninterrupted session still completes on time.
 */
class FrontAppTimer(private val packageName: String, private val needed: Duration) {
    private var since: Instant? = null
    private var accumulated: Duration = Duration.ZERO

    /** Reports what's in front now; returns how many tasks were completed since the last call (usually 0 or 1). */
    fun update(front: String?, screenOn: Boolean, at: Instant): Int {
        since?.let { start -> if (at > start) accumulated += Duration.between(start, at) }
        since = if (front == packageName && screenOn) at else null
        val completed = (accumulated.toMillis() / needed.toMillis()).toInt()
        accumulated = accumulated.minus(needed.multipliedBy(completed.toLong()))
        return completed
    }

    /** When the next task completes if the app simply stays in front, or null if it isn't in front. */
    fun dueAt(): Instant? = since?.plus(needed.minus(accumulated))
}
