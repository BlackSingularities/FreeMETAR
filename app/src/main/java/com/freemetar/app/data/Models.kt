package com.freemetar.app.data

/** Lotnisko z bazy OurAirports. */
data class Airport(
    val ident: String,
    val type: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val elevationFt: Int?,
    val country: String,
    val region: String,
    val municipality: String,
    val iata: String,
    val scheduled: Boolean,
    val wikipedia: String,
    val homeLink: String,
) {
    val typeLabel: String
        get() = when (type) {
            "large_airport" -> "Duże lotnisko"
            "medium_airport" -> "Średnie lotnisko"
            "small_airport" -> "Małe lotnisko"
            "seaplane_base" -> "Baza hydroplanów"
            "heliport" -> "Heliport"
            "balloonport" -> "Balonowisko"
            else -> type
        }
}

data class RunwayEnd(
    val ident: String,
    val headingTrue: Double?,
    val elevationFt: Int?,
    val displacedFt: Int?,
    val lat: Double?,
    val lon: Double?,
)

data class Runway(
    val lengthFt: Int?,
    val widthFt: Int?,
    val surface: String,
    val lighted: Boolean,
    val closed: Boolean,
    val le: RunwayEnd,
    val he: RunwayEnd,
) {
    val name: String get() = listOf(le.ident, he.ident).filter { it.isNotBlank() }.joinToString("/")

    val surfaceLabel: String
        get() {
            val s = surface.uppercase()
            return when {
                s.startsWith("ASP") || s.contains("ASPH") -> "Asfalt"
                s.startsWith("CON") -> "Beton"
                s.startsWith("GRS") || s.contains("GRASS") || s.startsWith("TURF") -> "Trawa"
                s.startsWith("GRV") || s.contains("GRAVEL") -> "Żwir"
                s.startsWith("DIRT") || s.startsWith("CLAY") -> "Grunt"
                s.startsWith("WAT") -> "Woda"
                s.startsWith("SNOW") || s.startsWith("ICE") -> "Śnieg/lód"
                s.isBlank() -> "—"
                else -> surface
            }
        }
}

data class Frequency(val type: String, val description: String, val mhz: Double) {
    val label: String
        get() = when (type.uppercase()) {
            "ATIS" -> "ATIS"
            "TWR" -> "Wieża"
            "GND" -> "Ziemia"
            "APP" -> "Zbliżanie"
            "DEP" -> "Odloty"
            "CTR", "CNTR" -> "Kontrola obszaru"
            "DEL", "CLD" -> "Delivery"
            "AFIS", "INFO" -> "Informacja"
            "UNIC", "CTAF" -> "UNICOM/CTAF"
            "RDO" -> "Radio"
            else -> type
        }

    /** Kolejność wyświetlania jak w karcie lotniska: od ATIS do odlotów. */
    val order: Int
        get() = listOf("ATIS", "DEL", "CLD", "GND", "TWR", "AFIS", "INFO", "UNIC", "CTAF", "APP", "DEP", "CTR")
            .indexOf(type.uppercase()).let { if (it < 0) 99 else it }
}

data class Navaid(
    val ident: String,
    val name: String,
    val type: String,
    val frequencyKhz: Int?,
    val lat: Double,
    val lon: Double,
    val associatedAirport: String,
) {
    val frequencyLabel: String
        get() = when {
            frequencyKhz == null -> "—"
            type == "NDB" || frequencyKhz < 2000 -> "$frequencyKhz kHz"
            else -> "%.2f MHz".format(frequencyKhz / 1000.0)
        }
}

/** Pojedyncza warstwa chmur. */
data class CloudLayer(val cover: String, val baseFt: Int?, val type: String? = null)

/** METAR w postaci z API aviationweather.gov. */
data class Metar(
    val station: String,
    val raw: String,
    val obsTimeEpoch: Long,
    val tempC: Double?,
    val dewpointC: Double?,
    val windDir: Int?,          // null przy VRB
    val windVariable: Boolean,
    val windKt: Int?,
    val gustKt: Int?,
    val visibility: String?,
    val altimHpa: Double?,
    val wx: String?,
    val clouds: List<CloudLayer>,
    val flightCategory: String?,
    val lat: Double?,
    val lon: Double?,
    val elevationM: Double?,
    val name: String?,
)

data class Taf(val station: String, val raw: String, val issueTime: String?)

/** Pełny obraz pogody dla lotniska. */
data class WeatherBundle(
    val metar: Metar?,
    val history: List<Metar>,
    val taf: Taf?,
    val fetchedAt: Long,
)
