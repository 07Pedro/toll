package com.petr.toll.session

import com.petr.toll.rules.Decision
import com.petr.toll.rules.EngineState
import com.petr.toll.rules.TollEngine
import com.petr.toll.rules.TollEvent
import com.petr.toll.rules.TollSettings
import java.time.Instant
import java.time.LocalDate

/**
 * Holds the rules engine's state for the running app: every event goes through [send], is logged under its Toll day
 * (ticks aren't logged), and produces a fresh [Decision]. Not thread-safe: the repository calls it from one thread.
 */
class TollSession(
    private val engine: TollEngine,
    settings: TollSettings,
    private val log: (day: LocalDate, event: TollEvent) -> Unit,
) {
    var settings: TollSettings = settings
        private set
    var state: EngineState = EngineState()
        private set
    var decision: Decision? = null
        private set

    /** Rebuilds the state from logged events, oldest first. Nothing is logged again. */
    fun restore(events: List<TollEvent>) {
        state = engine.replay(events, settings)
        decision = if (state.clock != null) engine.decide(state, settings) else null
    }

    fun send(event: TollEvent): Decision {
        state = engine.reduce(state, event, settings)
        if (event !is TollEvent.Tick) log(state.day!!, event)
        return engine.decide(state, settings).also { decision = it }
    }

    /** New settings apply from the next event on; call [send] with a tick to see their effect now. */
    fun updateSettings(newSettings: TollSettings) {
        settings = newSettings
    }
}

/**
 * Wall time that moves with the phone's monotonic clock, so changing the phone's time while Toll runs can't skip to a
 * new day or stretch a pass. Re-anchored only when the process starts.
 */
class TollClock(private val wallMillis: () -> Long, private val elapsedMillis: () -> Long) {
    private val anchorWall = wallMillis()
    private val anchorElapsed = elapsedMillis()

    fun now(): Instant = Instant.ofEpochMilli(anchorWall + (elapsedMillis() - anchorElapsed))
}
