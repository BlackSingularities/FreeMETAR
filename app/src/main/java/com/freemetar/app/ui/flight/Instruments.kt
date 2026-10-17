package com.freemetar.app.ui.flight

import com.freemetar.app.domain.loc

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.freemetar.app.data.RoutePoint
import com.freemetar.app.data.link.FlightState
import com.freemetar.app.domain.Geo
import com.freemetar.app.ui.theme.Fm
import com.freemetar.app.ui.theme.Mono
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

// Kolory przyrządów są stałe jak w kabinie — niezależne od motywu aplikacji
private val SKY = Color(0xFF1C64B4)
private val GROUND = Color(0xFF7A4A22)
private val TAPE = Color(0xE6141A20)
private val WHITE = Color(0xFFF2F5F8)
private val SYMBOL = Color(0xFFFFC21A)

private fun DrawScope.label(m: TextMeasurer, text: String, center: Offset, color: Color, size: TextUnit, bold: Boolean = false) {
    val l = m.measure(text, TextStyle(color = color, fontSize = size, fontFamily = Mono, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal))
    drawText(l, topLeft = Offset(center.x - l.size.width / 2f, center.y - l.size.height / 2f))
}

/**
 * Główny wyświetlacz lotu: sztuczny horyzont z drabinką pochylenia i skalą przechylenia,
 * taśmy prędkości i wysokości, wariometr, taśma kursu i pasek trybów autopilota (FMA).
 */
@Composable
fun Pfd(s: FlightState, modifier: Modifier = Modifier) {
    val m = rememberTextMeasurer()
    val c = Fm.c
    val ias = s.speed.iasKt ?: 0.0
    val alt = s.position.altIndFt ?: s.position.altMslFt ?: 0.0
    val desc = loc("PFD: prędkość ${ias.roundToInt()} węzłów, wysokość ${alt.roundToInt()} stóp, kurs ${(s.attitude.hdgMagDeg ?: 0.0).roundToInt()}", "PFD: speed ${ias.roundToInt()} knots, altitude ${alt.roundToInt()} feet, heading ${(s.attitude.hdgMagDeg ?: 0.0).roundToInt()}")
    Canvas(modifier.fillMaxWidth().aspectRatio(1.12f).semantics { contentDescription = desc }) {
        val w = size.width; val h = size.height
        val fmaH = h * 0.085f
        val hdgH = h * 0.12f
        val tapeW = w * 0.17f
        val att = Rect(tapeW, fmaH, w - tapeW - w * 0.05f, h - hdgH)
        val center = att.center
        val ppd = att.height / 42f // pikseli na stopień pochylenia
        val pitch = (s.attitude.pitchDeg ?: 0.0).toFloat()
        val bank = (s.attitude.bankDeg ?: 0.0).toFloat()

        drawRect(Color(0xFF0E1216))

        // --- horyzont ---
        clipRect(att.left, att.top, att.right, att.bottom) {
            rotate(-bank, center) {
                translate(0f, pitch * ppd) {
                    drawRect(SKY, Offset(-w, -h * 2 + center.y), Size(w * 3, h * 2))
                    drawRect(GROUND, Offset(-w, center.y), Size(w * 3, h * 2))
                    drawLine(WHITE, Offset(-w, center.y), Offset(w * 2, center.y), 2.5f)
                    for (p in -30..30 step 5) {
                        if (p == 0) continue
                        val y = center.y - p * ppd
                        val half = if (p % 10 == 0) att.width * 0.16f else att.width * 0.08f
                        drawLine(WHITE, Offset(center.x - half, y), Offset(center.x + half, y), 2f)
                        if (p % 10 == 0) {
                            label(m, abs(p).toString(), Offset(center.x - half - 18, y), WHITE, 11.sp)
                            label(m, abs(p).toString(), Offset(center.x + half + 18, y), WHITE, 11.sp)
                        }
                    }
                }
            }
        }

        // --- skala przechylenia ---
        val r = att.height * 0.42f
        for (a in listOf(-60, -45, -30, -20, -10, 0, 10, 20, 30, 45, 60)) {
            val rad = Math.toRadians(a.toDouble() - 90)
            val len = if (a % 30 == 0) 14f else 8f
            drawLine(WHITE, Offset(center.x + (r) * cos(rad).toFloat(), center.y + r * sin(rad).toFloat()),
                Offset(center.x + (r + len) * cos(rad).toFloat(), center.y + (r + len) * sin(rad).toFloat()), 2f)
        }
        rotate(-bank, center) {
            val tip = Offset(center.x, center.y - r + 2)
            drawPath(Path().apply { moveTo(tip.x, tip.y); lineTo(tip.x - 9, tip.y + 14); lineTo(tip.x + 9, tip.y + 14); close() }, SYMBOL)
        }

        // --- symbol samolotu ---
        val wing = att.width * 0.2f
        drawLine(SYMBOL, Offset(center.x - wing - 30, center.y), Offset(center.x - 30, center.y), 7f, cap = StrokeCap.Round)
        drawLine(SYMBOL, Offset(center.x + 30, center.y), Offset(center.x + wing + 30, center.y), 7f, cap = StrokeCap.Round)
        drawRect(SYMBOL, Offset(center.x - 5, center.y - 5), Size(10f, 10f))

        // --- taśma prędkości ---
        val pxKt = att.height / 90f
        drawRect(TAPE, Offset(0f, att.top), Size(tapeW, att.height))
        clipRect(0f, att.top, tapeW, att.bottom) {
            val base = floor(ias / 10).toInt() * 10
            for (v in base - 60..base + 60 step 10) {
                if (v < 0) continue
                val y = center.y - ((v - ias) * pxKt).toFloat()
                drawLine(WHITE, Offset(tapeW - 12, y), Offset(tapeW, y), 2f)
                if (v % 20 == 0) label(m, v.toString(), Offset(tapeW * 0.45f, y), WHITE, 12.sp)
            }
            s.autopilot.spdKt?.takeIf { s.autopilot.athr || s.autopilot.master }?.let { bug ->
                val y = center.y - ((bug - ias) * pxKt).toFloat()
                drawPath(Path().apply { moveTo(tapeW, y); lineTo(tapeW - 10, y - 8); lineTo(tapeW - 10, y + 8); close() }, c.accent)
            }
        }
        readout(m, Rect(2f, center.y - 18, tapeW - 2, center.y + 18), ias.roundToInt().toString(), WHITE)
        s.autopilot.spdKt?.let { label(m, it.roundToInt().toString(), Offset(tapeW / 2, fmaH + 10f), c.accent, 13.sp, true) }

        // --- taśma wysokości ---
        val ax = att.right
        val pxFt = att.height / 1100f
        drawRect(TAPE, Offset(ax, att.top), Size(tapeW, att.height))
        clipRect(ax, att.top, ax + tapeW, att.bottom) {
            val base = floor(alt / 100).toInt() * 100
            for (v in base - 600..base + 600 step 100) {
                val y = center.y - ((v - alt) * pxFt).toFloat()
                drawLine(WHITE, Offset(ax, y), Offset(ax + if (v % 500 == 0) 14 else 8, y), 2f)
                if (v % 500 == 0) label(m, v.toString(), Offset(ax + tapeW * 0.58f, y), WHITE, 11.sp)
            }
            s.autopilot.altFt?.let { bug ->
                val y = (center.y - ((bug - alt) * pxFt).toFloat()).coerceIn(att.top + 6, att.bottom - 6)
                drawRect(c.accent, Offset(ax, y - 8), Size(6f, 16f))
            }
        }
        readout(m, Rect(ax + 2, center.y - 18, ax + tapeW - 2, center.y + 18), alt.roundToInt().toString(), WHITE)
        s.autopilot.altFt?.let { label(m, it.roundToInt().toString(), Offset(ax + tapeW / 2, fmaH + 10f), c.accent, 13.sp, true) }

        // --- wariometr ---
        val vs = s.speed.vsFpm ?: 0.0
        val vx = ax + tapeW + w * 0.025f
        drawRect(TAPE, Offset(ax + tapeW, att.top + att.height * 0.15f), Size(w * 0.05f, att.height * 0.7f))
        val vsClamped = vs.coerceIn(-4000.0, 4000.0)
        val vy = center.y - (vsClamped / 4000.0 * att.height * 0.33f).toFloat()
        drawLine(WHITE, Offset(vx, center.y), Offset(vx, vy), 3f)
        if (abs(vs) >= 100) label(m, (vs / 100).roundToInt().toString(), Offset(vx, if (vs > 0) att.top + att.height * 0.11f else att.bottom - att.height * 0.11f), WHITE, 10.sp)

        // --- taśma kursu ---
        val hdg = s.attitude.hdgMagDeg ?: s.attitude.hdgTrueDeg ?: 0.0
        val hy = h - hdgH
        drawRect(TAPE, Offset(att.left, hy), Size(att.width, hdgH))
        val pxDeg = att.width / 70f
        clipRect(att.left, hy, att.right, h) {
            val base = floor(hdg / 5).toInt() * 5
            for (d in base - 40..base + 40 step 5) {
                val x = center.x + ((d - hdg) * pxDeg).toFloat()
                val norm = ((d % 360) + 360) % 360
                drawLine(WHITE, Offset(x, hy), Offset(x, hy + if (norm % 10 == 0) 12 else 6), 2f)
                if (norm % 10 == 0) label(m, "%02d".format(norm / 10), Offset(x, hy + hdgH * 0.55f), WHITE, 11.sp)
            }
            s.autopilot.hdgDeg?.let { bug ->
                var diff = bug - hdg
                diff = ((diff + 540) % 360) - 180
                val x = center.x + (diff * pxDeg).toFloat()
                drawPath(Path().apply { moveTo(x, hy); lineTo(x - 8, hy + 10); lineTo(x + 8, hy + 10); close() }, c.accent)
            }
        }
        drawLine(SYMBOL, Offset(center.x, hy - 4), Offset(center.x, hy + 14), 3f)
        readout(m, Rect(center.x - 30, hy - 30, center.x + 30, hy - 6), "%03d".format(hdg.roundToInt() % 360), WHITE)

        // --- FMA ---
        drawRect(Color(0xFF0E1216), Offset(0f, 0f), Size(w, fmaH))
        val ap = s.autopilot
        val lateral = when { ap.modes.apr -> "APPR"; ap.modes.loc -> "LOC"; ap.modes.nav -> "NAV"; ap.modes.hdg -> "HDG"; else -> "" }
        val vertical = when { ap.modes.alt -> "ALT"; ap.modes.flc -> "FLC"; ap.modes.vs -> "V/S"; else -> "" }
        val cells = listOf(
            (if (ap.athr) "A/THR" else "") to c.green,
            lateral to c.green,
            vertical to c.green,
            listOfNotNull(if (ap.master) "AP" else null, if (ap.fd) "FD" else null).joinToString(" ") to WHITE,
        )
        val cw = w / cells.size
        cells.forEachIndexed { i, (t, col) ->
            if (i > 0) drawLine(Color(0xFF3A4652), Offset(cw * i, 4f), Offset(cw * i, fmaH - 4), 1.5f)
            if (t.isNotEmpty()) label(m, t, Offset(cw * i + cw / 2, fmaH / 2), col, 13.sp, true)
        }
    }
}

private fun DrawScope.readout(m: TextMeasurer, r: Rect, text: String, color: Color) {
    drawRect(Color.Black, r.topLeft, r.size)
    drawRect(color, r.topLeft, r.size, style = Stroke(2f))
    label(m, text, r.center, color, 15.sp, true)
}

/**
 * Wyświetlacz nawigacyjny „kursem do góry”: pierścienie zasięgu, trasa w magencie,
 * samolot u dołu ekranu, wiatr i prędkości w rogach.
 */
@Composable
fun NavDisplay(s: FlightState, route: List<RoutePoint>, rangeNm: Int, modifier: Modifier = Modifier) {
    val m = rememberTextMeasurer()
    val c = Fm.c
    Canvas(modifier.fillMaxWidth().aspectRatio(1f).semantics { contentDescription = loc("Mapa nawigacyjna, zasięg $rangeNm mil", "Navigation display, range $rangeNm miles") }) {
        val w = size.width; val h = size.height
        val ac = Offset(w / 2, h * 0.78f)
        val radius = h * 0.7f
        val scale = radius / rangeNm
        val lat = s.position.lat; val lon = s.position.lon
        val heading = s.attitude.trackTrueDeg ?: s.attitude.hdgTrueDeg ?: 0.0

        drawRect(Color(0xFF0E1216))
        // Pierścienie: pełny zasięg i połowa
        listOf(1f, 0.5f).forEach { f ->
            drawArc(WHITE.copy(alpha = 0.5f), 200f, 140f, false, Offset(ac.x - radius * f, ac.y - radius * f), Size(radius * 2 * f, radius * 2 * f),
                style = Stroke(1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))))
            label(m, "${(rangeNm * f).roundToInt()}", Offset(ac.x - radius * f * 0.93f, ac.y - radius * f * 0.4f), c.accent, 10.sp)
        }
        // Znaczniki kursu na łuku
        for (d in 0 until 360 step 10) {
            val rel = ((d - heading + 540) % 360) - 180
            if (abs(rel) > 70) continue
            val a = Math.toRadians(rel - 90)
            val p1 = Offset(ac.x + radius * cos(a).toFloat(), ac.y + radius * sin(a).toFloat())
            val len = if (d % 30 == 0) 14f else 7f
            val p2 = Offset(ac.x + (radius - len) * cos(a).toFloat(), ac.y + (radius - len) * sin(a).toFloat())
            drawLine(WHITE, p1, p2, 2f)
            if (d % 30 == 0) label(m, "%02d".format(d / 10), Offset(ac.x + (radius - 28) * cos(a).toFloat(), ac.y + (radius - 28) * sin(a).toFloat()), WHITE, 11.sp)
        }

        // Trasa względem samolotu
        if (lat != null && lon != null) {
            val pts = route.filter { it.hasPosition }.map { p ->
                val d = Geo.distanceNm(lat, lon, p.lat!!, p.lon!!)
                val b = Geo.bearing(lat, lon, p.lat, p.lon)
                val rel = Math.toRadians(b - heading)
                p to Offset(ac.x + (sin(rel) * d * scale).toFloat(), ac.y - (cos(rel) * d * scale).toFloat())
            }
            clipRect(0f, 0f, w, h) {
                if (pts.size >= 2) {
                    val path = Path().apply { pts.forEachIndexed { i, (_, o) -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
                    drawPath(path, c.magenta, style = Stroke(3f))
                }
                pts.forEach { (p, o) ->
                    if (o.x in -20f..w + 20 && o.y in -20f..h + 20) {
                        drawCircle(c.magenta, 5f, o)
                        label(m, p.ident, Offset(o.x + 26, o.y - 12), c.magenta, 10.sp)
                    }
                }
            }
        }

        // Samolot
        drawPath(Path().apply {
            moveTo(ac.x, ac.y - 18); lineTo(ac.x + 14, ac.y + 12); lineTo(ac.x, ac.y + 5); lineTo(ac.x - 14, ac.y + 12); close()
        }, SYMBOL)

        // Rogi: GS/TAS, wiatr
        label(m, "GS ${(s.speed.gsKt ?: 0.0).roundToInt()}  TAS ${(s.speed.tasKt ?: 0.0).roundToInt()}", Offset(w * 0.24f, h * 0.05f), WHITE, 12.sp, true)
        val wd = s.environment.windDirDeg; val wsp = s.environment.windSpdKt
        if (wd != null && wsp != null) {
            label(m, "%03d°/%d".format(wd.roundToInt(), wsp.roundToInt()), Offset(w * 0.15f, h * 0.11f), WHITE, 12.sp)
            // Strzałka: dokąd wieje wiatr, względem kursu
            val rel = Math.toRadians(wd + 180 - heading)
            val o = Offset(w * 0.08f, h * 0.2f)
            val tip = Offset(o.x + (sin(rel) * 18).toFloat(), o.y - (cos(rel) * 18).toFloat())
            val tail = Offset(o.x - (sin(rel) * 18).toFloat(), o.y + (cos(rel) * 18).toFloat())
            drawLine(c.green, tail, tip, 3f)
            drawCircle(c.green, 4f, tip)
        }
        label(m, "TRK %03d°".format(heading.roundToInt() % 360), Offset(w / 2, h * 0.05f), c.green, 13.sp, true)
    }
}
