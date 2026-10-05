package com.petr.toll.probe

import android.content.Context
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Dumps live in the app's private storage. Pull them from the PC with
 * `adb exec-out run-as com.petr.toll tar -cf - files/dumps > dumps.tar` (works for debug builds).
 */
class DumpStore(context: Context) {
    private val dir = File(context.filesDir, "dumps")

    fun count(): Int = dir.listFiles { file -> file.extension == "json" }?.size ?: 0

    fun deleteAll() {
        dir.listFiles()?.forEach(File::delete)
    }

    fun save(dump: DumpFile): File =
        write("${dump.screen}", DumpFormat.json.encodeToString(DumpFile.serializer(), dump))

    /** Counts as a step like a dump does; [step] is 1-based. */
    fun saveSkip(step: Int): File {
        val marker = SkipMarker(step = step, savedAt = LocalDateTime.now().format(SAVED_AT))
        return write("SKIPPED", DumpFormat.json.encodeToString(SkipMarker.serializer(), marker))
    }

    private fun write(label: String, json: String): File {
        dir.mkdirs()
        val stamp = LocalDateTime.now().format(FILE_STAMP)
        return File(dir, "${stamp}_$label.json").apply { writeText(json) }
    }

    companion object {
        private val FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
        val SAVED_AT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    }
}
