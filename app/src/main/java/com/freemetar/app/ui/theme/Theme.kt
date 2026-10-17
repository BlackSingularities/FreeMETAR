package com.freemetar.app.ui.theme

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
 * Szklany kokpit: kolory mają znaczenie jak w awionice.
 * cyjan — wartość wybrana / element do dotknięcia, magenta — aktywna trasa i nawigacja,
 * zieleń — tryb włączony / VFR, bursztyn — uwaga, czerwień — ostrzeżenie / IFR.
 */
@Immutable
data class FmColors(
    val paper: Color,     // tło ekranu
    val card: Color,      // panel
    val terrain: Color,   // panel wyżej / zaznaczenie
    val ink: Color,       // dane i tekst główny
    val ink2: Color,      // etykiety
    val rule: Color,      // linie i obwódki
    val accent: Color,    // cyjan: wybrane, interaktywne
    val magenta: Color,   // trasa, aktywna nawigacja
    val green: Color,     // tryb włączony
    val caution: Color,   // bursztyn
    val danger: Color,    // czerwień
    val vfr: Color,
    val mvfr: Color,
    val ifr: Color,
    val lifr: Color,
    val isNight: Boolean,
) {
    /** Zgodność ze starszym kodem: dane radiowe i wiatr w kolorze wartości wybranej. */
    val blue: Color get() = accent

    fun category(cat: String?): Color = when (cat?.uppercase()) {
        "VFR" -> vfr
        "MVFR" -> mvfr
        "IFR" -> ifr
        "LIFR" -> lifr
        else -> ink2
    }
}

/**
 * AMOLED — domyślny, w stylu OpenPDF: czysta czerń, biel jako akcent interfejsu (aktywny element
 * to biały blok z czarnym tekstem), czerwień tylko do usuwania i ostrzeżeń. Kolory znaczeniowe
 * zostają wyłącznie w danych (kategorie lotu, tryby AP, trasa, wartości wybrane).
 */
val AmoledColors = FmColors(
    paper = Color(0xFF000000),
    card = Color(0xFF000000),
    terrain = Color(0xFF1C1C1C),
    ink = Color(0xFFFFFFFF),
    ink2 = Color(0xB3FFFFFF),
    rule = Color(0x33FFFFFF),
    accent = Color(0xFF4FD1E8),
    magenta = Color(0xFFF25CD0),
    green = Color(0xFF3FE07A),
    caution = Color(0xFFFFB020),
    danger = Color(0xFFFF3B47),
    vfr = Color(0xFF3FE07A),
    mvfr = Color(0xFF4FA3FF),
    ifr = Color(0xFFFF3B47),
    lifr = Color(0xFFF25CD0),
    isNight = true,
)

/** Grafit — poprzedni „szklany kokpit” z głębią paneli. */
val CockpitColors = FmColors(
    paper = Color(0xFF1B2128),
    card = Color(0xFF232B34),
    terrain = Color(0xFF2C3640),
    ink = Color(0xFFE8EDF2),
    ink2 = Color(0xFF93A1AE),
    rule = Color(0xFF3A4652),
    accent = Color(0xFF38D6F5),
    magenta = Color(0xFFF25CD0),
    green = Color(0xFF3FE07A),
    caution = Color(0xFFFFB020),
    danger = Color(0xFFFF4D4D),
    vfr = Color(0xFF3FE07A),
    mvfr = Color(0xFF38D6F5),
    ifr = Color(0xFFFF4D4D),
    lifr = Color(0xFFF25CD0),
    isNight = true,
)

/** Jasny ekran — te same role kolorów, czytelne w słońcu. */
val DaylightColors = FmColors(
    paper = Color(0xFFE9EDF1),
    card = Color(0xFFFFFFFF),
    terrain = Color(0xFFDDE3E9),
    ink = Color(0xFF10161C),
    ink2 = Color(0xFF4F5D6A),
    rule = Color(0xFFC3CCD5),
    accent = Color(0xFF0587A6),
    magenta = Color(0xFFB5179E),
    green = Color(0xFF138A3E),
    caution = Color(0xFFB86E00),
    danger = Color(0xFFD32F2F),
    vfr = Color(0xFF138A3E),
    mvfr = Color(0xFF0587A6),
    ifr = Color(0xFFD32F2F),
    lifr = Color(0xFFB5179E),
    isNight = false,
)

val LocalFm = staticCompositionLocalOf { AmoledColors }

/** Skrót: `Fm.c.ink` zamiast `LocalFm.current.ink`. */
object Fm {
    val c: FmColors @Composable get() = LocalFm.current
}

// IBM Plex Sans Condensed: wąski, techniczny krój interfejsu; B612 Mono — krój kokpitów Airbusa — dla danych
val Plex = FontFamily(
    Font(R.font.plexcond_regular, FontWeight.Normal),
    Font(R.font.plexcond_medium, FontWeight.Medium),
    Font(R.font.plexcond_semibold, FontWeight.SemiBold),
    Font(R.font.plexcond_bold, FontWeight.Bold),
)
val Mono = FontFamily(
    Font(R.font.b612mono_regular, FontWeight.Normal),
    Font(R.font.b612mono_bold, FontWeight.Bold),
)

/** Tekst depesz i danych: METAR, TAF, kody ICAO, częstotliwości. */
val RawText = TextStyle(fontFamily = Mono, fontSize = 14.sp, lineHeight = 22.sp)

private fun typography(): Typography {
    val base = Typography()
    fun TextStyle.p() = copy(fontFamily = Plex)
    return Typography(
        displayLarge = base.displayLarge.p(), displayMedium = base.displayMedium.p(), displaySmall = base.displaySmall.p(),
        headlineLarge = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp),
        headlineMedium = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 23.sp, lineHeight = 28.sp),
        headlineSmall = TextStyle(fontFamily = Plex, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 25.sp),
        titleLarge = TextStyle(fontFamily = Plex, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 23.sp),
        titleMedium = TextStyle(fontFamily = Plex, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp),
        titleSmall = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 19.sp),
        bodyLarge = TextStyle(fontFamily = Plex, fontSize = 16.sp, lineHeight = 22.sp),
        bodyMedium = TextStyle(fontFamily = Plex, fontSize = 15.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontFamily = Plex, fontSize = 13.sp, lineHeight = 17.sp),
        labelLarge = TextStyle(fontFamily = Plex, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
        labelMedium = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 13.sp),
        labelSmall = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 12.sp),
    )
}

@Composable
fun OpenAirBookTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    // SYSTEM = AMOLED (domyślny), NIGHT = grafit, DAY = jasny ekran do pracy w słońcu
    val c = when (mode) {
        ThemeMode.DAY -> DaylightColors
        ThemeMode.NIGHT -> CockpitColors
        ThemeMode.SYSTEM -> AmoledColors
    }
    val scheme = if (c.isNight) darkColorScheme(
        // Biel jako akcent interfejsu: przyciski, przełączniki, pola w fokusie
        primary = c.ink, onPrimary = Color.Black, secondary = c.ink, onSecondary = Color.Black,
        background = c.paper, onBackground = c.ink, surface = c.paper, onSurface = c.ink,
        surfaceVariant = c.terrain, onSurfaceVariant = c.ink2, surfaceContainer = c.card,
        surfaceContainerHigh = c.card, surfaceContainerHighest = c.terrain, surfaceContainerLow = c.card,
        outline = c.rule, outlineVariant = c.rule, error = c.danger,
        // Wybrany chip / aktywny element: biały blok, czarny tekst
        secondaryContainer = c.ink, onSecondaryContainer = Color.Black,
    ) else lightColorScheme(
        primary = c.accent, onPrimary = Color.White, secondary = c.accent, onSecondary = Color.White,
        background = c.paper, onBackground = c.ink, surface = c.paper, onSurface = c.ink,
        surfaceVariant = c.terrain, onSurfaceVariant = c.ink2, surfaceContainer = c.card,
        surfaceContainerHigh = c.card, surfaceContainerHighest = c.terrain, surfaceContainerLow = c.card,
        outline = c.rule, outlineVariant = c.rule, error = c.danger, secondaryContainer = c.terrain, onSecondaryContainer = c.ink,
    )
    CompositionLocalProvider(LocalFm provides c) {
        MaterialTheme(colorScheme = scheme, typography = typography(), content = content)
    }
}
