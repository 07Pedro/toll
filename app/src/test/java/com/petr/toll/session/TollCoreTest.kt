package com.petr.toll.session

import com.petr.toll.rules.ScreenKind
import com.petr.toll.rules.TollEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Startup, Start, restarts and settings, against real files: the repository's logic without Android. */
class TollCoreTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val zone = ZoneId.of("Europe/Zurich")
    private var clock = Instant.parse("2026-10-05T16:00:00Z") // 18:00 in Zurich, Toll day 2026-10-05

    private fun core(dir: File = temp.root) = TollCore(zone, { clock }, dir)

    private fun advance(minutes: Long) {
        clock = clock.plus(Duration.ofMinutes(minutes))
    }

    @Test
    fun `first run watches only - nothing is charged or logged`() {
        val core = core()
        assertFalse(core.enforcing)
        assertNull(core.send(TollEvent.Screen(clock, ScreenKind.PAID)))
        assertFalse(core.quickPass())
        val home = core.home(serviceOn = true)
        assertFalse(home.enforcing)
        assertNull(home.today)
        assertEquals(LocalDate.parse("2026-10-05"), home.settings.startDate)
        assertFalse(File(temp.root, "events").exists())
    }

    @Test
    fun `start begins week 1 today and only once`() {
        clock = Instant.parse("2026-10-07T01:00:00Z") // 03:00 in Zurich: still Toll day 2026-10-06
        val core = core()
        assertTrue(core.start())
        assertFalse(core.start())
        assertEquals(LocalDate.parse("2026-10-06"), core.stored.settings.startDate)
        assertNotNull(core.send(TollEvent.Screen(clock, ScreenKind.PAID)))
    }

    @Test
    fun `a restart restores today's state from the log`() {
        val first = core()
        first.start()
        first.send(TollEvent.Screen(clock, ScreenKind.PAID))
        advance(20)
        val live = first.send(TollEvent.Screen(clock, ScreenKind.OUTSIDE))!!

        val second = core()
        assertTrue(second.enforcing)
        assertEquals(live.paidToday, second.decision!!.paidToday)
        assertEquals(Duration.ofMinutes(20), second.decision!!.paidToday)
    }

    @Test
    fun `the first quick pass of the day opens Instagram`() {
        val core = core()
        core.start()
        assertTrue(core.quickPass())
    }

    @Test
    fun `loosening waits 24 hours and wakes the app up when due`() {
        val core = core()
        core.start()
        val looser = core.stored.settings.copy(weekdayStartLimit = Duration.ofHours(4))
        core.saveSettings(looser)
        assertEquals(Duration.ofHours(3), core.stored.settings.weekdayStartLimit)
        assertEquals(1, core.stored.pending.size)
        assertEquals(clock.plus(Duration.ofHours(24)), core.stored.pending.single().effectiveAt)
        assertTrue(core.nextWakeUp()!! <= clock.plus(Duration.ofHours(24)))

        val tighter = core.stored.settings.copy(weekdayStartLimit = Duration.ofHours(2))
        core.saveSettings(tighter)
        assertEquals(Duration.ofHours(2), core.stored.settings.weekdayStartLimit)
    }

    @Test
    fun `history lists past days from their logs plus today`() {
        val core = core()
        core.start()
        core.send(TollEvent.Screen(clock, ScreenKind.PAID))
        advance(30)
        core.send(TollEvent.Screen(clock, ScreenKind.OUTSIDE))
        advance(24 * 60)
        core.send(TollEvent.Screen(clock, ScreenKind.PAID))
        advance(5)
        core.send(TollEvent.Tick(clock))

        val history = core.home(serviceOn = true).history
        assertEquals(listOf(LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-06")), history.map { it.day })
        assertEquals(Duration.ofMinutes(30), history.first().paid)
        assertEquals(Duration.ofMinutes(5), history.last().paid)
    }
}
