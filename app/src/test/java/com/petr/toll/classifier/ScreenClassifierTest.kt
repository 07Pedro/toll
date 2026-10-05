package com.petr.toll.classifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Engine behaviour against a small fixed rule set; the real rules are checked in [SignaturesAssetTest]. */
class ScreenClassifierTest {

    private val signatures = Signatures.parse(
        """
        {
          "rules": [
            { "id": "thread", "screen": "DM_THREAD", "ids": [["row_thread_composer_edittext"]] },
            { "id": "post", "screen": "POST", "ids": [["row_feed_photo_profile_name"], ["action_bar_button_back"]] },
            { "id": "grid", "screen": "EXPLORE", "ids": [["explore_grid"]], "notIds": ["tab_bar_hidden"] },
            { "id": "dialog", "screen": "COMMENTS", "windowClass": ["CommentsDialog"] },
            { "id": "reels_tab", "screen": "REELS_VIEWER", "tabIds": ["clips_tab"] },
            { "id": "home_tab", "screen": "FEED", "tabIds": ["feed_tab"] }
          ],
          "itemKeyIds": ["clips_author_username"]
        }
        """.trimIndent(),
    )
    private val classifier = ScreenClassifier(signatures)

    private fun snapshot(vararg nodes: UiNode, windowClass: String? = null) =
        ScreenSnapshot("com.instagram.android", windowClass, UiNode(children = nodes.toList()))

    private fun tab(id: String, selected: Boolean = false) =
        UiNode(viewId = id, children = listOf(UiNode(viewId = "tab_icon", selected = selected)))

    @Test
    fun `first matching rule wins`() {
        val c = classifier.classify(snapshot(UiNode(viewId = "row_thread_composer_edittext"), tab("feed_tab", selected = true)))
        assertEquals(Screen.DM_THREAD, c.screen)
        assertEquals("thread", c.ruleId)
    }

    @Test
    fun `view IDs match exactly, not as fragments`() {
        val c = classifier.classify(snapshot(UiNode(viewId = "row_thread_composer_edittext_container")))
        assertEquals(Screen.UNKNOWN, c.screen)
    }

    @Test
    fun `invisible nodes are ignored`() {
        val c = classifier.classify(
            snapshot(UiNode(viewId = "row_thread_composer_edittext", visible = false), tab("feed_tab", selected = true)),
        )
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
    fun `a tab counts as selected when only its icon is selected`() {
        val c = classifier.classify(snapshot(tab("feed_tab"), tab("clips_tab", selected = true)))
        assertEquals(Screen.REELS_VIEWER, c.screen)
        assertEquals("reels_tab", c.ruleId)
        assertEquals("clips_tab", c.selectedTab)
    }

    @Test
    fun `selected nodes outside the tabs are not a tab`() {
        val song = UiNode(viewId = "pog_music_note_song_title_text", desc = "In The End", selected = true)
        val c = classifier.classify(snapshot(song, tab("feed_tab")))
        assertEquals(Screen.UNKNOWN, c.screen)
        assertNull(c.selectedTab)
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
    fun `only the lookups a decision needs are made`() {
        val query = CountingQuery(SnapshotQuery(snapshot(UiNode(viewId = "row_thread_composer_edittext"))))
        classifier.classify(query)
        assertEquals(listOf("row_thread_composer_edittext"), query.asked)
    }

    @Test
    fun `item key changes with the visible author and ignores hidden pages`() {
        val first = classifier.itemKey(snapshot(UiNode(viewId = "clips_author_username", text = "anna")))
        val same = classifier.itemKey(
            snapshot(
                UiNode(viewId = "clips_author_username", text = "anna"),
                UiNode(viewId = "clips_author_username", text = "ben", visible = false),
            ),
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
            """{ "rules": [ { "id": "a", "screen": "FEED", "tabIds": ["feed_tab"] }, { "id": "a", "screen": "PROFILE", "tabIds": ["profile_tab"] } ] }""",
        )
    }

    private class CountingQuery(private val inner: ScreenQuery) : ScreenQuery by inner {
        val asked = mutableListOf<String>()

        override fun hasVisible(viewId: String): Boolean {
            asked += viewId
            return inner.hasVisible(viewId)
        }
    }
}
