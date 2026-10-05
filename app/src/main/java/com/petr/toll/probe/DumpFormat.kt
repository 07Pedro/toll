package com.petr.toll.probe

import com.petr.toll.classifier.Bounds
import com.petr.toll.classifier.Classification
import com.petr.toll.classifier.Screen
import com.petr.toll.classifier.ScreenSnapshot
import com.petr.toll.classifier.UiNode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One saved screen. Dumps leave the phone (adb pull), so all text goes through [Redactor] first. */
@Serializable
data class DumpFile(
    val savedAt: String,
    val instagramVersion: String? = null,
    val screen: Screen,
    val rule: String? = null,
    val windowClass: String? = null,
    val strictRedaction: Boolean,
    val root: DumpNode,
)

/** Written instead of a dump when Petr skips a walkthrough step, so step numbers stay aligned with files. */
@Serializable
data class SkipMarker(
    val skipped: Boolean = true,
    val step: Int,
    val savedAt: String,
)

@Serializable
data class DumpNode(
    val id: String? = null,
    val cls: String? = null,
    val text: String? = null,
    val desc: String? = null,
    val sel: Boolean = false,
    val clk: Boolean = false,
    val scr: Boolean = false,
    val vis: Boolean = true,
    /** left, top, right, bottom */
    val b: List<Int> = emptyList(),
    val kids: List<DumpNode> = emptyList(),
)

object DumpFormat {
    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    /** [strict] overrides the redaction choice, e.g. false for Settings screens, which hold no messages. */
    fun create(
        snapshot: ScreenSnapshot,
        classification: Classification,
        instagramVersion: String?,
        savedAt: String,
        strict: Boolean = Redactor.isStrict(classification.screen, snapshot.root),
    ): DumpFile {
        return DumpFile(
            savedAt = savedAt,
            instagramVersion = instagramVersion,
            screen = classification.screen,
            rule = classification.ruleId,
            windowClass = snapshot.windowClass,
            strictRedaction = strict,
            root = toDumpNode(snapshot.root, strict),
        )
    }

    /** Back to the classifier's model, so saved dumps can be replayed as test fixtures. */
    fun toUiNode(node: DumpNode): UiNode = UiNode(
        viewId = node.id,
        className = node.cls,
        text = node.text,
        desc = node.desc,
        selected = node.sel,
        clickable = node.clk,
        scrollable = node.scr,
        visible = node.vis,
        bounds = node.b.takeIf { it.size == 4 }?.let { Bounds(it[0], it[1], it[2], it[3]) } ?: Bounds.EMPTY,
        children = node.kids.map(::toUiNode),
    )

    private fun toDumpNode(node: UiNode, strict: Boolean): DumpNode = DumpNode(
        id = node.viewId,
        cls = node.className,
        text = Redactor.text(node.text, strict),
        desc = Redactor.desc(node.desc, strict, node.selected),
        sel = node.selected,
        clk = node.clickable,
        scr = node.scrollable,
        vis = node.visible,
        b = with(node.bounds) { listOf(left, top, right, bottom) },
        kids = node.children.map { toDumpNode(it, strict) },
    )
}

/**
 * Keeps friends' messages out of dumps.
 * Normal screens keep texts of up to [MAX_WORDS] words and [MAX_CHARS] characters (button labels, usernames).
 * Strict screens (messages, anything with a text field, anything unrecognised) keep no text at all,
 * except the selected tab's short description, which the classifier needs.
 */
object Redactor {
    const val MAX_WORDS = 3
    const val MAX_CHARS = 25

    fun isStrict(screen: Screen, root: UiNode): Boolean =
        screen == Screen.DM_INBOX || screen == Screen.DM_THREAD || screen == Screen.UNKNOWN ||
            root.walk().any { it.className?.endsWith("EditText") == true }

    fun text(value: String?, strict: Boolean): String? =
        if (value.isNullOrEmpty() || (!strict && isShort(value))) value else redacted(value)

    fun desc(value: String?, strict: Boolean, selected: Boolean): String? =
        if (value.isNullOrEmpty() || ((!strict || selected) && isShort(value))) value else redacted(value)

    private fun isShort(value: String) =
        value.length <= MAX_CHARS && value.trim().split(Regex("\\s+")).size <= MAX_WORDS

    private fun redacted(value: String) = "[redacted:${value.length}]"
}
