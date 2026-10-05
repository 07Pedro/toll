package com.petr.toll.ui

/**
 * The current probe walkthrough, in the order saved screens are matched to steps. Each step ends with one
 * Save or one Skip, so the number of saved files is the number of steps done.
 */
object Walkthrough {
    data class Step(val text: String, val note: String? = null)

    const val TITLE = "Saved and profiles"
    const val INTRO = "Six screens Toll needs to see for the new free rules."

    val STEPS = listOf(
        Step("Your Saved: the list of collections", note = "Your profile, then the menu (three lines), then Saved."),
        Step("Open one collection, so its grid shows"),
        Step("Open one saved video or post from that grid"),
        Step("Swipe up once to the next saved item", note = "Skip if it doesn't move to another item."),
        Step("On a friend's profile, open one of their posts"),
        Step("Swipe up once to their next post", note = "Skip if it doesn't move to another post."),
    )

    val BUTTON_TESTS = listOf(
        "On your feed, scrolled to the top, tap Open a story on Toll's panel, not Instagram's own story bubble. " +
            "Toll should open a friend's story by itself.",
        "Back on the feed, tap Open messages on the panel, not Instagram's messages button.",
    )
}
