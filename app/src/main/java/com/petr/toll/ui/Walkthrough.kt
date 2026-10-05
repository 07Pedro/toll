package com.petr.toll.ui

/**
 * The current probe walkthrough, in the order saved screens are matched to steps. Each step ends with one
 * Save or one Skip, so the number of saved files is the number of steps done.
 */
object Walkthrough {
    data class Step(val text: String, val note: String? = null)

    const val TITLE = "Re-check"
    const val INTRO = "A few screens Toll still needs to see after the first walkthrough."

    val STEPS = listOf(
        Step("Your feed, scrolled to the very top, with the round story bubbles showing"),
        Step("In a chat, open a reel a friend sent"),
        Step("Swipe up once to the next reel", note = "The panel should turn red: that's paid."),
        Step("A post (not a reel) someone sent, opened from the chat", note = "Skip if you don't have one."),
        Step("A story someone sent, opened from the chat", note = "Skip if you don't have one."),
        Step("Tap a message notification", note = "Skip if none arrives."),
        Step("Tap a likes or comments notification", note = "Skip if you don't have one."),
    )

    val BUTTON_TESTS = listOf(
        "On your feed, scrolled to the top, tap Open a story on Toll's panel, not Instagram's own story bubble. " +
            "Toll should open a friend's story by itself.",
        "Back on the feed, tap Open messages on the panel, not Instagram's messages button.",
    )
}
