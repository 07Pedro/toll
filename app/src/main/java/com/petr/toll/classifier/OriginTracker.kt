package com.petr.toll.classifier

/**
 * Decides free or paid from the sequence of screens, because a reel opened from a DM looks exactly like the Reels tab.
 * Rules (PLAN.md, "Reel opened from a DM is context, not a screen"):
 * - A reel or post viewer entered directly from a DM screen is free.
 * - It turns paid once a different item is on screen (the user swiped onward).
 * - A viewer reached any other way is paid. Going back to the DM thread re-arms the context.
 * - Stories are always free. Comments belong to the item underneath.
 * - Unrecognised screens (often transition frames) don't change the context.
 *
 * Phase 0 version: no visits or time yet; those arrive with the Phase 1 session tracker.
 */
class OriginTracker {
    private var fromDm = false
    private var dmItem: String? = null
    private var last = Access.UNKNOWN

    fun update(screen: Screen, itemKey: String?): Access {
        val access = when (screen) {
            Screen.DM_INBOX, Screen.DM_THREAD -> {
                fromDm = true
                dmItem = null
                Access.FREE
            }
            Screen.STORY -> Access.FREE
            Screen.REELS_VIEWER, Screen.POST -> viewer(itemKey)
            Screen.COMMENTS -> if (last == Access.FREE) Access.FREE else Access.PAID
            Screen.FEED, Screen.EXPLORE, Screen.PROFILE -> {
                fromDm = false
                dmItem = null
                Access.PAID
            }
            Screen.UNKNOWN -> Access.UNKNOWN
        }
        if (screen != Screen.UNKNOWN && screen != Screen.COMMENTS) last = access
        return access
    }

    private fun viewer(itemKey: String?): Access {
        if (!fromDm) return Access.PAID
        val opened = dmItem
        if (opened == null) {
            // First frames of the viewer: remember the item once it has loaded.
            dmItem = itemKey
            return Access.FREE
        }
        if (itemKey != null && itemKey != opened) {
            fromDm = false
            dmItem = null
            return Access.PAID
        }
        return Access.FREE
    }
}
