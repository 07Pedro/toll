package com.petr.toll.session

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StepCounterTest {
    private val day = LocalDate.parse("2026-10-05")
    private val counter = StepCounter(goal = 1_000)

    @Test
    fun `every thousand steps in a day completes one task`() {
        assertEquals(0, counter.onReading(10_000, day))
        assertEquals(0, counter.onReading(10_999, day))
        assertEquals(1, counter.onReading(11_000, day))
        assertEquals(2, counter.onReading(13_500, day))
        assertEquals(500, counter.progress(day))
    }

    @Test
    fun `a reboot resets the sensor total without losing steps`() {
        counter.onReading(10_000, day)
        counter.onReading(10_800, day)
        assertEquals(1, counter.onReading(300, day)) // rebooted: 300 new steps on top of 800
        assertEquals(100, counter.progress(day))
    }

    @Test
    fun `a new day starts from zero and rewards again`() {
        counter.onReading(0, day)
        counter.onReading(1_500, day)
        val next = day.plusDays(1)
        assertEquals(0, counter.progress(next))
        assertEquals(0, counter.onReading(2_000, next))
        assertEquals(1, counter.onReading(2_500, next))
    }
}
