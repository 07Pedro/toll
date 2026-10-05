package com.petr.toll.classifier

data class Classification(
    val screen: Screen,
    /** Id of the matching rule, or null when nothing matched. */
    val ruleId: String?,
    /** View ID of the selected bottom tab, if any; shown on the probe label. */
    val selectedTab: String?,
)

class ScreenClassifier(private val signatures: Signatures) {

    fun classify(snapshot: ScreenSnapshot): Classification {
        val visible = snapshot.root.walk().filter { it.visible }.toList()
        val ids = visible.mapNotNull { it.viewId }
        val selectedDescs = visible.filter { it.selected }.mapNotNull { it.desc?.let(::normalizeDesc) }
        val tab = selectedTab(snapshot.root)

        val rule = signatures.rules.firstOrNull { matches(it, ids, tab, selectedDescs, snapshot.windowClass) }
        return Classification(rule?.screen ?: Screen.UNKNOWN, rule?.id, tab)
    }

    /**
     * View ID of the selected bottom tab ("feed_tab", "direct_tab"…), or null. Matching by ID rather than description
     * survives redacted dumps and ignores other "selected" nodes such as marquee song titles in DM notes.
     */
    private fun selectedTab(root: UiNode): String? =
        root.walk().firstOrNull { node ->
            node.visible && node.viewId?.endsWith(TAB_SUFFIX) == true && node.walk().any { it.selected }
        }?.viewId

    /**
     * A short hash of the visible author/caption texts. It changes when a different reel or post is on screen,
     * which is how a swipe onward from a DM reel is noticed. Null if none of [Signatures.itemKeyIds] is visible.
     * Only the hash leaves this function, so captions never reach logs.
     */
    fun itemKey(snapshot: ScreenSnapshot): String? {
        val parts = snapshot.root.walk()
            .filter { node -> node.visible && containsAny(node.viewId, signatures.itemKeyIds) }
            .mapNotNull { node -> (node.text ?: node.desc)?.trim()?.takeIf { it.isNotEmpty() } }
            .toList()
        if (parts.isEmpty()) return null
        return parts.joinToString("|").hashCode().toUInt().toString(16)
    }

    private fun matches(rule: Rule, ids: List<String>, tab: String?, selectedDescs: List<String>, windowClass: String?): Boolean {
        if (rule.ids.any { group -> ids.none { id -> containsAny(id, group) } }) return false
        if (rule.notIds.isNotEmpty() && ids.any { id -> containsAny(id, rule.notIds) }) return false
        if (rule.tabIds.isNotEmpty() && rule.tabIds.none { it == tab }) return false
        if (rule.selectedTab.isNotEmpty() &&
            selectedDescs.none { desc -> rule.selectedTab.any { it.equals(desc, ignoreCase = true) } }
        ) return false
        if (rule.windowClass.isNotEmpty() && !containsAny(windowClass, rule.windowClass)) return false
        return true
    }
}

private const val TAB_SUFFIX = "_tab"

internal fun containsAny(value: String?, fragments: List<String>): Boolean =
    value != null && fragments.any { value.contains(it, ignoreCase = true) }
