package com.petr.toll.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class FrontAppTimerTest {
    private val duo = "com.duolingo"
    private val t0 = Instant.parse("2026-10-05T17:00:00Z")
    private val timer = FrontAppTimer(duo, Duration.ofMinutes(5))

    private fun at(minutes: Double): Instant = t0.plusMillis((minutes * 60_000).toLong())

    @Test
    fun `five minutes in a row complete one task`() {
        assertEquals(0, timer.update(duo, screenOn = true, at = at(0.0)))
        assertEquals(at(5.0), timer.dueAt())
        assertEquals(1, timer.update(duo, screenOn = true, at = at(5.0)))
        assertEquals(at(10.0), timer.dueAt())
    }

    @Test
    fun `time adds up across visits`() {
        timer.update(duo, true, at(0.0))
        assertEquals(0, timer.update("com.instagram.android", true, at(3.0)))
        assertNull(timer.dueAt())
        timer.update(duo, true, at(10.0))
        assertEquals(at(12.0), timer.dueAt())
        assertEquals(1, timer.update("com.android.launcher", true, at(12.5)))
    }

    @Test
    fun `screen off doesn't count`() {
        timer.update(duo, true, at(0.0))
        timer.update(duo, screenOn = false, at = at(2.0))
        assertNull(timer.dueAt())
        assertEquals(0, timer.update(duo, true, at(30.0)))
        assertEquals(at(33.0), timer.dueAt())
    }

    @Test
    fun `a long session completes several tasks - the engine applies the daily cap`() {
        timer.update(duo, true, at(0.0))
        assertEquals(3, timer.update(null, true, at(16.0)))
    }
}
