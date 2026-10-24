package com.freemetar.app.ui.flight.avionics

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Path
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

/** PFD w stylu Garmin G1000/G3000 z widokiem syntetycznym (SVT), zasilany z symulatora: AFCS, radia, HSI, bugi. */
class GarminPfd : PfdRenderer {
    private val p = Pen()
    private val clock = SimpleDateFormat("HH:mm:ss", Locale.US)

    private companion object {
        const val CX = 500f
        const val CY = 330f
        const val PPD = 8f

        const val PANEL = 0x99101820.toInt()
        const val PANEL_DARK = 0xCC0A0F16.toInt()
        const val GREEN = 0xFF2EF03A.toInt()
        const val CYAN = 0xFF3FE0FF.toInt()
        const val MAGENTA = 0xFFFF33E6.toInt()
        const val YELLOW = 0xFFF5C800.toInt()
        const val YELLOW_DARK = 0xFFB08A00.toInt()
        const val GREY = 0xFF9AA0A6.toInt()
        const val LADDER = 0xCCE6E6E6.toInt()

        // prędkości charakterystyczne (Cessna 172)
        const val VS0 = 40f
        const val VFE = 85f
        const val VS1 = 48f
        const val VNO = 129f
        const val VNE = 163f

        const val HX = CX
        const val HY = 580f
        const val HR = 120f
    }

    private val skyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, CY - 900f, 0f, CY, 0xFF0B3C8C.toInt(), 0xFF6FA8E8.toInt(), Shader.TileMode.CLAMP)
    }
    private val groundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, CY, 0f, CY + 700f, 0xFF3F7A2A.toInt(), 0xFF153F12.toInt(), Shader.TileMode.CLAMP)
    }

    override val vw = 1000f
    override val vh = 750f

    override val baroRect = RectF(700f, 528f, 862f, 582f)

    override fun draw(c: Canvas, d: FlightData) {
        drawAttitude(c, d)
        drawSpeed(c, d)
        drawAltitude(c, d)
        drawVsi(c, d)
        drawHsi(c, d)
        drawTopBar(c, d)
        drawBaro(c, d)
        drawWind(c, d)
        drawBottomBar(c, d)
    }

    // ---------------------------------------------------------------- SVT / horyzont
    private fun drawAttitude(c: Canvas, d: FlightData) {
        if (!d.attValid) {
            p.rect(c, 0f, 0f, 1000f, 750f, 0xFF20262E.toInt())
            p.line(c, 350f, 190f, 650f, 450f, Col.RED, 8f)
            p.line(c, 650f, 190f, 350f, 450f, Col.RED, 8f)
            p.text(c, "AHRS FAIL", CX, 520f, 40f, Col.RED)
            return
        }
        c.save()
        c.rotate(-d.roll, CX, CY)
        c.translate(0f, d.pitch * PPD)
        c.drawRect(CX - 1200, CY - 3000, CX + 1200, CY, skyPaint)
        c.drawRect(CX - 1200, CY, CX + 1200, CY + 3000, groundPaint)
        drawTerrain(c, d)
        p.line(c, CX - 1200, CY, CX + 1200, CY, Col.WHITE, 4f)

        // kursy na linii horyzontu
        var h = floorTo(d.heading - 60f, 10)
        while (h <= d.heading + 60f) {
            val x = CX + angDiff(h.toFloat(), d.heading) * PPD
            p.line(c, x, CY, x, CY + 14, Col.WHITE, 3f)
            if (mod(h, 30) == 0) {
                val n = mod(h, 360)
                p.text(c, if (n == 0) "360" else n.toString(), x, CY + 34, 28f, Col.WHITE)
            }
            h += 10
        }

        // drabinka pochylenia
        c.save()
        c.clipRect(CX - 170, CY - 200 - d.pitch * PPD, CX + 170, CY + 190 - d.pitch * PPD)
        val i0 = ceil((d.pitch - 25f) / 2.5f).toInt()
        val i1 = floor((d.pitch + 25f) / 2.5f).toInt()
        for (i in i0..i1) {
            if (i == 0) continue
            val deg = i * 2.5f
            if (abs(deg) > 90f) continue
            val y = CY - deg * PPD
            val half = when {
                i % 4 == 0 -> 120f
                i % 2 == 0 -> 55f
                else -> 25f
            }
            p.line(c, CX - half, y, CX + half, y, LADDER, 4f)
            if (i % 4 == 0) {
                val lbl = abs(deg).roundToInt().toString()
                p.text(c, lbl, CX - half - 22, y, 26f, LADDER)
                p.text(c, lbl, CX + half + 22, y, 26f, LADDER)
            }
        }
        c.restore()
        c.restore()

        // znacznik toru lotu (FPM)
        if (d.fpv && d.hasTrack && d.speedKt > 5f) {
            c.save()
            c.rotate(-d.roll, CX, CY)
            val x = CX + angDiff(d.track, d.heading).coerceIn(-25f, 25f) * PPD
            val y = CY - (d.fpa - d.pitch).coerceIn(-25f, 25f) * PPD
            p.circle(c, x, y, 13f, GREEN, 4f)
            p.line(c, x - 13, y, x - 36, y, GREEN, 4f)
            p.line(c, x + 13, y, x + 36, y, GREEN, 4f)
            p.line(c, x, y - 13, x, y - 26, GREEN, 4f)
            c.restore()
        }

        drawPitchChevrons(c, d)
        drawRollScale(c, d)
        SimLayer.fdVBar(c, p, d, CX, CY, PPD, 95f, MAGENTA)
        drawAircraft(c)
        // G1000: ścieżka schodzenia przy taśmie wysokości (GS zielony z ILS), odchylenie boczne w HSI
        SimLayer.deviation(c, p, d, CX, 0f, 676f, CY, 30f, GREEN, showLateral = false)
        SimLayer.marker(c, p, d, 650f, 110f, 26f)
        if (d.stallWarn) p.text(c, "STALL", CX, CY - 130, 46f, Col.RED, bold = true)
        if (d.raValid && d.raFt < 2500f) {
            val ra = if (d.raFt < 100f) d.raFt.roundToInt() else (d.raFt / 10f).roundToInt() * 10
            p.text(c, "RA " + ra, CX + 110f, CY + 160f, 32f, if (d.raFt < 200f) YELLOW else GREEN, Paint.Align.LEFT)
        }
    }

    // ---------------------------------------------------------------- teren 3D (SVT)
    private val quad = Path()
    private val terrainFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val terrainEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
        color = 0x30000000
    }
    private var sx = FloatArray(0)
    private var sy = FloatArray(0)
    private var sd = FloatArray(0)

    /** Rzutuje siatkę DEM na widok z kabiny (rzut kątowy zgodny z drabinką pochylenia). */
    private fun drawTerrain(c: Canvas, d: FlightData) {
        val g = d.terrain ?: return
        if (!d.altValid) return
        val n = g.n
        if (sx.size != n * n) {
            sx = FloatArray(n * n); sy = FloatArray(n * n); sd = FloatArray(n * n)
        }
        val (offN, offE) = g.offsetM(d.lat, d.lon)
        val camAlt = d.mslFt * 0.3048f
        val h = Math.toRadians((d.heading + d.magVar).toDouble())
        val sh = sin(h).toFloat()
        val ch = cos(h).toFloat()
        var minE = Float.MAX_VALUE
        var maxE = -Float.MAX_VALUE
        for (k in 0 until n * n) {
            val e = g.elevM[k]
            if (e < minE) minE = e
            if (e > maxE) maxE = e
        }
        for (i in 0 until n) for (j in 0 until n) {
            val k = i * n + j
            val north = (g.pointNorth(i) - offN).toFloat()
            val east = (g.pointEast(j) - offE).toFloat()
            val fwd = east * sh + north * ch
            val lat = east * ch - north * sh
            val up = g.elevM[k] - camAlt
            sd[k] = fwd
            if (fwd > 40f) {
                sx[k] = CX + Math.toDegrees(atan2(lat.toDouble(), fwd.toDouble())).toFloat() * PPD
                sy[k] = CY - Math.toDegrees(atan2(up.toDouble(), fwd.toDouble())).toFloat() * PPD
            }
        }
        // kolejność malarza: od najdalszych
        val cells = ArrayList<Int>((n - 1) * (n - 1))
        for (i in 0 until n - 1) for (j in 0 until n - 1) cells += i * n + j
        cells.sortByDescending { k -> sd[k] + sd[k + 1] + sd[k + n] + sd[k + n + 1] }
        val range = (maxE - minE).coerceAtLeast(1f)
        for (k in cells) {
            val a = k
            val b = k + 1
            val cc = k + n + 1
            val dd = k + n
            if (sd[a] <= 40f || sd[b] <= 40f || sd[cc] <= 40f || sd[dd] <= 40f) continue
            val elev = (g.elevM[a] + g.elevM[b] + g.elevM[cc] + g.elevM[dd]) / 4f
            val clearanceFt = (camAlt - elev) * 3.28084f
            val dist = (sd[a] + sd[b] + sd[cc] + sd[dd]) / 4f
            // cieniowanie od nachylenia (światło z północnego zachodu)
            val slope = ((g.elevM[a] - g.elevM[b]) + (g.elevM[dd] - g.elevM[a]) * -1f) / 3000f
            var col = when {
                clearanceFt < 100f -> 0xFFC8231E.toInt()
                clearanceFt < 1000f -> 0xFFC9A51E.toInt()
                else -> mix(0xFF2F6B22.toInt(), 0xFF8C8A4A.toInt(), (elev - minE) / range * 0.8f)
            }
            col = mix(col, 0xFF000000.toInt(), (0.15f - slope * 2f).coerceIn(0f, 0.35f))
            col = mix(col, 0xFF9DB7CF.toInt(), (dist / 16000f).coerceIn(0f, 0.55f))
            quad.reset()
            quad.moveTo(sx[a], sy[a])
            quad.lineTo(sx[b], sy[b])
            quad.lineTo(sx[cc], sy[cc])
            quad.lineTo(sx[dd], sy[dd])
            quad.close()
            terrainFill.color = col
            c.drawPath(quad, terrainFill)
            c.drawPath(quad, terrainEdge)
        }
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        val u = t.coerceIn(0f, 1f)
        fun ch(sh: Int) = (((a shr sh) and 0xFF) * (1 - u) + ((b shr sh) and 0xFF) * u).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Czerwone chevrony wskazujące najkrótszą drogę do horyzontu. */
    private fun drawPitchChevrons(c: Canvas, d: FlightData) {
        if (d.pitch <= 30f && d.pitch >= -20f) return
        val dir = if (d.pitch > 0f) 1f else -1f
        c.save()
        c.rotate(-d.roll, CX, CY)
        for (k in 1..2) {
            val y = CY + dir * (64f + k * 56f)
            p.line(c, CX - 58, y + dir * 38, CX, y, Col.RED, 6f)
            p.line(c, CX, y, CX + 58, y + dir * 38, Col.RED, 6f)
        }
        c.restore()
    }

    private fun drawRollScale(c: Canvas, d: FlightData) {
        val r = 196f
        for (a in intArrayOf(10, 20, 30, 45, 60)) {
            val r2 = if (a == 30 || a == 60) r + 32f else r + 18f
            p.radial(c, CX, CY, a.toFloat(), r, r2, Col.WHITE, 4f)
            p.radial(c, CX, CY, -a.toFloat(), r, r2, Col.WHITE, 4f)
        }
        p.poly(c, Col.WHITE, Col.BLACK, 2f, CX, CY - r - 4, CX - 16, CY - r - 34, CX + 16, CY - r - 34)
        c.save()
        c.rotate(-d.roll, CX, CY)
        val col = if (abs(d.roll) > 45f) YELLOW else Col.WHITE
        p.poly(c, col, Col.BLACK, 2f, CX, CY - r + 2, CX - 16, CY - r + 30, CX + 16, CY - r + 30)
        val off = (d.slip * 180f).coerceIn(-40f, 40f)
        p.poly(c, col, Col.BLACK, 2f,
            CX - 16 + off, CY - r + 34, CX + 16 + off, CY - r + 34,
            CX + 16 + off, CY - r + 44, CX - 16 + off, CY - r + 44)
        c.restore()
    }

    private fun drawAircraft(c: Canvas) {
        // symbol samolotu w stylu „single cue” (żółte strzałki)
        p.poly(c, YELLOW, Col.BLACK, 2.5f, CX, CY, CX - 132, CY + 42, CX - 98, CY + 42)
        p.poly(c, YELLOW_DARK, Col.BLACK, 2.5f, CX, CY, CX - 98, CY + 42, CX - 62, CY + 42)
        p.poly(c, YELLOW, Col.BLACK, 2.5f, CX, CY, CX + 132, CY + 42, CX + 98, CY + 42)
        p.poly(c, YELLOW_DARK, Col.BLACK, 2.5f, CX, CY, CX + 98, CY + 42, CX + 62, CY + 42)
        p.poly(c, YELLOW, Col.BLACK, 2.5f, CX - 188, CY - 5, CX - 155, CY - 5, CX - 155, CY + 5, CX - 188, CY + 5)
        p.poly(c, YELLOW, Col.BLACK, 2.5f, CX + 188, CY - 5, CX + 155, CY - 5, CX + 155, CY + 5, CX + 188, CY + 5)
    }

    // ---------------------------------------------------------------- prędkość
    private fun drawSpeed(c: Canvas, d: FlightData) {
        val l = 120f
        val r = 250f
        val t = 86f
        val b = 510f
        val k = 4.4f
        p.rect(c, l, t, r, b, PANEL)
        if (!d.speedValid) {
            p.line(c, l, t, r, b, Col.RED, 5f)
            p.line(c, r, t, l, b, Col.RED, 5f)
            return
        }
        val s = d.speedKt
        c.save()
        c.clipRect(l, t, r + 14, b)
        var v = floorTo(s - 60f, 5).coerceAtLeast(0)
        while (v <= s + 60f) {
            val y = CY - (v - s) * k
            val ten = v % 10 == 0
            p.line(c, r - if (ten) 22f else 12f, y, r, y, Col.WHITE, 3f)
            if (ten && v >= 20) p.text(c, v.toString(), r - 30, y, 36f, Col.WHITE, Paint.Align.RIGHT)
            v += 5
        }
        // pasy prędkości: z danych samolotu w symulatorze, bez nich — Cessna 172
        fun band(from: Float, to: Float, x0: Float, x1: Float, col: Int) {
            p.rect(c, x0, CY - (to - s) * k, x1, CY - (from - s) * k, col)
        }
        val vs0 = if (d.vs0.isNaN()) VS0 else d.vs0
        val vs1 = if (d.vs1.isNaN()) VS1 else d.vs1
        val vno = if (d.vc.isNaN()) VNO else d.vc
        val vne = if (d.vmo.isNaN()) VNE else d.vmo
        val vfe = if (d.vs0.isNaN()) VFE else (vs0 * 2.1f).coerceAtMost(vno)
        band(vs0, vfe, r + 1, r + 6, Col.WHITE)
        band(vs1, vno, r + 6, r + 13, GREEN)
        if (vne > vno) band(vno, vne, r + 6, r + 13, YELLOW)
        band(vne, vne + 300f, r + 1, r + 13, Col.RED)
        // prędkość wybrana (cyjanowy bug)
        if (!d.spdBug.isNaN()) SimLayer.speedBug(c, p, r - 2, (CY - (d.spdBug - s) * k).coerceIn(t, b), CYAN)
        c.restore()
        SimLayer.selSpeed(d)?.let {
            p.rect(c, l, t - 44, r, t - 4, PANEL_DARK)
            p.text(c, it + if (d.machBug.isNaN()) "KT" else "", (l + r) / 2, t - 24, 30f, CYAN)
        }

        // trend (magenta)
        val tr = d.speedTrend.coerceIn(-55f, 55f)
        if (abs(tr) > 2f) p.rect(c, r - 6, minOf(CY, CY - tr * k), r, maxOf(CY, CY - tr * k), MAGENTA)

        // okno odczytu
        p.poly(c, Col.BLACK, Col.WHITE, 2.5f,
            l - 10, CY - 32, r - 50, CY - 32, r - 50, CY - 50, r - 14, CY - 50, r - 14, CY - 14,
            r + 4, CY, r - 14, CY + 14, r - 14, CY + 50, r - 50, CY + 50, r - 50, CY + 32, l - 10, CY + 32)
        val tens = floor(s / 10f).toInt()
        if (tens > 0) p.text(c, tens.toString(), r - 52, CY, 52f, Col.WHITE, Paint.Align.RIGHT)
        p.drum(c, r - 49, CY - 48, r - 15, CY + 48, s, 44f, 48f, Col.WHITE, r - 32, Paint.Align.CENTER) { kk ->
            mod(kk, 10).toString()
        }

        // GS i TAS w jednym wierszu pod tasma
        p.rect(c, 20f, b + 8, 330f, b + 50, PANEL_DARK)
        p.text(c, "GS", 32f, b + 29, 24f, Col.WHITE, Paint.Align.LEFT)
        p.text(c, if (d.gsKt.isNaN()) "---" else d.gsKt.roundToInt().toString() + "KT", 78f, b + 29, 28f, Col.WHITE, Paint.Align.LEFT)
        if (!d.tasKt.isNaN()) {
            p.text(c, "TAS", 184f, b + 29, 24f, Col.WHITE, Paint.Align.LEFT)
            p.text(c, d.tasKt.roundToInt().toString() + "KT", 240f, b + 29, 28f, Col.WHITE, Paint.Align.LEFT)
        }
    }

    // ---------------------------------------------------------------- wysokość
    private fun drawAltitude(c: Canvas, d: FlightData) {
        val l = 700f
        val r = 840f
        val t = 86f
        val b = 510f
        val k = 0.5f
        p.rect(c, l, t, r, b, PANEL)
        if (!d.altValid) {
            p.line(c, l, t, r, b, Col.RED, 5f)
            p.line(c, r, t, l, b, Col.RED, 5f)
            return
        }
        val a = d.altFt
        c.save()
        c.clipRect(l, t, r, b)
        var v = floorTo(a - 500f, 20)
        while (v <= a + 500f) {
            val y = CY - (v - a) * k
            val hundred = mod(v, 100) == 0
            p.line(c, l, y, l + if (hundred) 20f else 10f, y, Col.WHITE, 2.5f)
            if (hundred) p.text(c, v.toString(), r - 12, y, 34f, Col.WHITE, Paint.Align.RIGHT)
            v += 20
        }
        if (!d.altBug.isNaN()) SimLayer.altBug(c, p, l, (CY - (d.altBug - a) * k).coerceIn(t, b), CYAN)
        c.restore()
        // wysokość wybrana: cyjanowe pole nad taśmą
        if (!d.altBug.isNaN()) {
            p.rect(c, l, t - 44, r, t - 4, PANEL_DARK)
            p.text(c, d.altBug.roundToInt().toString() + "FT", (l + r) / 2, t - 24, 30f, CYAN)
        }

        p.poly(c, Col.BLACK, Col.WHITE, 2.5f,
            l - 16, CY, l, CY - 14, l, CY - 32, r - 50, CY - 32, r - 50, CY - 50,
            r + 4, CY - 50, r + 4, CY + 50, r - 50, CY + 50, r - 50, CY + 32, l, CY + 32, l, CY + 14)
        val hundreds = floor(a / 100f).toInt()
        p.text(c, hundreds.toString(), r - 52, CY, 50f, Col.WHITE, Paint.Align.RIGHT)
        p.drum(c, r - 49, CY - 48, r + 3, CY + 48, a / 20f, 36f, 36f, Col.WHITE, r - 22, Paint.Align.CENTER) { kk ->
            String.format(Locale.US, "%02d", mod(kk * 20, 100))
        }
    }

    // ---------------------------------------------------------------- wariometr
    private fun vy(v: Float): Float {
        val a = abs(v).coerceAtMost(4300f)
        val g = when {
            a <= 1000f -> a * 0.065f
            a <= 2000f -> 65f + (a - 1000f) * 0.055f
            else -> 120f + (a - 2000f) * 0.032f
        }
        return CY - sign(v) * g
    }

    private fun drawVsi(c: Canvas, d: FlightData) {
        val l = 848f
        val r = 916f
        p.rect(c, l, 86f, r, 510f, PANEL)
        for (v in intArrayOf(500, 1000, 1500, 2000, 4000)) {
            for (sg in intArrayOf(-1, 1)) {
                val y = vy((sg * v).toFloat())
                val major = v == 1000 || v == 2000 || v == 4000
                p.line(c, l, y, l + if (major) 16f else 9f, y, Col.WHITE, 2.5f)
                if (major) p.text(c, ((sg * v) / 1000).toString(), r - 6, y, 26f, Col.WHITE, Paint.Align.RIGHT)
            }
        }
        p.line(c, l, CY, l + 20, CY, Col.WHITE, 3f)
        if (!d.vsBug.isNaN()) {
            val yb = vy(d.vsBug)
            p.poly(c, 0, CYAN, 3f, l, yb - 10, l + 14, yb - 10, l + 8, yb, l + 14, yb + 10, l, yb + 10)
        }
        if (!d.altValid) return
        val vs = d.vsFpm
        val y = vy(vs)
        val txt = if (abs(vs) >= 100f) ((vs / 50f).roundToInt() * 50).toString() else ""
        p.poly(c, Col.BLACK, Col.WHITE, 2.5f, l + 2, y, l + 20, y - 22, 992f, y - 22, 992f, y + 22, l + 20, y + 22)
        p.text(c, txt, 986f, y, 30f, Col.WHITE, Paint.Align.RIGHT)
    }

    // ---------------------------------------------------------------- HSI
    private fun drawHsi(c: Canvas, d: FlightData) {
        p.circle(c, HX, HY, HR + 26, PANEL_DARK)
        p.rect(c, HX - 58, HY - HR - 64, HX + 58, HY - HR - 20, Col.BLACK)
        p.frame(c, HX - 58, HY - HR - 64, HX + 58, HY - HR - 20, GREY, 2f)
        if (!d.attValid) {
            p.text(c, "HDG", HX, HY - HR - 42, 30f, Col.RED)
            p.line(c, HX - HR, HY - HR, HX + HR, HY + HR, Col.RED, 5f)
            p.line(c, HX + HR, HY - HR, HX - HR, HY + HR, Col.RED, 5f)
            return
        }
        val h = d.heading
        p.text(c, String.format(Locale.US, "%03d°", mod(h.roundToInt(), 360)), HX, HY - HR - 42, 32f, Col.WHITE)
        p.poly(c, Col.WHITE, Col.BLACK, 2f, HX, HY - HR - 2, HX - 12, HY - HR - 24, HX + 12, HY - HR - 24)

        // obracajaca sie roza
        c.save()
        c.rotate(-h, HX, HY)
        var deg = 0
        while (deg < 360) {
            val ten = deg % 10 == 0
            p.radial(c, HX, HY, deg.toFloat(), HR - if (ten) 22f else 12f, HR, Col.WHITE, if (ten) 3f else 2f)
            if (deg % 30 == 0) {
                val rad = Math.toRadians(deg.toDouble())
                val rr = HR - 44
                val tx = HX + (rr * sin(rad)).toFloat()
                val ty = HY - (rr * cos(rad)).toFloat()
                val lbl = when (deg) {
                    0 -> "N"
                    90 -> "E"
                    180 -> "S"
                    270 -> "W"
                    else -> (deg / 10).toString()
                }
                c.save()
                c.rotate(deg.toFloat(), tx, ty)
                p.text(c, lbl, tx, ty, 30f, Col.WHITE)
                c.restore()
            }
            deg += 5
        }
        // bug kursu (cyjan)
        if (!d.hdgBug.isNaN()) {
            c.save()
            c.rotate(d.hdgBug, HX, HY)
            p.poly(c, CYAN, Col.BLACK, 1.5f, HX - 12, HY - HR - 2, HX - 12, HY - HR + 12, HX + 12, HY - HR + 12, HX + 12, HY - HR - 2, HX + 4, HY - HR - 2, HX, HY - HR + 6, HX - 4, HY - HR - 2)
            c.restore()
        }
        c.restore()

        // wskaźniki namiaru: NAV2 (podwójna linia) i ADF (pojedyncza), cyjan
        for ((brg, double) in listOf(d.brg2 to true, d.brgAdf to false)) {
            if (brg.isNaN()) continue
            c.save()
            c.rotate(angDiff(brg, h), HX, HY)
            if (double) {
                p.line(c, HX - 4, HY - HR + 24, HX - 4, HY + HR - 24, CYAN, 2f)
                p.line(c, HX + 4, HY - HR + 24, HX + 4, HY + HR - 24, CYAN, 2f)
            } else {
                p.line(c, HX, HY - HR + 24, HX, HY + HR - 24, CYAN, 2.5f)
            }
            p.poly(c, 0, CYAN, 2.5f, HX, HY - HR + 14, HX - 10, HY - HR + 30, HX + 10, HY - HR + 30)
            c.restore()
        }

        // strzałka kursu i CDI z aktywnego źródła: GPS magenta, VOR/LOC zielone
        val n = d.nav
        val col = if (n.source == NavInfo.Source.GPS) MAGENTA else GREEN
        val course = if (!n.course.isNaN()) n.course else if (d.hasTrack) d.track else Float.NaN
        if (!course.isNaN()) {
            c.save()
            c.rotate(angDiff(course, h), HX, HY)
            p.poly(c, col, Col.BLACK, 2f,
                HX, HY - HR + 8, HX - 15, HY - HR + 38, HX + 15, HY - HR + 38)
            p.line(c, HX, HY - HR + 26, HX, HY - 44, col, 5f)
            p.line(c, HX, HY + 44, HX, HY + HR - 16, col, 5f)
            for (i in intArrayOf(-2, -1, 1, 2)) p.circle(c, HX + i * 26f, HY, 5f, Col.WHITE, 2f)
            if (!n.cdi.isNaN()) {
                p.line(c, HX + n.cdi.coerceIn(-2.5f, 2.5f) * 26f, HY - 40, HX + n.cdi.coerceIn(-2.5f, 2.5f) * 26f, HY + 40, col, 6f)
                // TO/FROM
                if (n.toFrom != 0) {
                    val y = if (n.toFrom == 1) HY - 56 else HY + 56
                    val dir = if (n.toFrom == 1) -1f else 1f
                    p.poly(c, Col.WHITE, Col.BLACK, 1.5f, HX + 40, y + dir * 10, HX + 32, y - dir * 4, HX + 48, y - dir * 4)
                }
            }
            c.restore()
        }
        if (d.hasTrack) {
            // tor: mały magentowy romb na róży
            c.save()
            c.rotate(angDiff(d.track, h), HX, HY)
            p.poly(c, MAGENTA, Col.BLACK, 1.5f, HX, HY - HR + 2, HX - 6, HY - HR + 10, HX, HY - HR + 18, HX + 6, HY - HR + 10)
            c.restore()
        }
        val srcName = when (n.source) {
            NavInfo.Source.GPS -> "GPS"
            NavInfo.Source.VOR -> "VOR1"
            NavInfo.Source.LOC -> "LOC1"
            NavInfo.Source.NONE -> null
        }
        srcName?.let { p.text(c, it, HX - 52, HY - 48, 22f, col) }
        if (n.source == NavInfo.Source.GPS) {
            p.text(c, if (d.gps.approach) "APR" else "ENR", HX + 52, HY - 48, 22f, Col.WHITE)
        } else if (!n.dmeNm.isNaN()) {
            p.text(c, String.format(Locale.US, "%.1f", n.dmeNm), HX + 52, HY - 48, 22f, col)
        }
        if (!course.isNaN() && !n.course.isNaN()) {
            p.text(c, String.format(Locale.US, "CRS %03d°", mod(n.course.roundToInt(), 360)), HX + 182, HY - HR - 54, 24f, col)
        }
        if (!d.hdgBug.isNaN()) {
            p.text(c, String.format(Locale.US, "HDG %03d°", mod(d.hdgBug.roundToInt(), 360)), HX - 182, HY - HR - 54, 24f, CYAN)
        }
        // symbol samolotu
        p.poly(c, Col.WHITE, Col.BLACK, 2f,
            HX, HY - 20, HX - 4, HY - 7, HX - 25, HY + 3, HX - 25, HY + 10, HX - 4, HY + 5,
            HX - 4, HY + 18, HX - 11, HY + 23, HX - 11, HY + 27, HX + 11, HY + 27,
            HX + 11, HY + 23, HX + 4, HY + 18, HX + 4, HY + 5, HX + 25, HY + 10,
            HX + 25, HY + 3, HX + 4, HY - 7)
    }

    // ---------------------------------------------------------------- gorny pasek
    // NAV1 po lewej, AFCS i nawigacja GPS na środku, COM1 po prawej — jak na górze PFD G1000
    private fun drawTopBar(c: Canvas, d: FlightData) {
        p.rect(c, 0f, 0f, 1000f, 70f, PANEL_DARK)
        p.line(c, 0f, 70f, 1000f, 70f, GREY, 2f)
        for (x in floatArrayOf(220f, 780f)) p.line(c, x, 2f, x, 68f, GREY, 2f)
        p.line(c, 220f, 35f, 780f, 35f, GREY, 1.5f)

        fun freq(v: Float) = if (v.isNaN()) "---.--" else String.format(Locale.US, "%.2f", v)
        fun freqC(v: Float) = if (v.isNaN()) "---.---" else String.format(Locale.US, "%.3f", v)
        p.text(c, "NAV1", 14f, 18f, 20f, Col.WHITE, Paint.Align.LEFT)
        p.text(c, freq(d.nav1Stby), 80f, 18f, 24f, Col.WHITE, Paint.Align.LEFT)
        p.text(c, freq(d.nav1), 80f, 50f, 24f, GREEN, Paint.Align.LEFT)
        p.text(c, "COM1", 986f, 18f, 20f, Col.WHITE, Paint.Align.RIGHT)
        p.text(c, freqC(d.com1), 920f, 18f, 24f, GREEN, Paint.Align.RIGHT)
        p.text(c, freqC(d.com1Stby), 920f, 50f, 24f, Col.WHITE, Paint.Align.RIGHT)

        // AFCS: boczny aktywny | boczny uzbrojony | AP YD | pionowy + nastawa | pionowy uzbrojony
        val f = d.fma
        val lat = when (f.lateral) {
            Fma.Lateral.HDG -> "HDG"
            Fma.Lateral.NAV_GPS -> "GPS"
            Fma.Lateral.NAV_VOR -> "VOR"
            Fma.Lateral.LOC -> "LOC"
            Fma.Lateral.BC -> "BC"
            Fma.Lateral.ROL -> "ROL"
            null -> null
        }
        lat?.let { p.text(c, it, 270f, 18f, 24f, GREEN) }
        if (f.lateralArmed == Fma.Lateral.LOC) p.text(c, "LOC", 350f, 18f, 24f, Col.WHITE)
        if (f.ap) p.text(c, "AP", 470f, 18f, 24f, GREEN)
        if (f.yd) p.text(c, "YD", 530f, 18f, 24f, GREEN)
        val vert = when (f.vertical) {
            Fma.Vertical.ALT -> "ALT"
            Fma.Vertical.VS -> "VS"
            Fma.Vertical.FLC -> "FLC"
            Fma.Vertical.GS -> "GS"
            Fma.Vertical.PIT -> "PIT"
            Fma.Vertical.ALTS, null -> null
        }
        vert?.let { p.text(c, it, 600f, 18f, 24f, GREEN) }
        val target = when (f.vertical) {
            Fma.Vertical.VS -> if (d.vsBug.isNaN()) null else ((d.vsBug / 50f).roundToInt() * 50).toString() + "FPM"
            Fma.Vertical.FLC -> SimLayer.selSpeed(d)?.let { it + "KT" }
            Fma.Vertical.ALT -> if (d.altBug.isNaN()) null else d.altBug.roundToInt().toString() + "FT"
            else -> null
        }
        target?.let { p.text(c, it, 680f, 18f, 22f, GREEN) }
        val armed = f.verticalArmed.joinToString(" ") { if (it == Fma.Vertical.GS) "GS" else "ALTS" }
        if (armed.isNotEmpty()) p.text(c, armed, 750f, 18f, 22f, Col.WHITE, Paint.Align.RIGHT)

        // Nawigacja GPS: następny punkt, odległość, DTK i tor
        val g = d.gps
        if (g.nextId != null) {
            p.text(c, "→ " + g.nextId, 232f, 52f, 22f, MAGENTA, Paint.Align.LEFT)
            if (!g.nextDistNm.isNaN()) p.text(c, String.format(Locale.US, "DIS %.1fNM", g.nextDistNm), 400f, 52f, 22f, Col.WHITE, Paint.Align.LEFT)
            if (!g.dtk.isNaN()) p.text(c, String.format(Locale.US, "DTK %03d°", mod(g.dtk.roundToInt(), 360)), 548f, 52f, 22f, Col.WHITE, Paint.Align.LEFT)
        }
        if (d.hasTrack) p.text(c, String.format(Locale.US, "TRK %03d°", mod(d.track.roundToInt(), 360)), 768f, 52f, 22f, MAGENTA, Paint.Align.RIGHT)
    }

    // ---------------------------------------------------------------- dolny pasek
    private fun drawBottomBar(c: Canvas, d: FlightData) {
        p.rect(c, 0f, 706f, 1000f, 750f, PANEL_DARK)
        p.line(c, 0f, 706f, 1000f, 706f, GREY, 2f)
        p.text(c, "XPDR", 24f, 728f, 24f, Col.WHITE, Paint.Align.LEFT)
        p.text(c, d.squawk ?: "----", 98f, 728f, 26f, GREEN, Paint.Align.LEFT)
        val mode = when (d.xpdrMode) { "alt" -> "ALT"; "on" -> "ON"; "standby" -> "STBY"; "ground" -> "GND"; "test" -> "TST"; else -> "OFF" }
        p.text(c, mode, 176f, 728f, 24f, if (mode == "ALT" || mode == "ON") GREEN else Col.WHITE, Paint.Align.LEFT)
        if (!d.oatC.isNaN()) {
            p.text(c, "OAT", 420f, 728f, 24f, Col.WHITE, Paint.Align.LEFT)
            p.text(c, d.oatC.roundToInt().toString() + "°C", 480f, 728f, 26f, Col.WHITE, Paint.Align.LEFT)
        }
        // Czas z symulatora (UTC), bez niego — lokalny telefonu
        if (d.simTime != null) {
            p.text(c, "UTC", 816f, 728f, 24f, Col.WHITE, Paint.Align.LEFT)
            p.text(c, d.simTime.removeSuffix("Z").let { it.take(2) + ":" + it.drop(2) }, 976f, 728f, 26f, Col.WHITE, Paint.Align.RIGHT)
        } else {
            p.text(c, "LCL", 816f, 728f, 24f, Col.WHITE, Paint.Align.LEFT)
            p.text(c, clock.format(Date()), 976f, 728f, 26f, Col.WHITE, Paint.Align.RIGHT)
        }
    }


    // ---------------------------------------------------------------- wiatr
    private fun drawWind(c: Canvas, d: FlightData) {
        p.rect(c, 20f, 566f, 210f, 638f, PANEL_DARK)
        p.frame(c, 20f, 566f, 210f, 638f, GREY, 2f)
        if (!d.windValid) {
            p.text(c, "NO WIND", 115f, 590f, 24f, Col.WHITE)
            p.text(c, "DATA", 115f, 616f, 24f, Col.WHITE)
            return
        }
        c.save()
        c.rotate(d.windDir - d.heading + 180f, 60f, 602f)
        p.line(c, 60f, 624f, 60f, 580f, Col.WHITE, 4f)
        p.poly(c, Col.WHITE, 0, 0f, 60f, 627f, 51f, 610f, 69f, 610f)
        c.restore()
        p.text(c, String.format(Locale.US, "%03d°", mod(d.windDir.roundToInt(), 360)), 148f, 590f, 24f, Col.WHITE)
        p.text(c, d.windKt.roundToInt().toString() + "KT", 148f, 616f, 24f, Col.WHITE)
    }

    // ---------------------------------------------------------------- baro
    private fun drawBaro(c: Canvas, d: FlightData) {
        p.rect(c, baroRect.left, baroRect.top, baroRect.right, baroRect.bottom, PANEL_DARK)
        val y = baroRect.centerY()
        if (!d.minimumsFt.isNaN()) {
            p.text(c, "MIN", 872f, y - 12, 20f, CYAN, Paint.Align.LEFT)
            p.text(c, d.minimumsFt.roundToInt().toString() + "FT", 872f, y + 14, 24f, CYAN, Paint.Align.LEFT)
        }
        if (d.std) {
            p.text(c, "STD BARO", baroRect.centerX(), y, 28f, CYAN)
        } else {
            val txt = if (d.inHg) String.format(Locale.US, "%.2fIN", d.qnh * 0.02953f)
            else String.format(Locale.US, "%.0fHPA", d.qnh)
            p.text(c, txt, baroRect.centerX(), y, 30f, CYAN)
        }
    }
}
