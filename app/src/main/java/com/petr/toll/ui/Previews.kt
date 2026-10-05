package com.petr.toll.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.petr.toll.rules.Decision
import com.petr.toll.rules.Gate
import com.petr.toll.rules.Hold
import com.petr.toll.rules.Limits
import com.petr.toll.rules.PendingChange
import com.petr.toll.rules.Price
import com.petr.toll.rules.Prices
import com.petr.toll.rules.SettingsPatch
import com.petr.toll.rules.Sentences
import com.petr.toll.rules.Tier
import com.petr.toll.rules.TollSettings
import com.petr.toll.ui.home.DaySummary
import com.petr.toll.ui.home.EarnState
import com.petr.toll.ui.home.HomeScreen
import com.petr.toll.ui.home.HomeState
import com.petr.toll.ui.home.SettingsScreen
import com.petr.toll.ui.home.TurnOffState
import com.petr.toll.ui.overlay.GateScreen
import com.petr.toll.ui.overlay.GuardKind
import com.petr.toll.ui.overlay.GuardScreen
import com.petr.toll.ui.overlay.HoldScreen
import com.petr.toll.ui.overlay.LocalOverlayInsets
import com.petr.toll.ui.overlay.OverlayInsets
import com.petr.toll.ui.overlay.QuickPassDial
import com.petr.toll.ui.overlay.StillHereScreen
import com.petr.toll.ui.overlay.TimerDial
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Every Toll screen with sample numbers, for checking the design without the service or Instagram.
 * Reached from Test mode. Nothing here touches real data.
 */
@Composable
fun ScreenPreviews(onExit: () -> Unit) {
    val pages = remember { previewPages() }
    var index by rememberSaveable { mutableIntStateOf(0) }
    val page = pages[index]
    val insets = OverlayInsets(
        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 56.dp,
    )
    Box(Modifier.fillMaxSize()) {
        TollTheme(dark = page.dark ?: androidx.compose.foundation.isSystemInDarkTheme()) {
            CompositionLocalProvider(LocalOverlayInsets provides insets) {
                Box(Modifier.fillMaxSize().background(LocalTollPalette.current.background)) { page.content() }
            }
        }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xE6000000))
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BarButton("‹") { index = (index - 1 + pages.size) % pages.size }
            Column(Modifier.weight(1f)) {
                Text(
                    "PREVIEW ${index + 1}/${pages.size} · SAMPLE NUMBERS",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFF5B83D),
                )
                Text(page.title, style = MaterialTheme.typography.labelMedium, color = Color.White)
            }
            BarButton("›") { index = (index + 1) % pages.size }
            BarButton("Close", onExit)
        }
    }
}

private class PreviewPage(val title: String, val dark: Boolean?, val content: @Composable () -> Unit)

@Composable
private fun BarButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}

// ---- sample data ----

private val TODAY: LocalDate = LocalDate.now()
private val NOW: Instant = Instant.now()
private fun min(m: Long): Duration = Duration.ofMinutes(m)

private fun sample(
    paid: Long,
    limit: Long,
    opens: Int = 9,
    gate: Gate? = null,
    stillHere: Boolean = false,
    quickPassLeft: Duration? = null,
    quickPassesLeft: Int = 3,
): Decision {
    val tier = Tier.of(min(paid), min(limit))
    return Decision(
        at = NOW,
        day = TODAY,
        limit = min(limit),
        paidToday = min(paid),
        tier = tier,
        opensToday = opens,
        gate = gate,
        stillHere = stillHere,
        greyscale = tier >= Tier.GREY,
        showTimer = tier >= Tier.TYPING,
        quickPassLeft = quickPassLeft,
        quickPassLength = min(3),
        earnedToday = Duration.ZERO,
        earnLeft = min(30),
        quickPassesLeft = quickPassesLeft,
        passLeft = null,
        nextChangeAt = null,
    )
}

private fun sampleSettings(weeksIn: Long) = TollSettings(startDate = TODAY.minusDays(7 * weeksIn + 2))

private fun sampleHome(
    enforcing: Boolean,
    today: Decision?,
    quickPassesLeft: Int = 2,
    turnOff: TurnOffState = TurnOffState.Running,
): HomeState {
    val settings = sampleSettings(1)
    val pattern = listOf(250L, 230, 290, 205, 180, 195, 310, 260, 170, 150, 165, 140, 175)
    val history = pattern.mapIndexed { i, paid ->
        val day = TODAY.minusDays((pattern.size - i).toLong())
        DaySummary(day, min(paid), Limits.limitFor(day, settings), 12)
    } + listOfNotNull(today?.let { DaySummary(TODAY, it.paidToday, it.limit, it.opensToday) })
    return HomeState(
        serviceOn = true,
        enforcing = enforcing,
        today = today?.copy(quickPassesLeft = quickPassesLeft),
        todayDate = TODAY,
        settings = settings,
        history = if (enforcing) history else emptyList(),
        pending = emptyList(),
        earn = EarnState(stepsAllowed = enforcing, steps = 420, stepsGoal = 1000, duolingo = min(3), duolingoGoal = min(5)),
        turnOff = turnOff,
    )
}

private fun previewPages(): List<PreviewPage> {
    val limit = Limits.limitFor(TODAY, sampleSettings(1)).toMinutes()
    val typingGate = Gate(
        price = Price.Typing(Sentences.short(9)),
        reflex = false,
        ifBackSoon = Prices.entry(Tier.TYPING, true, 10, min(100), 1),
    )
    val reflexGate = Gate(
        price = Price.Typing(Sentences.long(10, min(112))),
        reflex = true,
        ifBackSoon = Price.QrAndHold(min(1)),
    )
    val overGate = Gate(price = Price.QrAndHold(min(4)), reflex = false, ifBackSoon = Price.QrAndHold(min(8)))
    return listOf(
        PreviewPage("Gate: typing (half the limit)", dark = true) {
            GateScreen(sample(100, limit, gate = typingGate), typingGate, {}, {}, {}, {})
        },
        PreviewPage("Gate: came back within 10 min", dark = true) {
            GateScreen(sample(112, limit, opens = 10, gate = reflexGate), reflexGate, {}, {}, {}, {})
        },
        PreviewPage("Gate: over the limit", dark = true) {
            GateScreen(sample(limit + 25, limit, opens = 17, gate = overGate), overGate, {}, {}, {}, {})
        },
        PreviewPage("Hold: try it, the dot moves", dark = true) {
            var hold by remember { mutableStateOf(Hold(min(4))) }
            HoldScreen(hold, onChange = { hold = it }, onComplete = { hold = Hold(min(4)) }, onGiveUp = {})
        },
        PreviewPage("Typing toll: try it", dark = true) {
            TypingScreen(Sentences.long(14, min(140)), onDone = {}, onCancel = {})
        },
        PreviewPage("Still here?", dark = true) {
            StillHereScreen(sample(140, limit, stillHere = true), {}, {})
        },
        PreviewPage("Timer dial and quick pass, top left", dark = null) { DialSheet(limit) },
        PreviewPage("Home: before Start", dark = null) {
            HomeScreen(sampleHome(enforcing = false, today = null), {}, {}, {}, {}, {}, {}, {}, {}, {})
        },
        PreviewPage("Home: a day in progress", dark = null) {
            HomeScreen(sampleHome(enforcing = true, today = sample(108, limit, opens = 11)), {}, {}, {}, {}, {}, {}, {}, {}, {})
        },
        PreviewPage("Home: no quick passes left", dark = null) {
            HomeScreen(sampleHome(enforcing = true, today = sample(170, limit, opens = 19), quickPassesLeft = 0), {}, {}, {}, {}, {}, {}, {}, {}, {})
        },
        PreviewPage("Settings, with a change waiting", dark = null) {
            val s = sampleSettings(1)
            SettingsScreen(
                current = s,
                pending = listOf(PendingChange(NOW, NOW.plus(Duration.ofHours(21)), SettingsPatch(quickPassesPerDay = 4))),
                turnOff = TurnOffState.Running,
                onSave = {},
                onRequestTurnOff = {},
                onCancelTurnOff = {},
                onBack = {},
            )
        },
        PreviewPage("Home: turning off in 23 h", dark = null) {
            val off = TurnOffState.Requested(NOW.minus(Duration.ofHours(1)), NOW.plus(Duration.ofHours(23)))
            HomeScreen(sampleHome(enforcing = true, today = sample(60, limit, opens = 5), turnOff = off), {}, {}, {}, {}, {}, {}, {}, {}, {})
        },
        PreviewPage("Home: Toll is off", dark = null) {
            val off = TurnOffState.Off(NOW.minus(Duration.ofHours(5)))
            HomeScreen(sampleHome(enforcing = false, today = null, turnOff = off), {}, {}, {}, {}, {}, {}, {}, {}, {})
        },
        PreviewPage("Guard: Toll's own settings", dark = true) {
            GuardScreen(GuardKind.PROTECTED, turnOffAt = null, onBack = {}, onOpenToll = {}, onContinue = {})
        },
        PreviewPage("Guard: Advanced Protection", dark = true) {
            GuardScreen(GuardKind.ADVANCED_PROTECTION, turnOffAt = null, onBack = {}, onOpenToll = {}, onContinue = {})
        },
    )
}

/** The dials as they'd sit over Instagram: on a mid-grey stand-in for a photo, at each stage. */
@Composable
private fun DialSheet(limit: Long) {
    val p = LocalTollPalette.current
    val insets = LocalOverlayInsets.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = insets.top + 16.dp, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        listOf<Pair<String, @Composable () -> Unit>>(
            "Half the limit" to { TimerDial(sample(limit / 2 + 5, limit)) },
            "Three quarters" to { TimerDial(sample(limit * 3 / 4 + 10, limit)) },
            "12 min over" to { TimerDial(sample(limit + 12, limit)) },
            "Quick pass" to { QuickPassDial(Duration.ofSeconds(161), min(3)) },
            "Quick pass ending" to { QuickPassDial(Duration.ofSeconds(21), min(3)) },
        ).forEach { (label, dial) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF8A8F86))
                        .padding(10.dp),
                ) { TollTheme(dark = true) { dial() } }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF2F2F2))
                        .padding(10.dp),
                ) { TollTheme(dark = true) { dial() } }
                Text(label, style = MaterialTheme.typography.bodyMedium, color = p.ink)
            }
        }
    }
}
