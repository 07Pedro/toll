package com.petr.toll.service

import android.view.accessibility.AccessibilityNodeInfo
import com.petr.toll.classifier.ScreenQuery
import com.petr.toll.probe.descendants

/**
 * Answers the classifier from the live screen with one `findAccessibilityNodeInfosByViewId` call per view ID (memoised
 * for this screen read). Instagram does the search in its own process, so this replaces walking hundreds of nodes over
 * IPC (150 ms–1.5 s per read in Phase 0).
 */
class LiveScreenQuery(private val root: AccessibilityNodeInfo, override val windowClass: String?) : ScreenQuery {
    private val idPrefix = "${root.packageName}:id/"
    private val found = HashMap<String, List<AccessibilityNodeInfo>>()

    /** Number of lookups this read needed; logged to keep an eye on speed. */
    val lookups: Int get() = found.size

    private fun visible(viewId: String): List<AccessibilityNodeInfo> = found.getOrPut(viewId) {
        root.findAccessibilityNodeInfosByViewId(idPrefix + viewId).filter { it.isVisibleToUser }
    }

    override fun hasVisible(viewId: String): Boolean = visible(viewId).isNotEmpty()

    override fun isSelected(viewId: String): Boolean =
        visible(viewId).any { node -> node.descendants(maxNodes = 10).any { it.isSelected } }

    override fun texts(viewId: String): List<String> =
        visible(viewId).mapNotNull { (it.text ?: it.contentDescription)?.toString()?.trim()?.takeIf(String::isNotEmpty) }
}
