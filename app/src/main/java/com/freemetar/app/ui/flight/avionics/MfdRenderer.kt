package com.freemetar.app.ui.flight.avionics

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import com.freemetar.app.data.RoutePoint
import com.freemetar.app.data.link.FlightState
import com.freemetar.app.data.nav.FixResult
import com.freemetar.app.domain.Geo
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Wyświetlacz nawigacyjny (ND/MFD) w stylu kabiny: Airbus ND, Boeing ND (też 787, ATR, E190 w ich kolorach),
 * Garmin G1000 MFD z paskiem EIS silnika oraz mapa GPS w stylu GNS 530 dla samolotów z zegarami.
 * Rysuje w pikselach pola [w] × [h]; tryb ARC (wycinek przed samolotem) albo ROSE (pełna róża).
 */
class MfdRenderer(private val style: DisplayStyle) {
    private val p = Pen()
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private enum class Layout { AIRBUS, BOEING, GARMIN, GNS }

    /** Kolory i ustawienia jednego stylu. */
    private data class Skin(
        val layout: Layout, val bg: Int, val ink: Int, val label: Int, val active: Int, val route: Int, val toWpt: Int,
        val hdgBug: Int, val track: Int, val lubber: Int, val aircraft: Int, val airport: Int, val vor: Int, val ndb: Int,
        val fix: Int, val range: Int, val headingUp: Boolean,
    )

    private val skin = when (style) {
        DisplayStyle.AIRBUS -> Skin(Layout.AIRBUS, Col.BLACK, Col.WHITE, Col.WHITE, 0xFF2BF22B.toInt(), 0xFF2BF22B.toInt(), Col.WHITE,
            0xFF1FE6FF.toInt(), 0xFF2BF22B.toInt(), 0xFFFFF200.toInt(), 0xFFFFF200.toInt(), 0xFFFF55FF.toInt(), 0xFFFF55FF.toInt(),
            0xFFFF55FF.toInt(), 0xFFFF55FF.toInt(), 0xFF1FE6FF.toInt(), headingUp = true)
        DisplayStyle.BOEING, DisplayStyle.B787 -> Skin(Layout.BOEING, Col.BLACK, Col.WHITE, Col.WHITE, 0xFFEE55DD.toInt(), Col.WHITE, 0xFFEE55DD.toInt(),
            0xFFEE55DD.toInt(), Col.WHITE, Col.WHITE, Col.WHITE, 0xFF33DDFF.toInt(), 0xFF33DDFF.toInt(), 0xFF33DDFF.toInt(),
            0xFF33DDFF.toInt(), Col.WHITE, headingUp = false)
        DisplayStyle.ATR -> Skin(Layout.BOEING, 0xFF06080C.toInt(), Col.WHITE, 0xFF2BD9F0.toInt(), 0xFFE54BD2.toInt(), Col.WHITE, 0xFFE54BD2.toInt(),
            0xFF2BD9F0.toInt(), 0xFF27E327.toInt(), 0xFFFFE000.toInt(), 0xFFFFE000.toInt(), 0xFF2BD9F0.toInt(), 0xFF27E327.toInt(),
            0xFF27E327.toInt(), Col.WHITE, 0xFF2BD9F0.toInt(), headingUp = true)
        DisplayStyle.EMBRAER -> Skin(Layout.BOEING, 0xFF05070A.toInt(), Col.WHITE, 0xFF2FD8F2.toInt(), 0xFFE85AD6.toInt(), Col.WHITE, 0xFFE85AD6.toInt(),
            0xFF2FD8F2.toInt(), 0xFFE85AD6.toInt(), Col.WHITE, Col.WHITE, 0xFF2FD8F2.toInt(), 0xFF35E635.toInt(),
            0xFF35E635.toInt(), Col.WHITE, 0xFF2FD8F2.toInt(), headingUp = true)
        DisplayStyle.GARMIN -> Skin(Layout.GARMIN, 0xFF0A1218.toInt(), Col.WHITE, 0xFF3FE0FF.toInt(), 0xFFFF33E6.toInt(), Col.WHITE, 0xFFFF33E6.toInt(),
            0xFF3FE0FF.toInt(), 0xFFFF33E6.toInt(), Col.WHITE, Col.WHITE, 0xFF3FE0FF.toInt(), 0xFF4F8CFF.toInt(),
            0xFF6AC66A.toInt(), 0xFFB0B8C0.toInt(), Col.WHITE, headingUp = true)
        else -> Skin(Layout.GNS, 0xFF020A06.toInt(), 0xFFE8F0E8.toInt(), 0xFF5CE65C.toInt(), 0xFFFF33E6.toInt(), Col.WHITE, 0xFFFF33E6.toInt(),
            0xFF3FE0FF.toInt(), 0xFF5CE65C.toInt(), Col.WHITE, Col.WHITE, 0xFF3FE0FF.toInt(), 0xFF4F8CFF.toInt(),
            0xFF6AC66A.toInt(), 0xFFB0B8C0.toInt(), 0xFF5CE65C.toInt(), headingUp = false)
    }

    // geometria bieżącej klatki
    private var cx = 0f
    private var cy = 0f
    private var radius = 0f
    private var scale = 0f
    private var up = 0f
    private var u = 1f  // jednostka: 1/1000 wysokości

    fun draw(
        c: Canvas, s: FlightState, d: FlightData, w: Float, h: Float, rangeNm: Int, rose: Boolean,
        route: List<RoutePoint>, fixes: List<FixResult>, layers: Set<String>,
    ) {
        u = h / 1000f
        p.rect(c, 0f, 0f, w, h, skin.bg)
        // Obszar mapy: G1000 ma z lewej pasek EIS, GNS — kolumnę danych z prawej
        val left = if (skin.layout == Layout.GARMIN) w * 0.2f else 0f
        val right = if (skin.layout == Layout.GNS) w * 0.7f else w
        val top = if (skin.layout == Layout.GARMIN) 70 * u else 0f
        val mw = right - left
        val mh = h - top
        cx = left + mw / 2
        if (rose) {
            cy = top + mh * 0.54f
            radius = minOf(mh * 0.38f, mw * 0.42f)
        } else {
            cy = top + mh * 0.8f
            radius = minOf(mh * 0.68f, mw * 0.62f)
        }
        scale = radius / rangeNm
        up = if (skin.headingUp) d.heading else d.track

        c.save()
        c.clipRect(left, top, right, h)
        rings(c, rangeNm, rose)
        val lat = s.position.lat
        val lon = s.position.lon
        if (lat != null && lon != null) {
            drawFixes(c, d, lat, lon, fixes, layers, rangeNm, rose)
            drawRoute(c, d, lat, lon, route)
        }
        compass(c, d, rose)
        course(c, d)
        aircraft(c)
        c.restore()

        when (skin.layout) {
            Layout.AIRBUS -> airbusData(c, d, w, h, lat, lon, route)
            Layout.BOEING -> boeingData(c, d, w, h, lat, lon, route)
            Layout.GARMIN -> { garminTop(c, d, w, top, lat, lon, route); eis(c, s, d, left, h) }
            Layout.GNS -> gnsColumn(c, d, right, w, h, lat, lon, route)
        }
        // zasięg w rogu mapy
        p.text(c, "$rangeNm", left + 14 * u, h - 30 * u, 26 * u, skin.range, Paint.Align.LEFT)
    }

    // ------------------------------------------------------------ projekcja
    private fun project(d: FlightData, lat: Double, lon: Double, tlat: Double, tlon: Double): Pair<Float, Float> {
        val dist = Geo.distanceNm(lat, lon, tlat, tlon)
        // namiar rzeczywisty → magnetyczny, względem kierunku „w górę”
        val rel = Math.toRadians(Geo.bearing(lat, lon, tlat, tlon) - d.magVar - up)
        return (cx + (sin(rel) * dist * scale).toFloat()) to (cy - (cos(rel) * dist * scale).toFloat())
    }

    // ------------------------------------------------------------ pierścienie zasięgu
    private fun rings(c: Canvas, rangeNm: Int, rose: Boolean) {
        dash.color = (skin.ink and 0x00FFFFFF) or 0x77000000
        dash.strokeWidth = 2f * u
        dash.pathEffect = DashPathEffect(floatArrayOf(14 * u, 12 * u), 0f)
        for (f in floatArrayOf(0.5f, 1f)) {
            val r = radius * f
            if (rose) c.drawCircle(cx, cy, r, dash)
            else c.drawArc(cx - r, cy - r, cx + r, cy + r, 200f, 140f, false, dash)
        }
        if (skin.layout != Layout.GARMIN) {
            val lbl = (rangeNm / 2f).let { if (it % 1f == 0f) it.toInt().toString() else String.format(Locale.US, "%.1f", it) }
            val a = Math.toRadians(-60.0)
            p.text(c, lbl, cx + (sin(a) * radius * 0.5).toFloat() - 16 * u, cy - (cos(a) * radius * 0.5).toFloat(), 24 * u, skin.range)
        }
    }

    // ------------------------------------------------------------ róża / łuk
    private fun compass(c: Canvas, d: FlightData, rose: Boolean) {
        for (deg in 0 until 360 step 5) {
            val rel = angDiff(deg.toFloat(), up)
            if (!rose && abs(rel) > 62f) continue
            val ten = deg % 10 == 0
            p.radial(c, cx, cy, rel, radius - if (ten) 22 * u else 12 * u, radius, skin.ink, if (ten) 3f * u else 2f * u)
            if (deg % 30 == 0) {
                val a = Math.toRadians(rel.toDouble())
                val rr = radius - 44 * u
                val big = deg % 90 == 0
                val lbl = if (skin.layout == Layout.GARMIN || skin.layout == Layout.GNS) when (deg) {
                    0 -> "N"; 90 -> "E"; 180 -> "S"; 270 -> "W"; else -> (deg / 10).toString()
                } else (deg / 10).toString()
                p.text(c, lbl, cx + (sin(a) * rr).toFloat(), cy - (cos(a) * rr).toFloat(), (if (big) 32f else 26f) * u, skin.ink)
            }
        }
        // znacznik kursu / toru u góry
        p.line(c, cx, cy - radius - 26 * u, cx, cy - radius + 4 * u, skin.lubber, 5f * u)
        // bug kursu z autopilota
        if (!d.hdgBug.isNaN()) {
            val rel = angDiff(d.hdgBug, up)
            if (rose || abs(rel) <= 62f) {
                c.save()
                c.rotate(rel, cx, cy)
                val y = cy - radius
                p.poly(c, 0, skin.hdgBug, 3.5f * u, cx - 18 * u, y - 16 * u, cx + 18 * u, y - 16 * u, cx + 18 * u, y, cx + 6 * u, y,
                    cx, y + 8 * u, cx - 6 * u, y, cx - 18 * u, y)
                c.restore()
            } else {
                // poza łukiem: wartość przy krawędzi
                val txt = String.format(Locale.US, "%03d", mod(d.hdgBug.roundToInt(), 360))
                p.text(c, txt, cx + (if (rel > 0) 1 else -1) * radius * 0.82f, cy - radius * 0.72f, 26 * u, skin.hdgBug)
            }
        }
        // tor (przy mapie kursem do góry) albo kurs (przy mapie torem do góry)
        if (d.hasTrack && skin.headingUp) {
            val rel = angDiff(d.track, up)
            c.save()
            c.rotate(rel, cx, cy)
            p.poly(c, 0, skin.track, 3f * u, cx, cy - radius + 2 * u, cx + 9 * u, cy - radius + 16 * u, cx, cy - radius + 30 * u, cx - 9 * u, cy - radius + 16 * u)
            c.restore()
        } else if (!skin.headingUp) {
            val rel = angDiff(d.heading, up)
            c.save()
            c.rotate(rel, cx, cy)
            p.poly(c, 0, skin.ink, 3f * u, cx, cy - radius + 4 * u, cx - 12 * u, cy - radius + 26 * u, cx + 12 * u, cy - radius + 26 * u)
            c.restore()
            // linia toru przed samolotem
            dash.color = skin.ink
            dash.strokeWidth = 2f * u
            dash.pathEffect = DashPathEffect(floatArrayOf(16 * u, 10 * u), 0f)
            c.drawLine(cx, cy, cx, cy - radius, dash)
        }
    }

    // ------------------------------------------------------------ kurs ILS / VOR
    private fun course(c: Canvas, d: FlightData) {
        val n = d.nav
        if (n.course.isNaN() || n.source == NavInfo.Source.GPS || n.source == NavInfo.Source.NONE) return
        val col = if (skin.layout == Layout.AIRBUS) 0xFFFF55FF.toInt() else 0xFF33DDFF.toInt()
        c.save()
        c.rotate(angDiff(n.course, up), cx, cy)
        dash.color = col
        dash.strokeWidth = 3f * u
        dash.pathEffect = DashPathEffect(floatArrayOf(20 * u, 10 * u), 0f)
        c.drawLine(cx, cy + radius, cx, cy - radius, dash)
        if (!n.cdi.isNaN()) p.line(c, cx + n.cdi.coerceIn(-2.5f, 2.5f) * 24 * u, cy - 70 * u, cx + n.cdi.coerceIn(-2.5f, 2.5f) * 24 * u, cy + 70 * u, col, 5f * u)
        c.restore()
    }

    // ------------------------------------------------------------ samolot
    private fun aircraft(c: Canvas) {
        val k = u
        when (skin.layout) {
            Layout.AIRBUS -> {
                p.line(c, cx, cy - 30 * k, cx, cy + 34 * k, skin.aircraft, 6f * k)
                p.line(c, cx - 30 * k, cy - 4 * k, cx + 30 * k, cy - 4 * k, skin.aircraft, 6f * k)
                p.line(c, cx - 12 * k, cy + 26 * k, cx + 12 * k, cy + 26 * k, skin.aircraft, 5f * k)
            }
            Layout.BOEING -> p.poly(c, 0, skin.aircraft, 3.5f * k, cx, cy - 34 * k, cx - 18 * k, cy + 20 * k, cx + 18 * k, cy + 20 * k)
            else -> p.poly(c, skin.aircraft, Col.BLACK, 2f * k,
                cx, cy - 22 * k, cx - 4 * k, cy - 8 * k, cx - 24 * k, cy + 2 * k, cx - 24 * k, cy + 9 * k, cx - 4 * k, cy + 5 * k,
                cx - 4 * k, cy + 17 * k, cx - 10 * k, cy + 22 * k, cx - 10 * k, cy + 26 * k, cx + 10 * k, cy + 26 * k,
                cx + 10 * k, cy + 22 * k, cx + 4 * k, cy + 17 * k, cx + 4 * k, cy + 5 * k, cx + 24 * k, cy + 9 * k,
                cx + 24 * k, cy + 2 * k, cx + 4 * k, cy - 8 * k)
        }
    }

    // ------------------------------------------------------------ pomoce nawigacyjne
    private fun drawFixes(c: Canvas, d: FlightData, lat: Double, lon: Double, fixes: List<FixResult>, layers: Set<String>, rangeNm: Int, rose: Boolean) {
        for (f in fixes) {
            if (f.type !in layers) continue
            if (Geo.distanceNm(lat, lon, f.lat, f.lon) > rangeNm * 1.05) continue
            val (x, y) = project(d, lat, lon, f.lat, f.lon)
            if (!rose && y > cy + 10 * u) continue
            val col = when (f.type) { "airport" -> skin.airport; "vor" -> skin.vor; "ndb" -> skin.ndb; else -> skin.fix }
            when (f.type) {
                "airport" -> {
                    p.circle(c, x, y, 9 * u, col, 3f * u)
                    if (skin.layout == Layout.AIRBUS) p.line(c, x - 14 * u, y, x + 14 * u, y, col, 3f * u)
                }
                "vor" -> {
                    if (skin.layout == Layout.AIRBUS || skin.layout == Layout.BOEING) {
                        p.line(c, x - 10 * u, y, x + 10 * u, y, col, 3f * u)
                        p.line(c, x, y - 10 * u, x, y + 10 * u, col, 3f * u)
                    } else {
                        val pts = FloatArray(12)
                        for (k in 0 until 6) {
                            val a = Math.toRadians(60.0 * k)
                            pts[k * 2] = x + (10 * u * cos(a)).toFloat(); pts[k * 2 + 1] = y + (10 * u * sin(a)).toFloat()
                        }
                        p.poly(c, 0, col, 2.5f * u, *pts)
                        p.circle(c, x, y, 2.5f * u, col)
                    }
                }
                "ndb" -> {
                    p.circle(c, x, y, 3.5f * u, col)
                    p.circle(c, x, y, 10 * u, col, 1.5f * u)
                }
                else -> p.poly(c, 0, col, 2.5f * u, x, y - 9 * u, x + 8 * u, y + 6 * u, x - 8 * u, y + 6 * u)
            }
            if (f.type != "waypoint" || rangeNm <= 40) {
                p.text(c, f.ident, x + 14 * u, y - 14 * u, 22 * u, col, Paint.Align.LEFT)
            }
        }
    }

    // ------------------------------------------------------------ trasa
    /** Indeks następnego punktu trasy: z GPS symulatora (po identyfikatorze), inaczej najbliższy przed samolotem. */
    private fun nextIndex(d: FlightData, lat: Double, lon: Double, pts: List<RoutePoint>): Int {
        d.gps.nextId?.let { id -> pts.indexOfFirst { it.ident.equals(id, true) }.takeIf { it >= 0 }?.let { return it } }
        var best = -1
        var bestD = Double.MAX_VALUE
        pts.forEachIndexed { i, rp ->
            val brg = Geo.bearing(lat, lon, rp.lat!!, rp.lon!!) - d.magVar
            if (abs(angDiff(brg.toFloat(), d.track)) < 90f) {
                val dist = Geo.distanceNm(lat, lon, rp.lat, rp.lon)
                if (dist < bestD) { bestD = dist; best = i }
            }
        }
        return best
    }

    private fun drawRoute(c: Canvas, d: FlightData, lat: Double, lon: Double, route: List<RoutePoint>) {
        val pts = route.filter { it.hasPosition }
        if (pts.isEmpty()) return
        val xy = pts.map { project(d, lat, lon, it.lat!!, it.lon!!) }
        val next = nextIndex(d, lat, lon, pts)
        for (i in 1 until xy.size) {
            val active = i == next
            p.line(c, xy[i - 1].first, xy[i - 1].second, xy[i].first, xy[i].second, if (active) skin.active else skin.route, (if (active) 5f else 3.5f) * u)
        }
        pts.forEachIndexed { i, rp ->
            val (x, y) = xy[i]
            val col = if (i == next) skin.toWpt else skin.route
            p.poly(c, 0, col, 2.5f * u, x, y - 10 * u, x + 10 * u, y, x, y + 10 * u, x - 10 * u, y)
            p.text(c, rp.ident, x + 16 * u, y - 14 * u, 22 * u, col, Paint.Align.LEFT)
            rp.constraint?.let { p.text(c, it, x + 16 * u, y + 12 * u, 18 * u, 0xFFFF55FF.toInt(), Paint.Align.LEFT) }
        }
    }

    /** Następny punkt: identyfikator, namiar (mag.), odległość, czas — z GPS symulatora albo z planu. */
    private data class Next(val id: String, val brg: Float, val dist: Float, val eteSec: Float)

    private fun next(d: FlightData, lat: Double?, lon: Double?, route: List<RoutePoint>): Next? {
        val g = d.gps
        if (g.nextId != null && !g.nextDistNm.isNaN()) return Next(g.nextId, g.nextBrg, g.nextDistNm, g.nextEteSec)
        if (lat == null || lon == null) return null
        val pts = route.filter { it.hasPosition }
        val i = nextIndex(d, lat, lon, pts).takeIf { it >= 0 } ?: return null
        val rp = pts[i]
        val dist = Geo.distanceNm(lat, lon, rp.lat!!, rp.lon!!).toFloat()
        val brg = wrap360((Geo.bearing(lat, lon, rp.lat, rp.lon) - d.magVar).toFloat())
        val ete = if (!d.gsKt.isNaN() && d.gsKt > 40f) dist / d.gsKt * 3600f else Float.NaN
        return Next(rp.ident, brg, dist, ete)
    }

    private fun ete(sec: Float) = if (sec.isNaN()) "--:--" else String.format(Locale.US, "%d:%02d", (sec / 3600).toInt(), ((sec % 3600) / 60).toInt())
    private fun eteMin(sec: Float) = if (sec.isNaN()) "--:--" else String.format(Locale.US, "%02d:%02d", (sec / 60).toInt(), (sec % 60).toInt())

    // ------------------------------------------------------------ Airbus: rogi ND
    private fun airbusData(c: Canvas, d: FlightData, w: Float, h: Float, lat: Double?, lon: Double?, route: List<RoutePoint>) {
        val green = 0xFF2BF22B.toInt()
        val cyan = 0xFF1FE6FF.toInt()
        val x0 = 16 * u
        p.text(c, "GS", x0, 30 * u, 28 * u, Col.WHITE, Paint.Align.LEFT)
        p.text(c, if (d.gsKt.isNaN()) "---" else d.gsKt.roundToInt().toString(), x0 + 48 * u, 30 * u, 32 * u, green, Paint.Align.LEFT)
        p.text(c, "TAS", x0 + 130 * u, 30 * u, 28 * u, Col.WHITE, Paint.Align.LEFT)
        p.text(c, if (d.tasKt.isNaN()) "---" else d.tasKt.roundToInt().toString(), x0 + 196 * u, 30 * u, 32 * u, green, Paint.Align.LEFT)
        if (d.windValid) {
            p.text(c, String.format(Locale.US, "%03d/%d", mod(d.windDir.roundToInt(), 360), d.windKt.roundToInt()), x0, 66 * u, 28 * u, green, Paint.Align.LEFT)
            windArrow(c, x0 + 26 * u, 112 * u, d, green)
        }
        next(d, lat, lon, route)?.let { n ->
            val xr = w - 16 * u
            p.text(c, n.id, xr - 170 * u, 30 * u, 30 * u, Col.WHITE, Paint.Align.LEFT)
            p.text(c, String.format(Locale.US, "%03d°", mod(n.brg.roundToInt(), 360)), xr, 30 * u, 30 * u, green, Paint.Align.RIGHT)
            p.text(c, String.format(Locale.US, "%.1f", n.dist), xr - 50 * u, 66 * u, 30 * u, green, Paint.Align.RIGHT)
            p.text(c, "NM", xr, 66 * u, 24 * u, cyan, Paint.Align.RIGHT)
            p.text(c, ete(n.eteSec), xr, 100 * u, 28 * u, green, Paint.Align.RIGHT)
        }
        // Źródła radiowe w dolnych rogach (VOR1/ADF)
        val n = d.nav
        if (n.source == NavInfo.Source.VOR || n.source == NavInfo.Source.LOC) {
            p.text(c, if (n.source == NavInfo.Source.LOC) "ILS1" else "VOR1", x0, h - 100 * u, 26 * u, Col.WHITE, Paint.Align.LEFT)
            n.ident?.let { p.text(c, it, x0, h - 68 * u, 26 * u, 0xFFFF55FF.toInt(), Paint.Align.LEFT) }
            if (!n.dmeNm.isNaN()) p.text(c, String.format(Locale.US, "%.1f NM", n.dmeNm), x0 + 110 * u, h - 68 * u, 26 * u, green, Paint.Align.LEFT)
        }
        if (!d.brgAdf.isNaN()) p.text(c, "ADF1", w - 16 * u, h - 100 * u, 26 * u, green, Paint.Align.RIGHT)
    }

    // ------------------------------------------------------------ Boeing / Collins / Honeywell / Thales: rogi ND
    private fun boeingData(c: Canvas, d: FlightData, w: Float, h: Float, lat: Double?, lon: Double?, route: List<RoutePoint>) {
        val x0 = 16 * u
        p.text(c, "GS", x0, 30 * u, 26 * u, skin.label, Paint.Align.LEFT)
        p.text(c, if (d.gsKt.isNaN()) "---" else d.gsKt.roundToInt().toString(), x0 + 44 * u, 30 * u, 32 * u, Col.WHITE, Paint.Align.LEFT)
        p.text(c, "TAS", x0 + 124 * u, 30 * u, 26 * u, skin.label, Paint.Align.LEFT)
        p.text(c, if (d.tasKt.isNaN()) "---" else d.tasKt.roundToInt().toString(), x0 + 184 * u, 30 * u, 32 * u, Col.WHITE, Paint.Align.LEFT)
        if (d.windValid) {
            p.text(c, String.format(Locale.US, "%03d°/%d", mod(d.windDir.roundToInt(), 360), d.windKt.roundToInt()), x0, 66 * u, 28 * u, Col.WHITE, Paint.Align.LEFT)
            windArrow(c, x0 + 26 * u, 112 * u, d, Col.WHITE)
        }
        // Kurs/tor u góry: „TRK 123 MAG” jak w 737/787, „HDG 123” w pozostałych
        val top = if (skin.headingUp) "HDG" else "TRK"
        val v = mod((if (skin.headingUp) d.heading else d.track).roundToInt(), 360)
        p.text(c, top, w / 2 - 60 * u, 30 * u, 26 * u, skin.label, Paint.Align.RIGHT)
        p.rect(c, w / 2 - 50 * u, 10 * u, w / 2 + 50 * u, 50 * u, Col.BLACK)
        p.frame(c, w / 2 - 50 * u, 10 * u, w / 2 + 50 * u, 50 * u, Col.WHITE, 2f * u)
        p.text(c, String.format(Locale.US, "%03d", v), w / 2, 30 * u, 32 * u, Col.WHITE)
        p.text(c, "MAG", w / 2 + 60 * u, 30 * u, 24 * u, skin.label, Paint.Align.LEFT)
        next(d, lat, lon, route)?.let { n ->
            val xr = w - 16 * u
            p.text(c, n.id, xr, 30 * u, 30 * u, skin.toWpt, Paint.Align.RIGHT)
            p.text(c, "ETE " + ete(n.eteSec), xr, 66 * u, 26 * u, Col.WHITE, Paint.Align.RIGHT)
            p.text(c, String.format(Locale.US, "%.1f NM", n.dist), xr, 100 * u, 28 * u, Col.WHITE, Paint.Align.RIGHT)
        }
        // Źródło nawigacji radiowej w dolnym lewym rogu
        val n = d.nav
        if (n.source == NavInfo.Source.VOR || n.source == NavInfo.Source.LOC) {
            p.text(c, if (n.source == NavInfo.Source.LOC) "ILS L" else "VOR L", x0, h - 100 * u, 26 * u, 0xFF3EEA4C.toInt(), Paint.Align.LEFT)
            n.ident?.let { p.text(c, it, x0, h - 68 * u, 26 * u, Col.WHITE, Paint.Align.LEFT) }
            if (!n.dmeNm.isNaN()) p.text(c, String.format(Locale.US, "DME %.1f", n.dmeNm), x0, h - 36 * u, 24 * u, Col.WHITE, Paint.Align.LEFT)
        }
    }

    private fun windArrow(c: Canvas, x: Float, y: Float, d: FlightData, col: Int) {
        c.save()
        c.rotate(d.windDir + 180f - up, x, y)
        p.line(c, x, y + 22 * u, x, y - 22 * u, col, 4f * u)
        p.poly(c, col, col, 1f, x, y + 26 * u, x - 9 * u, y + 10 * u, x + 9 * u, y + 10 * u)
        c.restore()
    }

    // ------------------------------------------------------------ G1000 MFD: pasek nawigacji
    private fun garminTop(c: Canvas, d: FlightData, w: Float, top: Float, lat: Double?, lon: Double?, route: List<RoutePoint>) {
        val x0 = w * 0.2f
        p.rect(c, x0, 0f, w, top, 0xCC0A0F16.toInt())
        p.line(c, x0, top, w, top, 0xFF9AA0A6.toInt(), 2f * u)
        val n = next(d, lat, lon, route)
        val fields = listOf(
            "GS" to (if (d.gsKt.isNaN()) "---KT" else d.gsKt.roundToInt().toString() + "KT"),
            "DTK" to (if (d.gps.dtk.isNaN()) "---°" else String.format(Locale.US, "%03d°", mod(d.gps.dtk.roundToInt(), 360))),
            "TRK" to (if (d.hasTrack) String.format(Locale.US, "%03d°", mod(d.track.roundToInt(), 360)) else "---°"),
            "ETE" to (n?.let { ete(it.eteSec) } ?: "--:--"),
        )
        val fw = (w - x0) / fields.size
        fields.forEachIndexed { i, (k, v) ->
            val x = x0 + fw * i + 10 * u
            p.text(c, k, x, top / 2, 22 * u, Col.WHITE, Paint.Align.LEFT)
            p.text(c, v, x + 54 * u, top / 2, 28 * u, 0xFFFF33E6.toInt(), Paint.Align.LEFT)
        }
    }

    // ------------------------------------------------------------ G1000 MFD: EIS (silnik)
    private fun eis(c: Canvas, s: FlightState, d: FlightData, x1: Float, h: Float) {
        p.rect(c, 0f, 0f, x1, h, 0xFF101418.toInt())
        p.line(c, x1, 0f, x1, h, 0xFF9AA0A6.toInt(), 2f * u)
        val e = s.engines.firstOrNull()
        val turbine = s.aircraft.engineType == "jet" || s.aircraft.engineType == "turboprop"
        var y = 30 * u
        fun gauge(label: String, value: Double?, max: Double, unit: String, green: ClosedFloatingPointRange<Double>? = null) {
            p.text(c, label, 12 * u, y, 22 * u, Col.WHITE, Paint.Align.LEFT)
            p.text(c, value?.let { (if (max >= 100) it.roundToInt().toString() else String.format(Locale.US, "%.1f", it)) + unit } ?: "---",
                x1 - 12 * u, y, 24 * u, Col.WHITE, Paint.Align.RIGHT)
            y += 26 * u
            val bx0 = 14 * u; val bx1 = x1 - 14 * u
            p.rect(c, bx0, y - 6 * u, bx1, y + 6 * u, 0xFF2A3038.toInt())
            green?.let { g -> p.rect(c, bx0 + ((g.start / max) * (bx1 - bx0)).toFloat(), y - 6 * u, bx0 + ((g.endInclusive / max) * (bx1 - bx0)).toFloat(), y + 6 * u, 0xFF1F8A2A.toInt()) }
            value?.let { v ->
                val x = bx0 + ((v / max).coerceIn(0.0, 1.0) * (bx1 - bx0)).toFloat()
                p.poly(c, Col.WHITE, Col.BLACK, 1f, x, y + 8 * u, x - 8 * u, y - 10 * u, x + 8 * u, y - 10 * u)
            }
            y += 34 * u
        }
        if (turbine) {
            gauge("N1", e?.n1Pct, 110.0, "%", 20.0..100.0)
            gauge("N2", e?.n2Pct, 110.0, "%", 50.0..102.0)
            gauge("ITT", e?.egtC, 1000.0, "°C", 300.0..800.0)
        } else {
            gauge("RPM", e?.rpm, 3000.0, "", 2100.0..2700.0)
            gauge("EGT", e?.egtC, 900.0, "°C", 500.0..800.0)
        }
        gauge("FFLOW", e?.fuelFlowKgH, if (turbine) 1200.0 else 80.0, "")
        p.text(c, "kg/h", x1 - 12 * u, y - 52 * u, 18 * u, 0xFF9AA0A6.toInt(), Paint.Align.RIGHT)
        // paliwo: zbiorniki L / R
        p.text(c, "FUEL QTY kg", 12 * u, y, 22 * u, Col.WHITE, Paint.Align.LEFT)
        y += 30 * u
        val tanks = s.fuel.tanks
        val l = tanks["leftKg"] ?: tanks["left"] ?: tanks.values.firstOrNull()
        val r = tanks["rightKg"] ?: tanks["right"] ?: tanks.values.drop(1).firstOrNull()
        p.text(c, "L " + (l?.roundToInt() ?: "--"), 12 * u, y, 24 * u, 0xFF3FE07A.toInt(), Paint.Align.LEFT)
        p.text(c, "R " + (r?.roundToInt() ?: "--"), x1 - 12 * u, y, 24 * u, 0xFF3FE07A.toInt(), Paint.Align.RIGHT)
        y += 32 * u
        p.text(c, "TOT " + (s.fuel.totalKg?.roundToInt() ?: "--"), 12 * u, y, 24 * u, Col.WHITE, Paint.Align.LEFT)
        y += 40 * u
        // klapy, podwozie, trym
        d.flapsIndex?.let { f -> p.text(c, "FLAPS " + f + "/" + ((d.flapsPositions ?: 1) - 1), 12 * u, y, 22 * u, Col.WHITE, Paint.Align.LEFT); y += 30 * u }
        d.gearDown?.let { g -> p.text(c, if (g) "GEAR DN" else "GEAR UP", 12 * u, y, 22 * u, if (g) 0xFF3FE07A.toInt() else Col.WHITE, Paint.Align.LEFT); y += 30 * u }
        if (!d.oatC.isNaN()) p.text(c, "OAT " + d.oatC.roundToInt() + "°C", 12 * u, y, 22 * u, Col.WHITE, Paint.Align.LEFT)
    }

    // ------------------------------------------------------------ GNS 530: kolumna danych
    private fun gnsColumn(c: Canvas, d: FlightData, x0: Float, w: Float, h: Float, lat: Double?, lon: Double?, route: List<RoutePoint>) {
        val green = 0xFF5CE65C.toInt()
        p.rect(c, x0, 0f, w, h, 0xFF041008.toInt())
        p.line(c, x0, 0f, x0, h, green, 2f * u)
        val n = next(d, lat, lon, route)
        val rows = listOf(
            "GS" to (if (d.gsKt.isNaN()) "---kt" else d.gsKt.roundToInt().toString() + "kt"),
            "DTK" to (if (d.gps.dtk.isNaN()) "---°" else String.format(Locale.US, "%03d°", mod(d.gps.dtk.roundToInt(), 360))),
            "TRK" to (if (d.hasTrack) String.format(Locale.US, "%03d°", mod(d.track.roundToInt(), 360)) else "---°"),
            "DIS" to (n?.let { String.format(Locale.US, "%.1fnm", it.dist) } ?: "---"),
            "ETE" to (n?.let { eteMin(it.eteSec) } ?: "--:--"),
            "BRG" to (n?.let { if (it.brg.isNaN()) "---°" else String.format(Locale.US, "%03d°", mod(it.brg.roundToInt(), 360)) } ?: "---°"),
        )
        val rh = (h - 80 * u) / rows.size
        p.text(c, n?.id ?: "----", (x0 + w) / 2, 40 * u, 32 * u, 0xFFFF33E6.toInt())
        rows.forEachIndexed { i, (k, v) ->
            val y = 80 * u + rh * i + rh / 2
            p.text(c, k, x0 + 12 * u, y - rh * 0.22f, 20 * u, green, Paint.Align.LEFT)
            p.text(c, v, w - 12 * u, y + rh * 0.12f, 30 * u, Col.WHITE, Paint.Align.RIGHT)
            p.line(c, x0 + 8 * u, y + rh / 2, w - 8 * u, y + rh / 2, 0xFF1C3A22.toInt(), 1.5f * u)
        }
    }
}
