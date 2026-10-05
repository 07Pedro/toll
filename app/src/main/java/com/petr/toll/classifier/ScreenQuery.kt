package com.petr.toll.classifier

/**
 * The questions [ScreenClassifier] asks about a screen. Live, each question is one accessibility lookup by exact view
 * ID (fast, no tree walk); in tests and for saved dumps, [SnapshotQuery] answers from a [ScreenSnapshot]. Both answer
 * the same rules, so the fixtures check exactly what runs on the phone.
 */
interface ScreenQuery {
    /** Class name from the last window-state event, e.g. an activity. */
    val windowClass: String?

    /** Whether a visible node has exactly this view ID (entry name, e.g. "feed_tab"). */
    fun hasVisible(viewId: String): Boolean

    /** Whether a visible node with this ID, or a node inside it, is selected. */
    fun isSelected(viewId: String): Boolean

    /** Non-empty texts (or else descriptions) of the visible nodes with this ID. */
    fun texts(viewId: String): List<String>
}

class SnapshotQuery(private val snapshot: ScreenSnapshot) : ScreenQuery {
    private val visible: Map<String, List<UiNode>> =
        snapshot.root.walk().filter { it.visible && it.viewId != null }.groupBy { it.viewId!! }

    override val windowClass: String? get() = snapshot.windowClass

    override fun hasVisible(viewId: String): Boolean = viewId in visible

    override fun isSelected(viewId: String): Boolean =
        visible[viewId].orEmpty().any { node -> node.walk().any { it.selected } }

    override fun texts(viewId: String): List<String> =
        visible[viewId].orEmpty().mapNotNull { (it.text ?: it.desc)?.trim()?.takeIf(String::isNotEmpty) }
}
