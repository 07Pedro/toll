package com.petr.toll.probe

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.petr.toll.R
import com.petr.toll.classifier.Access
import com.petr.toll.ui.Walkthrough
import kotlin.math.abs

/**
 * The probe's floating panel over Instagram: live guess, which walkthrough step is next, and the buttons.
 * Tap the top row to fold it to a single line; drag the top row to move it.
 * An accessibility overlay needs no "display over other apps" permission. Plain Views keep it free of Compose's
 * lifecycle setup, which a service window doesn't have.
 */
class ProbeOverlay(private val service: AccessibilityService, private val actions: Actions) {

    interface Actions {
        fun onDump()
        fun onSkip()
        fun onOpenDms()
        fun onOpenFirstStory()
    }

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val params = WindowManager.LayoutParams(
        dp(PANEL_WIDTH_DP),
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = dp(12)
        y = dp(120)
    }

    private val dumps = DumpStore(service)
    private val regular: Typeface = service.resources.getFont(R.font.overpass_400)
    private val semibold: Typeface = service.resources.getFont(R.font.overpass_600)
    private val heavy: Typeface = service.resources.getFont(R.font.overpass_800)

    private var panel: LinearLayout? = null
    private lateinit var dot: View
    private lateinit var title: TextView
    private lateinit var fold: TextView
    private lateinit var body: LinearLayout
    private lateinit var stepLabel: TextView
    private lateinit var stepText: TextView
    private lateinit var stepActions: LinearLayout
    private lateinit var testActions: LinearLayout
    private lateinit var status: TextView
    private lateinit var details: TextView

    private var collapsed = false
    /** A button press is waiting for the service's answer; more presses are ignored until then. */
    private var busyUntil = 0L
    private var saved = 0
    private val hideStatus = Runnable { status.visibility = View.GONE }

    fun show() {
        if (panel != null) return
        panel = buildPanel().also { windowManager.addView(it, params) }
        applyCollapsed()
        refreshProgress()
    }

    fun hide() {
        val view = panel ?: return
        status.removeCallbacks(hideStatus)
        windowManager.removeView(view)
        panel = null
    }

    fun render(headline: String, access: Access, detailText: String) {
        if (panel == null) return
        title.text = headline
        (dot.background as GradientDrawable).setColor(colorFor(access))
        details.text = detailText
    }

    fun setStatus(message: String) {
        if (panel == null) return
        busyUntil = 0L
        val before = saved
        refreshProgress()
        // A new save or skip shows as progress instead of the file name.
        status.text = when {
            saved <= before -> message
            message.startsWith("Skipped") -> "Skipped step $saved. ${nextHint()}"
            else -> "Saved step $saved. ${nextHint()}"
        }
        status.visibility = View.VISIBLE
        status.removeCallbacks(hideStatus)
        status.postDelayed(hideStatus, STATUS_MS)
    }

    private fun refreshProgress() {
        saved = dumps.count()
        val total = Walkthrough.STEPS.size
        val finished = saved >= total
        if (!finished) {
            stepLabel.text = "STEP ${saved + 1} OF $total"
            stepText.text = Walkthrough.STEPS[saved].text
        } else {
            stepLabel.text = "ALL $total STEPS DONE · BUTTON TESTS"
            stepText.text = "On your feed, scrolled to the top, tap Open a story: Toll should open a friend's " +
                "story by itself. Then go back and tap Open messages. Use these buttons, not Instagram's own."
        }
        // Nothing left to save or skip; the button tests take their place.
        stepActions.visibility = if (finished) View.GONE else View.VISIBLE
        testActions.visibility = if (finished) View.VISIBLE else View.GONE
    }

    private fun nextHint(): String =
        if (saved < Walkthrough.STEPS.size) "Next: ${Walkthrough.STEPS[saved].text}." else "That was the last step."

    private fun buildPanel(): LinearLayout {
        dot = View(service).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(colorFor(Access.UNKNOWN))
            }
            layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply { marginEnd = dp(10) }
        }
        title = text(15f, heavy, WHITE).apply {
            text = "Looking…"
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        fold = text(12f, semibold, MUTED).apply { setPadding(dp(10), dp(4), 0, dp(4)) }
        val header = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(2))
            addView(dot)
            addView(title)
            addView(fold)
        }
        makeDraggable(header)

        stepLabel = text(11f, semibold, AMBER).apply {
            letterSpacing = 0.1f
            setPadding(0, dp(12), 0, dp(2))
        }
        stepText = text(15f, regular, WHITE)
        val save = text(15f, heavy, INK).apply {
            text = "Save"
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(12))
            background = rounded(WHITE, dp(12))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f).apply { marginEnd = dp(8) }
            setOnClickListener { press("Saving…") { actions.onDump() } }
        }
        val skip = text(15f, semibold, WHITE).apply {
            text = "Skip"
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), CHIP_LINE)
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { press("Skipping…") { actions.onSkip() } }
        }
        stepActions = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = matchWidth(top = dp(12))
            addView(save)
            addView(skip)
        }
        testActions = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = matchWidth(top = dp(12))
            visibility = View.GONE
            addView(bigButton("Open a story") { press("Opening a story…") { actions.onOpenFirstStory() } })
            addView(bigButton("Open messages", top = dp(8)) { press("Opening messages…") { actions.onOpenDms() } })
        }
        val tools = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = matchWidth(top = dp(8))
            addView(chip("Test story") { press("Opening a story…") { actions.onOpenFirstStory() } })
            addView(chip("Test DMs") { press("Opening messages…") { actions.onOpenDms() } })
            addView(chip("Details") { details.visibility = if (details.visibility == View.GONE) View.VISIBLE else View.GONE })
        }
        status = text(13f, semibold, AMBER).apply {
            setPadding(0, dp(10), 0, 0)
            visibility = View.GONE
        }
        details = text(11f, Typeface.MONOSPACE, MUTED).apply {
            setPadding(0, dp(8), 0, 0)
            visibility = View.GONE
        }
        body = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            addView(stepLabel)
            addView(stepText)
            addView(stepActions)
            addView(testActions)
            addView(tools)
            addView(status)
            addView(details)
        }
        return LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(PANEL, dp(18))
            elevation = dp(8).toFloat()
            setPadding(dp(14), dp(12), dp(14), dp(14))
            addView(header)
            addView(body)
        }
    }

    /**
     * Shows [working] at once, then runs [action]. Reading Instagram can take a second or more, so without instant
     * feedback a press looks ignored and gets repeated. Presses during that wait are dropped.
     */
    private fun press(working: String, action: () -> Unit) {
        val now = SystemClock.uptimeMillis()
        if (now < busyUntil) return
        busyUntil = now + BUSY_MS
        status.text = working
        status.visibility = View.VISIBLE
        status.removeCallbacks(hideStatus)
        status.postDelayed(hideStatus, STATUS_MS)
        action()
    }

    private fun toggleCollapsed() {
        collapsed = !collapsed
        applyCollapsed()
    }

    private fun applyCollapsed() {
        body.visibility = if (collapsed) View.GONE else View.VISIBLE
        fold.text = if (collapsed) "Show" else "Hide"
        params.width = if (collapsed) WindowManager.LayoutParams.WRAP_CONTENT else dp(PANEL_WIDTH_DP)
        title.layoutParams = if (collapsed) {
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        } else {
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        title.maxWidth = if (collapsed) dp(200) else Int.MAX_VALUE
        panel?.let { windowManager.updateViewLayout(it, params) }
    }

    /** Drag the top row to move the panel; tap it to fold or unfold. */
    @SuppressLint("ClickableViewAccessibility")
    private fun makeDraggable(handle: View) {
        val slop = ViewConfiguration.get(service).scaledTouchSlop
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var dragging = false
        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) dragging = true
                    if (dragging) {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        panel?.let { windowManager.updateViewLayout(it, params) }
                    }
                }
                MotionEvent.ACTION_UP -> if (!dragging) toggleCollapsed()
            }
            true
        }
    }

    // ---- building blocks ----

    private fun text(sizeSp: Float, face: Typeface, color: Int) = TextView(service).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        typeface = face
        setTextColor(color)
        includeFontPadding = false
        setLineSpacing(0f, 1.15f)
    }

    private fun bigButton(label: String, top: Int = 0, onClick: () -> Unit) = text(15f, heavy, INK).apply {
        text = label
        gravity = Gravity.CENTER
        setPadding(0, dp(12), 0, dp(12))
        background = rounded(WHITE, dp(12))
        layoutParams = matchWidth(top = top)
        setOnClickListener { onClick() }
    }

    private fun chip(label: String, onClick: () -> Unit) = text(13f, semibold, WHITE).apply {
        text = label
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(8), dp(12), dp(8))
        background = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setStroke(dp(1), CHIP_LINE)
        }
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(6)
        }
        setOnClickListener { onClick() }
    }

    private fun matchWidth(top: Int) =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = top
        }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        cornerRadius = radius.toFloat()
        setColor(color)
    }

    private fun colorFor(access: Access): Int = when (access) {
        Access.FREE -> GO
        Access.PAID -> BARRIER
        Access.UNKNOWN -> MUTED
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), service.resources.displayMetrics).toInt()

    private companion object {
        const val PANEL_WIDTH_DP = 272
        const val STATUS_MS = 6000L
        /** Longest a press blocks repeats if the service never answers. */
        const val BUSY_MS = 3000L

        // Always dark: the panel sits on top of Instagram in either theme. Same values as the app's dark palette.
        const val PANEL = 0xF2161A1F.toInt()
        const val WHITE = 0xFFF4F5F6.toInt()
        const val INK = 0xFF16191D.toInt()
        const val MUTED = 0xFF9AA0A8.toInt()
        const val GO = 0xFF3FC28A.toInt()
        const val BARRIER = 0xFFFF5A5F.toInt()
        const val AMBER = 0xFFF5B83D.toInt()
        const val CHIP_LINE = 0x40FFFFFF
    }
}
