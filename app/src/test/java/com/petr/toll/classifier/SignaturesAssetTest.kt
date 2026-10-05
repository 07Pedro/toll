package com.petr.toll.classifier

import com.petr.toll.probe.DumpFile
import com.petr.toll.probe.DumpFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Checks the shipped signatures.json and replays labelled dumps.
 * Fixtures live in src/test/resources/fixtures/<EXPECTED_SCREEN>/<any name>.json, one folder per screen,
 * e.g. fixtures/DM_THREAD/2026-10-05_thread.json. A dump only goes in after a human confirmed what screen it was.
 */
class SignaturesAssetTest {
    // Gradle runs unit tests with the module directory (app/) as the working directory.
    private val signatures = Signatures.parse(File("src/main/assets/signatures.json").readText())

    @Test
    fun `asset parses and covers every screen`() {
        val covered = signatures.rules.map { it.screen }.toSet()
        // SAVED gets its rule from the "Saved and profiles" walkthrough; its view IDs aren't known yet.
        val missing = Screen.entries.filter { it != Screen.UNKNOWN && it != Screen.SAVED } - covered
        assertTrue("No rule for $missing", missing.isEmpty())
    }

    @Test
    fun `DM thread rule comes first, so DMs win over everything`() {
        assertEquals(Screen.DM_THREAD, signatures.rules.first().screen)
    }

    @Test
    fun `labelled dumps classify as labelled`() {
        val root = File("src/test/resources/fixtures")
        val classifier = ScreenClassifier(signatures)
        val failures = root.listFiles { file -> file.isDirectory }.orEmpty().flatMap { folder ->
            val expected = Screen.valueOf(folder.name)
            folder.listFiles { file -> file.extension == "json" }.orEmpty().mapNotNull { file ->
                val dump = DumpFormat.json.decodeFromString(DumpFile.serializer(), file.readText())
                val snapshot = ScreenSnapshot("com.instagram.android", dump.windowClass, DumpFormat.toUiNode(dump.root))
                val actual = classifier.classify(snapshot)
                if (actual.screen == expected) null else "${folder.name}/${file.name}: got ${actual.screen} (rule ${actual.ruleId})"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
