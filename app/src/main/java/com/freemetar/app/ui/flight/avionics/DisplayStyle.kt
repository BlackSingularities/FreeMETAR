package com.freemetar.app.ui.flight.avionics

import com.freemetar.app.data.link.FlightState
import com.freemetar.app.domain.AircraftProfile

/**
 * Styl wyświetlaczy dopasowany do samolotu (jak w OpenPFD): PFD, MFD/ND i panel analogowy.
 * Wybrany ręcznie profil samolotu ma pierwszeństwo — zmiana profilu zmienia też wyświetlacze.
 */
enum class DisplayStyle(val label: String) {
    AIRBUS("A320"), BOEING("737"), B787("787"), ATR("ATR 72"), EMBRAER("E190"),
    GARMIN("G1000"), CESSNA("C172"), HELI("R44"), GLIDER("Szybowiec");

    /** Panel zegarów analogowych (rysowany na całym polu, bez wirtualnego kwadratu PFD). */
    val analog get() = this == CESSNA || this == HELI || this == GLIDER

    companion object {
        private val heavyBoeing = Regex("""\b(b?78[789]|b?77[0-9w]|b?74[0-9]|dreamliner)""", RegexOption.IGNORE_CASE)
        private val atr = Regex("""\b(atr\s?-?\s?[47]2)""", RegexOption.IGNORE_CASE)
        private val primus = Regex("""\b(e-?1[79]0|e-?1[79]5|erj|embraer|crj|q400|dash ?8|dhc-?8)""", RegexOption.IGNORE_CASE)
        private val glider = Regex("""\b(glider|szybowiec|ask ?2[1-3]|ls ?[468]|discus|dg-?[0-9]{3}|arcus|duo|ventus|nimbus|swift s1|astir|jantar|pirat|bocian|junior|grob 10[23])""", RegexOption.IGNORE_CASE)
        /** Samoloty z zegarami analogowymi w MSFS (bez szklanego kokpitu). */
        private val classic = Regex("""\b(classic|steam|analog|c152|cessna 152|cub|j-?3|super cub|citabria|decathlon|extra ?330|cap ?10|zlin|savage|husky|aviat|pitts|dr1|stearman|spitfire|p-?51|vl3|icon a5|wilga|an-?2)""", RegexOption.IGNORE_CASE)

        fun of(s: FlightState, profile: AircraftProfile?): DisplayStyle {
            val a = s.aircraft
            val text = listOfNotNull(a.title, a.atcModel, a.atcType).joinToString(" ")
            if (a.category == "helicopter" || a.engineType == "helicopter") return HELI
            if (glider.containsMatchIn(text) || (a.engineType == "none" && a.category == "ga")) return GLIDER
            // Automatycznie: typy z własną awioniką rozpoznane po nazwie, zanim zadziała ogólny profil
            if (profile == null) {
                if (atr.containsMatchIn(text)) return ATR
                if (primus.containsMatchIn(text)) return EMBRAER
            }
            return when (profile ?: AircraftProfile.detect(a.title, a.atcModel, a.category, a.engineType)) {
                AircraftProfile.AIRBUS -> AIRBUS
                AircraftProfile.BOEING -> if (heavyBoeing.containsMatchIn(text)) B787 else BOEING
                AircraftProfile.BIZJET -> if (primus.containsMatchIn(text)) EMBRAER else GARMIN
                AircraftProfile.TURBOPROP -> when {
                    atr.containsMatchIn(text) -> ATR
                    primus.containsMatchIn(text) -> EMBRAER
                    a.category == "airliner" -> ATR
                    else -> GARMIN
                }
                AircraftProfile.PISTON -> if (classic.containsMatchIn(text)) CESSNA else GARMIN
            }
        }
    }
}
