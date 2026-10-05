package com.petr.toll.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.petr.toll.rules.Commitments
import com.petr.toll.rules.PendingChange
import com.petr.toll.rules.Rules
import com.petr.toll.rules.Taper
import com.petr.toll.rules.TollSettings
import com.petr.toll.ui.LocalTollPalette
import com.petr.toll.ui.short
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Limits and quick passes. Each row says whether its change would apply now (stricter) or wait 24 hours
 * (looser), using the same rule as [Commitments.plan].
 */
@Composable
fun SettingsScreen(
    current: TollSettings,
    pending: List<PendingChange>,
    turnOff: TurnOffState,
    onSave: (TollSettings) -> Unit,
    onRequestTurnOff: () -> Unit,
    onCancelTurnOff: () -> Unit,
    onBack: () -> Unit,
) {
    val p = LocalTollPalette.current
    var draft by remember(current) { mutableStateOf(current) }

    Column(
        Modifier
            .fillMaxSize()
            .background(p.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Settings", style = MaterialTheme.typography.headlineSmall, color = p.ink, modifier = Modifier.weight(1f))
            Quiet("Back", onBack)
        }
        Text(
            "Stricter changes apply straight away. Looser ones wait 24 hours, so a weak moment can't undo your plan.",
            style = MaterialTheme.typography.bodyMedium,
            color = p.muted,
        )

        Section("DAILY LIMIT") {
            Stepper(
                label = "Weekdays, in week 1",
                value = draft.weekdayStartLimit.short(),
                waits = draft.weekdayStartLimit > current.weekdayStartLimit,
                onMinus = { draft = draft.copy(weekdayStartLimit = step(draft.weekdayStartLimit, -10, 30, 600)) },
                onPlus = { draft = draft.copy(weekdayStartLimit = step(draft.weekdayStartLimit, 10, 30, 600)) },
            )
            Stepper(
                label = "Weekends, in week 1",
                value = draft.weekendStartLimit.short(),
                waits = draft.weekendStartLimit > current.weekendStartLimit,
                onMinus = { draft = draft.copy(weekendStartLimit = step(draft.weekendStartLimit, -10, 30, 720)) },
                onPlus = { draft = draft.copy(weekendStartLimit = step(draft.weekendStartLimit, 10, 30, 720)) },
            )
            val taper = draft.taper
            if (taper is Taper.Subtract) {
                Stepper(
                    label = "Less each week",
                    value = taper.amount.short(),
                    waits = draft.taper != current.taper && Commitments.taperIsLooser(current, draft.taper),
                    onMinus = { draft = draft.copy(taper = Taper.Subtract(step(taper.amount, -5, 0, 60))) },
                    onPlus = { draft = draft.copy(taper = Taper.Subtract(step(taper.amount, 5, 0, 60))) },
                )
            }
            Stepper(
                label = "Goal: the lowest it goes",
                value = draft.floor.short(),
                waits = draft.floor > current.floor,
                onMinus = { draft = draft.copy(floor = step(draft.floor, -5, 10, 180)) },
                onPlus = { draft = draft.copy(floor = step(draft.floor, 5, 10, 180)) },
            )
        }

        Section("QUICK PASS") {
            Stepper(
                label = "Passes a day",
                value = "${draft.quickPassesPerDay}",
                waits = draft.quickPassesPerDay > current.quickPassesPerDay,
                onMinus = { draft = draft.copy(quickPassesPerDay = (draft.quickPassesPerDay - 1).coerceIn(0, 10)) },
                onPlus = { draft = draft.copy(quickPassesPerDay = (draft.quickPassesPerDay + 1).coerceIn(0, 10)) },
            )
            Stepper(
                label = "Length of a pass",
                value = draft.quickPassLength.short(),
                waits = draft.quickPassLength > current.quickPassLength,
                onMinus = { draft = draft.copy(quickPassLength = step(draft.quickPassLength, -1, 1, 10)) },
                onPlus = { draft = draft.copy(quickPassLength = step(draft.quickPassLength, 1, 1, 10)) },
            )
        }

        Section("EARN TIME") {
            Stepper(
                label = "Each task adds",
                value = draft.earnPerTask.short(),
                waits = draft.earnPerTask > current.earnPerTask,
                onMinus = { draft = draft.copy(earnPerTask = step(draft.earnPerTask, -5, 5, 30)) },
                onPlus = { draft = draft.copy(earnPerTask = step(draft.earnPerTask, 5, 5, 30)) },
            )
            Stepper(
                label = "Most earned a day",
                value = draft.earnCapPerDay.short(),
                waits = draft.earnCapPerDay > current.earnCapPerDay,
                onMinus = { draft = draft.copy(earnCapPerDay = step(draft.earnCapPerDay, -10, 0, 120)) },
                onPlus = { draft = draft.copy(earnCapPerDay = step(draft.earnCapPerDay, 10, 0, 120)) },
            )
        }

        if (pending.isNotEmpty()) {
            Section("WAITING") {
                pending.forEach { change ->
                    Text(
                        "From ${change.effectiveAt.atZone(ZoneId.systemDefault()).format(WHEN)}: ${describeChange(change)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = p.amber,
                    )
                }
            }
        }

        val changed = draft != current
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (changed) p.ink else p.line)
                .clickable(enabled = changed) { onSave(draft) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (changed) "Save" else "No changes",
                style = MaterialTheme.typography.labelLarge,
                color = if (changed) p.surface else p.muted,
            )
        }
        Text(
            "Your plan started on ${current.startDate.format(DateTimeFormatter.ofPattern("EEE d MMM"))}. Weeks count from that day.",
            style = MaterialTheme.typography.bodyMedium,
            color = p.muted,
        )

        TurnOffSection(turnOff, onRequestTurnOff, onCancelTurnOff)
    }
}

/** Turning Toll off is a request that takes effect a day later, so it can't happen on impulse. */
@Composable
private fun TurnOffSection(turnOff: TurnOffState, onRequest: () -> Unit, onCancel: () -> Unit) {
    val p = LocalTollPalette.current
    var confirming by remember { mutableStateOf(false) }
    Section("TURN OFF TOLL") {
        when (turnOff) {
            is TurnOffState.Running -> {
                Text(
                    "Toll keeps working for ${Rules.TURN_OFF_DELAY.toHours()} hours after you ask, then switches off " +
                        "until you turn it back on. You can change your mind until then.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = p.muted,
                )
                Outlined(
                    label = if (confirming) "Tap again: turn off in ${Rules.TURN_OFF_DELAY.toHours()} h" else "Turn off Toll",
                    color = if (confirming) p.barrier else p.ink,
                ) {
                    if (confirming) {
                        confirming = false
                        onRequest()
                    } else {
                        confirming = true
                    }
                }
            }
            is TurnOffState.Requested -> {
                Text(
                    "Toll switches off on ${turnOff.effectiveAt.atZone(ZoneId.systemDefault()).format(WHEN_LONG)}.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = p.amber,
                )
                Outlined(label = "Keep Toll on", color = p.ink, onClick = onCancel)
            }
            is TurnOffState.Off -> Text(
                "Toll is off. You can turn it back on from the home screen.",
                style = MaterialTheme.typography.bodyMedium,
                color = p.muted,
            )
        }
    }
}

@Composable
private fun Outlined(label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.5.dp, color, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

private val WHEN = DateTimeFormatter.ofPattern("EEE HH:mm")
private val WHEN_LONG = DateTimeFormatter.ofPattern("EEE d MMM 'at' HH:mm")

private fun step(d: Duration, byMinutes: Long, minMinutes: Long, maxMinutes: Long): Duration =
    Duration.ofMinutes((d.toMinutes() + byMinutes).coerceIn(minMinutes, maxMinutes))

private fun describeChange(change: PendingChange): String {
    val patch = change.patch
    val parts = listOfNotNull(
        patch.weekdayStartLimit?.let { "weekdays start at ${it.short()}" },
        patch.weekendStartLimit?.let { "weekends start at ${it.short()}" },
        patch.floor?.let { "goal ${it.short()}" },
        patch.taper?.let { t -> if (t is Taper.Subtract) "${t.amount.short()} less each week" else "a slower weekly cut" },
        patch.quickPassesPerDay?.let { "$it quick passes a day" },
        patch.quickPassLength?.let { "quick passes of ${it.short()}" },
        patch.earnPerTask?.let { "tasks add ${it.short()}" },
        patch.earnCapPerDay?.let { "earn up to ${it.short()} a day" },
    )
    return if (parts.isEmpty()) "a settings change" else parts.joinToString(", ")
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val p = LocalTollPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(p.surface, RoundedCornerShape(20.dp))
            .border(1.dp, p.line, RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = p.muted)
        content()
    }
}

@Composable
private fun Stepper(label: String, value: String, waits: Boolean, onMinus: () -> Unit, onPlus: () -> Unit) {
    val p = LocalTollPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = p.ink)
            if (waits) Text("Looser: waits 24 h", style = MaterialTheme.typography.labelMedium, color = p.amber)
        }
        Round("−", onMinus)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = p.ink,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        Round("+", onPlus)
    }
}

@Composable
private fun Round(symbol: String, onClick: () -> Unit) {
    val p = LocalTollPalette.current
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .border(1.5.dp, p.line, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = MaterialTheme.typography.titleMedium, color = p.ink)
    }
}

@Composable
private fun Quiet(label: String, onClick: () -> Unit) {
    val p = LocalTollPalette.current
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = p.muted)
    }
}
