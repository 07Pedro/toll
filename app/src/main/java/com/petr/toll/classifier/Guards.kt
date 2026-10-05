package com.petr.toll.classifier

import kotlinx.serialization.Serializable

/** What the guard does on a matched page. Mirrors ui.overlay.GuardKind. */
enum class GuardPage { PROTECTED, ADVANCED_PROTECTION }

/**
 * A system page the guard watches while Toll is on: Toll's accessibility page, its App info, the uninstall dialog
 * (PROTECTED: notice + Back), and the Advanced Protection page (a notice with Continue; never blocked).
 * A rule matches when the front app is one of [packages] and all conditions hold:
 * - [ids]: every group needs a visible node with one of its exact view IDs;
 * - [texts]: every group needs a visible node whose text or description equals one of its texts.
 */
@Serializable
data class GuardRule(
    val id: String,
    val kind: GuardPage,
    val packages: List<String>,
    val ids: List<List<String>> = emptyList(),
    val texts: List<List<String>> = emptyList(),
) {
    init {
        require(ids.isNotEmpty() || texts.isNotEmpty()) { "Guard rule $id has no condition and would match every page" }
    }
}

data class GuardMatch(val ruleId: String, val kind: GuardPage)

/** Checks guard rules in order; the first match wins. Only the listed pages are ever matched, never Settings at large. */
class GuardDetector(private val rules: List<GuardRule>) {
    fun detect(packageName: String, query: ScreenQuery): GuardMatch? = rules.firstOrNull { rule ->
        packageName in rule.packages &&
            rule.ids.all { group -> group.any(query::hasVisible) } &&
            rule.texts.all { group -> group.any(query::hasText) }
    }?.let { GuardMatch(it.id, it.kind) }
}
