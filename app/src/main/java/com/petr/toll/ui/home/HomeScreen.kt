package com.petr.toll.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petr.toll.rules.Limits
import com.petr.toll.rules.PendingChange
import com.petr.toll.rules.Taper
import com.petr.toll.rules.Tier
import com.petr.toll.rules.TollSettings
import com.petr.toll.ui.BarrierStripe
import com.petr.toll.ui.LadderMeter
import com.petr.toll.ui.LocalTollPalette
import com.petr.toll.ui.Overpass
import com.petr.toll.ui.short
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DAY = DateTimeFormatter.ofPattern("EEE d MMM")
private val TIME = DateTimeFormatter.ofPattern("HH:mm")

/** Toll's home: today on the ladder, the quick pass, this week's limits, and the last two weeks. */
@Composable
fun HomeScreen(
    state: HomeState,
    onStart: () -> Unit,
    onQuickPass: () -> Unit,
    onTurnOn: () -> Unit,
    onSettings: () -> Unit,
    onTestMode: () -> Unit,
) {
    val p = LocalTollPalette.current
    Column(
        Modifier
            .fillMaxSize()
            .background(p.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("TOLL", style = MaterialTheme.typography.displaySmall, color = p.ink, modifier = Modifier.weight(1f))
                Quiet("Settings", onSettings)
            }
            BarrierStripe(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(2.dp)))
        }
        if (!state.serviceOn) OffCard(onTurnOn)
        if (!state.enforcing) {
            StartCard(state, onStart)
        } else {
            TodayCard(state)
            QuickPassCard(state, onQuickPass)
            WeekCard(state)
            HistoryCard(state)
        }
        Quiet("Test mode", onTestMode)
    }
}

@Composable
private fun OffCard(onTurnOn: () -> Unit) {
    val p = LocalTollPalette.current
    Card {
        Text("Toll is switched off", style = MaterialTheme.typography.titleLarge, color = p.barrier)
        Text(
            "Instagram is free right now: nothing is counted or blocked. Switch Toll on in Accessibility settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = p.muted,
        )
        Primary("Open Accessibility settings", onTurnOn)
    }
}

/** Before the first start: what Toll will do, and the button that starts week 1 today. */
@Composable
private fun StartCard(state: HomeState, onStart: () -> Unit) {
    val p = LocalTollPalette.current
    val s = state.settings
    val taper = when (val t = s.taper) {
        is Taper.Subtract -> "${t.amount.short()} less each week"
        is Taper.Multiply -> "${Math.round((1 - t.factor) * 100)}% less each week"
    }
    Card {
        Eyebrow("READY WHEN YOU ARE")
        Text("Start Toll", style = MaterialTheme.typography.titleLarge, color = p.ink)
        Text(
            "Week 1 starts today: ${s.weekdayStartLimit.short()} on weekdays and ${s.weekendStartLimit.short()} at " +
                "weekends, then $taper, down to ${s.floor.short()} a day. Messages, stories and things friends send " +
                "you stay free.",
            style = MaterialTheme.typography.bodyMedium,
            color = p.muted,
        )
        Text(
            "Until you start, Toll only watches. Nothing is counted or blocked.",
            style = MaterialTheme.typography.bodyMedium,
            color = p.muted,
        )
        Primary("Start week 1 today", onStart)
    }
}

@Composable
private fun TodayCard(state: HomeState) {
    val p = LocalTollPalette.current
    val today = state.today
    val limit = today?.limit ?: Limits.limitFor(state.todayDate, state.settings)
    val paid = today?.paidToday ?: Duration.ZERO
    val tier = Tier.of(paid, limit)
    Card {
        Eyebrow("TODAY · ${state.todayDate.format(DAY).uppercase()}")
        Row(verticalAlignment = Alignment.Bottom) {
            Text(paid.short(), fontFamily = Overpass, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, color = p.ink)
            Text(
                "  of ${limit.short()}",
                style = MaterialTheme.typography.bodyLarge,
                color = p.muted,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        LadderMeter(paid, limit)
        Text(nextStep(paid, limit, tier), style = MaterialTheme.typography.bodyMedium, color = p.ink)
        val opens = today?.opensToday ?: 0
        if (opens > 0) {
            Text(
                if (opens == 1) "Opened once today" else "Opened $opens times today",
                style = MaterialTheme.typography.bodyMedium,
                color = p.muted,
            )
        }
    }
}

private fun nextStep(paid: Duration, limit: Duration, tier: Tier): String = when (tier) {
    Tier.FREE -> "Free for another ${(Tier.TYPING.threshold(limit) - paid).short()}. After that you type to get in."
    Tier.TYPING -> "You type to get in. The grey screen starts at ${Tier.GREY.threshold(limit).short()}."
    Tier.GREY -> "Grey screen and \"Still here?\". Holds start at ${limit.short()}."
    Tier.OVER -> "${(paid - limit).short()} over. Every 10 minutes costs a hold."
}

@Composable
private fun QuickPassCard(state: HomeState, onQuickPass: () -> Unit) {
    val p = LocalTollPalette.current
    val today = state.today
    val left = today?.quickPassesLeft ?: state.settings.quickPassesPerDay
    val runningUntil = today?.let { t -> t.quickPassLeft?.let { t.at.plus(it) } }
    val length = state.settings.quickPassLength
    Card {
        Eyebrow("QUICK PASS")
        when {
            runningUntil != null -> {
                val ends = runningUntil.atZone(ZoneId.systemDefault()).toLocalTime().format(TIME)
                Text("Running until $ends", style = MaterialTheme.typography.titleLarge, color = p.go)
                Text("No toll until then. The minutes still count.", style = MaterialTheme.typography.bodyMedium, color = p.muted)
            }
            left > 0 -> {
                Text(
                    "Open Instagram right away for ${length.short()}, with no toll. For showing someone something quickly.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = p.muted,
                )
                Primary("Use a quick pass", onQuickPass)
                Text(
                    if (left == 1) "1 left today" else "$left left today",
                    style = MaterialTheme.typography.labelMedium,
                    color = p.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            else -> {
                Text("None left today", style = MaterialTheme.typography.titleMedium, color = p.ink)
                Text(
                    "You get ${state.settings.quickPassesPerDay} again at ${"%02d:00".format(state.settings.dayStartHour)}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = p.muted,
                )
            }
        }
    }
}

@Composable
private fun WeekCard(state: HomeState) {
    val p = LocalTollPalette.current
    val s = state.settings
    val week = Limits.weekNumber(state.todayDate, s.startDate)
    fun weekday(w: Int) = Limits.limitForWeek(s.weekdayStartLimit, w, s.taper, s.floor)
    fun weekend(w: Int) = Limits.limitForWeek(s.weekendStartLimit, w, s.taper, s.floor)
    val nextStart = s.startDate.plusDays(7L * week)
    val goalWeek = (week..520).firstOrNull { weekday(it) <= s.floor && weekend(it) <= s.floor }
    Card {
        Eyebrow("WEEK $week")
        Row {
            Stat("Weekdays", weekday(week).short(), Modifier.weight(1f))
            Stat("Weekends", weekend(week).short(), Modifier.weight(1f))
        }
        val reachedGoal = weekday(week) <= s.floor && weekend(week) <= s.floor
        Text(
            if (reachedGoal) {
                "You're at your goal of ${s.floor.short()} a day."
            } else {
                "From ${nextStart.format(DAY)}: ${weekday(week + 1).short()} on weekdays, ${weekend(week + 1).short()} at weekends." +
                    (goalWeek?.let { " Down to ${s.floor.short()} in week $it." } ?: "")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = p.muted,
        )
        state.pending.forEach { change ->
            Text(
                "Waiting until ${change.effectiveAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE HH:mm"))}: " +
                    describe(change, s),
                style = MaterialTheme.typography.bodyMedium,
                color = p.amber,
            )
        }
    }
}

private fun describe(change: PendingChange, s: TollSettings): String {
    val parts = mutableListOf<String>()
    val patch = change.patch
    patch.weekdayStartLimit?.let { parts += "weekdays start at ${it.short()}" }
    patch.weekendStartLimit?.let { parts += "weekends start at ${it.short()}" }
    patch.floor?.let { parts += "goal ${it.short()}" }
    patch.taper?.let {
        parts += when (it) {
            is Taper.Subtract -> "${it.amount.short()} less each week"
            is Taper.Multiply -> "${Math.round((1 - it.factor) * 100)}% less each week"
        }
    }
    patch.quickPassesPerDay?.let { parts += "$it quick passes a day" }
    patch.quickPassLength?.let { parts += "quick passes of ${it.short()}" }
    return if (parts.isEmpty()) "a settings change" else parts.joinToString(", ")
}

@Composable
private fun HistoryCard(state: HomeState) {
    val p = LocalTollPalette.current
    val byDay = state.history.associateBy { it.day }
    val days = (13 downTo 0).map { state.todayDate.minusDays(it.toLong()) }.map { date ->
        byDay[date] ?: DaySummary(date, Duration.ZERO, Limits.limitFor(date, state.settings), 0)
    }
    var selected by remember(state.todayDate) { mutableIntStateOf(days.lastIndex) }
    val pick = days[selected]
    val over = pick.paid - pick.limit
    Card {
        Eyebrow("INSTAGRAM PER DAY · LAST 2 WEEKS")
        Text(
            buildString {
                append(if (pick.day == state.todayDate) "Today" else pick.day.format(DAY))
                append(": ${pick.paid.short()} of ${pick.limit.short()}")
                if (!over.isNegative && !over.isZero) append(", ${over.short()} over")
            },
            style = MaterialTheme.typography.titleMedium,
            color = p.ink,
        )
        HistoryChart(days, selected, onSelect = { selected = it })
        Row(Modifier.fillMaxWidth().padding(start = AXIS_WIDTH.dp)) {
            days.forEachIndexed { i, d ->
                Text(
                    d.day.dayOfWeek.name.take(1),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (i == selected) p.ink else p.muted,
                    fontWeight = if (d.day == state.todayDate) FontWeight.ExtraBold else FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text("Tap a day to see it. The dashed line is that day's limit.", style = MaterialTheme.typography.bodyMedium, color = p.muted)
    }
}

private const val AXIS_WIDTH = 34

/** One series (paid time per day) as bars, the day's limit as a dashed step line. Over-limit days get a label. */
@Composable
private fun HistoryChart(days: List<DaySummary>, selected: Int, onSelect: (Int) -> Unit) {
    val p = LocalTollPalette.current
    val measurer = rememberTextMeasurer()
    val label = MaterialTheme.typography.labelSmall.copy(color = p.muted)
    val maxMinutes = days.maxOf { maxOf(it.paid.toMinutes(), it.limit.toMinutes()) }.coerceAtLeast(60)
    val stepHours = if (maxMinutes <= 6 * 60) 1 else 2
    val topMinutes = ((maxMinutes / 60) / stepHours + 1) * stepHours * 60f

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(170.dp)
            .pointerInput(days.size) {
                detectTapGestures { offset ->
                    val left = AXIS_WIDTH.dp.toPx()
                    val slot = (size.width - left) / days.size
                    onSelect(((offset.x - left) / slot).toInt().coerceIn(0, days.lastIndex))
                }
            },
    ) {
        val left = AXIS_WIDTH.dp.toPx()
        val top = 14.dp.toPx()
        val plotH = size.height - top
        val slot = (size.width - left) / days.size
        fun y(minutes: Float) = top + plotH * (1 - minutes / topMinutes)

        // Recessive grid and hour labels.
        var h = 0
        while (h * 60 <= topMinutes) {
            val gy = y(h * 60f)
            drawLine(p.line, Offset(left, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx())
            if (h > 0) {
                drawText(measurer, "$h h", topLeft = Offset(0f, gy - 7.dp.toPx()), style = label)
            }
            h += stepHours
        }

        // Bars: rounded top, square on the baseline, 2 px gaps come from the slot padding.
        val barW = slot * 0.58f
        val radius = 4.dp.toPx()
        days.forEachIndexed { i, d ->
            if (d.paid.isZero) return@forEachIndexed
            val x = left + slot * i + (slot - barW) / 2
            val barTop = y(d.paid.toMinutes().toFloat().coerceAtMost(topMinutes))
            val path = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(x, barTop, x + barW, y(0f)),
                        topLeft = CornerRadius(radius),
                        topRight = CornerRadius(radius),
                        bottomRight = CornerRadius.Zero,
                        bottomLeft = CornerRadius.Zero,
                    ),
                )
            }
            drawPath(path, if (i == selected) p.ink else p.ink.copy(alpha = 0.4f))
            val overMinutes = d.paid.toMinutes() - d.limit.toMinutes()
            if (overMinutes > 0) {
                val text = "+$overMinutes"
                val m = measurer.measure(text, label)
                drawText(m, topLeft = Offset(x + barW / 2 - m.size.width / 2, barTop - m.size.height - 2.dp.toPx()))
            }
        }

        // The limit as a dashed step line.
        val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
        val limitPath = Path()
        days.forEachIndexed { i, d ->
            val ly = y(d.limit.toMinutes().toFloat())
            val x0 = left + slot * i
            if (i == 0) limitPath.moveTo(x0, ly) else limitPath.lineTo(x0, ly)
            limitPath.lineTo(x0 + slot, ly)
        }
        drawPath(limitPath, p.barrier, style = Stroke(width = 2.dp.toPx(), pathEffect = dash))
    }
}

// ---- building blocks ----

@Composable
private fun Card(content: @Composable () -> Unit) {
    val p = LocalTollPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(p.surface, RoundedCornerShape(20.dp))
            .border(1.dp, p.line, RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
private fun Eyebrow(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = LocalTollPalette.current.muted)
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    val p = LocalTollPalette.current
    Column(modifier) {
        Text(value, fontFamily = Overpass, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = p.ink)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = p.muted)
    }
}

@Composable
private fun Primary(label: String, onClick: () -> Unit) {
    val p = LocalTollPalette.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(p.ink)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = p.surface)
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
