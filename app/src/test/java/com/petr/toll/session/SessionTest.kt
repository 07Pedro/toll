package com.petr.toll.session

import com.petr.toll.classifier.Screen
import com.petr.toll.rules.ScreenKind
import com.petr.toll.rules.TollEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class SessionTrackerTest {
    private val tracker = SessionTracker()

    @Test
    fun `only changes produce events`() {
        assertEquals(ScreenKind.PAID, tracker.instagram(Screen.FEED, null))
        assertNull(tracker.instagram(Screen.FEED, "x"))
        assertNull(tracker.instagram(Screen.EXPLORE, null))
        assertEquals(ScreenKind.FREE, tracker.instagram(Screen.DM_INBOX, null))
        assertEquals(ScreenKind.OUTSIDE, tracker.outside())
        assertNull(tracker.outside())
    }

    @Test
    fun `DM reel is free until the swipe`() {
        tracker.instagram(Screen.DM_THREAD, null)
        assertNull(tracker.instagram(Screen.REELS_VIEWER, "a"))
        assertEquals(ScreenKind.PAID, tracker.instagram(Screen.REELS_VIEWER, "b"))
    }

    @Test
    fun `typing challenge keeps the visit going as free time`() {
        tracker.instagram(Screen.FEED, null)
        assertEquals(ScreenKind.FREE, tracker.tollChallenge())
        assertEquals(ScreenKind.PAID, tracker.instagram(Screen.FEED, null))
    }

    @Test
    fun `unknown screens are reported as unknown`() {
        assertEquals(ScreenKind.UNKNOWN, tracker.instagram(Screen.UNKNOWN, null))
    }
}

class EventCodecTest {
    private val at = Instant.parse("2026-10-05T16:30:00.123Z")

    @Test
    fun `every logged event survives a round trip`() {
        val events = listOf(
            TollEvent.Screen(at, ScreenKind.PAID),
            TollEvent.Screen(at, ScreenKind.OUTSIDE),
            TollEvent.ScreenOff(at),
            TollEvent.ScreenOn(at),
            TollEvent.TollPaid(at),
            TollEvent.StillHereDismissed(at),
            TollEvent.QuickPassStarted(at),
        )
        for (event in events) assertEquals(event, EventCodec.decode(EventCodec.encode(event)!!))
    }

    @Test
    fun `ticks are not logged`() {
        assertNull(EventCodec.encode(TollEvent.Tick(at)))
    }

    @Test
    fun `broken lines are skipped`() {
        assertNull(EventCodec.decode("{\"type\":\"screen\",\"at\":12"))
        assertNull(EventCodec.decode("{\"type\":\"teleport\",\"at\":1}"))
        assertNull(EventCodec.decode("{\"type\":\"screen\",\"at\":1}"))
    }
}
