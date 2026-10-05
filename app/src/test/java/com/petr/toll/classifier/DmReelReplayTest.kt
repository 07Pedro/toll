package com.petr.toll.classifier

import com.petr.toll.probe.DumpFile
import com.petr.toll.probe.DumpFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.File

/**
 * Replays walkthroughs on Instagram 449: a reel from a chat and the swipe onward (2026-10-04), and a saved item or a
 * friend's post and the scroll onward (2026-10-05).
 */
class DmReelReplayTest {
    private val classifier = ScreenClassifier(Signatures.parse(File("src/main/assets/signatures.json").readText()))

    private fun fixture(path: String): ScreenSnapshot {
        val dump = DumpFormat.json.decodeFromString(DumpFile.serializer(), File("src/test/resources/fixtures/$path.json").readText())
        return ScreenSnapshot("com.instagram.android", dump.windowClass, DumpFormat.toUiNode(dump.root))
    }

    private fun OriginTracker.see(path: String): Access {
        val snapshot = fixture(path)
        return update(classifier.classify(snapshot).screen, classifier.itemKey(snapshot))
    }

    @Test
    fun `reel from a DM is free, the next reel is paid`() {
        val tracker = OriginTracker()
        assertEquals(Access.FREE, tracker.see("DM_THREAD/ig449_dm_thread"))
        assertEquals(Access.FREE, tracker.see("REELS_VIEWER/ig449_dm_reel_opened"))
        assertEquals(Access.PAID, tracker.see("REELS_VIEWER/ig449_dm_reel_after_swipe"))
    }

    @Test
    fun `post shared in a DM opens in the reels viewer and is free`() {
        val tracker = OriginTracker()
        tracker.see("DM_THREAD/ig449_dm_thread")
        assertEquals(Access.FREE, tracker.see("REELS_VIEWER/ig449_dm_shared_post_carousel"))
    }

    @Test
    fun `the swipe changes the item key`() {
        val opened = classifier.itemKey(fixture("REELS_VIEWER/ig449_dm_reel_opened"))
        val next = classifier.itemKey(fixture("REELS_VIEWER/ig449_dm_reel_after_swipe"))
        assertNotEquals(null, opened)
        assertNotEquals(opened, next)
    }

    @Test
    fun `saved item is free, scrolling on to the next is paid`() {
        val tracker = OriginTracker()
        assertEquals(Access.FREE, tracker.see("SAVED/ig449_saved_collections"))
        assertEquals(Access.FREE, tracker.see("SAVED/ig449_saved_collection_grid"))
        assertEquals(Access.FREE, tracker.see("POST/ig449_saved_item_opened"))
        assertEquals(Access.PAID, tracker.see("POST/ig449_saved_list_scrolled_on"))
    }

    @Test
    fun `post from a friend's profile is free, scrolling on is paid`() {
        val tracker = OriginTracker()
        assertEquals(Access.FREE, tracker.see("PROFILE/ig449_friend_profile"))
        assertEquals(Access.FREE, tracker.see("POST/ig449_profile_post_opened"))
        assertEquals(Access.PAID, tracker.see("POST/ig449_profile_post_scrolled_on"))
    }

    @Test
    fun `reels tab is paid`() {
        assertEquals(Access.PAID, OriginTracker().see("REELS_VIEWER/ig449_reels_tab"))
    }
}
