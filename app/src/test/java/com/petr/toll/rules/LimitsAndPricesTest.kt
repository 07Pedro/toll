package com.petr.toll.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class LimitsAndPricesTest {
    private val zone = ZoneId.of("Europe/Prague")
    private val monday = LocalDate.of(2026, 10, 5)
    private val settings = TollSettings(startDate = monday)

    private fun min(m: Long) = Duration.ofMinutes(m)
    private fun weekday(week: Int) = monday.plusDays(7L * (week - 1))
    private fun saturday(week: Int) = monday.plusDays(7L * (week - 1) + 5)

    @Test fun weekdayLimitDrops20MinutesAWeekToTheFloor() {
        val expected = listOf(180L, 160, 140, 120, 100, 80, 60, 45, 45)
        expected.forEachIndexed { i, m -> assertEquals("week ${i + 1}", min(m), Limits.limitFor(weekday(i + 1), settings)) }
    }

    @Test fun weekendLimitReachesTheFloorInWeek14() {
        assertEquals(min(300), Limits.limitFor(saturday(1), settings))
        assertEquals(min(280), Limits.limitFor(saturday(2), settings))
        assertEquals(min(60), Limits.limitFor(saturday(13), settings))
        assertEquals(min(45), Limits.limitFor(saturday(14), settings))
    }

    @Test fun multiplyTaperRoundsToMinutes() {
        val s = settings.copy(taper = Taper.Multiply(0.9))
        assertEquals(min(162), Limits.limitFor(weekday(2), s))
    }

    @Test fun weeksCountFromTheStartDate() {
        assertEquals(1, Limits.weekNumber(monday, monday))
        assertEquals(1, Limits.weekNumber(monday.plusDays(6), monday))
        assertEquals(2, Limits.weekNumber(monday.plusDays(7), monday))
        assertEquals(1, Limits.weekNumber(monday.minusDays(3), monday))
    }

    @Test fun tollDayStartsAt4am() {
        val saturday = LocalDate.of(2026, 10, 10)
        fun at(h: Int, m: Int): Instant = saturday.atTime(h, m).atZone(zone).toInstant()
        assertEquals(saturday.minusDays(1), Limits.tollDay(at(3, 59), zone, 4))
        assertEquals(saturday, Limits.tollDay(at(4, 0), zone, 4))
    }

    @Test fun tiersSplitAtHalfThreeQuartersAndTheLimit() {
        val limit = min(180)
        assertEquals(Tier.FREE, Tier.of(min(89), limit))
        assertEquals(Tier.TYPING, Tier.of(min(90), limit))
        assertEquals(Tier.TYPING, Tier.of(min(134), limit))
        assertEquals(Tier.GREY, Tier.of(min(135), limit))
        assertEquals(Tier.GREY, Tier.of(min(179), limit))
        assertEquals(Tier.OVER, Tier.of(min(180), limit))
    }

    @Test fun holdDoublesAndCapsAt30Minutes() {
        val holds = (1..8).map { Prices.holdFor(it, reflex = false).toMinutes() }
        assertEquals(listOf(1L, 2, 4, 8, 16, 30, 30, 30), holds)
        assertEquals(min(2), Prices.holdFor(1, reflex = true))
        assertEquals(min(30), Prices.holdFor(6, reflex = true))
    }

    @Test fun reflexPaysTheNextTiersPrice() {
        assertEquals(Price.None, Prices.entry(Tier.FREE, false, 1, min(10), 1))
        assertEquals(Price.Typing(Sentences.short(3)), Prices.entry(Tier.FREE, true, 3, min(10), 1))
        assertEquals(Price.Typing(Sentences.long(3, min(100))), Prices.entry(Tier.TYPING, true, 3, min(100), 1))
        assertEquals(Price.QrAndHold(min(1)), Prices.entry(Tier.GREY, true, 3, min(150), 1))
        assertEquals(Price.QrAndHold(min(4)), Prices.entry(Tier.OVER, true, 3, min(200), 2))
    }

    @Test fun sentencesReadNaturally() {
        assertEquals(
            listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd", "101st", "111th", "112th"),
            listOf(1, 2, 3, 4, 11, 12, 13, 21, 22, 101, 111, 112).map { Sentences.ordinal(it) },
        )
        assertEquals("45 minutes", Sentences.spoken(min(45)))
        assertEquals("1 hour", Sentences.spoken(min(60)))
        assertEquals("1 hour and 1 minute", Sentences.spoken(min(61)))
        assertEquals("2 hours and 20 minutes", Sentences.spoken(min(140)))
        assertEquals(
            "Opening Instagram for the 14th time today. I have already spent 2 hours and 20 minutes on it.",
            Sentences.long(14, min(140)),
        )
    }

    @Test fun typingIgnoresCaseAndPunctuationButNotWords() {
        val target = Sentences.short(14)
        assertTrue(Sentences.matches(target, "opening instagram for the 14th time today"))
        assertTrue(Sentences.matches(target, "  Opening   Instagram, for the 14th time today!! "))
        assertFalse(Sentences.matches(target, "Opening Instagram for the 15th time today."))
        assertFalse(Sentences.matches(target, "Opening Instagram today."))
    }

    @Test fun holdPausesOnShortSlipsAndResetsAfter10Seconds() {
        val t0 = Instant.parse("2026-10-05T10:00:00Z")
        fun s(sec: Long): Instant = t0.plusSeconds(sec)
        var hold = Hold(required = Duration.ofSeconds(60)).press(s(0)).release(s(30))
        assertEquals(Duration.ofSeconds(30), hold.progressAt(s(35)))

        // Back on the dot after 5 s: keeps the 30 s.
        hold = hold.press(s(35))
        assertFalse(hold.isComplete(s(64)))
        assertTrue(hold.isComplete(s(65)))

        // Off the dot for 15 s: starts over.
        val slipped = Hold(required = Duration.ofSeconds(60)).press(s(0)).release(s(30))
        assertEquals(Duration.ZERO, slipped.progressAt(s(41)))
        assertEquals(Duration.ofSeconds(5), slipped.press(s(45)).progressAt(s(50)))
    }
}
