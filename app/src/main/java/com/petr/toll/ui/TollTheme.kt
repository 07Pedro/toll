package com.petr.toll.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.petr.toll.R

// Toll's look: a toll gate. Concrete greys, barrier red for "you pay here", go green for free,
// signal amber for attention. Type is Overpass, drawn after the lettering on motorway signs.

@Immutable
data class TollPalette(
    val background: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    /** Paid, and Toll's brand colour. */
    val barrier: Color,
    /** Free. */
    val go: Color,
    /** Attention: test mode, warnings. */
    val amber: Color,
    /** Unrecognised. */
    val slate: Color,
)

val LightPalette = TollPalette(
    background = Color(0xFFEFF0EC),
    surface = Color(0xFFFFFFFF),
    ink = Color(0xFF16191D),
    muted = Color(0xFF5C6168),
    line = Color(0xFFD9DBD4),
    barrier = Color(0xFFD62839),
    go = Color(0xFF1F8A5B),
    amber = Color(0xFFB87A00),
    slate = Color(0xFF6B7280),
)

val DarkPalette = TollPalette(
    background = Color(0xFF111316),
    surface = Color(0xFF1B1E23),
    ink = Color(0xFFECEEF0),
    muted = Color(0xFF9AA0A8),
    line = Color(0xFF2E3238),
    barrier = Color(0xFFFF5A5F),
    go = Color(0xFF3FC28A),
    amber = Color(0xFFF5B83D),
    slate = Color(0xFF9AA0A8),
)

val LocalTollPalette = staticCompositionLocalOf { LightPalette }

val Overpass = FontFamily(
    Font(R.font.overpass_400, FontWeight.Normal),
    Font(R.font.overpass_600, FontWeight.SemiBold),
    Font(R.font.overpass_800, FontWeight.ExtraBold),
)

private val TollTypography = Typography(
    displaySmall = TextStyle(fontFamily = Overpass, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = 0.14.em),
    headlineSmall = TextStyle(fontFamily = Overpass, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = Overpass, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Overpass, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = Overpass, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Overpass, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = Overpass, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Overpass, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 16.sp, fontFeatureSettings = "tnum"),
    labelSmall = TextStyle(fontFamily = Overpass, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.1.em),
)

/**
 * The toll ladder as one red getting stronger: free, typing, grey, holds. Validated as an ordinal ramp on the
 * dark surface (one hue, monotone lightness, every step clears the background). Used on always-dark overlays.
 */
val LadderRamp = listOf(Color(0xFF6E3A3F), Color(0xFF9E464C), Color(0xFFD05158), Color(0xFFFF6B70))

@Composable
fun TollTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val p = if (dark) DarkPalette else LightPalette
    val colors = if (dark) {
        darkColorScheme(
            primary = p.barrier, onPrimary = Color.White,
            secondary = p.go, tertiary = p.amber, error = p.barrier,
            background = p.background, onBackground = p.ink,
            surface = p.surface, onSurface = p.ink,
            surfaceVariant = p.line, onSurfaceVariant = p.muted, outline = p.line,
        )
    } else {
        lightColorScheme(
            primary = p.barrier, onPrimary = Color.White,
            secondary = p.go, tertiary = p.amber, error = p.barrier,
            background = p.background, onBackground = p.ink,
            surface = p.surface, onSurface = p.ink,
            surfaceVariant = p.line, onSurfaceVariant = p.muted, outline = p.line,
        )
    }
    CompositionLocalProvider(LocalTollPalette provides p) {
        MaterialTheme(colorScheme = colors, typography = TollTypography, content = content)
    }
}

/** The red-and-white stripes of a toll barrier arm. */
@Composable
fun BarrierStripe(modifier: Modifier = Modifier) {
    val red = LocalTollPalette.current.barrier
    Canvas(modifier) {
        val stripe = 9.dp.toPx()
        val h = size.height
        clipRect {
            drawRect(Color(0xFFF6F6F4))
            var x = -h
            while (x < size.width) {
                val path = Path().apply {
                    moveTo(x, h)
                    lineTo(x + h, 0f)
                    lineTo(x + h + stripe, 0f)
                    lineTo(x + stripe, h)
                    close()
                }
                drawPath(path, red)
                x += stripe * 2
            }
        }
    }
}
