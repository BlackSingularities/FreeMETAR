package com.freemetar.app.ui.flight.avionics

import android.graphics.Canvas
import android.graphics.Paint
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Elementy PFD zasilane z symulatora, wspólne dla wszystkich stylów: dyrektor lotu, skale odchylenia
 * LOC/GS (albo CDI z GPS), bugi nastaw autopilota, znaczniki markerów. Każdy renderer woła je
 * we własnej geometrii i kolorach, żeby wyglądały jak w danej kabinie.
 */
internal object SimLayer {

    /** Dyrektor lotu jako dwie belki (Airbus, Boeing, Embraer, ATR); [cx], [cy] — środek symbolu samolotu. */
    fun fdCrossBars(c: Canvas, p: Pen, d: FlightData, cx: Float, cy: Float, ppd: Float, half: Float, color: Int, w: Float = 5f) {
        if (!d.fdOn || d.fdPitch.isNaN() || d.fdRoll.isNaN() || d.onGround && d.speedKt < 40f) return
        val dy = ((d.fdPitch - d.pitch).coerceIn(-10f, 10f)) * ppd
        // Belka przechylenia: 1 jednostka ≈ różnica przechylenia/3 stopnia pochylenia (jak w prawdziwych FD)
        val dx = ((d.fdRoll - d.roll).coerceIn(-30f, 30f) / 3f) * ppd
        p.line(c, cx - half, cy - dy, cx + half, cy - dy, color, w)
        p.line(c, cx + dx, cy - half, cx + dx, cy + half, color, w)
    }

    /** Dyrektor lotu jako „V” (Garmin, Collins): klin obrócony o różnicę przechylenia, przesunięty o pochylenie. */
    fun fdVBar(c: Canvas, p: Pen, d: FlightData, cx: Float, cy: Float, ppd: Float, span: Float, color: Int) {
        if (!d.fdOn || d.fdPitch.isNaN() || d.fdRoll.isNaN() || d.onGround && d.speedKt < 40f) return
        val dy = ((d.fdPitch - d.pitch).coerceIn(-10f, 10f)) * ppd
        c.save()
        c.rotate(d.fdRoll - d.roll, cx, cy)
        val y = cy - dy
        p.poly(c, color, Col.BLACK, 2f, cx, y, cx - span, y + span * 0.32f, cx - span, y + span * 0.42f, cx, y + span * 0.12f)
        p.poly(c, color, Col.BLACK, 2f, cx, y, cx + span, y + span * 0.32f, cx + span, y + span * 0.42f, cx, y + span * 0.12f)
        c.restore()
    }

    /**
     * Skale odchylenia: boczna pod horyzontem ([locY], środek [cx]) i pionowa z prawej ([gsX], środek [cy]).
     * [dot] — odstęp kropek. LOC/GS z odbiornika NAV1, a bez ILS — CDI z GPS (bez skali pionowej).
     */
    fun deviation(
        c: Canvas, p: Pen, d: FlightData, cx: Float, locY: Float, gsX: Float, cy: Float, dot: Float, pointer: Int,
        scale: Int = Col.WHITE, showLateral: Boolean = true,
    ) {
        val n = d.nav
        if (showLateral && !n.cdi.isNaN()) {
            for (i in -2..2) if (i != 0) p.circle(c, cx + i * dot, locY, dot * 0.16f, scale, 2.5f)
            p.line(c, cx, locY - dot * 0.45f, cx, locY + dot * 0.45f, Col.YELLOW, 3f)
            val x = cx + n.cdi.coerceIn(-2.4f, 2.4f) * dot
            if (abs(n.cdi) >= 2.4f) p.poly(c, 0, pointer, 3f, x, locY - dot * 0.3f, x + dot * 0.3f, locY, x, locY + dot * 0.3f, x - dot * 0.3f, locY)
            else p.poly(c, pointer, pointer, 2f, x, locY - dot * 0.3f, x + dot * 0.3f, locY, x, locY + dot * 0.3f, x - dot * 0.3f, locY)
        }
        if (!n.gs.isNaN()) {
            for (i in -2..2) if (i != 0) p.circle(c, gsX, cy + i * dot, dot * 0.16f, scale, 2.5f)
            p.line(c, gsX - dot * 0.45f, cy, gsX + dot * 0.45f, cy, Col.YELLOW, 3f)
            // GS dodatnie = ścieżka powyżej samolotu → romb w górę
            val y = cy - n.gs.coerceIn(-2.4f, 2.4f) * dot
            p.poly(c, pointer, pointer, 2f, gsX, y - dot * 0.3f, gsX + dot * 0.3f, y, gsX, y + dot * 0.3f, gsX - dot * 0.3f, y)
        }
    }

    /** Opis źródła nawigacji (np. „ILS IGD 4.2 NM”, „GPS KABEL 12.0”). */
    fun navLine(d: FlightData): String? {
        val n = d.nav
        val src = when (n.source) {
            NavInfo.Source.LOC -> if (!n.gs.isNaN()) "ILS" else "LOC"
            NavInfo.Source.VOR -> "VOR"
            NavInfo.Source.GPS -> "GPS"
            NavInfo.Source.NONE -> return null
        }
        val dme = if (n.dmeNm.isNaN()) "" else " " + String.format(Locale.US, "%.1f", n.dmeNm) + "NM"
        return src + (n.ident?.let { " $it" } ?: "") + dme
    }

    /** Bug prędkości na prawej krawędzi taśmy: dwa trójkąty jak w Boeingu/Garminie albo klin (Airbus). */
    fun speedBug(c: Canvas, p: Pen, x: Float, y: Float, color: Int, wedge: Boolean = false) {
        if (wedge) p.poly(c, color, color, 2f, x, y, x + 22, y - 12, x + 22, y + 12)
        else p.poly(c, 0, color, 3.5f, x, y - 18, x + 18, y - 18, x + 18, y - 8, x + 6, y, x + 18, y + 8, x + 18, y + 18, x, y + 18)
    }

    /** Bug wysokości na lewej krawędzi taśmy wysokości. */
    fun altBug(c: Canvas, p: Pen, x: Float, y: Float, color: Int) {
        p.poly(c, 0, color, 3.5f, x, y - 26, x + 16, y - 26, x + 16, y - 10, x + 6, y, x + 16, y + 10, x + 16, y + 26, x, y + 26)
    }

    /** Marker: OM (cyjan), MM (bursztyn), IM (biały) w ramce. */
    fun marker(c: Canvas, p: Pen, d: FlightData, x: Float, y: Float, size: Float) {
        val (t, col) = when (d.marker) {
            1 -> "OM" to 0xFF33DDFF.toInt()
            2 -> "MM" to Col.AMBER
            3 -> "IM" to Col.WHITE
            else -> return
        }
        // Miganie 2 Hz jak w odbiorniku markerów
        if ((System.currentTimeMillis() / 250) % 2 == 0L) return
        p.frame(c, x - size * 0.9f, y - size * 0.6f, x + size * 0.9f, y + size * 0.6f, col, 3f)
        p.text(c, t, x, y, size, col, bold = true)
    }

    /** Wybrana prędkość w kt albo Mach (tekst nad taśmą). */
    fun selSpeed(d: FlightData): String? = when {
        !d.machBug.isNaN() -> String.format(Locale.US, ".%02d", (d.machBug * 100).roundToInt())
        !d.spdBug.isNaN() -> d.spdBug.roundToInt().toString()
        else -> null
    }

    /** Mach do wyświetlenia (od 0,40). */
    fun machText(d: FlightData): String? =
        d.mach.takeIf { !it.isNaN() && it >= 0.4f }?.let { String.format(Locale.US, ".%03d", (it * 1000).roundToInt().coerceAtMost(999)) }

    fun vmo(d: FlightData, fallback: Float) = if (d.vmo.isNaN()) fallback else d.vmo

    /** Pas niskiej prędkości: od przeciągnięcia (czerwono-czarny) i zapas 1,23 Vs (bursztyn). */
    fun lowSpeed(c: Canvas, p: Pen, d: FlightData, x: Float, w: Float, yOf: (Float) -> Float, bottom: Float) {
        if (d.onGround) return
        val vs = (if (d.flapsIndex != null && d.flapsIndex > 0 && !d.vs0.isNaN()) d.vs0 else d.vs1).takeIf { !it.isNaN() } ?: return
        val yStall = yOf(vs)
        if (yStall < bottom) {
            var y = yStall
            var red = true
            while (y < bottom) {
                p.rect(c, x, y, x + w, y + 12, if (red) Col.RED else Col.BLACK)
                y += 12f
                red = !red
            }
        }
        val yMin = yOf(vs * 1.23f)
        p.rect(c, x + w * 0.3f, yMin, x + w * 0.7f, yStall.coerceAtMost(bottom), Col.AMBER)
    }

    fun text(c: Canvas, p: Pen, s: String, x: Float, y: Float, size: Float, color: Int, align: Paint.Align = Paint.Align.CENTER) =
        p.text(c, s, x, y, size, color, align)
}
