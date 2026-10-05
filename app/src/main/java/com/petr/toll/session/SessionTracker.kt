package com.petr.toll.session

import com.petr.toll.classifier.Access
import com.petr.toll.classifier.OriginTracker
import com.petr.toll.classifier.Screen
import com.petr.toll.rules.ScreenKind

/**
 * Turns what the service sees into the rules engine's [ScreenKind]s.
 * - Instagram screens go through [OriginTracker]: a reel or post opened from a DM is free until the next item.
 * - Toll's challenge screen (typing) is free time inside the current visit, so a long challenge doesn't end the visit
 *   and charge again. Toll's own windows over Instagram (gate, timer) aren't apps, so Instagram stays "in front".
 * - Anything else is outside Instagram.
 *
 * Each method returns the new kind, or null when nothing changed and no event needs to be sent.
 */
class SessionTracker {
    private val origin = OriginTracker()
    private var last: ScreenKind? = null

    /** The kind last reported, e.g. for the probe label. */
    val current: ScreenKind? get() = last

    fun instagram(screen: Screen, itemKey: String?): ScreenKind? = emit(
        when (origin.update(screen, itemKey)) {
            Access.FREE -> ScreenKind.FREE
            Access.PAID -> ScreenKind.PAID
            Access.UNKNOWN -> ScreenKind.UNKNOWN
        },
    )

    fun tollChallenge(): ScreenKind? = emit(ScreenKind.FREE)

    fun outside(): ScreenKind? = emit(ScreenKind.OUTSIDE)

    /** Forget the last kind (e.g. after the screen turned off), so the next read is sent even if it's the same. */
    fun reset() {
        last = null
    }

    private fun emit(kind: ScreenKind): ScreenKind? = if (kind == last) null else kind.also { last = it }
}
