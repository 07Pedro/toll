package com.petr.toll.ui

import com.petr.toll.rules.Price
import com.petr.toll.rules.Sentences
import java.time.Duration

/** "45 min", "3 h", "2 h 14 min". */
fun Duration.short(): String {
    val total = toMinutes().coerceAtLeast(0)
    val h = total / 60
    val m = total % 60
    return when {
        h == 0L -> "$m min"
        m == 0L -> "$h h"
        else -> "$h h $m min"
    }
}

/** "2:41". */
fun Duration.clock(): String {
    val s = seconds.coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/** How a price reads in a sentence: "a 4-minute hold". */
fun Price.summary(): String = when (this) {
    Price.None -> "nothing"
    is Price.Typing -> if (Sentences.isLong(sentence)) "typing a longer sentence" else "typing a sentence"
    is Price.QrAndHold -> "a ${hold.toMinutes()}-minute hold"
}
