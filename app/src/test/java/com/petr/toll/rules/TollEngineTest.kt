package com.petr.toll.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class TollEngineTest {
    private val zone = ZoneId.of("Europe/Prague")
    private val monday = LocalDate.of(2026, 10, 5)
    private val engine = TollEngine(zone)

    private fun min(m: Long) = Duration.ofMinutes(m)
    private fun at(day: LocalDate, h: Int, m: Int = 0): Instant = day.atTime(h, m).atZone(zone).toInstant()

    /** Drives the engine like the service would. */
    private inner class Sim(start: Instant, val settings: TollSettings) {
        var state = EngineState()
        var now: Instant = start

        fun send(e: TollEvent) {
            state = engine.reduce(state, e, settings)
            now = e.at
        }

        fun wait(d: Duration) = send(TollEvent.Tick(now + d))
        fun waitMin(m: Long) = wait(min(m))
        fun screen(kind: ScreenKind) = send(TollEvent.Screen(now, kind))
        fun pay() = send(TollEvent.TollPaid(now))
        fun quickPass() = send(TollEvent.QuickPassStarted(now))

        /** Scrolls minute by minute, dismissing "Still here?" whenever it shows. */
        fun scroll(minutes: Int) = repeat(minutes) {
            waitMin(1)
            if (d.stillHere) send(TollEvent.StillHereDismissed(now))
        }

        val d: Decision get() = engine.decide(state, settings)
        val paid: Duration get() = state.paidToday
    }

    private fun sim(start: Instant, settings: TollSettings = TollSettings(startDate = monday)) = Sim(start, settings)

    /** A 60-minute limit keeps the numbers small: typing from 30, grey from 45, over from 60. */
    private fun smallLimit() = TollSettings(startDate = monday, weekdayStartLimit = min(60))

    @Test fun underHalfTheLimitInstagramIsFree() {
        val sim = sim(at(monday, 10))
        sim.screen(ScreenKind.PAID)
        assertNull(sim.d.gate)
        sim.waitMin(30)
        assertEquals(min(30), sim.paid)
        assertFalse("no timer under half the limit", sim.d.showTimer)
        assertEquals(1, sim.state.opensToday)
    }

    @Test fun theTimerAppearsAtHalfTheLimitButNotDuringAQuickPass() {
        val sim = sim(at(monday, 10))
        sim.screen(ScreenKind.PAID)
        sim.waitMin(89)
        assertFalse(sim.d.showTimer)
        sim.waitMin(1)
        assertTrue(sim.d.showTimer)

        sim.screen(ScreenKind.FREE)
        assertFalse("never in chats or stories", sim.d.showTimer)

        sim.screen(ScreenKind.OUTSIDE)
        sim.waitMin(30)
        sim.quickPass()
        sim.screen(ScreenKind.PAID)
        assertFalse(sim.d.showTimer)
        assertNotNull(sim.d.quickPassLeft)
    }

    @Test fun chatsAndStoriesDontCount() {
        val sim = sim(at(monday, 10))
        sim.screen(ScreenKind.FREE)
        sim.waitMin(20)
        assertEquals(Duration.ZERO, sim.paid)
        assertEquals(0, sim.state.opensToday)
        assertFalse(sim.d.showTimer)
    }

    @Test fun crossingHalfMidVisitChangesNothingButTheNextVisitPaysByTyping() {
        val sim = sim(at(monday, 10))
        sim.screen(ScreenKind.PAID)
        sim.waitMin(100)
        assertNull(sim.d.gate)
        assertEquals(min(100), sim.paid)

        sim.screen(ScreenKind.OUTSIDE)
        sim.waitMin(60)
        sim.screen(ScreenKind.PAID)
        val gate = sim.d.gate
        assertNotNull(gate)
        assertEquals(Price.Typing("Opening Instagram for the 2nd time today."), gate!!.price)
        assertFalse(gate.reflex)

        // Time behind the gate doesn't count.
        sim.waitMin(5)
        assertEquals(min(100), sim.paid)
        sim.pay()
        assertNull(sim.d.gate)
        sim.waitMin(5)
        assertEquals(min(105), sim.paid)
    }

    @Test fun from75PercentInstagramIsGreyAndAsksStillHereEvery5Minutes() {
        val sim = sim(at(monday, 10))
        sim.screen(ScreenKind.PAID)
        sim.waitMin(135)
        assertTrue(sim.d.greyscale)
        assertFalse(sim.d.stillHere)

        sim.waitMin(10)
        assertTrue(sim.d.stillHere)
        assertEquals(min(140), sim.paid) // stopped counting while "Still here?" was up

        sim.send(TollEvent.StillHereDismissed(sim.now))
        assertFalse(sim.d.stillHere)
        sim.waitMin(3)
        assertEquals(min(143), sim.paid)
    }

    @Test fun overTheLimitEach10MinutesCostsAHoldThatDoubles() {
        val sim = sim(at(monday, 10), smallLimit())
        sim.screen(ScreenKind.PAID)
        sim.scroll(60)
        assertEquals(min(60), sim.paid)
        assertEquals(Price.QrAndHold(min(1)), sim.d.gate?.price)

        sim.waitMin(5)
        assertEquals(min(60), sim.paid)
        sim.pay()
        assertEquals(min(10), sim.d.passLeft)

        sim.scroll(10)
        assertEquals(min(70), sim.paid)
        assertEquals(Price.QrAndHold(min(2)), sim.d.gate?.price)
        sim.pay()
        sim.scroll(10)
        assertEquals(Price.QrAndHold(min(4)), sim.d.gate?.price)
        assertEquals(1, sim.state.opensToday)
    }

    @Test fun comingBackWithin10MinutesPaysTheNextTier() {
        val sim = sim(at(monday, 10))
        sim.screen(ScreenKind.PAID)
        sim.waitMin(10)
        sim.screen(ScreenKind.OUTSIDE)
        sim.waitMin(5)
        sim.screen(ScreenKind.PAID)
        val gate = sim.d.gate
        assertNotNull(gate)
        assertTrue(gate!!.reflex)
        assertEquals(Price.Typing(Sentences.short(2)), gate.price)

        sim.pay()
        sim.waitMin(1)
        sim.screen(ScreenKind.OUTSIDE)
        sim.waitMin(20)
        sim.screen(ScreenKind.PAID)
        assertNull(sim.d.gate)
    }

    @Test fun reflexOverTheLimitAddsADoubling() {
        val sim = sim(at(monday, 10), smallLimit())
        sim.screen(ScreenKind.PAID)
        sim.scroll(60)
        sim.pay()
        sim.scroll(10)
        assertEquals(Price.QrAndHold(min(2)), sim.d.gate?.price)

        sim.screen(ScreenKind.OUTSIDE)
        sim.waitMin(2)
        sim.screen(ScreenKind.PAID)
        val gate = sim.d.gate!!
        assertTrue(gate.reflex)
        assertEquals(Price.QrAndHold(min(4)), gate.price)
    }

    @Test fun movingBetweenChatsAndFeedInOneVisitChargesOnce() {
        val sim = sim(at(monday, 10), smallLimit())
        sim.screen(ScreenKind.PAID)
        sim.waitMin(30)
        sim.screen(ScreenKind.OUTSIDE)
        sim.waitMin(60)

        sim.screen(ScreenKind.FREE)
        assertNull(sim.d.gate)
        sim.screen(ScreenKind.PAID)
        assertNotNull(sim.d.gate)
        sim.pay()
        sim.screen(ScreenKind.FREE)
        sim.waitMin(3)
        sim.screen(ScreenKind.PAID)
        assertNull(sim.d.gate)
        assertEquals(2, sim.state.opensToday)
    }

    @Test fun leavingForUnder30SecondsKeepsTheVisit() {
        val sim = sim(at(monday, 10), smallLimit())
        sim.screen(ScreenKind.PAID)
        sim.waitMin(30)
        sim.screen(ScreenKind.OUTSIDE)
        sim.waitMin(60)
        sim.screen(ScreenKind.PAID)
        sim.pay()

        sim.screen(ScreenKind.OUTSIDE)
        sim.wait(Duration.ofSeconds(20))
        sim.screen(ScreenKind.PAID)
        assertNull(sim.d.gate)

        sim.waitMin(1)
        sim.screen(ScreenKind.OUTSIDE)
        sim.wait(Duration.ofSeconds(40))
        assertNull(sim.state.visit)
        sim.screen(ScreenKind.PAID)
        val price = sim.d.gate?.price
        assertTrue("reflex in the typing tier pays the longer sentence", price is Price.Typing && price.sentence.contains("already spent"))
    }

    @Test fun turningTheScreenOffEndsTheVisit() {
        val sim = sim(at(monday, 10))
        sim.screen(ScreenKind.PAID)
        sim.waitMin(10)
        sim.send(TollEvent.ScreenOff(sim.now))
        assertNull(sim.state.visit)
        sim.wait(Duration.ofSeconds(10))
        sim.send(TollEvent.ScreenOn(sim.now))
        sim.screen(ScreenKind.PAID)
        assertTrue(sim.d.gate!!.reflex)
        assertEquals(2, sim.state.opensToday)
    }

    @Test fun unrecognisedScreensCountButAreNeverGated() {
        val sim = sim(at(monday, 10), smallLimit())
        sim.screen(ScreenKind.UNKNOWN)
        sim.waitMin(70)
        assertEquals(min(70), sim.paid)
        assertNull(sim.d.gate)
        assertFalse(sim.d.stillHere)
        assertTrue(sim.d.greyscale)

        sim.screen(ScreenKind.PAID)
        assertEquals(Price.QrAndHold(min(1)), sim.d.gate?.price)
    }

    @Test fun quickPassSkipsEverythingFor3MinutesThenTheGateReturns() {
        val sim = sim(at(monday, 10), smallLimit())
        sim.screen(ScreenKind.PAID)
        sim.scroll(60)
        sim.screen(ScreenKind.OUTSIDE)
        sim.waitMin(1)

        sim.quickPass()
        sim.screen(ScreenKind.PAID)
        assertNull(sim.d.gate)
        assertFalse(sim.d.greyscale)
        assertEquals(min(3), sim.d.quickPassLeft)
        assertEquals(2, sim.d.quickPassesLeft)

        sim.waitMin(2)
        assertEquals(min(62), sim.paid) // still counts
        sim.waitMin(1)
        val gate = sim.d.gate!!
        assertFalse("quick-pass visits are never reflex", gate.reflex)
        assertEquals(Price.QrAndHold(min(1)), gate.price)
        assertEquals(min(63), sim.paid)
        assertEquals(1, sim.state.opensToday)
    }

    @Test fun quickPassVisitsDontMakeTheNextVisitReflex() {
        val sim = sim(at(monday, 10))
        sim.quickPass()
        sim.screen(ScreenKind.PAID)
        sim.waitMin(2)
        sim.screen(ScreenKind.OUTSIDE)
        sim.waitMin(2)
        sim.screen(ScreenKind.PAID)
        assertNull(sim.d.gate)
    }

    @Test fun threeQuickPassesADayResetAt4am() {
        val sim = sim(at(monday, 10))
        repeat(3) {
            sim.quickPass()
            sim.waitMin(5)
        }
        assertEquals(0, sim.d.quickPassesLeft)
        sim.quickPass()
        assertNull(sim.d.quickPassLeft)

        sim.send(TollEvent.Tick(at(monday.plusDays(1), 4)))
        assertEquals(3, sim.d.quickPassesLeft)
    }

    @Test fun paidTimeResetsAt4am() {
        val sim = sim(at(monday, 22))
        sim.screen(ScreenKind.UNKNOWN)
        sim.send(TollEvent.Tick(at(monday.plusDays(1), 5)))
        assertEquals(monday.plusDays(1), sim.d.day)
        assertEquals(min(60), sim.paid)
    }

    @Test fun earlySaturdayMorningStillUsesFridaysLimit() {
        val saturday = monday.plusDays(5)
        val sim = sim(at(saturday, 2))
        sim.send(TollEvent.Tick(sim.now))
        assertEquals(min(180), sim.d.limit)
        sim.send(TollEvent.Tick(at(saturday, 5)))
        assertEquals(min(300), sim.d.limit)
    }

    @Test fun nextChangeAtPointsToTheNextThreshold() {
        val sim = sim(at(monday, 10))
        sim.screen(ScreenKind.PAID)
        assertEquals(at(monday, 11, 30), sim.d.nextChangeAt)
    }
}
