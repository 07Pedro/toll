package com.petr.toll.classifier

import com.petr.toll.classifier.Access.FREE
import com.petr.toll.classifier.Access.PAID
import com.petr.toll.classifier.Access.UNKNOWN
import org.junit.Assert.assertEquals
import org.junit.Test

class OriginTrackerTest {
    private val tracker = OriginTracker()

    private fun see(screen: Screen, item: String? = null) = tracker.update(screen, item)

    @Test
    fun `reel opened from a DM is free until the next reel`() {
        assertEquals(FREE, see(Screen.DM_THREAD))
        assertEquals(FREE, see(Screen.REELS_VIEWER, "a"))
        assertEquals(FREE, see(Screen.REELS_VIEWER, "a"))
        assertEquals(PAID, see(Screen.REELS_VIEWER, "b"))
        assertEquals(PAID, see(Screen.REELS_VIEWER, "a"))
    }

    @Test
    fun `item key that loads late is remembered on first sight`() {
        see(Screen.DM_THREAD)
        assertEquals(FREE, see(Screen.REELS_VIEWER, null))
        assertEquals(FREE, see(Screen.REELS_VIEWER, "a"))
        assertEquals(FREE, see(Screen.REELS_VIEWER, null))
        assertEquals(PAID, see(Screen.REELS_VIEWER, "b"))
    }

    @Test
    fun `back to the thread re-arms`() {
        see(Screen.DM_THREAD)
        see(Screen.REELS_VIEWER, "a")
        see(Screen.REELS_VIEWER, "b")
        assertEquals(FREE, see(Screen.DM_THREAD))
        assertEquals(FREE, see(Screen.REELS_VIEWER, "c"))
    }

    @Test
    fun `reels tab and feed reels are paid`() {
        assertEquals(PAID, see(Screen.REELS_VIEWER, "a"))
        see(Screen.DM_INBOX)
        see(Screen.FEED)
        assertEquals(PAID, see(Screen.REELS_VIEWER, "a"))
    }

    @Test
    fun `post from a DM is free like a reel`() {
        see(Screen.DM_THREAD)
        assertEquals(FREE, see(Screen.POST, "p"))
        assertEquals(PAID, see(Screen.POST, "q"))
    }

    @Test
    fun `profiles and Saved are free origins, like a chat`() {
        assertEquals(FREE, see(Screen.PROFILE))
        assertEquals(FREE, see(Screen.POST, "p"))
        assertEquals(PAID, see(Screen.POST, "q"))
        assertEquals(FREE, see(Screen.SAVED))
        assertEquals(FREE, see(Screen.REELS_VIEWER, "r"))
        assertEquals(PAID, see(Screen.REELS_VIEWER, "s"))
    }

    @Test
    fun `search results are free but don't make what's opened from them free`() {
        assertEquals(FREE, see(Screen.SEARCH))
        assertEquals(PAID, see(Screen.REELS_VIEWER, "a"))
    }

    @Test
    fun `unknown transition frames keep the DM context`() {
        see(Screen.DM_THREAD)
        assertEquals(UNKNOWN, see(Screen.UNKNOWN))
        assertEquals(FREE, see(Screen.REELS_VIEWER, "a"))
    }

    @Test
    fun `comments belong to the item underneath`() {
        see(Screen.DM_THREAD)
        see(Screen.REELS_VIEWER, "a")
        assertEquals(FREE, see(Screen.COMMENTS))
        assertEquals(FREE, see(Screen.REELS_VIEWER, "a"))
        see(Screen.FEED)
        assertEquals(PAID, see(Screen.COMMENTS))
    }

    @Test
    fun `stories are always free and keep the context`() {
        assertEquals(FREE, see(Screen.STORY))
        see(Screen.DM_THREAD)
        assertEquals(FREE, see(Screen.STORY))
        assertEquals(FREE, see(Screen.REELS_VIEWER, "a"))
    }
}
