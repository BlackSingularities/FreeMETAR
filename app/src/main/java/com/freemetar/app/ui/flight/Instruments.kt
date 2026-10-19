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
import androidx.compose.ui.graphics.drawscope.clipPath
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
private val FPV = Color(0xFF3FE07A)
private val NAV_APT = Color(0xFF4FD1E8)
private val NAV_VOR = Color(0xFF3FE07A)
private val NAV_NDB = Color(0xFFFFB020)
private val NAV_WPT = Color(0xFF9AA4AE)

/** Przyspieszenie (kt/s) z kolejnych odczytów IAS, wygładzone — do strzałki trendu na taśmie prędkości. */
@androidx.compose.runtime.Composable
private fun rememberSpeedTrend(ias: Double): Double {
    val holder = androidx.compose.runtime.remember { doubleArrayOf(Double.NaN, 0.0, 0.0) } // ostatnia IAS, czas [s], trend
    val now = System.nanoTime() / 1e9
    if (holder[0].isNaN()) { holder[0] = ias; holder[1] = now }
    val dt = now - holder[1]
    if (dt >= 0.2) {
        val a = (ias - holder[0]) / dt
        holder[2] = holder[2] * 0.7 + a * 0.3
        holder[0] = ias; holder[1] = now
    }
    return holder[2]
}

internal fun DrawScope.label(m: TextMeasurer, text: String, center: Offset, color: Color, size: TextUnit, bold: Boolean = false) {
    val l = m.measure(text, TextStyle(color = color, fontSize = size, fontFamily = Mono, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal))
    drawText(l, topLeft = Offset(center.x - l.size.width / 2f, center.y - l.size.height / 2f))
}

/**
 * Główny wyświetlacz lotu: sztuczny horyzont z drabinką pochylenia i skalą przechylenia,
 * taśmy prędkości i wysokości, wariometr, taśma kursu i pasek trybów autopilota (FMA).
 * Do tego: wektor toru lotu (znos i kąt toru z prawdziwych danych), trend prędkości na 10 s,
 * Mach, QNH, wysokość nad terenem z pasem ziemi na taśmie, znacznik toru na taśmie kursu i wiatr.
 * Bez pasków dyrektora lotu — Link nie podaje poleceń FD, a zmyślone byłyby mylące.
 */
@Composable
fun Pfd(s: FlightState, modifier: Modifier = Modifier) {
    val m = rememberTextMeasurer()
    val c = Fm.c
    val ias = s.speed.iasKt ?: 0.0
    val trend = rememberSpeedTrend(ias)
    val alt = s.position.altIndFt ?: s.position.altMslFt ?: 0.0
    val desc = loc("PFD: prędkość ${ias.roundToInt()} węzłów, wysokość ${alt.roundToInt()} stóp, kurs ${(s.attitude.hdgMagDeg ?: 0.0).roundToInt()}", "PFD: speed ${ias.roundToInt()} knots, altitude ${alt.roundToInt()} feet, heading ${(s.attitude.hdgMagDeg ?: 0.0).roundToInt()}")
    Canvas(modifier.fillMaxWidth().aspectRatio(1.12f).semantics { contentDescription = desc }) {
        val w = size.width; val h = size.height
        val fmaH = h * 0.085f
        val hdgH = h * 0.12f
        val tapeW = w * 0.16f
        val vsiW = w * 0.1f   // wskaźnik prędkości pionowej po prawej
        val att = Rect(tapeW, fmaH, w - tapeW - vsiW, h - hdgH)
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
                    // Wektor toru lotu: w bok o znos (tor − kurs), w pionie kąt toru (V/S do GS)
                    val gs = s.speed.gsKt ?: 0.0
                    if (gs > 30) {
                        val drift = (((s.attitude.trackTrueDeg ?: s.attitude.hdgTrueDeg ?: 0.0) - (s.attitude.hdgTrueDeg ?: 0.0) + 540) % 360 - 180).coerceIn(-20.0, 20.0)
                        val fpa = Math.toDegrees(kotlin.math.atan((s.speed.vsFpm ?: 0.0) / (gs * 101.27)))
                        val fp = Offset(center.x + (drift * ppd).toFloat(), center.y - (fpa * ppd).toFloat())
                        drawCircle(FPV, 9f, fp, style = Stroke(3f))
                        drawLine(FPV, Offset(fp.x - 22, fp.y), Offset(fp.x - 9, fp.y), 3f)
                        drawLine(FPV, Offset(fp.x + 9, fp.y), Offset(fp.x + 22, fp.y), 3f)
                        drawLine(FPV, Offset(fp.x, fp.y - 9), Offset(fp.x, fp.y - 17), 3f)
                    }
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

        // --- ILS indicators (localizer i glideslope) ---
        drawIlsIndicators(m, s, center, att)

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
            // Trend: gdzie będzie prędkość za 10 s (pokazywany od 1 kt różnicy)
            if (abs(trend * 10) >= 1) {
                val ty = center.y - (trend * 10 * pxKt).toFloat()
                val tx = tapeW - 4
                drawLine(FPV, Offset(tx, center.y), Offset(tx, ty), 3f)
                val dir = if (trend > 0) 1 else -1
                drawPath(Path().apply { moveTo(tx, ty - dir * 8); lineTo(tx - 6, ty); lineTo(tx + 6, ty); close() }, FPV)
            }
            s.autopilot.spdKt?.takeIf { s.autopilot.athr || s.autopilot.master }?.let { bug ->
                val y = center.y - ((bug - ias) * pxKt).toFloat()
                drawPath(Path().apply { moveTo(tapeW, y); lineTo(tapeW - 10, y - 8); lineTo(tapeW - 10, y + 8); close() }, c.accent)
            }
        }
        readout(m, Rect(2f, center.y - 18, tapeW - 2, center.y + 18), ias.roundToInt().toString(), WHITE)
        s.autopilot.spdKt?.let { drawRect(Color.Black, Offset(2f, att.top), Size(tapeW - 4, 30f)); label(m, it.roundToInt().toString(), Offset(tapeW / 2, att.top + 15f), c.accent, 13.sp, true) }
        s.speed.mach?.takeIf { it >= 0.4 }?.let { label(m, "M" + "%.2f".format(java.util.Locale.US, it).removePrefix("0"), Offset(tapeW / 2, att.bottom - 12f), WHITE, 12.sp, true) }

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
            // Ziemia pod samolotem (wysokość − AGL): brązowy pas od poziomu terenu w dół
            s.position.altAglFt?.takeIf { it < 1200 }?.let { agl ->
                val gy = center.y + (agl * pxFt).toFloat()
                if (gy < att.bottom) {
                    drawRect(GROUND.copy(alpha = 0.85f), Offset(ax, gy), Size(tapeW, att.bottom - gy))
                    drawLine(SYMBOL, Offset(ax, gy), Offset(ax + tapeW, gy), 3f)
                }
            }
            s.autopilot.altFt?.let { bug ->
                val y = (center.y - ((bug - alt) * pxFt).toFloat()).coerceIn(att.top + 6, att.bottom - 6)
                drawRect(c.accent, Offset(ax, y - 8), Size(6f, 16f))
            }
        }
        readout(m, Rect(ax + 2, center.y - 18, ax + tapeW - 2, center.y + 18), alt.roundToInt().toString(), WHITE)
        s.autopilot.altFt?.let { drawRect(Color.Black, Offset(ax + 2, att.top), Size(tapeW - 4, 30f)); label(m, it.roundToInt().toString(), Offset(ax + tapeW / 2, att.top + 15f), c.accent, 13.sp, true) }
        s.environment.qnhHpa?.let { label(m, "QNH ${it.roundToInt()}", Offset(ax + tapeW / 2, att.bottom - 12f), c.accent, 11.sp, true) }
        // Wysokość nad terenem: od 2500 ft w dół, na dole horyzontu
        s.position.altAglFt?.takeIf { it < 2500 && s.aircraft.onGround != true }?.let { agl ->
            label(m, "${(agl / 10).roundToInt() * 10}", Offset(center.x, att.bottom - att.height * 0.24f), if (agl < 500) SYMBOL else FPV, 17.sp, true)
        }
        // Wiatr w lewym dolnym rogu horyzontu: kierunek/prędkość i strzałka względem kursu
        val wdir = s.environment.windDirDeg; val wspd = s.environment.windSpdKt
        if (wdir != null && wspd != null && wspd >= 1) {
            val wp = Offset(att.left + 34f, att.bottom - 44f)
            label(m, "%03d/%d".format(wdir.roundToInt(), wspd.roundToInt()), Offset(att.left + 44f, att.bottom - 14f), WHITE, 11.sp, true)
            val rel = Math.toRadians(wdir + 180 - (s.attitude.hdgTrueDeg ?: 0.0))
            val tip = Offset(wp.x + (sin(rel) * 14).toFloat(), wp.y - (cos(rel) * 14).toFloat())
            val tail = Offset(wp.x - (sin(rel) * 14).toFloat(), wp.y + (cos(rel) * 14).toFloat())
            drawLine(WHITE, tail, tip, 3f)
            drawCircle(WHITE, 4f, tip)
        }

        // --- wskaźnik prędkości pionowej (VSI): skala nieliniowa 0–1000–2000–6000 ft/min, wskazówka z prawej ---
        val vs = s.speed.vsFpm ?: 0.0
        val vsiX = ax + tapeW
        val vsiTop = att.top + att.height * 0.06f
        val vsiBottom = att.bottom - att.height * 0.06f
        val halfH = (vsiBottom - vsiTop) / 2f
        val vsiMid = center.y
        // Ułamek wychylenia: dokładnie do 1000 ft/min, potem ściśnięte jak na prawdziwym VSI
        fun vsiFrac(v: Double): Float {
            val a = abs(v)
            val f = when {
                a <= 1000 -> a / 1000 * 0.45
                a <= 2000 -> 0.45 + (a - 1000) / 1000 * 0.25
                else -> 0.7 + ((a.coerceAtMost(6000.0) - 2000) / 4000) * 0.3
            }
            return (if (v >= 0) f else -f).toFloat()
        }
        val vsiPath = Path().apply {
            moveTo(vsiX, vsiTop); lineTo(vsiX + vsiW * 0.75f, vsiTop); lineTo(w, vsiMid - halfH * 0.35f)
            lineTo(w, vsiMid + halfH * 0.35f); lineTo(vsiX + vsiW * 0.75f, vsiBottom); lineTo(vsiX, vsiBottom); close()
        }
        drawPath(vsiPath, TAPE)
        for (v in listOf(500, 1000, 1500, 2000, 4000, 6000)) {
            for (sign in listOf(1, -1)) {
                val y = vsiMid - vsiFrac((v * sign).toDouble()) * halfH
                val major = v == 1000 || v == 2000 || v == 6000
                drawLine(WHITE, Offset(vsiX + 2, y), Offset(vsiX + if (major) 12f else 7f, y), 2f)
                if (major) label(m, "${v / 1000}", Offset(vsiX + 22f, y), WHITE, 10.sp)
            }
        }
        drawLine(WHITE, Offset(vsiX + 2, vsiMid), Offset(vsiX + 14f, vsiMid), 3f)
        clipPath(vsiPath) {
            // Wskazówka z punktu za prawą krawędzią do wartości na skali
            val y = vsiMid - vsiFrac(vs) * halfH
            val col = if (abs(vs) > 6000) SYMBOL else FPV
            drawLine(col, Offset(w + vsiW * 1.2f, vsiMid), Offset(vsiX + 4, y), 4f, cap = StrokeCap.Round)
        }
        // Odczyt liczbowy (setki ft/min) przy końcu skali, od 100 ft/min
        if (abs(vs) >= 100) {
            val at = Offset(vsiX + vsiW * 0.45f, if (vs > 0) vsiTop - 2 else vsiBottom + 2)
            drawRect(Color.Black, Offset(vsiX, at.y - 11), Size(vsiW, 22f))
            label(m, (vs / 100).roundToInt().toString(), at, if (abs(vs) > 6000) SYMBOL else FPV, 11.sp, true)
        }

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
        // Tor (magnetyczny): zielony romb — różnica względem kursu to znos
        s.attitude.trackTrueDeg?.let { trk ->
            val magVar = (s.attitude.hdgTrueDeg ?: 0.0) - (s.attitude.hdgMagDeg ?: s.attitude.hdgTrueDeg ?: 0.0)
            val d = (((trk - magVar) - hdg + 540) % 360) - 180
            if (abs(d) < 35) {
                val x = center.x + (d * pxDeg).toFloat()
                drawPath(Path().apply { moveTo(x, hy + 2); lineTo(x + 6, hy + 10); lineTo(x, hy + 18); lineTo(x - 6, hy + 10); close() }, FPV, style = Stroke(2.5f))
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
 * Wyświetlacz nawigacyjny „torem do góry”: ARC (wycinek przed samolotem, samolot u dołu)
 * albo ROSE (pełna róża, samolot w środku). Trasa w magencie, nastawa kursu autopilota w cyjanie,
 * ramka następnego punktu (odległość, namiar, czas), wiatr i prędkości w rogach.
 * Z bazy MSFS (przez Link): lotniska (cyjanowe kółka), VOR (zielone sześciokąty), NDB (bursztynowe
 * kółka z kropkami) i punkty trasy (szare czteroramienne gwiazdki) — warstwy włączane w [layers].
 */
@Composable
fun NavDisplay(
    s: FlightState, route: List<RoutePoint>, rangeNm: Int, modifier: Modifier = Modifier, rose: Boolean = false,
    nav: List<com.freemetar.app.data.nav.FixResult> = emptyList(), layers: Set<String> = setOf("airport", "vor", "ndb"),
    aspect: Float = 1f,
) {
    val m = rememberTextMeasurer()
    val c = Fm.c
    Canvas(modifier.fillMaxWidth().aspectRatio(aspect).semantics { contentDescription = loc("Mapa nawigacyjna, zasięg $rangeNm mil", "Navigation display, range $rangeNm miles") }) {
        val w = size.width; val h = size.height
        val ac = if (rose) Offset(w / 2, h * 0.53f) else Offset(w / 2, h * 0.78f)
        val radius = if (rose) h * 0.38f else h * 0.7f
        val scale = radius / rangeNm
        val lat = s.position.lat; val lon = s.position.lon
        val heading = s.attitude.trackTrueDeg ?: s.attitude.hdgTrueDeg ?: 0.0

        drawRect(Color(0xFF0E1216))
        // Pierścienie: pełny zasięg i połowa
        listOf(1f, 0.5f).forEach { f ->
            drawArc(WHITE.copy(alpha = 0.5f), if (rose) 0f else 200f, if (rose) 360f else 140f, false, Offset(ac.x - radius * f, ac.y - radius * f), Size(radius * 2 * f, radius * 2 * f),
                style = Stroke(1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))))
            label(m, "${(rangeNm * f).roundToInt()}", Offset(ac.x - radius * f * 0.93f, ac.y - radius * f * 0.4f), c.accent, 10.sp)
        }
        // Znaczniki kursu na łuku
        for (d in 0 until 360 step 10) {
            val rel = ((d - heading + 540) % 360) - 180
            if (!rose && abs(rel) > 70) continue
            val a = Math.toRadians(rel - 90)
            val p1 = Offset(ac.x + radius * cos(a).toFloat(), ac.y + radius * sin(a).toFloat())
            val len = if (d % 30 == 0) 14f else 7f
            val p2 = Offset(ac.x + (radius - len) * cos(a).toFloat(), ac.y + (radius - len) * sin(a).toFloat())
            drawLine(WHITE, p1, p2, 2f)
            if (d % 30 == 0) label(m, "%02d".format(d / 10), Offset(ac.x + (radius - 28) * cos(a).toFloat(), ac.y + (radius - 28) * sin(a).toFloat()), WHITE, 11.sp)
        }

        // Lotniska i pomoce nawigacyjne z MSFS (pod trasą, żeby jej nie zasłaniały)
        if (lat != null && lon != null && nav.isNotEmpty()) {
            clipRect(0f, 0f, w, h) {
                nav.filter { it.type in layers }.forEach { f ->
                    val d = Geo.distanceNm(lat, lon, f.lat, f.lon)
                    if (d > rangeNm * 1.05) return@forEach
                    val rel = Math.toRadians(Geo.bearing(lat, lon, f.lat, f.lon) - heading)
                    val o = Offset(ac.x + (sin(rel) * d * scale).toFloat(), ac.y - (cos(rel) * d * scale).toFloat())
                    if (o.x !in -10f..w + 10 || o.y !in -10f..h + 10) return@forEach
                    // W trybie ARC tylko przed samolotem
                    if (!rose && o.y > ac.y + 10) return@forEach
                    val col = when (f.type) { "airport" -> NAV_APT; "vor" -> NAV_VOR; "ndb" -> NAV_NDB; else -> NAV_WPT }
                    when (f.type) {
                        "airport" -> drawCircle(col, 7f, o, style = Stroke(2.5f))
                        "vor" -> drawPath(Path().apply {
                            for (k in 0 until 6) {
                                val a = Math.toRadians(60.0 * k)
                                val p = Offset(o.x + 8 * cos(a).toFloat(), o.y + 8 * sin(a).toFloat())
                                if (k == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                            }
                            close()
                        }, col, style = Stroke(2.5f))
                        "ndb" -> { drawCircle(col, 3f, o); drawCircle(col, 8f, o, style = Stroke(1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 3f)))) }
                        else -> drawPath(Path().apply { moveTo(o.x, o.y - 6); lineTo(o.x + 2, o.y - 2); lineTo(o.x + 6, o.y); lineTo(o.x + 2, o.y + 2); lineTo(o.x, o.y + 6); lineTo(o.x - 2, o.y + 2); lineTo(o.x - 6, o.y); lineTo(o.x - 2, o.y - 2); close() }, col)
                    }
                    // Podpis: identyfikator (i częstotliwość VOR); punkty trasy podpisane tylko na małym zasięgu
                    if (f.type != "waypoint" || rangeNm <= 40) {
                        val text = if (f.type == "vor" && f.freqMhz != null) "${f.ident} ${"%.2f".format(java.util.Locale.US, f.freqMhz)}" else f.ident
                        val l = m.measure(text, TextStyle(color = col, fontSize = 9.sp, fontFamily = Mono))
                        drawText(l, topLeft = Offset(o.x + 10, o.y - l.size.height / 2f))
                    }
                }
            }
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

        // Nastawa kursu autopilota (magnetyczna → rzeczywista), cyjanowy znacznik na pierścieniu
        val magVar = (s.attitude.hdgTrueDeg ?: 0.0) - (s.attitude.hdgMagDeg ?: s.attitude.hdgTrueDeg ?: 0.0)
        s.autopilot.hdgDeg?.let { bug ->
            val rel = ((bug + magVar - heading + 540) % 360) - 180
            if (rose || abs(rel) <= 70) {
                val a = Math.toRadians(rel - 90)
                val p = Offset(ac.x + radius * cos(a).toFloat(), ac.y + radius * sin(a).toFloat())
                rotate(rel.toFloat(), p) {
                    drawPath(Path().apply { moveTo(p.x - 10, p.y - 10); lineTo(p.x + 10, p.y - 10); lineTo(p.x + 10, p.y); lineTo(p.x + 4, p.y); lineTo(p.x, p.y + 6); lineTo(p.x - 4, p.y); lineTo(p.x - 10, p.y); close() }, c.accent, style = Stroke(2.5f))
                }
            }
        }
        // Linia toru przed samolotem
        drawLine(WHITE.copy(alpha = 0.6f), ac, Offset(ac.x, ac.y - radius), 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))

        // Następny punkt trasy: najbliższy z tych przed samolotem (namiar w ±90° od toru)
        if (lat != null && lon != null) {
            val ahead = route.filter { it.hasPosition }.map { p -> p to Geo.distanceNm(lat, lon, p.lat!!, p.lon!!) }
                .filter { (p, _) -> abs(((Geo.bearing(lat, lon, p.lat!!, p.lon!!) - heading + 540) % 360) - 180) < 90 }
            ahead.minByOrNull { it.second }?.let { (p, d) ->
                val brg = (Geo.bearing(lat, lon, p.lat!!, p.lon!!) - magVar + 360) % 360
                val gs = s.speed.gsKt ?: 0.0
                val ete = if (gs > 40) d / gs * 60 else null
                val bx = w * 0.98f
                fun right(text: String, y: Float, col: Color, sz: TextUnit, bold: Boolean = false) {
                    val l = m.measure(text, TextStyle(color = col, fontSize = sz, fontFamily = Mono, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal))
                    drawText(l, topLeft = Offset(bx - l.size.width, y))
                }
                right(p.ident, h * 0.02f, c.magenta, 14.sp, true)
                right("%03d°  %.1f NM".format(brg.roundToInt() % 360, d), h * 0.075f, WHITE, 11.sp)
                ete?.let { right("ETE %d:%02d".format(it.toInt() / 60, (it % 60).toInt()), h * 0.12f, WHITE, 11.sp) }
            }
        }

        // Samolot
        drawPath(Path().apply {
            moveTo(ac.x, ac.y - 18); lineTo(ac.x + 14, ac.y + 12); lineTo(ac.x, ac.y + 5); lineTo(ac.x - 14, ac.y + 12); close()
        }, SYMBOL)

        // Rogi: GS/TAS, wiatr
        // Lewy górny róg: GS i TAS w dwóch liniach (na wąskim ND jedna linia nachodziła na TRK), pod nimi wiatr
        fun left(text: String, y: Float, col: Color, sz: TextUnit, bold: Boolean = false) {
            val l = m.measure(text, TextStyle(color = col, fontSize = sz, fontFamily = Mono, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal))
            drawText(l, topLeft = Offset(w * 0.025f, y))
        }
        left("GS ${(s.speed.gsKt ?: 0.0).roundToInt()}", h * 0.015f, WHITE, 12.sp, true)
        left("TAS ${(s.speed.tasKt ?: 0.0).roundToInt()}", h * 0.065f, WHITE, 11.sp)
        val wd = s.environment.windDirDeg; val wsp = s.environment.windSpdKt
        if (wd != null && wsp != null) {
            left("%03d°/%d".format(wd.roundToInt(), wsp.roundToInt()), h * 0.115f, WHITE, 11.sp)
            // Strzałka: dokąd wieje wiatr, względem kursu
            val rel = Math.toRadians(wd + 180 - heading)
            val o = Offset(w * 0.07f, h * 0.22f)
            val tip = Offset(o.x + (sin(rel) * 18).toFloat(), o.y - (cos(rel) * 18).toFloat())
            val tail = Offset(o.x - (sin(rel) * 18).toFloat(), o.y + (cos(rel) * 18).toFloat())
            drawLine(c.green, tail, tip, 3f)
            drawCircle(c.green, 4f, tip)
        }
        label(m, "TRK %03d°".format(((heading - magVar).roundToInt() + 360) % 360), Offset(w / 2, h * 0.05f), c.green, 13.sp, true)
    }
}

/**
 * Profil pionowy (VSD) pod mapą: wysokość na osi pionowej, odległość przed samolotem na poziomej.
 * Samolot po lewej, w cyjanie wysokość wybrana w autopilocie, w zieleni tor przy bieżącej
 * prędkości pionowej i GS, w magencie ograniczenia wysokości punktów trasy (gdy są w planie).
 */
@Composable
fun VerticalProfile(s: FlightState, route: List<RoutePoint>, rangeNm: Int, modifier: Modifier = Modifier) {
    val m = rememberTextMeasurer()
    val c = Fm.c
    val alt = s.position.altMslFt ?: return
    val lat = s.position.lat; val lon = s.position.lon
    val sel = s.autopilot.altFt
    val gs = s.speed.gsKt ?: 0.0
    val vs = s.speed.vsFpm ?: 0.0
    // Punkty trasy przed samolotem z ograniczeniem wysokości
    val heading = s.attitude.trackTrueDeg ?: s.attitude.hdgTrueDeg ?: 0.0
    val pts = if (lat != null && lon != null) route.filter { it.hasPosition && it.altitudeFt != null }.mapNotNull { p ->
        val d = Geo.distanceNm(lat, lon, p.lat!!, p.lon!!)
        val rel = abs(((Geo.bearing(lat, lon, p.lat, p.lon) - heading + 540) % 360) - 180)
        if (rel < 90 && d <= rangeNm) Triple(p.ident, d, p.altitudeFt!!) else null
    } else emptyList()
    val values = listOfNotNull(alt, sel) + pts.map { it.third.toDouble() }
    val top = ((values.max() + 2000) / 1000).let { kotlin.math.ceil(it) * 1000 }
    val bottom = (((values.min() - 2000) / 1000).let { floor(it) * 1000 }).coerceAtLeast(0.0)
    Canvas(modifier.fillMaxWidth().aspectRatio(3.2f).semantics { contentDescription = loc("Profil pionowy na $rangeNm mil", "Vertical profile over $rangeNm miles") }) {
        val w = size.width; val h = size.height
        val left = w * 0.12f; val right = w * 0.98f; val tp = h * 0.1f; val bt = h * 0.86f
        fun x(d: Double) = left + ((d / rangeNm) * (right - left)).toFloat()
        fun y(a: Double) = bt - (((a - bottom) / (top - bottom)) * (bt - tp)).toFloat()
        drawRect(Color(0xFF0E1216))
        // Siatka wysokości
        val step = if (top - bottom > 12000) 5000 else if (top - bottom > 5000) 2000 else 1000
        var a = (bottom / step).toInt() * step.toDouble()
        while (a <= top) {
            drawLine(WHITE.copy(alpha = 0.15f), Offset(left, y(a)), Offset(right, y(a)), 1f)
            label(m, if (a >= 18000) "FL${(a / 100).roundToInt()}" else "${a.roundToInt()}", Offset(left * 0.5f, y(a)), WHITE.copy(alpha = 0.7f), 9.sp)
            a += step
        }
        // Odległość: połowa i pełny zasięg
        listOf(0.5, 1.0).forEach { f ->
            drawLine(WHITE.copy(alpha = 0.25f), Offset(x(rangeNm * f), tp), Offset(x(rangeNm * f), bt), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            label(m, "${(rangeNm * f).roundToInt()}", Offset(x(rangeNm * f) - 10, h * 0.94f), c.accent, 9.sp)
        }
        // Wysokość wybrana
        sel?.let { drawLine(c.accent, Offset(left, y(it)), Offset(right, y(it)), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))) }
        // Ograniczenia trasy
        if (pts.size >= 2) drawPath(Path().apply { pts.sortedBy { it.second }.forEachIndexed { i, p -> if (i == 0) moveTo(x(p.second), y(p.third.toDouble())) else lineTo(x(p.second), y(p.third.toDouble())) } }, c.magenta, style = Stroke(2f))
        pts.forEach { (id, d, al) ->
            drawCircle(c.magenta, 4f, Offset(x(d), y(al.toDouble())))
            label(m, id, Offset(x(d), y(al.toDouble()) - 12), c.magenta, 9.sp)
        }
        // Tor przy bieżącej prędkości pionowej, aż do wysokości wybranej
        if (gs > 40) {
            val ftPerNm = vs / gs * 60
            val endAlt = if (sel != null && abs(vs) > 100 && (sel - alt) * vs > 0) sel else alt + ftPerNm * rangeNm
            val dEnd = if (abs(ftPerNm) < 1) rangeNm.toDouble() else ((endAlt - alt) / ftPerNm).coerceIn(0.0, rangeNm.toDouble())
            drawLine(FPV, Offset(x(0.0), y(alt)), Offset(x(dEnd), y(alt + ftPerNm * dEnd)), 3f)
            if (dEnd < rangeNm) drawLine(FPV, Offset(x(dEnd), y(alt + ftPerNm * dEnd)), Offset(right, y(alt + ftPerNm * dEnd)), 3f)
        }
        // Samolot
        val ay = y(alt)
        drawPath(Path().apply { moveTo(left + 14, ay); lineTo(left, ay - 7); lineTo(left, ay + 7); close() }, SYMBOL)
        label(m, "VSD", Offset(w * 0.95f, h * 0.06f), WHITE.copy(alpha = 0.6f), 9.sp)
    }
}

/**
 * ILS indicators - localizer i glideslope na PFD.
 *
 * Localizer: pozioma belka odchylenia (±2.5 dots od centerline)
 * Glideslope: pionowa belka odchylenia (±2.5 dots od ścieżki)
 * DME: dystans do progu
 */
private fun DrawScope.drawIlsIndicators(
    m: TextMeasurer,
    s: FlightState,
    center: Offset,
    att: Rect
) {
    // TODO: Przekazać AirportNav z głównego ViewModel
    // Na razie placeholder - rysuj scale zawsze, wskaźniki gdy są dane

    val ilsDev: IlsCalculator.IlsDeviation? = null // TODO: Calculate from actual data

    val localizerY = center.y + att.height * 0.42f // Pod symbolem samolotu
    val glideslopeX = center.x + att.width * 0.38f // Po prawej stronie

    // --- Localizer scale (pozioma, 5 dots) ---
    val locDotSpacing = 20f
    for (i in -2..2) {
        val x = center.x + i * locDotSpacing
        if (i == 0) {
            drawCircle(Color(0xFF00FF00), 3f, Offset(x, localizerY))
        } else {
            drawCircle(Color.White.copy(alpha = 0.5f), 2f, Offset(x, localizerY))
        }
    }

    // Wskaźnik localizer (jeśli są dane)
    ilsDev?.let { dev ->
        val locX = center.x + (dev.localizerDots * locDotSpacing).toFloat()
        drawLine(
            Color(0xFF00FF00),
            Offset(locX, localizerY - 8),
            Offset(locX, localizerY + 8),
            3f
        )
    }

    // --- Glideslope scale (pionowa, 5 dots) ---
    val gsDotSpacing = 20f
    for (i in -2..2) {
        val y = center.y - i * gsDotSpacing
        if (i == 0) {
            drawCircle(Color(0xFF00FF00), 3f, Offset(glideslopeX, y))
        } else {
            drawCircle(Color.White.copy(alpha = 0.5f), 2f, Offset(glideslopeX, y))
        }
    }

    // Wskaźnik glideslope (jeśli są dane)
    ilsDev?.let { dev ->
        val gsY = center.y - (dev.glideslopeDots * gsDotSpacing).toFloat()
        drawLine(
            Color(0xFF00FF00),
            Offset(glideslopeX - 8, gsY),
            Offset(glideslopeX + 8, gsY),
            3f
        )

        // DME distance
        dev.dmeNm?.let { dme ->
            label(
                m,
                "%.1f".format(dme),
                Offset(glideslopeX, center.y + att.height * 0.45f),
                Color(0xFF00FF00),
                11.sp,
                true
            )
        }
    }
}
