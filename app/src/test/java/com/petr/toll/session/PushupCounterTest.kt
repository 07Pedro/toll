package com.petr.toll.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushupCounterTest {
    private val far = 5f
    private val near = 0f
    private val max = 5f
    private val counter = PushupCounter()

    /** One push-up: down (near) at [down], up (far) [nearFor] ms later. */
    private fun rep(down: Long, nearFor: Long): Boolean {
        counter.onReading(near, max, down)
        return counter.onReading(far, max, down + nearFor)
    }

    @Test
    fun `a dip and back up counts one push-up`() {
        counter.onReading(far, max, 0)
        assertTrue(rep(down = 1_000, nearFor = 400))
        assertEquals(1, counter.count)
    }

    @Test
    fun `twenty steady push-ups count twenty`() {
        repeat(20) { i -> rep(down = 1_000L + i * 1_200, nearFor = 400) }
        assertEquals(20, counter.count)
    }

    @Test
    fun `a flicker shorter than the minimum doesn't count`() {
        assertFalse(rep(down = 1_000, nearFor = 50))
        assertEquals(0, counter.count)
    }

    @Test
    fun `reps faster than the minimum gap are ignored`() {
        rep(down = 1_000, nearFor = 200)
        rep(down = 1_300, nearFor = 200)
        assertEquals(1, counter.count)
    }

    @Test
    fun `repeated near readings don't restart the dip`() {
        counter.onReading(near, max, 1_000)
        counter.onReading(near, max, 1_100)
        assertTrue(counter.onReading(far, max, 1_200))
    }

    @Test
    fun `reset starts over`() {
        rep(down = 1_000, nearFor = 400)
        counter.reset()
        assertEquals(0, counter.count)
        assertTrue(rep(down = 1_100, nearFor = 400))
    }
}
