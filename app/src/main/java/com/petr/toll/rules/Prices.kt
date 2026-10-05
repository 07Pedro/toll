package com.petr.toll.rules

import java.time.Duration
import java.time.Instant

/** What getting past the gate costs. */
sealed class Price {
    object None : Price()

    /** Type [sentence] exactly (case and punctuation don't matter). */
    data class Typing(val sentence: String) : Price()

    /** Scan the QR code, then hold a thumb on the moving dot for [hold]. */
    data class QrAndHold(val hold: Duration) : Price()
}

object Prices {
    /**
     * Hold length for the [passNumber]th pass bought today: 1, 2, 4, 8, 16, then 30 minutes.
     * A reflex visit adds one doubling, still capped.
     */
    fun holdFor(passNumber: Int, reflex: Boolean): Duration {
        val doublings = (passNumber - 1 + if (reflex) 1 else 0).coerceIn(0, 30)
        return minOf(Rules.HOLD_BASE.multipliedBy(1L shl doublings), Rules.HOLD_CAP)
    }

    /**
     * Price of entering paid Instagram. A reflex visit pays the next tier's price;
     * over the limit, where there's no next tier, it adds a doubling to the hold instead.
     */
    fun entry(tier: Tier, reflex: Boolean, openNumber: Int, paidToday: Duration, nextPassNumber: Int): Price {
        val charged = if (reflex) tier.next() else tier
        return when (charged) {
            Tier.FREE -> Price.None
            Tier.TYPING -> Price.Typing(Sentences.short(openNumber))
            Tier.GREY -> Price.Typing(Sentences.long(openNumber, paidToday))
            Tier.OVER -> Price.QrAndHold(holdFor(nextPassNumber, reflex && tier == Tier.OVER))
        }
    }
}

object Sentences {
    fun short(openNumber: Int): String = "Opening Instagram for the ${ordinal(openNumber)} time today."

    fun long(openNumber: Int, paidToday: Duration): String =
        "${short(openNumber)} I have already spent ${spoken(paidToday)} on it."

    fun ordinal(n: Int): String {
        val suffix = if (n % 100 in 11..13) "th" else when (n % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
        return "$n$suffix"
    }

    /** "45 minutes", "1 hour", "2 hours and 20 minutes". */
    fun spoken(d: Duration): String {
        val total = d.toMinutes()
        val h = total / 60
        val m = total % 60
        val hours = if (h == 1L) "1 hour" else "$h hours"
        val minutes = if (m == 1L) "1 minute" else "$m minutes"
        return when {
            h == 0L -> minutes
            m == 0L -> hours
            else -> "$hours and $minutes"
        }
    }

    /** Same words in the same order. Case, punctuation and extra spaces don't matter. */
    fun matches(target: String, typed: String): Boolean = normalize(target) == normalize(typed)

    private fun normalize(s: String): String =
        s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
}

/**
 * The moving-dot hold. Lifting the thumb or losing the dot pauses it;
 * being off the dot for more than [Rules.HOLD_RESET_AFTER] resets it to the start.
 */
data class Hold(
    val required: Duration,
    val progress: Duration = Duration.ZERO,
    val pressedSince: Instant? = null,
    val releasedAt: Instant? = null,
) {
    fun press(at: Instant): Hold {
        if (pressedSince != null) return this
        val base = if (releasedAt != null && Duration.between(releasedAt, at) > Rules.HOLD_RESET_AFTER) Duration.ZERO else progress
        return copy(progress = base, pressedSince = at, releasedAt = null)
    }

    fun release(at: Instant): Hold {
        val since = pressedSince ?: return this
        return copy(progress = progress + Duration.between(since, at), pressedSince = null, releasedAt = at)
    }

    fun progressAt(at: Instant): Duration {
        val since = pressedSince
        val raw = when {
            since != null -> progress + Duration.between(since, at)
            releasedAt != null && Duration.between(releasedAt, at) > Rules.HOLD_RESET_AFTER -> Duration.ZERO
            else -> progress
        }
        return minOf(raw, required)
    }

    fun isComplete(at: Instant): Boolean = progressAt(at) >= required
}
