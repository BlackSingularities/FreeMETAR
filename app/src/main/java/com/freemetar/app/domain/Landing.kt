package com.freemetar.app.domain

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Geometria pasa: próg, kurs rzeczywisty i długość — do położenia przyziemienia. */
data class RunwayGeom(val ident: String, val thresholdLat: Double, val thresholdLon: Double, val headingTrueDeg: Double, val lengthFt: Double?)

/** Położenie punktu względem progu: wzdłuż osi (ft, + = za progiem) i w poprzek (ft, + = w prawo). */
data class RunwayOffset(val alongFt: Double, val crossFt: Double)

object RunwayMath {
    /** Przybliżenie płaskie (wystarczające na długości pasa). */
    fun offset(rw: RunwayGeom, lat: Double, lon: Double): RunwayOffset {
        val ftPerDegLat = 364_000.0
        val ftPerDegLon = 364_000.0 * cos(Math.toRadians(rw.thresholdLat))
        val north = (lat - rw.thresholdLat) * ftPerDegLat
        val east = (lon - rw.thresholdLon) * ftPerDegLon
        val h = Math.toRadians(rw.headingTrueDeg)
        val along = north * cos(h) + east * sin(h)
        val cross = east * cos(h) - north * sin(h)
        return RunwayOffset(along, cross)
    }

    /** Pas, na którym wylądował samolot: kurs zgodny (±30°) i najmniejsze odchylenie od osi. */
    fun pick(candidates: List<RunwayGeom>, lat: Double, lon: Double, headingTrueDeg: Double?): RunwayGeom? =
        candidates.filter { rw ->
            headingTrueDeg == null || abs(((rw.headingTrueDeg - headingTrueDeg + 540) % 360) - 180) <= 30
        }.map { it to offset(it, lat, lon) }
            .filter { (rw, o) -> o.alongFt > -1500 && o.alongFt < (rw.lengthFt ?: 15000.0) + 1000 && abs(o.crossFt) < 500 }
            .minByOrNull { abs(it.second.crossFt) }?.first
}

/** Dane przyziemienia do oceny. Brakujące wartości po prostu nie wpływają na wynik. */
data class TouchdownData(
    val vsFpm: Double,
    val gForce: Double? = null,
    val bankDeg: Double? = null,
    val pitchDeg: Double? = null,
    val iasKt: Double? = null,
    val runway: String? = null,
    val alongFt: Double? = null,
    val crossFt: Double? = null,
    val runwayLengthFt: Double? = null,
    val crosswindKt: Double? = null,
    val bounces: Int = 0,
)

@Serializable
data class LandingReport(
    val score: Int,
    val grade: String,
    val vsFpm: Int,
    val gForce: Double? = null,
    val bankDeg: Double? = null,
    val pitchDeg: Double? = null,
    val iasKt: Int? = null,
    val runway: String? = null,
    val alongFt: Int? = null,
    val crossFt: Int? = null,
    val crosswindKt: Int? = null,
    val bounces: Int = 0,
    val notes: List<String> = emptyList(),
)

/**
 * Ocena lądowania 0–100. Progi odpowiadają typowym kryteriom: miękkie przyziemienie 60–240 ft/min,
 * twarde od 400 ft/min, ciężkie (wymaga przeglądu w realnym lotnictwie) od 600 ft/min lub 2 g;
 * strefa przyziemienia 500–3000 ft za progiem.
 */
object LandingScore {
    fun evaluate(t: TouchdownData): LandingReport {
        var score = 100.0
        val notes = mutableListOf<String>()
        val vs = abs(t.vsFpm)
        when {
            vs < 60 -> { score -= 5; notes += "Bardzo miękko (${vs.roundToInt()} ft/min) — uważaj na przeciąganie wyrównania." }
            vs <= 240 -> notes += "Miękkie przyziemienie: ${vs.roundToInt()} ft/min."
            vs <= 400 -> { score -= 10 + (vs - 240) / 160 * 15; notes += "Wyczuwalne przyziemienie: ${vs.roundToInt()} ft/min." }
            vs <= 600 -> { score -= 25 + (vs - 400) / 200 * 25; notes += "Twarde lądowanie: ${vs.roundToInt()} ft/min." }
            else -> { score -= 60; notes += "Bardzo twarde lądowanie: ${vs.roundToInt()} ft/min — w realnym lotnictwie przegląd techniczny." }
        }
        t.gForce?.let { g ->
            when {
                g >= 2.0 -> { score -= 30; notes += "Przeciążenie ${"%.2f".format(g)} g." }
                g >= 1.6 -> { score -= 15; notes += "Przeciążenie ${"%.2f".format(g)} g." }
                g >= 1.4 -> score -= 5
            }
        }
        t.bankDeg?.let { b ->
            val a = abs(b)
            when {
                a > 8 -> { score -= 20; notes += "Duże przechylenie przy przyziemieniu: ${a.roundToInt()}°." }
                a > 4 -> { score -= 8; notes += "Przechylenie ${a.roundToInt()}° — przyziemienie na jedno koło." }
            }
        }
        t.pitchDeg?.let { p ->
            when {
                p < 0 -> { score -= 20; notes += "Przyziemienie na przednie koło (pochylenie ${"%.1f".format(p)}°)." }
                p > 10 -> { score -= 10; notes += "Duże pochylenie ${p.roundToInt()}° — ryzyko uderzenia ogonem." }
            }
        }
        t.alongFt?.let { a ->
            when {
                a < 0 -> { score -= 40; notes += "Przyziemienie przed progiem pasa (${(-a).roundToInt()} ft)." }
                a < 500 -> { score -= 5; notes += "Przyziemienie tuż za progiem (${a.roundToInt()} ft)." }
                a <= 3000 -> notes += "W strefie przyziemienia: ${a.roundToInt()} ft za progiem."
                else -> {
                    val remaining = t.runwayLengthFt?.let { it - a }
                    score -= if (a > 4500) 25 else 10
                    notes += "Długie lądowanie: ${a.roundToInt()} ft za progiem" + (remaining?.let { ", zostało ${it.roundToInt()} ft pasa." } ?: ".")
                }
            }
        }
        t.crossFt?.let { x ->
            val a = abs(x)
            val side = if (x >= 0) "w prawo" else "w lewo"
            when {
                a > 50 -> { score -= 25; notes += "Daleko od osi pasa: ${a.roundToInt()} ft $side." }
                a > 25 -> { score -= 12; notes += "Odchylenie od osi: ${a.roundToInt()} ft $side." }
                a > 12 -> score -= 4
            }
        }
        if (t.bounces > 0) { score -= 12.0 * t.bounces; notes += "Odbicia: ${t.bounces}." }
        // Silny wiatr boczny łagodzi ocenę przechylenia i osi
        t.crosswindKt?.let { if (abs(it) >= 15) { score += 5; notes += "Wiatr boczny ${abs(it).roundToInt()} kt — wynik złagodzony." } }

        val s = score.roundToInt().coerceIn(0, 100)
        val grade = when {
            s >= 90 -> "Doskonałe"
            s >= 75 -> "Dobre"
            s >= 55 -> "Poprawne"
            s >= 35 -> "Twarde"
            else -> "Bardzo twarde"
        }
        return LandingReport(
            score = s, grade = grade, vsFpm = t.vsFpm.roundToInt(), gForce = t.gForce, bankDeg = t.bankDeg, pitchDeg = t.pitchDeg,
            iasKt = t.iasKt?.roundToInt(), runway = t.runway, alongFt = t.alongFt?.roundToInt(), crossFt = t.crossFt?.roundToInt(),
            crosswindKt = t.crosswindKt?.roundToInt(), bounces = t.bounces, notes = notes,
        )
    }
}
