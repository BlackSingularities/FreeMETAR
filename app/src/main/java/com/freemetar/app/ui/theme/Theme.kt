package com.freemetar.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Paleta „kokpit nocą”: granat, błękit przyrządów, bursztyn ostrzeżeń
val Night = Color(0xFF0B1220)
val Panel = Color(0xFF131C2E)
val PanelHi = Color(0xFF1B2740)
val Line = Color(0xFF26344F)
val Sky = Color(0xFF38BDF8)
val Amber = Color(0xFFF59E0B)
val TextHi = Color(0xFFE6EDF7)
val TextLo = Color(0xFF8A9AB5)

val CatVfr = Color(0xFF22C55E)
val CatMvfr = Color(0xFF3B82F6)
val CatIfr = Color(0xFFEF4444)
val CatLifr = Color(0xFFD946EF)

fun categoryColor(cat: String?): Color = when (cat?.uppercase()) {
    "VFR" -> CatVfr
    "MVFR" -> CatMvfr
    "IFR" -> CatIfr
    "LIFR" -> CatLifr
    else -> TextLo
}

private val colors = darkColorScheme(
    primary = Sky,
    onPrimary = Night,
    secondary = Amber,
    onSecondary = Night,
    background = Night,
    onBackground = TextHi,
    surface = Panel,
    onSurface = TextHi,
    surfaceVariant = PanelHi,
    onSurfaceVariant = TextLo,
    surfaceContainer = Panel,
    surfaceContainerHigh = PanelHi,
    surfaceContainerHighest = PanelHi,
    outline = Line,
    outlineVariant = Line,
    error = CatIfr,
)

val Mono = FontFamily.Monospace

private val typography = Typography().let { t ->
    t.copy(
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = t.labelSmall.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold),
    )
}

val RawText = TextStyle(fontFamily = Mono, fontSize = 14.sp, lineHeight = 21.sp)

@Composable
fun FreeMetarTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
