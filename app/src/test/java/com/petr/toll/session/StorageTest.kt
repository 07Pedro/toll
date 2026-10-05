package com.petr.toll.session

import com.petr.toll.rules.PendingChange
import com.petr.toll.rules.ScreenKind
import com.petr.toll.rules.SettingsPatch
import com.petr.toll.rules.Taper
import com.petr.toll.rules.TollEngine
import com.petr.toll.rules.TollEvent
import com.petr.toll.rules.TollSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class StorageTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val day = LocalDate.parse("2026-10-05")
    private val at = Instant.parse("2026-10-05T16:00:00Z")

    @Test
    fun `event log keeps events per day in order and skips ticks`() {
        val store = EventStore(File(temp.root, "events"))
        store.append(day, TollEvent.Screen(at, ScreenKind.PAID))
        store.append(day, TollEvent.Tick(at.plusSeconds(5)))
        store.append(day, TollEvent.Screen(at.plusSeconds(60), ScreenKind.OUTSIDE))
        store.append(day.plusDays(1), TollEvent.ScreenOff(at.plusSeconds(86_400)))

        assertEquals(
            listOf(TollEvent.Screen(at, ScreenKind.PAID), TollEvent.Screen(at.plusSeconds(60), ScreenKind.OUTSIDE)),
            store.read(day),
        )
        assertEquals(listOf(day, day.plusDays(1)), store.days())
        assertEquals(emptyList<TollEvent>(), store.read(day.minusDays(1)))
    }

    @Test
    fun `a half-written last line doesn't lose the rest`() {
        val dir = File(temp.root, "events")
        val store = EventStore(dir)
        store.append(day, TollEvent.Screen(at, ScreenKind.FREE))
        File(dir, "$day.jsonl").appendText("{\"type\":\"scr")
        assertEquals(listOf(TollEvent.Screen(at, ScreenKind.FREE)), store.read(day))
    }

    @Test
    fun `settings and pending changes survive a round trip`() {
        val store = SettingsStore(File(temp.root, "settings.json"))
        assertNull(store.load())
        val value = StoredSettings(
            TollSettings(
                startDate = day,
                taper = Taper.Multiply(0.9),
                quickPassLength = Duration.ofSeconds(150),
                earnPerTask = Duration.ofMinutes(5),
                earnCapPerDay = Duration.ofMinutes(20),
            ),
            listOf(
                PendingChange(at, at.plus(Duration.ofHours(24)), SettingsPatch(weekdayStartLimit = Duration.ofMinutes(200))),
                PendingChange(at, at.plus(Duration.ofHours(24)), SettingsPatch(taper = Taper.Subtract(Duration.ofMinutes(10)))),
                PendingChange(at, at.plus(Duration.ofHours(24)), SettingsPatch(earnCapPerDay = Duration.ofMinutes(40))),
            ),
        )
        store.save(value)
        assertEquals(value, store.load())
        store.save(value.copy(pending = emptyList()))
        assertEquals(value.copy(pending = emptyList()), store.load())
    }

    @Test
    fun `settings saved before earn time load with its defaults`() {
        val file = File(temp.root, "settings.json")
        file.writeText(
            """
            {"startDate":"2026-10-05","weekdayStartLimit":10800,"weekendStartLimit":18000,"floor":2700,
             "taper":{"kind":"subtract","amount":1200.0},"dayStartHour":4,"quickPassesPerDay":3,"quickPassLength":180}
            """.trimIndent(),
        )
        val loaded = SettingsStore(file).load()!!.settings
        assertEquals(TollSettings(startDate = day), loaded)
    }

    @Test
    fun `a damaged settings file reads as missing, not as a crash`() {
        val file = File(temp.root, "settings.json").apply { writeText("{ not json") }
        assertNull(SettingsStore(file).load())
    }
}

class TollSessionTest {
    private val zone = ZoneId.of("Europe/Zurich")
    private val settings = TollSettings(startDate = LocalDate.parse("2026-10-05"))
    private val at = Instant.parse("2026-10-05T16:00:00Z")

    @Test
    fun `send logs real events under their Toll day and restore rebuilds the same state`() {
        val logged = mutableListOf<Pair<LocalDate, TollEvent>>()
        val session = TollSession(TollEngine(zone), settings) { d, e -> logged += d to e }
        session.send(TollEvent.Screen(at, ScreenKind.PAID))
        session.send(TollEvent.Tick(at.plusSeconds(600)))
        val live = session.send(TollEvent.Screen(at.plusSeconds(900), ScreenKind.OUTSIDE))

        assertEquals(2, logged.size)
        assertEquals(LocalDate.parse("2026-10-05"), logged.first().first)

        val restored = TollSession(TollEngine(zone), settings) { _, _ -> error("restore must not log") }
        restored.restore(logged.map { it.second })
        assertEquals(live.paidToday, restored.decision!!.paidToday)
        assertEquals(Duration.ofMinutes(15), live.paidToday)
    }

    @Test
    fun `clock follows the monotonic clock, not later wall-clock changes`() {
        var wall = 1_000_000L
        var elapsed = 50L
        val clock = TollClock({ wall }, { elapsed })
        wall += 86_400_000 // someone moves the phone's time a day ahead
        elapsed += 2_000
        assertEquals(Instant.ofEpochMilli(1_002_000), clock.now())
    }
}
