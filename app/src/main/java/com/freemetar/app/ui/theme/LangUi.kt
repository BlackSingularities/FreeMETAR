package com.freemetar.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.freemetar.app.domain.AppLang

/**
 * Język w drzewie Compose — zmiana w ustawieniach przebudowuje cały interfejs.
 * Teksty w komponentach: `tr("polski", "English")`.
 */
val LocalLang = staticCompositionLocalOf { AppLang.PL }

@Composable
fun tr(pl: String, en: String): String = if (LocalLang.current == AppLang.PL) pl else en

/** Podpis autora — w ustawieniach, na ekranie startowym i w README. */
const val AUTHOR_SITE = "redzikowski.com"
const val AUTHOR_URL = "https://redzikowski.com"
