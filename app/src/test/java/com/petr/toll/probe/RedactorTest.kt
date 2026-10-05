package com.petr.toll.probe

import com.petr.toll.classifier.Classification
import com.petr.toll.classifier.NavTarget
import com.petr.toll.classifier.Screen
import com.petr.toll.classifier.ScreenSnapshot
import com.petr.toll.classifier.UiNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactorTest {

    @Test
    fun `normal screens keep short labels and redact sentences`() {
        assertEquals("Like", Redactor.text("Like", strict = false))
        assertEquals("anna.k", Redactor.text("anna.k", strict = false))
        assertEquals("[redacted:27]", Redactor.text("see you at the cinema at 8!", strict = false))
    }

    @Test
    fun `strict screens redact everything except a selected short tab`() {
        assertEquals("[redacted:2]", Redactor.text("ok", strict = true))
        assertEquals("[redacted:6]", Redactor.desc("lol ok", strict = true, selected = false))
        assertEquals("Messages", Redactor.desc("Messages", strict = true, selected = true))
    }

    @Test
    fun `message screens, unknown screens and text fields are strict`() {
        val plain = UiNode(children = listOf(UiNode(className = "android.widget.TextView")))
        val withField = UiNode(children = listOf(UiNode(className = "android.widget.EditText")))
        assertTrue(Redactor.isStrict(Screen.DM_THREAD, plain))
        assertTrue(Redactor.isStrict(Screen.DM_INBOX, plain))
        assertTrue(Redactor.isStrict(Screen.UNKNOWN, plain))
        assertTrue(Redactor.isStrict(Screen.FEED, withField))
        assertFalse(Redactor.isStrict(Screen.FEED, plain))
    }

    @Test
    fun `dump round trip keeps structure and redacts message text`() {
        val tree = UiNode(
            viewId = "root",
            children = listOf(
                UiNode(viewId = "direct_text_message_text_view", text = "meet me at 7"),
                UiNode(viewId = "tab", desc = "Messages", selected = true),
            ),
        )
        val dump = DumpFormat.create(
            ScreenSnapshot("com.instagram.android", "MainActivity", tree),
            Classification(Screen.DM_THREAD, "dm_thread_composer", "Messages"),
            instagramVersion = "1.0",
            savedAt = "2026-10-04T20:00:00",
        )
        val json = DumpFormat.json.encodeToString(DumpFile.serializer(), dump)
        assertFalse(json.contains("meet me"))
        val back = DumpFormat.toUiNode(DumpFormat.json.decodeFromString(DumpFile.serializer(), json).root)
        assertEquals(listOf("root", "direct_text_message_text_view", "tab"), back.walk().map { it.viewId }.toList())
        assertEquals("Messages", back.children[1].desc)
    }

    @Test
    fun `DM button matches by id fragment or description`() {
        val target = NavTarget(ids = listOf("action_bar_inbox_button"), descs = listOf("Messages"))
        assertTrue(NavMatch.isDmButton("action_bar_inbox_button_container", null, target))
        assertTrue(NavMatch.isDmButton(null, "messages, 3 unread", target))
        assertFalse(NavMatch.isDmButton("tab_home", "Home", target))
    }

    @Test
    fun `own story is skipped in the tray`() {
        val skip = listOf("your story", "add to story")
        assertTrue(NavMatch.isSkipped(listOf("Your story"), skip))
        assertFalse(NavMatch.isSkipped(listOf("anna.k's story, unseen"), skip))
    }
}
