package com.petr.toll.ui.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petr.toll.rules.Decision
import com.petr.toll.rules.Gate
import com.petr.toll.rules.Hold
import com.petr.toll.rules.Price
import com.petr.toll.rules.Rules
import com.petr.toll.rules.Sentences
import com.petr.toll.rules.Tier
import com.petr.toll.ui.BarrierStripe
import com.petr.toll.ui.LadderMeter
import com.petr.toll.ui.LadderRamp
import com.petr.toll.ui.LightPalette
import com.petr.toll.ui.LocalTollPalette
import com.petr.toll.ui.Overpass
import com.petr.toll.ui.clock
import com.petr.toll.ui.short
import com.petr.toll.ui.summary
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.hypot
import kotlin.math.sin

// ---- the gate ----

@Composable
fun GateScreen(
    decision: Decision,
    gate: Gate,
    onMessages: () -> Unit,
    onStories: () -> Unit,
    onPay: () -> Unit,
    onLeave: () -> Unit,
) {
    val p = LocalTollPalette.current
    val insets = LocalOverlayInsets.current
    Column(
        Modifier
            .fillMaxSize()
            .background(p.background)
            .padding(top = insets.top),
    ) {
        BarrierStripe(Modifier.fillMaxWidth().height(14.dp))
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = insets.bottom + 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("TOLL", style = MaterialTheme.typography.labelSmall, color = p.muted)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(headline(gate.price), style = MaterialTheme.typography.headlineSmall, color = p.ink)
                Text(reason(decision, gate), style = MaterialTheme.typography.bodyMedium, color = p.muted)
            }
            UsageBlock(decision)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { PriceDetail(gate.price) }
            Text("Free, no toll", style = MaterialTheme.typography.labelSmall, color = p.muted)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FreeButton("Messages", onMessages, Modifier.weight(1f))
                FreeButton("Stories", onStories, Modifier.weight(1f))
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // The light theme's deeper red keeps white text readable (about 5:1).
                BigButton(payLabel(gate.price), onPay, container = LightPalette.barrier, content = Color.White)
                Text(
                    "Coming back within 10 minutes costs ${gate.ifBackSoon.summary()}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = p.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            QuietButton("Leave Instagram", onLeave)
        }
    }
}

/** What paying involves, so the choice is concrete before pressing Pay. */
@Composable
private fun PriceDetail(price: Price) {
    val p = LocalTollPalette.current
    when (price) {
        Price.None -> Unit
        is Price.Typing -> Column(
            Modifier
                .fillMaxWidth()
                .border(1.dp, p.line, RoundedCornerShape(16.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("YOU'LL TYPE", style = MaterialTheme.typography.labelSmall, color = p.muted)
            Text(price.sentence, style = MaterialTheme.typography.titleMedium, color = p.ink)
        }
        is Price.QrAndHold -> Column(
            Modifier
                .fillMaxWidth()
                .border(1.dp, p.line, RoundedCornerShape(16.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("YOU'LL HOLD", style = MaterialTheme.typography.labelSmall, color = p.muted)
            Text(
                "Your thumb on a slowly moving dot for ${minutes(price.hold)}. Off it for more than 10 seconds and " +
                    "it starts over. Each hold today is longer than the last.",
                style = MaterialTheme.typography.bodyLarge,
                color = p.ink,
            )
        }
    }
}

private fun headline(price: Price): String = when (price) {
    Price.None -> "Instagram is open"
    is Price.Typing -> "Type a sentence to get in"
    is Price.QrAndHold -> "Hold for ${minutes(price.hold)} to get in"
}

private fun payLabel(price: Price): String = when (price) {
    Price.None -> "Go in"
    is Price.Typing -> "Type it"
    is Price.QrAndHold -> "Start the ${price.hold.toMinutes()}-minute hold"
}

private fun reason(decision: Decision, gate: Gate): String = when {
    gate.reflex -> "You left less than 10 minutes ago, so this time costs one step more."
    decision.tier == Tier.OVER ->
        "You're over today's limit. Every 10 minutes costs a hold, and each hold is longer than the last."
    decision.tier == Tier.GREY -> "You've used three quarters of today's limit."
    else -> "You've used half of today's limit."
}

private fun minutes(d: Duration): String = if (d.toMinutes() == 1L) "1 minute" else "${d.toMinutes()} minutes"

/** Today's paid time as a hero number, then where it sits on the toll ladder. */
@Composable
private fun UsageBlock(decision: Decision) {
    val p = LocalTollPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                decision.paidToday.short(),
                fontFamily = Overpass,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 34.sp,
                color = p.ink,
            )
            Text(
                "  of ${decision.limit.short()} today",
                style = MaterialTheme.typography.bodyLarge,
                color = p.muted,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        LadderMeter(decision.paidToday, decision.limit)
        Text(
            "${Sentences.ordinal(decision.opensToday.coerceAtLeast(1))} time in Instagram today",
            style = MaterialTheme.typography.bodyMedium,
            color = p.muted,
        )
    }
}

// ---- the hold ----

/**
 * Keep a thumb on a slowly drifting dot for [required]. Slipping off pauses; more than
 * [Rules.HOLD_RESET_AFTER] off the dot starts it over. The progress logic is [Hold], shared with the tests.
 */
@Composable
fun HoldScreen(hold: Hold, onChange: (Hold) -> Unit, onComplete: () -> Unit, onGiveUp: () -> Unit) {
    val p = LocalTollPalette.current
    val insets = LocalOverlayInsets.current
    val density = LocalDensity.current
    val required = hold.required
    // The hold lives in TollOverlays, so it survives the window being hidden for a moment.
    val current by rememberUpdatedState(hold)
    val change by rememberUpdatedState(onChange)
    val complete by rememberUpdatedState(onComplete)
    var now by remember { mutableStateOf(Instant.now()) }
    var t by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(required) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { frame ->
                t = (frame - start) / 1_000_000_000f
                now = Instant.now()
            }
            if (current.isComplete(now)) {
                complete()
                break
            }
        }
    }

    val dotRadius = with(density) { 30.dp.toPx() }
    fun dotCenter(size: Size): Offset = Offset(
        x = size.width / 2 + size.width * 0.3f * sin(t * 0.21f),
        y = size.height * 0.55f + size.height * 0.16f * sin(t * 0.29f + 1f),
    )

    val progress = hold.progressAt(now)
    val left = required - progress
    val offFor = hold.releasedAt?.let { Duration.between(it, now) }
    val status = when {
        hold.pressedSince != null -> "Keep going."
        offFor != null && progress > Duration.ZERO -> {
            val secondsLeft = (Rules.HOLD_RESET_AFTER - offFor).seconds.coerceAtLeast(0)
            "Off the dot. Back on within $secondsLeft s or it starts over."
        }
        else -> "Put your thumb on the dot and follow it."
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(p.background)
            .pointerInput(required) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fun onDot(position: Offset): Boolean {
                        val c = dotCenter(Size(size.width.toFloat(), size.height.toFloat()))
                        return hypot(position.x - c.x, position.y - c.y) <= dotRadius * 1.8f
                    }
                    var touching = onDot(down.position)
                    if (touching) change(current.press(Instant.now()))
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        val nowTouching = change.pressed && onDot(change.position)
                        if (nowTouching != touching) {
                            change(if (nowTouching) current.press(Instant.now()) else current.release(Instant.now()))
                            touching = nowTouching
                        }
                        if (event.changes.none { it.pressed }) break
                    }
                    if (touching) change(current.release(Instant.now()))
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = dotCenter(size)
            val ring = dotRadius + 12.dp.toPx()
            val fraction = progress.toMillis().toFloat() / required.toMillis().coerceAtLeast(1)
            drawCircle(p.line, radius = ring, center = c, style = Stroke(5.dp.toPx()))
            drawArc(
                color = p.go,
                startAngle = -90f,
                sweepAngle = 360f * fraction,
                useCenter = false,
                topLeft = Offset(c.x - ring, c.y - ring),
                size = Size(ring * 2, ring * 2),
                style = Stroke(5.dp.toPx(), cap = StrokeCap.Round),
            )
            drawCircle(if (hold.pressedSince != null) p.go else p.ink, radius = dotRadius, center = c)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = insets.top + 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("TOLL", style = MaterialTheme.typography.labelSmall, color = p.muted)
            Text("Hold the dot", style = MaterialTheme.typography.headlineSmall, color = p.ink)
            Text(
                left.clock(),
                fontFamily = Overpass,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 48.sp,
                color = p.ink,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(status, style = MaterialTheme.typography.bodyMedium, color = if (offFor != null) p.amber else p.muted)
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = insets.bottom + 16.dp, start = 24.dp, end = 24.dp),
        ) {
            QuietButton("Give up and go back", onGiveUp)
        }
    }
}

// ---- self-protection ----

/** Which Android page the guard is covering. */
enum class GuardKind {
    /** Toll's accessibility page, App info, or the uninstall dialog. */
    PROTECTED,

    /** Advanced Protection, which would switch Toll off at once. Never blocked, only explained. */
    ADVANCED_PROTECTION,
}

/**
 * Shown over Android's Settings when a page would switch Toll off. For Toll's own pages the way out is the
 * 24-hour request in the app; Advanced Protection is a security feature, so it always gets a plain Continue.
 */
@Composable
fun GuardScreen(kind: GuardKind, turnOffAt: Instant?, onBack: () -> Unit, onOpenToll: () -> Unit, onContinue: () -> Unit) {
    val p = LocalTollPalette.current
    val insets = LocalOverlayInsets.current
    Column(
        Modifier
            .fillMaxSize()
            .background(p.background)
            .padding(top = insets.top),
    ) {
        BarrierStripe(Modifier.fillMaxWidth().height(14.dp))
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = insets.bottom + 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("TOLL", style = MaterialTheme.typography.labelSmall, color = p.muted)
            when (kind) {
                GuardKind.PROTECTED -> {
                    Text("Toll is protected", style = MaterialTheme.typography.headlineSmall, color = p.ink)
                    Text(
                        "Switching Toll off or uninstalling it goes through the Toll app and takes " +
                            "${Rules.TURN_OFF_DELAY.toHours()} hours, so it never happens on impulse.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = p.muted,
                    )
                    if (turnOffAt != null) {
                        Text(
                            "Your turn-off request takes effect on " +
                                "${turnOffAt.atZone(ZoneId.systemDefault()).format(GUARD_WHEN)}.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = p.amber,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    BigButton(if (turnOffAt == null) "Open Toll" else "Open Toll to see it", onOpenToll, container = p.ink, content = p.background)
                    QuietButton("Go back", onBack)
                }
                GuardKind.ADVANCED_PROTECTION -> {
                    Text("This switches Toll off", style = MaterialTheme.typography.headlineSmall, color = p.ink)
                    Text(
                        "Turning on Advanced Protection stops Toll straight away. That's your call: it's a security " +
                            "feature. To pause Toll instead, request a turn-off in the Toll app; it takes " +
                            "${Rules.TURN_OFF_DELAY.toHours()} hours.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = p.muted,
                    )
                    Spacer(Modifier.weight(1f))
                    BigButton("Go back", onBack, container = p.ink, content = p.background)
                    QuietButton("Continue", onContinue)
                }
            }
        }
    }
}

private val GUARD_WHEN = DateTimeFormatter.ofPattern("EEE d MMM 'at' HH:mm")

// ---- "Still here?" ----

@Composable
fun StillHereScreen(decision: Decision, onContinue: () -> Unit, onLeave: () -> Unit) {
    val p = LocalTollPalette.current
    val insets = LocalOverlayInsets.current
    Column(
        Modifier
            .fillMaxSize()
            .background(p.background.copy(alpha = 0.94f))
            .padding(start = 24.dp, end = 24.dp, top = insets.top + 48.dp, bottom = insets.bottom + 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("TOLL", style = MaterialTheme.typography.labelSmall, color = p.muted)
        Text("Still here?", fontFamily = Overpass, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, color = p.ink)
        Text(
            "${decision.paidToday.short()} on Instagram today. Your limit is ${decision.limit.short()}.",
            style = MaterialTheme.typography.bodyLarge,
            color = p.muted,
        )
        Spacer(Modifier.weight(1f))
        BigButton("Leave Instagram", onLeave, container = p.ink, content = p.background)
        QuietButton("Keep scrolling", onContinue)
    }
}

// ---- dials (top-left, translucent, never touchable) ----

/** Today's paid time against the limit, shown from half the limit. The ring fills toward the limit. */
@Composable
fun TimerDial(decision: Decision) {
    val left = decision.limit - decision.paidToday
    val fraction = decision.paidToday.toMillis().toFloat() / decision.limit.toMillis().coerceAtLeast(1)
    val ring = if (decision.tier >= Tier.GREY) LadderRamp[3] else LadderRamp[2]
    Dial(
        fraction = fraction,
        ring = ring,
        value = if (left.isNegative) "+${left.negated().toMinutes()}" else "${left.toMinutes()}",
        unit = if (left.isNegative) "over" else "min",
    )
}

/** Quick-pass countdown; the ring empties, and turns red for the last 30 seconds. */
@Composable
fun QuickPassDial(left: Duration, length: Duration) {
    val p = LocalTollPalette.current
    val ending = left <= Rules.QUICK_PASS_WARNING
    Dial(
        fraction = left.toMillis().toFloat() / length.toMillis().coerceAtLeast(1),
        ring = if (ending) LadderRamp[3] else p.go,
        value = left.clock(),
        unit = "pass",
    )
}

@Composable
private fun Dial(fraction: Float, ring: Color, value: String, unit: String) {
    Box(
        Modifier
            .size(52.dp)
            .alpha(0.9f)
            .background(Color(0xB316191D), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().padding(4.dp)) {
            val stroke = 3.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(Color(0x33FFFFFF), -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(ring, -90f, 360f * fraction.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                value,
                fontFamily = Overpass,
                fontWeight = FontWeight.ExtraBold,
                fontSize = if (value.length > 3) 12.sp else 15.sp,
                color = Color(0xFFF4F5F6),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(unit, fontFamily = Overpass, fontSize = 8.sp, color = Color(0xFFB8BDC4))
        }
    }
}

// ---- buttons ----

@Composable
private fun BigButton(label: String, onClick: () -> Unit, container: Color, content: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = content, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun FreeButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalTollPalette.current
    Column(
        modifier
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.5.dp, p.go, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = p.ink)
        Text("free", style = MaterialTheme.typography.labelSmall, color = p.go)
    }
}

@Composable
private fun QuietButton(label: String, onClick: () -> Unit) {
    val p = LocalTollPalette.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = p.muted)
    }
}
