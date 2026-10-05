package com.petr.toll.session

import com.petr.toll.rules.PendingChange
import com.petr.toll.rules.SettingsPatch
import com.petr.toll.rules.Taper
import com.petr.toll.rules.TollEvent
import com.petr.toll.rules.TollSettings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** The event log: one JSON-lines file per Toll day under [dir], e.g. `2026-10-05.jsonl`. */
class EventStore(private val dir: File) {

    fun append(day: LocalDate, event: TollEvent) {
        val line = EventCodec.encode(event) ?: return
        dir.mkdirs()
        File(dir, "$day$SUFFIX").appendText(line + "\n")
    }

    fun read(day: LocalDate): List<TollEvent> {
        val file = File(dir, "$day$SUFFIX")
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull(EventCodec::decode)
    }

    /** Days that have a log, oldest first. */
    fun days(): List<LocalDate> =
        dir.listFiles { file -> file.name.endsWith(SUFFIX) }.orEmpty()
            .mapNotNull { runCatching { LocalDate.parse(it.name.removeSuffix(SUFFIX)) }.getOrNull() }
            .sorted()

    private companion object {
        const val SUFFIX = ".jsonl"
    }
}

/** Settings in force plus the loosening changes still waiting out their 24 hours. */
data class StoredSettings(val settings: TollSettings, val pending: List<PendingChange>)

/** Settings live in one small JSON file, written atomically (temp file, then rename). */
class SettingsStore(private val file: File) {

    fun load(): StoredSettings? {
        if (!file.exists()) return null
        val stored = runCatching { json.decodeFromString(SettingsFile.serializer(), file.readText()) }.getOrNull() ?: return null
        return stored.toModel()
    }

    fun save(value: StoredSettings) {
        file.parentFile?.mkdirs()
        val temp = File(file.path + ".tmp")
        temp.writeText(json.encodeToString(SettingsFile.serializer(), SettingsFile.of(value)))
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
    }

    private companion object {
        val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }
    }
}

// File formats. Durations are whole seconds, instants epoch milliseconds, dates ISO strings.

@Serializable
private data class SettingsFile(
    val startDate: String,
    val weekdayStartLimit: Long,
    val weekendStartLimit: Long,
    val floor: Long,
    val taper: TaperFile,
    val dayStartHour: Int,
    val quickPassesPerDay: Int,
    val quickPassLength: Long,
    // Added with earn time (2026-10-05); older files don't have them, so they default.
    val earnPerTask: Long? = null,
    val earnCapPerDay: Long? = null,
    val pending: List<PendingFile> = emptyList(),
) {
    fun toModel(): StoredSettings {
        val defaults = TollSettings(startDate = LocalDate.parse(startDate))
        val settings = defaults.copy(
            weekdayStartLimit = Duration.ofSeconds(weekdayStartLimit),
            weekendStartLimit = Duration.ofSeconds(weekendStartLimit),
            floor = Duration.ofSeconds(floor),
            taper = taper.toModel(),
            dayStartHour = dayStartHour,
            quickPassesPerDay = quickPassesPerDay,
            quickPassLength = Duration.ofSeconds(quickPassLength),
            earnPerTask = earnPerTask?.let(Duration::ofSeconds) ?: defaults.earnPerTask,
            earnCapPerDay = earnCapPerDay?.let(Duration::ofSeconds) ?: defaults.earnCapPerDay,
        )
        return StoredSettings(settings, pending.map { it.toModel() })
    }

    companion object {
        fun of(value: StoredSettings): SettingsFile = with(value.settings) {
            SettingsFile(
                startDate = startDate.toString(),
                weekdayStartLimit = weekdayStartLimit.seconds,
                weekendStartLimit = weekendStartLimit.seconds,
                floor = floor.seconds,
                taper = TaperFile.of(taper),
                dayStartHour = dayStartHour,
                quickPassesPerDay = quickPassesPerDay,
                quickPassLength = quickPassLength.seconds,
                earnPerTask = earnPerTask.seconds,
                earnCapPerDay = earnCapPerDay.seconds,
                pending = value.pending.map(PendingFile::of),
            )
        }
    }
}

/** "subtract": [amount] seconds per week; "multiply": [amount] is the weekly factor. */
@Serializable
private data class TaperFile(val kind: String, val amount: Double) {
    fun toModel(): Taper = when (kind) {
        MULTIPLY -> Taper.Multiply(amount)
        else -> Taper.Subtract(Duration.ofSeconds(amount.toLong()))
    }

    companion object {
        const val SUBTRACT = "subtract"
        const val MULTIPLY = "multiply"

        fun of(taper: Taper): TaperFile = when (taper) {
            is Taper.Subtract -> TaperFile(SUBTRACT, taper.amount.seconds.toDouble())
            is Taper.Multiply -> TaperFile(MULTIPLY, taper.factor)
        }
    }
}

@Serializable
private data class PendingFile(val requestedAt: Long, val effectiveAt: Long, val patch: PatchFile) {
    fun toModel() = PendingChange(Instant.ofEpochMilli(requestedAt), Instant.ofEpochMilli(effectiveAt), patch.toModel())

    companion object {
        fun of(change: PendingChange) =
            PendingFile(change.requestedAt.toEpochMilli(), change.effectiveAt.toEpochMilli(), PatchFile.of(change.patch))
    }
}

@Serializable
private data class PatchFile(
    val weekdayStartLimit: Long? = null,
    val weekendStartLimit: Long? = null,
    val floor: Long? = null,
    val taper: TaperFile? = null,
    val quickPassesPerDay: Int? = null,
    val quickPassLength: Long? = null,
    val earnPerTask: Long? = null,
    val earnCapPerDay: Long? = null,
) {
    fun toModel() = SettingsPatch(
        weekdayStartLimit = weekdayStartLimit?.let(Duration::ofSeconds),
        weekendStartLimit = weekendStartLimit?.let(Duration::ofSeconds),
        floor = floor?.let(Duration::ofSeconds),
        taper = taper?.toModel(),
        quickPassesPerDay = quickPassesPerDay,
        quickPassLength = quickPassLength?.let(Duration::ofSeconds),
        earnPerTask = earnPerTask?.let(Duration::ofSeconds),
        earnCapPerDay = earnCapPerDay?.let(Duration::ofSeconds),
    )

    companion object {
        fun of(patch: SettingsPatch) = PatchFile(
            weekdayStartLimit = patch.weekdayStartLimit?.seconds,
            weekendStartLimit = patch.weekendStartLimit?.seconds,
            floor = patch.floor?.seconds,
            taper = patch.taper?.let(TaperFile::of),
            quickPassesPerDay = patch.quickPassesPerDay,
            quickPassLength = patch.quickPassLength?.seconds,
            earnPerTask = patch.earnPerTask?.seconds,
            earnCapPerDay = patch.earnCapPerDay?.seconds,
        )
    }
}
