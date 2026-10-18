package com.freemetar.app.domain

import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Przeliczenia wartości dla komend OpenTakeoff Link — czysta logika, testowalna bez Androida. */
object ControlMath {
    /** Kurs po zmianie o [delta] stopni, zawinięty do 0–359. */
    fun heading(current: Double?, delta: Int): Int = (((current ?: 0.0).roundToInt() + delta) % 360 + 360) % 360

    /** Wysokość nastawy: zaokrąglona do 100 ft, w zakresie 0–50 000. */
    fun altitude(current: Double?, delta: Int): Int = ((((current ?: 0.0) + delta) / 100).roundToInt() * 100).coerceIn(0, 50_000)

    fun verticalSpeed(current: Double?, delta: Int): Int = ((((current ?: 0.0) + delta) / 100).roundToInt() * 100).coerceIn(-8000, 8000)

    fun speed(current: Double?, delta: Int): Int = ((current ?: 0.0).roundToInt() + delta).coerceIn(0, 500)

    /** MHz → Hz dla zdarzeń *_SET_HZ; null, gdy poza pasmem COM/NAV. */
    fun comHz(mhz: Double): Long? = (mhz * 1_000_000).roundToLong().takeIf { it in 118_000_000L..136_990_000L }
    fun navHz(mhz: Double): Long? = (mhz * 1_000_000).roundToLong().takeIf { it in 108_000_000L..117_950_000L }

    /** Tekst wpisany przez użytkownika („121,5”, „121.500”, „1215”) → MHz. */
    fun parseMhz(text: String): Double? {
        val t = text.trim().replace(',', '.')
        t.toDoubleOrNull()?.let { if (it in 100.0..140.0) return it }
        // Same cyfry, jak na panelu radia: 1215 → 121.5, 121500 → 121.500
        val digits = t.filter { it.isDigit() }
        if (digits.length in 4..6) return (digits.take(3) + "." + digits.drop(3)).toDoubleOrNull()
        return null
    }

    /** Kod transpondera: dokładnie 4 cyfry 0–7. */
    fun squawk(text: String): Int? = text.trim().takeIf { it.length == 4 && it.all { c -> c in '0'..'7' } }?.toInt()

    /**
     * Wychylenie osi z kąta telefonu: strefa martwa, czułość i przycięcie do ±[max].
     * [angleDeg] — odchylenie od pozycji zerowej, [fullDeg] — kąt pełnego wychylenia.
     */
    fun axis(angleDeg: Double, fullDeg: Double, deadDeg: Double, sensitivity: Double, max: Double): Double {
        val a = when {
            angleDeg > deadDeg -> angleDeg - deadDeg
            angleDeg < -deadDeg -> angleDeg + deadDeg
            else -> 0.0
        }
        return (a / (fullDeg - deadDeg) * sensitivity).coerceIn(-max, max)
    }
}
