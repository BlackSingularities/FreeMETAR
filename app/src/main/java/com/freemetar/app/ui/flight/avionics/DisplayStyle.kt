package com.freemetar.app.ui.flight.avionics

import com.freemetar.app.data.link.FlightState
import com.freemetar.app.domain.AircraftProfile

/**
 * Styl wyświetlaczy (jak w OpenPFD): PFD, MFD/ND i panel analogowy. Każdy profil samolotu ma dokładnie
 * jeden styl, więc wyświetlacze zawsze pasują do autopilota i paneli tego samego profilu.
 */
enum class DisplayStyle(val label: String) {
    AIRBUS("A320"), BOEING("737"), B787("787"), ATR("ATR 72"), EMBRAER("E190"),
    GARMIN("G1000"), CESSNA("C172"), HELI("R44"), GLIDER("Szybowiec");

    /** Panel zegarów analogowych (rysowany na całym polu, bez wirtualnego kwadratu PFD). */
    val analog get() = this == CESSNA || this == HELI || this == GLIDER

    companion object {
        fun of(p: AircraftProfile): DisplayStyle = when (p) {
            AircraftProfile.A320 -> AIRBUS
            AircraftProfile.B737 -> BOEING
            AircraftProfile.B787 -> B787
            AircraftProfile.ATR72 -> ATR
            AircraftProfile.E190 -> EMBRAER
            AircraftProfile.G1000 -> GARMIN
            AircraftProfile.C172 -> CESSNA
            AircraftProfile.R44 -> HELI
            AircraftProfile.GLIDER -> GLIDER
        }

        /** Ręcznie wybrany profil ma pierwszeństwo, inaczej rozpoznany z danych samolotu. */
        fun of(s: FlightState, profile: AircraftProfile?): DisplayStyle = of(profile ?: s.detectedProfile())
    }
}

/** Profil rozpoznany z danych samolotu przysłanych przez Link. */
fun FlightState.detectedProfile(): AircraftProfile =
    AircraftProfile.detect(aircraft.title, aircraft.atcModel, aircraft.atcType, aircraft.category, aircraft.engineType)
