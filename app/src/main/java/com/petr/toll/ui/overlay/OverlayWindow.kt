package com.petr.toll.ui.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/** Status and navigation bar heights. Overlay windows don't get Compose's window insets, so we pass them in. */
data class OverlayInsets(val top: Dp, val bottom: Dp)

val LocalOverlayInsets = staticCompositionLocalOf { OverlayInsets(0.dp, 0.dp) }

/**
 * One Compose window over other apps, owned by the accessibility service. Accessibility overlays need no
 * "display over other apps" permission. A ComposeView outside an activity needs its own lifecycle and saved-state
 * owners, which this class provides. [show] and [hide] are cheap to call repeatedly: the window is added once and
 * the composition lives until [hide].
 */
class OverlayWindow(
    private val service: AccessibilityService,
    private val kind: Kind,
    private val content: @Composable () -> Unit,
) {
    enum class Kind {
        /** Covers the screen and takes touches: gate, "Still here?", holds. */
        FULL_SCREEN,

        /** Small, top-left, never takes touches: the timer dial. */
        BADGE,
    }

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val params = createParams()
    private var view: ComposeView? = null
    private var owner: Owner? = null

    val isShowing: Boolean get() = view != null

    fun show() {
        if (view != null) return
        val newOwner = Owner().also { it.start() }
        val insets = insets()
        val newView = ComposeView(service).apply {
            setViewTreeLifecycleOwner(newOwner)
            setViewTreeSavedStateRegistryOwner(newOwner)
            setContent { CompositionLocalProvider(LocalOverlayInsets provides insets, content = content) }
        }
        windowManager.addView(newView, params)
        view = newView
        owner = newOwner
    }

    fun hide() {
        val current = view ?: return
        windowManager.removeView(current)
        owner?.destroy()
        view = null
        owner = null
    }

    /** Lets touches pass through to the app underneath while staying visible, e.g. for an injected tap. */
    fun touchThrough(enabled: Boolean) {
        val flags = if (enabled) params.flags or NOT_TOUCHABLE else params.flags and NOT_TOUCHABLE.inv()
        if (flags == params.flags) return
        params.flags = flags
        view?.let { windowManager.updateViewLayout(it, params) }
    }

    /** Keeps the screen awake while a long hold runs. */
    fun keepScreenOn(enabled: Boolean) {
        val flags = if (enabled) params.flags or KEEP_ON else params.flags and KEEP_ON.inv()
        if (flags == params.flags) return
        params.flags = flags
        view?.let { windowManager.updateViewLayout(it, params) }
    }

    private fun createParams(): WindowManager.LayoutParams {
        val base = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        return when (kind) {
            Kind.FULL_SCREEN -> WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                base or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                fitInsetsTypes = 0
            }
            Kind.BADGE -> WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                base or NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = px(12)
                y = statusBarPx() + px(8)
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                fitInsetsTypes = 0
            }
        }
    }

    private fun insets(): OverlayInsets {
        val density = service.resources.displayMetrics.density
        val bars = windowManager.currentWindowMetrics.windowInsets
            .getInsets(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        return OverlayInsets(top = (bars.top / density).dp, bottom = (bars.bottom / density).dp)
    }

    private fun statusBarPx(): Int =
        windowManager.currentWindowMetrics.windowInsets.getInsets(WindowInsets.Type.statusBars()).top

    private fun px(dp: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), service.resources.displayMetrics).toInt()

    private class Owner : LifecycleOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val savedState = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

        fun start() {
            savedState.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }

    private companion object {
        const val NOT_TOUCHABLE = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        const val KEEP_ON = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
    }
}
