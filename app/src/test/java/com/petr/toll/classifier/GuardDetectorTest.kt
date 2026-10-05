package com.petr.toll.classifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuardDetectorTest {
    private val settings = "com.android.settings"
    private val detector = GuardDetector(
        listOf(
            GuardRule("toll_app_info", GuardPage.PROTECTED, listOf(settings), texts = listOf(listOf("App info"), listOf("Toll"))),
            GuardRule("advanced_protection", GuardPage.ADVANCED_PROTECTION, listOf(settings), texts = listOf(listOf("Advanced Protection"))),
        ),
    )

    private fun page(vararg texts: String, hidden: String? = null) = SnapshotQuery(
        ScreenSnapshot(
            settings,
            null,
            UiNode(children = texts.map { UiNode(text = it) } + listOfNotNull(hidden?.let { UiNode(text = it, visible = false) })),
        ),
    )

    @Test
    fun `Toll's App info page is protected`() {
        assertEquals(GuardMatch("toll_app_info", GuardPage.PROTECTED), detector.detect(settings, page("App info", "Toll", "Uninstall")))
    }

    @Test
    fun `another app's App info page is left alone`() {
        assertNull(detector.detect(settings, page("App info", "Instagram", "Uninstall")))
    }

    @Test
    fun `texts must match whole, visible labels`() {
        assertNull(detector.detect(settings, page("App info", "Tolls and fees")))
        assertNull(detector.detect(settings, page("App info", hidden = "Toll")))
    }

    @Test
    fun `Advanced Protection gets its own kind`() {
        assertEquals(GuardPage.ADVANCED_PROTECTION, detector.detect(settings, page("advanced protection"))?.kind)
    }

    @Test
    fun `other apps are never guarded`() {
        assertNull(detector.detect("com.instagram.android", page("App info", "Toll")))
    }
}
