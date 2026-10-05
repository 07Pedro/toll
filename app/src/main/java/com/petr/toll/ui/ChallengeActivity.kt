package com.petr.toll.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.petr.toll.rules.Sentences
import com.petr.toll.ui.overlay.ChallengeResult
import kotlinx.coroutines.delay

/**
 * The typing toll. An activity rather than an overlay, so the keyboard works normally. It reports through
 * [ChallengeResult] before finishing; leaving it any other way counts as giving up.
 */
class ChallengeActivity : ComponentActivity() {
    private var reported = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sentence = intent.getStringExtra(EXTRA_SENTENCE) ?: run {
            report(false)
            return
        }
        enableEdgeToEdge()
        setContent {
            TollTheme(dark = true) {
                TypingScreen(sentence, onDone = { report(true) }, onCancel = { report(false) })
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) report(false)
    }

    private fun report(paid: Boolean) {
        if (!reported) {
            reported = true
            ChallengeResult.deliver(paid)
        }
        finish()
    }

    companion object {
        private const val EXTRA_SENTENCE = "sentence"

        fun start(context: Context, sentence: String) {
            context.startActivity(
                Intent(context, ChallengeActivity::class.java)
                    .putExtra(EXTRA_SENTENCE, sentence)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
            )
        }
    }
}

@Composable
private fun TypingScreen(sentence: String, onDone: () -> Unit, onCancel: () -> Unit) {
    val p = LocalTollPalette.current
    var typed by remember { mutableStateOf("") }
    var warning by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val target = words(sentence)
    val matched = target.zip(words(typed)).takeWhile { (a, b) -> a == b }.size
    val done = Sentences.matches(sentence, typed)

    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(done) {
        if (done) {
            delay(300)
            onDone()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(p.background)
            .safeDrawingPadding()
            .imePadding(),
    ) {
        BarrierStripe(Modifier.fillMaxWidth().height(14.dp))
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("TOLL", style = MaterialTheme.typography.labelSmall, color = p.muted)
            Text("Type this to get in", style = MaterialTheme.typography.headlineSmall, color = p.ink)
            Text(
                "Word for word. Capitals and punctuation don't matter. Pasting doesn't count.",
                style = MaterialTheme.typography.bodyMedium,
                color = p.muted,
            )
            Text(
                buildAnnotatedString {
                    sentence.split(" ").forEachIndexed { i, word ->
                        if (i > 0) append(" ")
                        withStyle(SpanStyle(color = if (i < matched) p.go else p.ink)) { append(word) }
                    }
                },
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, p.line, RoundedCornerShape(16.dp))
                    .padding(18.dp),
            )
            CompositionLocalProvider(LocalTextToolbar provides NoTextToolbar) {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { new ->
                        if (insertsSeveralWords(typed, new)) {
                            warning = "Pasting doesn't count. Type it word by word."
                        } else {
                            typed = new
                            warning = null
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done,
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (done) p.go else p.ink,
                        unfocusedBorderColor = p.line,
                        cursorColor = p.ink,
                        focusedTextColor = p.ink,
                        unfocusedTextColor = p.ink,
                    ),
                    shape = RoundedCornerShape(14.dp),
                )
            }
            val note = warning ?: if (done) "Paid. Back to Instagram." else "${matched} of ${target.size} words"
            Text(
                note,
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    warning != null -> p.barrier
                    done -> p.go
                    else -> p.muted
                },
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) {
                Text("Back without paying", style = MaterialTheme.typography.labelLarge, color = p.muted)
            }
        }
    }
}

private fun words(s: String): List<String> =
    s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().split(" ").filter { it.isNotEmpty() }

/** True when one edit inserts two or more words at once: a paste or autofill, not typing or swipe-typing. */
private fun insertsSeveralWords(old: String, new: String): Boolean {
    if (new.length - old.length < 2) return false
    var start = 0
    while (start < old.length && old[start] == new[start]) start++
    var end = 0
    while (end < old.length - start && old[old.length - 1 - end] == new[new.length - 1 - end]) end++
    val inserted = new.substring(start, new.length - end)
    return inserted.trim().split(Regex("\\s+")).count { it.isNotEmpty() } >= 2
}

/** No copy/paste menu on the typing field. */
private object NoTextToolbar : TextToolbar {
    override val status: TextToolbarStatus = TextToolbarStatus.Hidden
    override fun hide() = Unit
    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) = Unit
}
