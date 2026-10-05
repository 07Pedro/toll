package com.petr.toll.probe

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.petr.toll.classifier.Bounds
import com.petr.toll.classifier.ScreenSnapshot
import com.petr.toll.classifier.UiNode

/**
 * Copies the whole accessibility tree into [UiNode]s. The probe walks everything because it doesn't know Instagram's
 * view IDs yet; Phase 1 switches to direct ID lookups once the signatures are confirmed.
 */
object SnapshotReader {
    private const val MAX_NODES = 4000
    private const val MAX_DEPTH = 80

    fun read(root: AccessibilityNodeInfo, windowClass: String?): ScreenSnapshot {
        var count = 0
        val rect = Rect()

        fun convert(node: AccessibilityNodeInfo, depth: Int): UiNode {
            count++
            node.getBoundsInScreen(rect)
            val children = if (depth >= MAX_DEPTH) emptyList() else (0 until node.childCount).mapNotNull { i ->
                if (count >= MAX_NODES) null else node.getChild(i, PREFETCH)?.let { convert(it, depth + 1) }
            }
            return UiNode(
                viewId = node.entryName(),
                className = node.className?.toString(),
                text = node.text?.toString(),
                desc = node.contentDescription?.toString(),
                selected = node.isSelected,
                clickable = node.isClickable,
                scrollable = node.isScrollable,
                visible = node.isVisibleToUser,
                bounds = Bounds(rect.left, rect.top, rect.right, rect.bottom),
                children = children,
            )
        }

        return ScreenSnapshot(root.packageName?.toString().orEmpty(), windowClass, convert(root, 0))
    }
}

/**
 * Ask Android 13+ to send a batch of descendants with each node, instead of one IPC round trip per node.
 * Whole-tree reads took 150 ms–1.5 s without it.
 */
internal const val PREFETCH = AccessibilityNodeInfo.FLAG_PREFETCH_DESCENDANTS_HYBRID

/** "com.instagram.android:id/tab_bar" → "tab_bar". */
internal fun AccessibilityNodeInfo.entryName(): String? = viewIdResourceName?.substringAfter(":id/")

internal fun AccessibilityNodeInfo.descendants(maxNodes: Int = 4000): Sequence<AccessibilityNodeInfo> = sequence {
    val queue = ArrayDeque<AccessibilityNodeInfo>()
    queue.add(this@descendants)
    var seen = 0
    while (queue.isNotEmpty() && seen < maxNodes) {
        val node = queue.removeFirst()
        seen++
        yield(node)
        for (i in 0 until node.childCount) node.getChild(i, PREFETCH)?.let(queue::add)
    }
}
