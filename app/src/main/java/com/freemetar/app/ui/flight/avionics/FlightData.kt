package com.freemetar.app.ui.flight.avionics

/**
 * Migawka stanu samolotu dla rendererów PFD/MFD (przeniesionych z OpenPFD). Pola z OpenPFD zostały
 * pod tymi samymi nazwami, ale w FreeMETAR wypełnia je symulator przez Link ([fromSim]) — prędkość to IAS,
 * wysokość to wskazanie wysokościomierza. Brak danych: NaN (liczby) albo null.
 */
data class FlightData(
    val pitch: Float,          // stopnie, + nos w górę
    val roll: Float,           // stopnie, + prawe skrzydło w dół
    val heading: Float,        // kurs magnetyczny, stopnie
    val attValid: Boolean,
    val headingMagnetic: Boolean,
    val slip: Float,           // przyspieszenie poprzeczne / g (kulka)
    val speedKt: Float,        // prędkość przyrządowa (IAS), węzły
    val speedValid: Boolean,
    val speedTrend: Float,     // przyrost prędkości w ciągu 10 s, węzły
    val altFt: Float,          // wysokość wskazywana (względem QNH/STD), stopy
    val altValid: Boolean,
    val altFromBaro: Boolean,
    val vsFpm: Float,          // prędkość pionowa, ft/min
    val track: Float,          // kąt drogi magnetyczny
    val hasTrack: Boolean,
    val fpa: Float,            // kąt toru lotu, stopnie
    val qnh: Float,            // hPa
    val qnhSource: String,
    val std: Boolean,
    val inHg: Boolean,
    val fpv: Boolean,
    val gpsAccuracy: Float,
    val sats: Int,
    val gpsFix: Boolean,
    val raFt: Float = 0f,          // wysokość nad terenem (radiowysokościomierz), stopy
    val raValid: Boolean = false,
    val terrainFt: Float = Float.NaN, // poziom terenu w skali wysokościomierza
    val terrain: TerrainGrid? = null,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val mslFt: Float = 0f,
    val windDir: Float = 0f,       // kierunek wiatru magnetyczny („z”)
    val windKt: Float = 0f,
    val windValid: Boolean = false,
    val tasKt: Float = Float.NaN,
    val oatC: Float = Float.NaN,
    val drift: Float = 0f,         // kąt znosu = kąt drogi − kurs
    val gLoad: Float = 1f,
    val magVar: Float = 0f,        // deklinacja magnetyczna (E +)

    // ---- dane z symulatora (Link) ----
    val gsKt: Float = Float.NaN,
    val mach: Float = Float.NaN,
    val onGround: Boolean = false,
    /** Nastawy autopilota: prędkość (kt), Mach, wysokość, kurs, V/S; NaN = brak. */
    val spdBug: Float = Float.NaN,
    val machBug: Float = Float.NaN,
    val altBug: Float = Float.NaN,
    val hdgBug: Float = Float.NaN,
    val vsBug: Float = Float.NaN,
    val fma: Fma = Fma(),
    /** Dyrektor lotu: zadane pochylenie i przechylenie (bezwzględne). */
    val fdOn: Boolean = false,
    val fdPitch: Float = Float.NaN,
    val fdRoll: Float = Float.NaN,
    val nav: NavInfo = NavInfo(),
    val gps: GpsInfo = GpsInfo(),
    /** Namiary na stację NAV2 i NDB (bezwzględne, magnetyczne) do wskazówek RMI/BRG; NaN = brak sygnału. */
    val brg2: Float = Float.NaN,
    val brgAdf: Float = Float.NaN,
    val minimumsFt: Float = Float.NaN,
    /** 0 brak, 1 OM, 2 MM, 3 IM */
    val marker: Int = 0,
    /** Prędkości charakterystyczne (kt): przeciągnięcie w konfiguracji do lądowania / gładkiej, przelotowa, VMO. */
    val vs0: Float = Float.NaN,
    val vs1: Float = Float.NaN,
    val vc: Float = Float.NaN,
    val vmo: Float = Float.NaN,
    val stallWarn: Boolean = false,
    val overspeedWarn: Boolean = false,
    /** Przyrządy analogowe: zakręt (°/s), wskazania żyroskopów, ssanie. */
    val turnRate: Float = Float.NaN,
    val aiPitch: Float = Float.NaN,
    val aiRoll: Float = Float.NaN,
    val hiHeading: Float = Float.NaN,
    val suction: Float = Float.NaN,
    val com1: Float = Float.NaN,
    val com1Stby: Float = Float.NaN,
    val nav1: Float = Float.NaN,
    val nav1Stby: Float = Float.NaN,
    val squawk: String? = null,
    val xpdrMode: String? = null,
    val gearDown: Boolean? = null,
    val flapsIndex: Int? = null,
    val flapsPositions: Int? = null,
    val spoilersPct: Float = Float.NaN,
    val simTime: String? = null,
)

/** Tryby autopilota w neutralnej postaci; każdy styl zamienia je na własne nazwy na FMA. */
data class Fma(
    val ap: Boolean = false,
    val fd: Boolean = false,
    val yd: Boolean = false,
    val athrArmed: Boolean = false,
    val athrActive: Boolean = false,
    val toga: Boolean = false,
    /** Prędkość: SPD (trzymanie IAS), MACH albo null. */
    val speed: SpeedMode? = null,
    val lateral: Lateral? = null,
    val lateralArmed: Lateral? = null,
    val vertical: Vertical? = null,
    val verticalArmed: List<Vertical> = emptyList(),
) {
    enum class SpeedMode { SPD, MACH }
    enum class Lateral { HDG, NAV_GPS, NAV_VOR, LOC, BC, ROL }
    enum class Vertical { ALT, VS, FLC, GS, PIT, ALTS }

    val any get() = ap || fd || athrActive || lateral != null || vertical != null
}

/** Aktywne źródło nawigacji (CDI/HSI): GPS albo odbiornik NAV1. */
data class NavInfo(
    val source: Source = Source.NONE,
    val ident: String? = null,
    /** Kurs wybrany (OBS / kurs LOC / DTK z GPS), magnetyczny. */
    val course: Float = Float.NaN,
    /** Odchylenie boczne w kropkach (−2,5…2,5, + = kurs w prawo); NaN = brak sygnału. */
    val cdi: Float = Float.NaN,
    /** Odchylenie od ścieżki w kropkach (+ = ścieżka powyżej); NaN = brak GS. */
    val gs: Float = Float.NaN,
    val dmeNm: Float = Float.NaN,
    /** 1 TO, 2 FROM, 0 brak. */
    val toFrom: Int = 0,
    val loc: Boolean = false,
) {
    enum class Source { NONE, GPS, VOR, LOC }
}

data class GpsInfo(
    val active: Boolean = false,
    val nextId: String? = null,
    val prevId: String? = null,
    val nextDistNm: Float = Float.NaN,
    val nextBrg: Float = Float.NaN,
    val dtk: Float = Float.NaN,
    val xtkNm: Float = Float.NaN,
    val nextEteSec: Float = Float.NaN,
    val destEteSec: Float = Float.NaN,
    val approach: Boolean = false,
    /** Współrzędne punktów bieżącego odcinka (null = brak) — linia aktywnego odcinka na ND. */
    val nextLat: Double? = null,
    val nextLon: Double? = null,
    val prevLat: Double? = null,
    val prevLon: Double? = null,
    /** Numer następnego punktu i liczba punktów planu lotu w symulatorze (0 = brak planu). */
    val wpIndex: Int = 0,
    val wpCount: Int = 0,
)

fun wrap360(a: Float): Float {
    val x = a % 360f
    return if (x < 0f) x + 360f else x
}

fun wrap180(a: Float): Float {
    val x = wrap360(a)
    return if (x > 180f) x - 360f else x
}

fun angDiff(a: Float, b: Float): Float = wrap180(a - b)
