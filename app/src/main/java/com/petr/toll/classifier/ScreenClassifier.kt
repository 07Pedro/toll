package com.petr.toll.classifier

data class Classification(
    val screen: Screen,
    /** Id of the matching rule, or null when nothing matched. */
    val ruleId: String?,
    /** View ID of the selected bottom tab, if a rule needed to ask; shown on the probe label. */
    val selectedTab: String?,
)

/**
 * First matching rule wins. Conditions are checked cheapest first and stop at the first miss, so on the phone a screen
 * costs only the lookups its rules actually need (1–20, typically 6–8, on Instagram 449).
 */
class ScreenClassifier(private val signatures: Signatures) {
    private val tabIds = signatures.rules.flatMap { it.tabIds }.distinct()

    fun classify(snapshot: ScreenSnapshot): Classification = classify(SnapshotQuery(snapshot))

    fun classify(query: ScreenQuery): Classification {
        val tab = lazy {
            val barHidden = signatures.tabBarId?.let { !query.hasVisible(it) } ?: false
            if (barHidden) null else tabIds.firstOrNull { query.hasVisible(it) && query.isSelected(it) }
        }
        val rule = signatures.rules.firstOrNull { matches(it, query, tab) }
        return Classification(rule?.screen ?: Screen.UNKNOWN, rule?.id, if (tab.isInitialized()) tab.value else null)
    }

    fun itemKey(snapshot: ScreenSnapshot): String? = itemKey(SnapshotQuery(snapshot))

    /**
     * A short hash of the visible author/caption texts. It changes when a different reel or post is on screen,
     * which is how a swipe onward from a DM reel is noticed. Null if none of [Signatures.itemKeyIds] is visible.
     * Only the hash leaves this function, so captions never reach logs.
     */
    fun itemKey(query: ScreenQuery): String? {
        val parts = signatures.itemKeyIds.flatMap(query::texts)
        if (parts.isEmpty()) return null
        return parts.joinToString("|").hashCode().toUInt().toString(16)
    }

    private fun matches(rule: Rule, query: ScreenQuery, tab: Lazy<String?>): Boolean {
        if (rule.windowClass.isNotEmpty() && !containsAny(query.windowClass, rule.windowClass)) return false
        if (rule.ids.any { group -> group.none(query::hasVisible) }) return false
        if (rule.notIds.any(query::hasVisible)) return false
        if (rule.tabIds.isNotEmpty() && tab.value !in rule.tabIds) return false
        return true
    }
}

internal fun containsAny(value: String?, fragments: List<String>): Boolean =
    value != null && fragments.any { value.contains(it, ignoreCase = true) }
