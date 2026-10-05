package com.petr.toll.probe

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.petr.toll.classifier.Navigation
import com.petr.toll.classifier.containsAny

/** What a navigation attempt needs next. */
sealed interface NavResult {
    data class Done(val message: String) : NavResult

    /** The target isn't on this screen; going back once may help (e.g. from a story to the feed). */
    data class NotHere(val message: String) : NavResult

    /** Instagram refused the accessibility click; tap these screen coordinates like a finger instead. */
    data class Tap(val x: Float, val y: Float, val what: String) : NavResult
}

/** Debug versions of the gate's free shortcuts (Messages, Stories). */
class Navigator(private val navigation: Navigation) {

    fun openDms(root: AccessibilityNodeInfo): NavResult {
        val button = root.descendants().firstOrNull { node ->
            node.isVisibleToUser && NavMatch.isDmButton(node.entryName(), node.contentDescription?.toString(), navigation.dmButton)
        } ?: return NavResult.NotHere("Messages button isn't on this screen")
        return click(button, "messages")
    }

    fun openFirstStory(root: AccessibilityNodeInfo): NavResult {
        val tray = root.descendants().firstOrNull { node ->
            node.isVisibleToUser && containsAny(node.entryName(), navigation.storyTray.ids)
        } ?: root.descendants().firstOrNull { node ->
            // Fallback when the tray's ID is unknown: a scrolling row whose items talk about stories.
            node.isVisibleToUser && node.isScrollable &&
                (0 until node.childCount).count { i -> node.getChild(i)?.labels().orEmpty().any { STORY in it.lowercase() } } >= 2
        }
        if (tray == null) {
            Log.i(TAG, "story tray not found; scrollables: ${scrollables(root)}")
            return NavResult.NotHere("No stories bar here: scroll the feed to the very top")
        }
        // Instagram 449: reels_tray_container > unnamed RecyclerView > one item per story bubble.
        val list = tray.descendants(maxNodes = 20).firstOrNull { it.isScrollable } ?: tray
        val items = (0 until list.childCount).mapNotNull { list.getChild(it) }.filter { it.isVisibleToUser }
        val story = items.firstOrNull { item -> !NavMatch.isSkipped(item.labels(), navigation.storyTray.skipDescs) }
            ?: return NavResult.Done("No friend's story in the stories bar (${items.size} bubbles checked)")
        // Instagram 449 sometimes *accepts* the accessibility click on a bubble and then does nothing (seen twice on
        // 2026-10-05), so a click can't be trusted here: always tap like a finger.
        return tapOn(story, "first story")
    }

    /**
     * Tries the accessibility click on [node] and the clickable nodes inside it. Instagram 449 refuses it on story
     * bubbles, so if all refuse, asks for a tap at the centre of the node's avatar (or the node itself). Ancestors are
     * never clicked: a big container's click could do something unrelated.
     */
    private fun click(node: AccessibilityNodeInfo, what: String): NavResult {
        val candidates = node.descendants(maxNodes = 50).filter { it.isClickable }.toList()
        if (candidates.any { it.performAction(AccessibilityNodeInfo.ACTION_CLICK) }) return NavResult.Done("Opened $what")
        Log.i(TAG, "$what: click refused by ${candidates.size} nodes")
        return tapOn(node, what)
    }

    private fun tapOn(node: AccessibilityNodeInfo, what: String): NavResult.Tap {
        val target = node.descendants(maxNodes = 50).firstOrNull { it.entryName() == AVATAR } ?: node
        val rect = Rect().also(target::getBoundsInScreen)
        Log.i(TAG, "$what: tapping $rect")
        return NavResult.Tap(rect.exactCenterX(), rect.exactCenterY(), what)
    }

    /** View IDs, child counts and tops of visible scrolling containers, to find the tray's real ID in the log. */
    private fun scrollables(root: AccessibilityNodeInfo): String {
        val rect = Rect()
        return root.descendants().filter { it.isVisibleToUser && it.isScrollable }.take(8).joinToString { node ->
            node.getBoundsInScreen(rect)
            "${node.entryName()}(${node.childCount})@${rect.top}"
        }
    }

    /** Descriptions and texts of a tray item and its children, for skipping "Your story". */
    private fun AccessibilityNodeInfo.labels(): List<String> =
        descendants(maxNodes = 50).mapNotNull { (it.contentDescription ?: it.text)?.toString() }.toList()
}

private const val TAG = "TollProbe"
private const val STORY = "story"
private const val AVATAR = "avatar_image_view"
