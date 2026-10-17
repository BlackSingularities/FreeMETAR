package com.freemetar.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.freemetar.app.R
import com.freemetar.app.data.ThemeMode

/**
 * Kolory zaczerpnięte z map lotniczych VFR: papier mapy, czarny druk,
 * magenta lotnisk i przestrzeni, błękit IFR, zieleń terenu.
 * Wersja nocna zachowuje role kolorów na ciemnym tle kokpitu.
 */
@Immutable
data class FmColors(
    val paper: Color,     // tło
    val card: Color,      // powierzchnia pasków i pól
    val terrain: Color,   // wyróżnione tło (objaśnienia, zaznaczenia)
    val ink: Color,       // tekst główny
    val ink2: Color,      // tekst drugorzędny
    val rule: Color,      // linie podziału
    val magenta: Color,   // akcent: lotniska, wybór, nawigacja
    val blue: Color,      // dane radiowe i wiatr
    val caution: Color,   // ostrzeżenia
    val vfr: Color,
    val mvfr: Color,
    val ifr: Color,
    val lifr: Color,
    val isNight: Boolean,
) {
    fun category(cat: String?): Color = when (cat?.uppercase()) {
        "VFR" -> vfr
        "MVFR" -> mvfr
        "IFR" -> ifr
        "LIFR" -> lifr
        else -> ink2
    }
}

val DayColors = FmColors(
    paper = Color(0xFFEDF1EE),
    card = Color(0xFFFAFCFB),
    terrain = Color(0xFFDDE7D6),
    ink = Color(0xFF16202B),
    ink2 = Color(0xFF55636E),
    rule = Color(0xFFC5D0CB),
    magenta = Color(0xFFA3195B),
    blue = Color(0xFF1D4E89),
    caution = Color(0xFFB45309),
    vfr = Color(0xFF2E8540),
    mvfr = Color(0xFF1D4E89),
    ifr = Color(0xFFC62828),
    lifr = Color(0xFFA3195B),
    isNight = false,
)

val NightColors = FmColors(
    paper = Color(0xFF0F161D),
    card = Color(0xFF151F28),
    terrain = Color(0xFF1B2A30),
    ink = Color(0xFFE4EBE8),
    ink2 = Color(0xFF8D9BA3),
    rule = Color(0xFF26333C),
    magenta = Color(0xFFE0558F),
    blue = Color(0xFF6FA3E0),
    caution = Color(0xFFF2A93B),
    vfr = Color(0xFF4CB863),
    mvfr = Color(0xFF6FA3E0),
    ifr = Color(0xFFEF5350),
    lifr = Color(0xFFE0558F),
    isNight = true,
)

val LocalFm = staticCompositionLocalOf { DayColors }

/** Skrót: `Fm.c.ink` zamiast `LocalFm.current.ink`. */
object Fm {
    val c: FmColors @Composable get() = LocalFm.current
}

// Barlow dla interfejsu (pełne polskie znaki), B612 Mono — krój kokpitów Airbusa — dla kodów
val Barlow = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_bold, FontWeight.Bold),
)
val Mono = FontFamily(
    Font(R.font.b612mono_regular, FontWeight.Normal),
    Font(R.font.b612mono_bold, FontWeight.Bold),
)

/** Tekst depesz: METAR, TAF, kody ICAO, częstotliwości. */
val RawText = TextStyle(fontFamily = Mono, fontSize = 14.sp, lineHeight = 22.sp)

private fun typography(): Typography {
    val base = Typography()
    fun TextStyle.b() = copy(fontFamily = Barlow)
    return Typography(
        displayLarge = base.displayLarge.b(), displayMedium = base.displayMedium.b(), displaySmall = base.displaySmall.b(),
        headlineLarge = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = (-0.3).sp),
        headlineMedium = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
        headlineSmall = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
        titleLarge = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 24.sp),
        titleMedium = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
        titleSmall = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
        bodyLarge = TextStyle(fontFamily = Barlow, fontSize = 16.sp, lineHeight = 23.sp),
        bodyMedium = TextStyle(fontFamily = Barlow, fontSize = 15.sp, lineHeight = 21.sp),
        bodySmall = TextStyle(fontFamily = Barlow, fontSize = 13.sp, lineHeight = 18.sp),
        labelLarge = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
        labelMedium = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Medium, fontSize = 13.sp),
        labelSmall = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Medium, fontSize = 12.sp),
    )
}

@Composable
fun FreeMetarTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val night = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DAY -> false
        ThemeMode.NIGHT -> true
    }
    val c = if (night) NightColors else DayColors
    val scheme = if (night) darkColorScheme(
        primary = c.magenta, onPrimary = Color.White, secondary = c.blue, onSecondary = Color.White,
        background = c.paper, onBackground = c.ink, surface = c.paper, onSurface = c.ink,
        surfaceVariant = c.terrain, onSurfaceVariant = c.ink2, surfaceContainer = c.card,
        surfaceContainerHigh = c.card, surfaceContainerHighest = c.terrain, surfaceContainerLow = c.card,
        outline = c.rule, outlineVariant = c.rule, error = c.ifr, secondaryContainer = c.terrain, onSecondaryContainer = c.ink,
    ) else lightColorScheme(
        primary = c.magenta, onPrimary = Color.White, secondary = c.blue, onSecondary = Color.White,
        background = c.paper, onBackground = c.ink, surface = c.paper, onSurface = c.ink,
        surfaceVariant = c.terrain, onSurfaceVariant = c.ink2, surfaceContainer = c.card,
        surfaceContainerHigh = c.card, surfaceContainerHighest = c.terrain, surfaceContainerLow = c.card,
        outline = c.rule, outlineVariant = c.rule, error = c.ifr, secondaryContainer = c.terrain, onSecondaryContainer = c.ink,
    )
    CompositionLocalProvider(LocalFm provides c) {
        MaterialTheme(colorScheme = scheme, typography = typography(), content = content)
    }
}
