package com.petr.toll.classifier

/** Instagram screens Toll tells apart. Whether a viewer screen is free depends on where it was opened from; see [OriginTracker]. */
enum class Screen(val label: String) {
    DM_INBOX("DM inbox"),
    DM_THREAD("DM thread"),
    STORY("Story"),
    REELS_VIEWER("Reels viewer"),
    POST("Post"),
    COMMENTS("Comments"),
    FEED("Feed"),
    EXPLORE("Explore / search"),
    PROFILE("Profile"),
    UNKNOWN("Unknown"),
}

/** UNKNOWN screens are metered as paid but never gated (PLAN.md, failure policy). */
enum class Access { FREE, PAID, UNKNOWN }

data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    companion object {
        val EMPTY = Bounds(0, 0, 0, 0)
    }
}

/**
 * Android-free copy of one accessibility node, so classification runs in JVM tests and on saved dumps.
 * [viewId] is the entry name only: "row_thread_composer_edittext", not "com.instagram.android:id/row_thread_composer_edittext".
 */
data class UiNode(
    val viewId: String? = null,
    val className: String? = null,
    val text: String? = null,
    val desc: String? = null,
    val selected: Boolean = false,
    val clickable: Boolean = false,
    val scrollable: Boolean = false,
    val visible: Boolean = true,
    val bounds: Bounds = Bounds.EMPTY,
    val children: List<UiNode> = emptyList(),
) {
    fun walk(): Sequence<UiNode> = sequence {
        yield(this@UiNode)
        for (child in children) yieldAll(child.walk())
    }
}

data class ScreenSnapshot(
    val packageName: String,
    /** Class name from the last window-state event, e.g. an activity or dialog. */
    val windowClass: String?,
    val root: UiNode,
)

/** "Home, selected" and "Home" are the same tab. */
internal fun normalizeDesc(desc: String): String = desc.substringBefore(',').trim()
