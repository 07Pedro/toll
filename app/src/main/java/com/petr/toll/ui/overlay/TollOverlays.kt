package com.petr.toll.ui.overlay

import android.accessibilityservice.AccessibilityService
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.petr.toll.rules.Decision
import com.petr.toll.rules.Price
import com.petr.toll.ui.ChallengeActivity
import com.petr.toll.ui.TollTheme
import java.time.Duration

/**
 * Everything Toll draws over Instagram. The service calls [render] after every engine update; this class picks
 * the gate, "Still here?", a hold, or the dial, and keeps the windows and their composition alive between calls.
 */
class TollOverlays(private val service: AccessibilityService, private val actions: Actions) {

    interface Actions {
        /** Gate "Messages (free)": open Instagram's messages. */
        fun onFreeMessages()

        /** Gate "Stories (free)": open the first story. */
        fun onFreeStories()

        /** Go to the home screen. */
        fun onLeave()

        /** A challenge was completed: the toll is paid. */
        fun onTollPaid()

        fun onStillHereDismissed()
    }

    private var decision by mutableStateOf<Decision?>(null)

    /** Length of the hold running inside the gate, or null. */
    private var holding by mutableStateOf<Duration?>(null)

    /** The typing challenge is in front; the gate steps aside until it reports back. */
    private var typing = false

    private val full = OverlayWindow(service, OverlayWindow.Kind.FULL_SCREEN) { Full() }
    private val badge = OverlayWindow(service, OverlayWindow.Kind.BADGE) { Badge() }

    init {
        ChallengeResult.register { paid ->
            typing = false
            if (paid) actions.onTollPaid() else decision?.let(::render)
        }
    }

    fun render(decision: Decision) {
        this.decision = decision
        val wantsFull = !typing && (decision.gate != null || decision.stillHere)
        if (wantsFull) full.show() else {
            full.hide()
            holding = null
        }
        full.keepScreenOn(holding != null)
        val wantsBadge = !typing && !wantsFull && (decision.showTimer || decision.quickPassLeft != null)
        if (wantsBadge) badge.show() else badge.hide()
    }

    fun hideAll() {
        full.hide()
        badge.hide()
        holding = null
    }

    /** While true, the gate stays visible but touches reach Instagram, for an injected tap. */
    fun touchThrough(enabled: Boolean) = full.touchThrough(enabled)

    /** Call when the service unbinds. */
    fun release() {
        hideAll()
        ChallengeResult.clear()
    }

    private fun pay(price: Price) {
        when (price) {
            Price.None -> actions.onTollPaid()
            is Price.Typing -> {
                typing = true
                hideAll()
                ChallengeActivity.start(service, price.sentence)
            }
            is Price.QrAndHold -> {
                holding = price.hold
                full.keepScreenOn(true)
            }
        }
    }

    @Composable
    private fun Full() {
        val d = decision ?: return
        TollTheme(dark = true) {
            val gate = d.gate
            val hold = holding
            when {
                gate != null && hold != null -> HoldScreen(
                    required = hold,
                    onComplete = {
                        holding = null
                        full.keepScreenOn(false)
                        actions.onTollPaid()
                    },
                    onGiveUp = {
                        holding = null
                        full.keepScreenOn(false)
                    },
                )
                gate != null -> GateScreen(
                    decision = d,
                    gate = gate,
                    onMessages = actions::onFreeMessages,
                    onStories = actions::onFreeStories,
                    onPay = { pay(gate.price) },
                    onLeave = actions::onLeave,
                )
                d.stillHere -> StillHereScreen(d, onContinue = actions::onStillHereDismissed, onLeave = actions::onLeave)
            }
        }
    }

    @Composable
    private fun Badge() {
        val d = decision ?: return
        TollTheme(dark = true) {
            val passLeft = d.quickPassLeft
            if (passLeft != null) QuickPassDial(passLeft, d.quickPassLength) else if (d.showTimer) TimerDial(d)
        }
    }
}

/** How the typing challenge, an activity, reports back to the overlays in the same process. */
object ChallengeResult {
    private var listener: ((Boolean) -> Unit)? = null

    fun register(listener: (Boolean) -> Unit) {
        this.listener = listener
    }

    fun clear() {
        listener = null
    }

    fun deliver(paid: Boolean) {
        listener?.invoke(paid)
    }
}
