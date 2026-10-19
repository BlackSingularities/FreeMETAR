package com.freemetar.app.data

import kotlin.math.*

/**
 * Performance Calculator - obliczenia wydajności samolotu.
 *
 * Funkcje:
 * - Weight & Balance (środek ciężkości, limity)
 * - V-speeds (V1, VR, V2 z wagą)
 * - Runway analysis (czy wystarczy długość pasa)
 * - Fuel planning (paliwo na trasę)
 * - Climb/Descent profiles
 */
object PerformanceCalculator {

    /**
     * Oblicz V-speeds dla Boeing 737-800.
     *
     * @param weightLbs Waga startowa w funtach
     * @param flapSetting Ustawienie klap (1, 5, 15)
     * @param altitude Wysokość lotniska w stopach
     * @param temperature Temperatura w °C
     * @return V-speeds (V1, VR, V2)
     */
    fun calculateVSpeeds737(
        weightLbs: Double,
        flapSetting: Int = 5,
        altitude: Double = 0.0,
        temperature: Double = 15.0
    ): VSpeeds {
        // Uproszczone wzory - w rzeczywistości z tabel FCOM
        val weightFactor = weightLbs / 140000.0 // MTOW 737-800
        val altitudeFactor = 1.0 + (altitude / 10000.0) * 0.03
        val tempFactor = 1.0 + ((temperature - 15.0) / 10.0) * 0.02

        val baseV1 = when (flapSetting) {
            1 -> 155.0
            5 -> 145.0
            15 -> 135.0
            else -> 145.0
        }

        val baseVR = baseV1 + 5.0
        val baseV2 = baseVR + 15.0

        return VSpeeds(
            v1 = (baseV1 * weightFactor * altitudeFactor * tempFactor).roundToInt(),
            vr = (baseVR * weightFactor * altitudeFactor * tempFactor).roundToInt(),
            v2 = (baseV2 * weightFactor * altitudeFactor * tempFactor).roundToInt()
        )
    }

    /**
     * Oblicz V-speeds dla Airbus A320.
     */
    fun calculateVSpeedsA320(
        weightKg: Double,
        flapSetting: Int = 2,
        altitude: Double = 0.0,
        temperature: Double = 15.0
    ): VSpeeds {
        val weightFactor = weightKg / 78000.0 // MTOW A320
        val altitudeFactor = 1.0 + (altitude / 10000.0) * 0.03
        val tempFactor = 1.0 + ((temperature - 15.0) / 10.0) * 0.02

        val baseV1 = when (flapSetting) {
            1 -> 145.0
            2 -> 140.0
            3 -> 130.0
            else -> 140.0
        }

        val baseVR = baseV1 + 5.0
        val baseV2 = baseVR + 15.0

        return VSpeeds(
            v1 = (baseV1 * weightFactor * altitudeFactor * tempFactor).roundToInt(),
            vr = (baseVR * weightFactor * altitudeFactor * tempFactor).roundToInt(),
            v2 = (baseV2 * weightFactor * altitudeFactor * tempFactor).roundToInt()
        )
    }

    /**
     * Runway analysis - czy pas wystarczy do startu.
     *
     * @param runwayLengthFt Długość pasa w stopach
     * @param weightLbs Waga w funtach
     * @param altitude Wysokość w stopach
     * @param temperature Temperatura w °C
     * @param wind Wiatr (+ tailwind, - headwind)
     * @param slope Nachylenie pasa w %
     * @return Wynik analizy
     */
    fun runwayAnalysis(
        runwayLengthFt: Double,
        weightLbs: Double,
        altitude: Double = 0.0,
        temperature: Double = 15.0,
        wind: Double = 0.0,
        slope: Double = 0.0
    ): RunwayAnalysis {
        // Bazowa długość startu dla 737-800 przy MTOW
        val baseTakeoffDistance = 8000.0

        // Korekty
        val weightFactor = weightLbs / 140000.0
        val altitudeFactor = 1.0 + (altitude / 1000.0) * 0.05
        val tempFactor = 1.0 + ((temperature - 15.0) / 10.0) * 0.07
        val windFactor = 1.0 + (wind * 0.02) // +2% na każdy kt tailwind
        val slopeFactor = 1.0 + (slope * 0.1) // +10% na każdy 1% upslope

        val requiredDistance = baseTakeoffDistance *
                weightFactor *
                altitudeFactor *
                tempFactor *
                windFactor *
                slopeFactor

        val margin = runwayLengthFt - requiredDistance
        val marginPercent = (margin / requiredDistance) * 100

        return RunwayAnalysis(
            runwayLength = runwayLengthFt.roundToInt(),
            requiredDistance = requiredDistance.roundToInt(),
            margin = margin.roundToInt(),
            marginPercent = marginPercent.roundToInt(),
            sufficient = margin > 0
        )
    }

    /**
     * Fuel planning - paliwo na trasę.
     *
     * @param distanceNm Dystans w milach morskich
     * @param cruiseAltFt Wysokość przelotowa
     * @param aircraftType Typ samolotu
     * @return Zużycie paliwa
     */
    fun fuelPlanning(
        distanceNm: Double,
        cruiseAltFt: Double,
        aircraftType: String = "B737"
    ): FuelEstimate {
        // Uproszczone fuel flow (kg/h lub lbs/h)
        val cruiseFuelFlow = when {
            aircraftType.contains("737") -> 2400.0 // kg/h
            aircraftType.contains("A320") -> 2300.0
            aircraftType.contains("172") -> 35.0 // lbs/h
            else -> 2000.0
        }

        // TAS w przelotowym (uproszczone)
        val cruiseTas = when {
            cruiseAltFt > 30000 -> 450.0 // kt
            cruiseAltFt > 20000 -> 400.0
            cruiseAltFt > 10000 -> 350.0
            else -> 250.0
        }

        val flightTimeHours = distanceNm / cruiseTas
        val tripFuel = cruiseFuelFlow * flightTimeHours

        // Rezerwy
        val reserveFuel = cruiseFuelFlow * 0.5 // 30 minut
        val alternateFuel = tripFuel * 0.1 // 10% na alternate
        val contingencyFuel = tripFuel * 0.05 // 5% contingency

        val totalFuel = tripFuel + reserveFuel + alternateFuel + contingencyFuel

        return FuelEstimate(
            tripFuel = tripFuel.roundToInt(),
            reserveFuel = reserveFuel.roundToInt(),
            alternateFuel = alternateFuel.roundToInt(),
            contingencyFuel = contingencyFuel.roundToInt(),
            totalFuel = totalFuel.roundToInt(),
            unit = if (aircraftType.contains("172")) "lbs" else "kg"
        )
    }

    /**
     * Weight & Balance - CG calculation.
     */
    fun weightAndBalance(
        emptyWeight: Double,
        emptyArm: Double,
        items: List<WeightItem>
    ): WeightBalance {
        val totalWeight = emptyWeight + items.sumOf { it.weight }
        val totalMoment = emptyWeight * emptyArm + items.sumOf { it.weight * it.arm }
        val cg = totalMoment / totalWeight

        // Limity dla 737-800 (przykładowe)
        val forwardLimit = 15.0 // % MAC
        val aftLimit = 35.0

        return WeightBalance(
            totalWeight = totalWeight.roundToInt(),
            cg = cg,
            forwardLimit = forwardLimit,
            aftLimit = aftLimit,
            withinLimits = cg in forwardLimit..aftLimit
        )
    }
}

data class VSpeeds(
    val v1: Int,  // Decision speed
    val vr: Int,  // Rotation speed
    val v2: Int   // Takeoff safety speed
)

data class RunwayAnalysis(
    val runwayLength: Int,
    val requiredDistance: Int,
    val margin: Int,
    val marginPercent: Int,
    val sufficient: Boolean
)

data class FuelEstimate(
    val tripFuel: Int,
    val reserveFuel: Int,
    val alternateFuel: Int,
    val contingencyFuel: Int,
    val totalFuel: Int,
    val unit: String
)

data class WeightItem(
    val name: String,
    val weight: Double,
    val arm: Double
)

data class WeightBalance(
    val totalWeight: Int,
    val cg: Double,
    val forwardLimit: Double,
    val aftLimit: Double,
    val withinLimits: Boolean
)
