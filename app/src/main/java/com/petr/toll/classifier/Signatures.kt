package com.petr.toll.classifier

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Screen signatures, loaded from assets/signatures.json. When Instagram changes its UI, this file changes, not the code.
 * Rules are tried top to bottom and the first match wins.
 */
@Serializable
data class Signatures(
    val schemaVersion: Int = 1,
    val notes: String = "",
    /** If set and not visible, no bottom tab is selected: one lookup instead of one per tab. */
    val tabBarId: String? = null,
    val rules: List<Rule>,
    /** View IDs whose text identifies the reel or post on screen (author, caption). Used to notice a swipe onward. */
    val itemKeyIds: List<String> = emptyList(),
    val navigation: Navigation = Navigation(),
    /** System pages the guard watches while Toll is on (see [GuardRule]). */
    val guards: List<GuardRule> = emptyList(),
) {
    init {
        val duplicate = rules.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(duplicate.isEmpty()) { "Duplicate rule ids: $duplicate" }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): Signatures = json.decodeFromString(serializer(), text)
    }
}

/**
 * A rule matches when all of its conditions hold. View IDs are exact entry names ("feed_tab"), because on the phone
 * each one is a direct accessibility lookup.
 * - [ids]: every group needs at least one visible node with one of the group's view IDs.
 * - [notIds]: no visible node has any of these view IDs.
 * - [tabIds]: the selected bottom tab is one of these. A tab counts as selected when it or any node inside it is
 *   selected (Instagram 449 often marks only the tab's icon).
 * - [windowClass]: the last window class contains one of these.
 */
@Serializable
data class Rule(
    val id: String,
    val screen: Screen,
    val ids: List<List<String>> = emptyList(),
    val notIds: List<String> = emptyList(),
    val tabIds: List<String> = emptyList(),
    val windowClass: List<String> = emptyList(),
) {
    init {
        require(ids.isNotEmpty() || tabIds.isNotEmpty() || windowClass.isNotEmpty()) {
            "Rule $id has no positive condition and would match every screen"
        }
        require(ids.none { it.isEmpty() }) { "Rule $id has an empty ids group" }
    }
}

@Serializable
data class Navigation(
    val dmButton: NavTarget = NavTarget(),
    val storyTray: NavTray = NavTray(),
)

@Serializable
data class NavTarget(val ids: List<String> = emptyList(), val descs: List<String> = emptyList())

@Serializable
data class NavTray(val ids: List<String> = emptyList(), val skipDescs: List<String> = emptyList())
