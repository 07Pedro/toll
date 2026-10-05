package com.petr.toll.classifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Engine behaviour against a small fixed rule set; the real seeds are checked in [SignaturesAssetTest]. */
class ScreenClassifierTest {

    private val signatures = Signatures.parse(
        """
        {
          "rules": [
            { "id": "thread", "screen": "DM_THREAD", "ids": [["thread_composer"]] },
            { "id": "post", "screen": "POST", "ids": [["row_feed_photo"], ["button_back"]] },
            { "id": "not_feed", "screen": "EXPLORE", "ids": [["grid"]], "notIds": ["tab_bar_hidden"] },
            { "id": "home", "screen": "FEED", "selectedTab": ["Home"] },
            { "id": "dialog", "screen": "COMMENTS", "windowClass": ["CommentsDialog"] },
            { "id": "reels_tab", "screen": "REELS_VIEWER", "tabIds": ["clips_tab"] }
          ],
          "itemKeyIds": ["author"]
        }
        """.trimIndent(),
    )
    private val classifier = ScreenClassifier(signatures)

    private fun snapshot(vararg nodes: UiNode, windowClass: String? = null) =
        ScreenSnapshot("com.instagram.android", windowClass, UiNode(children = nodes.toList()))

    @Test
    fun `first matching rule wins`() {
        val c = classifier.classify(snapshot(UiNode(viewId = "row_thread_composer_edittext"), tab("Home", selected = true)))
        assertEquals(Screen.DM_THREAD, c.screen)
        assertEquals("thread", c.ruleId)
    }

    @Test
    fun `invisible nodes are ignored`() {
        val c = classifier.classify(snapshot(UiNode(viewId = "thread_composer", visible = false), tab("Home", selected = true)))
        assertEquals(Screen.FEED, c.screen)
    }

    @Test
    fun `every ids group must match`() {
        assertEquals(Screen.UNKNOWN, classifier.classify(snapshot(UiNode(viewId = "row_feed_photo_profile_name"))).screen)
        val both = snapshot(UiNode(viewId = "row_feed_photo_profile_name"), UiNode(viewId = "action_bar_button_back"))
        assertEquals(Screen.POST, classifier.classify(both).screen)
    }

    @Test
    fun `notIds excludes a rule`() {
        assertEquals(Screen.EXPLORE, classifier.classify(snapshot(UiNode(viewId = "explore_grid"))).screen)
        val hidden = snapshot(UiNode(viewId = "explore_grid"), UiNode(viewId = "tab_bar_hidden"))
        assertEquals(Screen.UNKNOWN, classifier.classify(hidden).screen)
    }

    @Test
    fun `selected tab matches case-insensitively and ignores state suffix`() {
        val c = classifier.classify(snapshot(tab("home, selected", selected = true), tab("Reels")))
        assertEquals(Screen.FEED, c.screen)
    }

    @Test
    fun `a tab counts as selected when only its icon is selected`() {
        val reels = UiNode(viewId = "clips_tab", desc = "Reels", children = listOf(UiNode(viewId = "tab_icon", selected = true)))
        val c = classifier.classify(snapshot(UiNode(viewId = "feed_tab", desc = "Home"), reels))
        assertEquals(Screen.REELS_VIEWER, c.screen)
        assertEquals("reels_tab", c.ruleId)
        assertEquals("clips_tab", c.selectedTab)
    }

    @Test
    fun `selected marquee text is not a tab`() {
        val song = UiNode(viewId = "pog_music_note_song_title_text", desc = "In The End", selected = true)
        val c = classifier.classify(snapshot(song, UiNode(viewId = "direct_tab", desc = "Message")))
        assertNull(c.selectedTab)
    }

    @Test
    fun `unselected tab does not count`() {
        assertEquals(Screen.UNKNOWN, classifier.classify(snapshot(tab("Home"))).screen)
    }

    @Test
    fun `window class rule`() {
        val c = classifier.classify(snapshot(windowClass = "com.instagram.CommentsDialogFragment"))
        assertEquals(Screen.COMMENTS, c.screen)
    }

    @Test
    fun `nothing matches gives unknown`() {
        val c = classifier.classify(snapshot(UiNode(viewId = "something_else")))
        assertEquals(Screen.UNKNOWN, c.screen)
        assertNull(c.ruleId)
    }

    @Test
    fun `item key changes with the visible author and ignores hidden pages`() {
        val first = classifier.itemKey(snapshot(UiNode(viewId = "clips_author_username", text = "anna")))
        val same = classifier.itemKey(
            snapshot(UiNode(viewId = "clips_author_username", text = "anna"), UiNode(viewId = "clips_author_username", text = "ben", visible = false)),
        )
        val next = classifier.itemKey(snapshot(UiNode(viewId = "clips_author_username", text = "ben")))
        assertEquals(first, same)
        assertNotEquals(first, next)
        assertNull(classifier.itemKey(snapshot(UiNode(viewId = "unrelated", text = "x"))))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rule without a positive condition is rejected`() {
        Signatures.parse("""{ "rules": [ { "id": "all", "screen": "FEED", "notIds": ["x"] } ] }""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `duplicate rule ids are rejected`() {
        Signatures.parse(
            """{ "rules": [ { "id": "a", "screen": "FEED", "selectedTab": ["Home"] }, { "id": "a", "screen": "PROFILE", "selectedTab": ["Profile"] } ] }""",
        )
    }

    private fun tab(desc: String, selected: Boolean = false) = UiNode(viewId = "tab", desc = desc, selected = selected)
}
