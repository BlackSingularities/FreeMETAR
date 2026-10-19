package com.freemetar.app.ui.flight

import com.freemetar.app.data.link.FlightState
import com.freemetar.app.data.nav.AirportNav
import com.freemetar.app.data.nav.NavRunway
import kotlin.math.*

/**
 * ILS deviation calculator - oblicza odchylenie localizer i glideslope.
 *
 * Localizer:
 * - Full scale deflection (FSD): ±2.5° od kursu pasa
 * - Dots: 5 pozycji (±2 dots = full scale)
 *
 * Glideslope:
 * - Full scale deflection: ±0.7° od ścieżki
 * - Dots: 5 pozycji (±2 dots = full scale)
 * - Typowa ścieżka: 3°
 */
object IlsCalculator {

    data class IlsDeviation(
        val localizerDots: Double,  // -2.5 do +2.5 (lewy/prawy od centerline)
        val glideslopeDots: Double, // -2.5 do +2.5 (poniżej/powyżej ścieżki)
        val dmeNm: Double?,         // Dystans do progu w NM
        val inRange: Boolean,       // Czy w zasięgu ILS (< 25 NM)
        val runway: NavRunway
    )

    /**
     * Oblicz odchylenie ILS dla bieżącej pozycji samolotu.
     *
     * @return IlsDeviation lub null jeśli nie ma ILS lub poza zasięgiem
     */
    fun calculateDeviation(
        state: FlightState,
        airportNav: AirportNav?
    ): IlsDeviation? {
        if (airportNav == null) return null

        val lat = state.position.lat ?: return null
        val lon = state.position.lon ?: return null
        val altFt = state.position.altMslFt ?: return null

        // Znajdź najbliższy pas z ILS w zasięgu
        val runway = findNearestIlsRunway(lat, lon, altFt, airportNav) ?: return null
        val ils = runway.ils ?: return null

        val thresholdLat = runway.thresholdLat ?: return null
        val thresholdLon = runway.thresholdLon ?: return null
        val thresholdElevFt = runway.elevFt ?: airportNav.airport?.elevFt ?: 0.0
        val locCourse = ils.locCourseDeg ?: return null
        val gsAngle = ils.gsAngleDeg ?: 3.0

        // Dystans do progu
        val dmeNm = distanceNm(lat, lon, thresholdLat, thresholdLon)

        // Sprawdź zasięg (25 NM dla ILS)
        if (dmeNm > 25.0) return null

        // Namiar od samolotu do progu
        val bearing = bearing(lat, lon, thresholdLat, thresholdLon)

        // Odchylenie localizer: kąt między kursem ILS a namiarem
        val locDevDeg = angleDiff(locCourse, bearing)

        // Odchylenie w dots: ±2.5° = ±2 dots
        val locDots = (locDevDeg / 2.5) * 2.0

        // Odchylenie glideslope
        val heightAboveThresholdFt = altFt - thresholdElevFt
        val expectedHeightFt = dmeNm * 6076.0 * tan(Math.toRadians(gsAngle))
        val gsDevFt = heightAboveThresholdFt - expectedHeightFt

        // Odchylenie w stopniach: ±0.7° = full scale
        val gsDevDeg = Math.toDegrees(atan(gsDevFt / (dmeNm * 6076.0)))
        val gsDots = (gsDevDeg / 0.7) * 2.0

        return IlsDeviation(
            localizerDots = locDots.coerceIn(-2.5, 2.5),
            glideslopeDots = gsDots.coerceIn(-2.5, 2.5),
            dmeNm = dmeNm,
            inRange = dmeNm <= 25.0,
            runway = runway
        )
    }

    /**
     * Znajdź najbliższy pas z ILS w zasięgu (< 25 NM, samolot mniej-więcej na przedłużeniu pasa).
     */
    private fun findNearestIlsRunway(
        lat: Double,
        lon: Double,
        altFt: Double,
        airportNav: AirportNav
    ): NavRunway? {
        return airportNav.airport?.runways.orEmpty()
            .filter { it.ils != null }
            .filter {
                val thresholdLat = it.thresholdLat ?: return@filter false
                val thresholdLon = it.thresholdLon ?: return@filter false
                val dist = distanceNm(lat, lon, thresholdLat, thresholdLon)

                // W zasięgu 25 NM
                if (dist > 25.0) return@filter false

                // Sprawdź czy samolot jest mniej-więcej na przedłużeniu pasa (±45°)
                val bearing = bearing(thresholdLat, thresholdLon, lat, lon)
                val locCourse = it.ils?.locCourseDeg ?: return@filter false
                val deviation = abs(angleDiff(locCourse, bearing))

                deviation < 45.0
            }
            .minByOrNull {
                val thresholdLat = it.thresholdLat!!
                val thresholdLon = it.thresholdLon!!
                distanceNm(lat, lon, thresholdLat, thresholdLon)
            }
    }

    /**
     * Dystans między dwoma punktami w milach morskich.
     */
    private fun distanceNm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 3440.065 // Promień Ziemi w NM
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    /**
     * Namiar od punktu 1 do punktu 2 (true bearing).
     */
    private fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLon = Math.toRadians(lon2 - lon1)
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)

        val y = sin(dLon) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLon)

        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    /**
     * Różnica kątów (-180 do +180).
     */
    private fun angleDiff(a: Double, b: Double): Double {
        var diff = b - a
        while (diff > 180) diff -= 360
        while (diff < -180) diff += 360
        return diff
    }
}
