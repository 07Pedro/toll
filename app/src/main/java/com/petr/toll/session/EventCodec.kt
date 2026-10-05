package com.petr.toll.session

import com.petr.toll.rules.ScreenKind
import com.petr.toll.rules.TollEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * One line of the event log. The log is the source of truth: the engine's state is rebuilt from it with
 * `TollEngine.replay` whenever the service starts.
 */
@Serializable
data class EventRecord(
    val type: String,
    /** Epoch milliseconds. */
    val at: Long,
    val kind: ScreenKind? = null,
    /** For time_earned: "pushups", "duolingo", "steps". */
    val task: String? = null,
)

object EventCodec {
    private val json = Json { ignoreUnknownKeys = true }

    /** Null for ticks: they only move time forward, and replay moves time forward with the next real event. */
    fun encode(event: TollEvent): String? {
        val at = event.at.toEpochMilli()
        val record = when (event) {
            is TollEvent.Screen -> EventRecord(SCREEN, at, event.kind)
            is TollEvent.ScreenOff -> EventRecord(SCREEN_OFF, at)
            is TollEvent.ScreenOn -> EventRecord(SCREEN_ON, at)
            is TollEvent.TollPaid -> EventRecord(TOLL_PAID, at)
            is TollEvent.StillHereDismissed -> EventRecord(STILL_HERE_DISMISSED, at)
            is TollEvent.QuickPassStarted -> EventRecord(QUICK_PASS_STARTED, at)
            is TollEvent.TimeEarned -> EventRecord(TIME_EARNED, at, task = event.task)
            is TollEvent.Tick -> return null
        }
        return json.encodeToString(EventRecord.serializer(), record)
    }

    /** Null for lines it can't read (e.g. a half-written last line after a crash), so one bad line never blocks replay. */
    fun decode(line: String): TollEvent? {
        val record = runCatching { json.decodeFromString(EventRecord.serializer(), line) }.getOrNull() ?: return null
        val at = Instant.ofEpochMilli(record.at)
        return when (record.type) {
            SCREEN -> record.kind?.let { TollEvent.Screen(at, it) }
            SCREEN_OFF -> TollEvent.ScreenOff(at)
            SCREEN_ON -> TollEvent.ScreenOn(at)
            TOLL_PAID -> TollEvent.TollPaid(at)
            STILL_HERE_DISMISSED -> TollEvent.StillHereDismissed(at)
            QUICK_PASS_STARTED -> TollEvent.QuickPassStarted(at)
            TIME_EARNED -> record.task?.let { TollEvent.TimeEarned(at, it) }
            else -> null
        }
    }

    private const val SCREEN = "screen"
    private const val SCREEN_OFF = "screen_off"
    private const val SCREEN_ON = "screen_on"
    private const val TOLL_PAID = "toll_paid"
    private const val STILL_HERE_DISMISSED = "still_here_dismissed"
    private const val QUICK_PASS_STARTED = "quick_pass_started"
    private const val TIME_EARNED = "time_earned"
}
