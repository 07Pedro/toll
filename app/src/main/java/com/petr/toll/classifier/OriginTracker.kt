package com.petr.toll.classifier

/**
 * Decides free or paid from the sequence of screens, because a reel opened from a chat looks exactly like the Reels tab.
 * Product rule (PLAN.md): "places you go on purpose are free; anything that feeds you an endless stream costs".
 * - Origins are chosen on purpose: a chat (DM inbox or thread), a profile, Saved. They are free.
 * - A single reel or post opened directly from an origin is free; it turns paid once a different item is on screen
 *   (the user swiped onward). Going back to an origin re-arms it.
 * - A viewer reached any other way (feed, Reels tab, Explore, search) is paid.
 * - Stories and search results are free. Comments belong to the item underneath.
 * - Unrecognised screens (often transition frames) don't change the context.
 */
class OriginTracker {
    private var fromOrigin = false
    private var openedItem: String? = null
    private var last = Access.UNKNOWN

    fun update(screen: Screen, itemKey: String?): Access {
        val access = when (screen) {
            Screen.DM_INBOX, Screen.DM_THREAD, Screen.PROFILE, Screen.SAVED -> {
                fromOrigin = true
                openedItem = null
                Access.FREE
            }
            Screen.STORY -> Access.FREE
            Screen.REELS_VIEWER, Screen.POST -> viewer(itemKey)
            Screen.COMMENTS -> if (last == Access.FREE) Access.FREE else Access.PAID
            Screen.SEARCH -> {
                fromOrigin = false
                openedItem = null
                Access.FREE
            }
            Screen.FEED, Screen.EXPLORE -> {
                fromOrigin = false
                openedItem = null
                Access.PAID
            }
            Screen.UNKNOWN -> Access.UNKNOWN
        }
        if (screen != Screen.UNKNOWN && screen != Screen.COMMENTS) last = access
        return access
    }

    private fun viewer(itemKey: String?): Access {
        if (!fromOrigin) return Access.PAID
        val opened = openedItem
        if (opened == null) {
            // First frames of the viewer: remember the item once it has loaded.
            openedItem = itemKey
            return Access.FREE
        }
        if (itemKey != null && itemKey != opened) {
            fromOrigin = false
            openedItem = null
            return Access.PAID
        }
        return Access.FREE
    }
}
