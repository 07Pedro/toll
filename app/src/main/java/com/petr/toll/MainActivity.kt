package com.petr.toll

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.petr.toll.probe.DumpStore
import com.petr.toll.service.TollAccessibilityService
import com.petr.toll.ui.BarrierStripe
import com.petr.toll.ui.LocalTollPalette
import com.petr.toll.ui.TollTheme
import com.petr.toll.ui.Walkthrough

/** Phase 0 home: switch the probe on, open Instagram, and follow the walkthrough step by step. */
class MainActivity : ComponentActivity() {
    private var resumes by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TollTheme {
                ProbeHome(refreshKey = resumes)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumes++
    }
}

private const val INSTAGRAM = "com.instagram.android"

@Composable
private fun ProbeHome(refreshKey: Int) {
    val context = LocalContext.current
    val p = LocalTollPalette.current
    val serviceOn = remember(refreshKey) { isServiceEnabled(context) }
    val instagram = remember(refreshKey) { TollAccessibilityService.instagramVersion(context) }
    var saved by remember(refreshKey) { mutableIntStateOf(DumpStore(context).count()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(p.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Header()
        StatusCard(
            serviceOn = serviceOn,
            instagramInstalled = instagram != null,
            onOpenSettings = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            onOpenInstagram = {
                context.packageManager.getLaunchIntentForPackage(INSTAGRAM)?.let(context::startActivity)
            },
        )
        WalkthroughCard(saved)
        ButtonTestsCard()
        Footer(
            instagram = instagram,
            saved = saved,
            onStartOver = {
                DumpStore(context).deleteAll()
                saved = 0
            },
        )
    }
}

@Composable
private fun Header() {
    val p = LocalTollPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("TOLL", style = MaterialTheme.typography.displaySmall, color = p.ink)
            Spacer(Modifier.weight(1f))
            Tag("TEST MODE", p.amber)
        }
        BarrierStripe(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(2.dp)),
        )
        Text(
            "This version only watches Instagram and labels each screen, so we can check Toll recognises them. " +
                "Nothing is blocked or counted yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = p.muted,
        )
    }
}

@Composable
private fun StatusCard(
    serviceOn: Boolean,
    instagramInstalled: Boolean,
    onOpenSettings: () -> Unit,
    onOpenInstagram: () -> Unit,
) {
    val p = LocalTollPalette.current
    Card {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Dot(if (serviceOn) p.go else p.barrier)
            Text(
                if (serviceOn) "Watching Instagram" else "Toll is switched off",
                style = MaterialTheme.typography.titleLarge,
                color = p.ink,
            )
        }
        if (serviceOn) {
            Text(
                "Open Instagram and work through the steps below. A small panel shows Toll's guess for each screen.",
                style = MaterialTheme.typography.bodyMedium,
                color = p.muted,
            )
            if (instagramInstalled) {
                PrimaryButton("Open Instagram", onOpenInstagram)
            } else {
                Text("Instagram isn't installed on this phone.", style = MaterialTheme.typography.bodyMedium, color = p.barrier)
            }
        } else {
            Text(
                "In Accessibility settings, find Toll under Downloaded apps and switch it on. Android will warn that " +
                    "Toll can see your screen: that's how it tells Instagram's screens apart. Toll has no internet " +
                    "access, so nothing leaves your phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = p.muted,
            )
            PrimaryButton("Open Accessibility settings", onOpenSettings)
        }
    }
}

@Composable
private fun WalkthroughCard(saved: Int) {
    val p = LocalTollPalette.current
    val total = Walkthrough.STEPS.size
    val done = saved.coerceAtMost(total)
    Card {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(Walkthrough.TITLE, style = MaterialTheme.typography.titleLarge, color = p.ink, modifier = Modifier.weight(1f))
            Text("$done of $total done", style = MaterialTheme.typography.labelMedium, color = p.muted)
        }
        SegmentedProgress(done = done, total = total)
        Text(
            "${Walkthrough.INTRO} For each step: open the screen, wait a second, then tap Save on the panel. " +
                "If you can't do a step, tap Skip instead.",
            style = MaterialTheme.typography.bodyMedium,
            color = p.muted,
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Walkthrough.STEPS.forEachIndexed { index, step ->
                StepRow(
                    number = index + 1,
                    text = step.text,
                    note = step.note,
                    state = when {
                        index < done -> StepState.DONE
                        index == done -> StepState.NEXT
                        else -> StepState.LATER
                    },
                )
            }
        }
        Text(
            "Saved screens record the layout only, never message text.",
            style = MaterialTheme.typography.bodyMedium,
            color = p.muted,
        )
    }
}

@Composable
private fun ButtonTestsCard() {
    val p = LocalTollPalette.current
    Card {
        Text("Then two button tests", style = MaterialTheme.typography.titleMedium, color = p.ink)
        Walkthrough.BUTTON_TESTS.forEachIndexed { index, test ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = p.muted, modifier = Modifier.width(14.dp))
                Text(test, style = MaterialTheme.typography.bodyMedium, color = p.ink)
            }
        }
        Text(
            "No saving needed. The yellow line on the panel says what happened.",
            style = MaterialTheme.typography.bodyMedium,
            color = p.muted,
        )
    }
}

@Composable
private fun Footer(instagram: String?, saved: Int, onStartOver: () -> Unit) {
    val p = LocalTollPalette.current
    var confirming by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        InfoRow("Instagram version", instagram ?: "Not installed")
        InfoRow("Saved screens", saved.toString())
        if (saved > 0) {
            TextButton(
                onClick = {
                    if (confirming) {
                        onStartOver()
                        confirming = false
                    } else {
                        confirming = true
                    }
                },
                contentPadding = ButtonDefaults.TextButtonContentPadding,
            ) {
                Text(
                    if (confirming) "Tap again to delete all $saved and start over" else "Start the ${Walkthrough.TITLE.lowercase()} over",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (confirming) p.barrier else p.muted,
                )
            }
        }
    }
}

// ---- building blocks ----

private enum class StepState { DONE, NEXT, LATER }

@Composable
private fun StepRow(number: Int, text: String, note: String?, state: StepState) {
    val p = LocalTollPalette.current
    val highlight = state == StepState.NEXT
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (highlight) p.background else Color.Transparent, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StepBadge(number, state)
        Column(Modifier.weight(1f)) {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
                color = if (state == StepState.DONE) p.muted else p.ink,
            )
            if (note != null) Text(note, style = MaterialTheme.typography.bodyMedium, color = p.muted)
        }
        if (highlight) Tag("NEXT", p.barrier)
    }
}

@Composable
private fun StepBadge(number: Int, state: StepState) {
    val p = LocalTollPalette.current
    val modifier = Modifier.size(28.dp)
    when (state) {
        StepState.DONE -> Box(modifier.background(p.go, CircleShape), contentAlignment = Alignment.Center) {
            Checkmark(Color.White)
        }
        StepState.NEXT -> Box(modifier.background(p.ink, CircleShape), contentAlignment = Alignment.Center) {
            Text("$number", style = MaterialTheme.typography.labelMedium, color = p.surface)
        }
        StepState.LATER -> Box(modifier.border(1.5.dp, p.line, CircleShape), contentAlignment = Alignment.Center) {
            Text("$number", style = MaterialTheme.typography.labelMedium, color = p.muted)
        }
    }
}

@Composable
private fun Checkmark(color: Color) {
    Canvas(Modifier.size(14.dp)) {
        val path = Path().apply {
            moveTo(size.width * 0.12f, size.height * 0.55f)
            lineTo(size.width * 0.4f, size.height * 0.82f)
            lineTo(size.width * 0.9f, size.height * 0.2f)
        }
        drawPath(path, color, style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun SegmentedProgress(done: Int, total: Int) {
    val p = LocalTollPalette.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(total) { i ->
            val color = when {
                i < done -> p.go
                i == done -> p.ink
                else -> p.line
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(6.dp)
                    .background(color, RoundedCornerShape(3.dp)),
            )
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    val p = LocalTollPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(p.surface, RoundedCornerShape(20.dp))
            .border(1.dp, p.line, RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    val p = LocalTollPalette.current
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = p.ink, contentColor = p.surface),
    ) { Text(label, style = MaterialTheme.typography.labelLarge) }
}

@Composable
private fun Tag(label: String, color: Color) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .border(1.dp, color, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
private fun Dot(color: Color) {
    Box(
        Modifier
            .size(12.dp)
            .background(color, CircleShape),
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    val p = LocalTollPalette.current
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = p.muted, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelMedium, color = p.ink)
    }
}

private fun isServiceEnabled(context: Context): Boolean {
    val ours = ComponentName(context, TollAccessibilityService::class.java)
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        ?: return false
    return enabled.split(':').any { ComponentName.unflattenFromString(it) == ours }
}
