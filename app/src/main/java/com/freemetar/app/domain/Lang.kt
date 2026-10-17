package com.freemetar.app.domain

import java.util.Locale

enum class AppLang { PL, EN }

/**
 * Język aplikacji. Teksty są dwujęzyczne w miejscu użycia: `loc("polski", "English")` —
 * tak samo w interfejsie i w logice (dekoder METAR, ocena lądowania), bez zależności od Androida.
 * Domyślnie polski (testy JVM); aplikacja ustawia język przy starcie z ustawień lub systemu.
 */
object Lang {
    @Volatile
    var current: AppLang = AppLang.PL

    val isPl: Boolean get() = current == AppLang.PL

    /** Locale do formatowania dat i liczb w bieżącym języku. */
    val locale: Locale get() = if (isPl) Locale("pl", "PL") else Locale.UK

    /** Język wynikający z ustawienia: PL / EN / SYSTEM (polski dla systemu po polsku, inaczej angielski). */
    fun resolve(setting: String?, system: Locale = Locale.getDefault()): AppLang = when (setting) {
        "PL" -> AppLang.PL
        "EN" -> AppLang.EN
        else -> if (system.language == "pl") AppLang.PL else AppLang.EN
    }
}

/** Tekst w bieżącym języku. */
fun loc(pl: String, en: String): String = if (Lang.isPl) pl else en
