package com.freemetar.app.domain

/**
 * Profil panelu sterowania dopasowany do samolotu. Każdy ma inny układ autopilota,
 * jak w prawdziwych kabinach: FCU Airbusa, MCP Boeinga, autopilot Garmin, prosty autopilot GA.
 */
enum class AircraftProfile(private val labelOf: () -> String, val short: String) {
    AIRBUS({ "Airbus (FCU)" }, "FCU"),
    BOEING({ "Boeing (MCP)" }, "MCP"),
    BIZJET({ loc("Odrzutowiec dyspozycyjny (Garmin)", "Business jet (Garmin)") }, "G3000"),
    TURBOPROP({ loc("Turbośmigłowy (Garmin)", "Turboprop (Garmin)") }, "G1000"),
    PISTON({ loc("Tłokowy GA", "Piston GA") }, "GA"),
    ;

    /** Nazwa w bieżącym języku (lambda, bo enum powstaje raz). */
    val label: String get() = labelOf()

    /** Czy profil ma automat ciągu (A/THR, A/T). */
    val hasAutothrottle get() = this == AIRBUS || this == BOEING || this == BIZJET
    /** Czy pokazywać spoilery i automatyczne hamowanie. */
    val hasSpoilers get() = this != PISTON && this != TURBOPROP

    companion object {
        private val airbus = Regex("""\b(airbus|a3[0-9]{2}|a2[0-9]{2}|a220|fenix|fbw|flybywire)""", RegexOption.IGNORE_CASE)
        private val boeing = Regex("""\b(boeing|b?7[0-9]7|pmdg|md-?11|md-?8[0-9])""", RegexOption.IGNORE_CASE)

        /**
         * Wybór z danych Link: najpierw producent w tytule/modelu (Airbus/Boeing),
         * potem kategoria i typ silnika. Bez danych — tłokowy GA jako najprostszy.
         */
        fun detect(title: String?, atcModel: String?, category: String?, engineType: String?): AircraftProfile {
            val text = listOfNotNull(title, atcModel).joinToString(" ")
            return when {
                airbus.containsMatchIn(text) -> AIRBUS
                boeing.containsMatchIn(text) -> BOEING
                category == "airliner" -> if (engineType == "turboprop") TURBOPROP else AIRBUS
                category == "bizjet" || engineType == "jet" -> BIZJET
                category == "turboprop" || engineType == "turboprop" -> TURBOPROP
                else -> PISTON
            }
        }

        fun fromName(name: String?): AircraftProfile? = entries.firstOrNull { it.name == name }
    }
}
