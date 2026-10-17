package com.freemetar.app.data.link

import android.content.Context
import com.freemetar.app.domain.Geo
import com.freemetar.app.domain.LandingReport
import com.freemetar.app.domain.LandingScore
import com.freemetar.app.domain.RunwayGeom
import com.freemetar.app.domain.RunwayMath
import com.freemetar.app.domain.TouchdownData
import com.freemetar.app.domain.Wind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID

@Serializable
data class TrackPoint(val t: Long, val lat: Double, val lon: Double, val altFt: Int, val gsKt: Int, val vsFpm: Int? = null, val iasKt: Int? = null)

/** Zapisany lot z symulatora: tor, lotniska, czasy. */
@Serializable
data class FlightLog(
    val id: String = UUID.randomUUID().toString(),
    val start: Long,
    val end: Long,
    val aircraft: String?,
    val from: String?,
    val to: String?,
    val maxAltFt: Int,
    val distanceNm: Int,
    val track: List<TrackPoint>,
    val landing: LandingReport? = null,
    val category: String? = null,
) {
    val durationMin get() = ((end - start) / 60_000).toInt()
}

@Serializable
private data class FlightLogFile(val app: String = "OpenAirBook", val version: Int = 1, val flights: List<FlightLog>)

/**
 * Rejestrator lotu oparty wyłącznie na kolejnych stanach — bez zależności od Androida, więc testowalny.
 * Start: w powietrzu i GS > 40 kt. Koniec: na ziemi i GS < 5 kt przez 60 s (albo [finish] po rozłączeniu).
 */
class FlightRecorder(
    /** Pasy w pobliżu punktu przyziemienia (z bazy MSFS lub OurAirports). */
    private val runwaysNear: (Double, Double) -> List<RunwayGeom> = { _, _ -> emptyList() },
    private val nearestAirport: (Double, Double) -> String?,
) {
    private val points = ArrayList<TrackPoint>()
    private var aircraft: String? = null
    private var category: String? = null
    private var stoppedSince: Long? = null

    // Przyziemienia: pierwsze po locie to lądowanie, kolejne w ciągu 15 s to odbicia
    private var lastTdSeq: Long? = null
    private var landing: TouchdownData? = null
    private var landingAt = 0L
    private var bounces = 0
    private var prev: FlightState? = null
    private var airborneSince: Long? = null

    /** Ostatnia ocena lądowania (do pokazania od razu po przyziemieniu). */
    var lastLanding: LandingReport? = null
        private set
    var recording = false
        private set

    val currentTrack: List<TrackPoint> get() = points.toList()

    /** Przyjmuje kolejny stan; zwraca zakończony lot, gdy samolot stanął po lądowaniu. */
    fun onState(s: FlightState, now: Long = System.currentTimeMillis()): FlightLog? {
        val lat = s.position.lat ?: return null
        val lon = s.position.lon ?: return null
        if (s.aircraft.paused == true) return null
        val gs = s.speed.gsKt ?: 0.0
        val onGround = s.aircraft.onGround == true
        val alt = (s.position.altMslFt ?: 0.0).toInt()

        if (!recording) {
            if (!onGround && gs > 40) {
                recording = true
                points.clear()
                aircraft = s.aircraft.title
                category = s.aircraft.category
                stoppedSince = null
                landing = null; bounces = 0; lastLanding = null
                airborneSince = now
                lastTdSeq = s.touchdown?.seq
                points += TrackPoint(now, lat, lon, alt, gs.toInt(), s.speed.vsFpm?.toInt(), s.speed.iasKt?.toInt())
            }
            prev = s
            return null
        }
        if (!onGround && airborneSince == null) airborneSince = now
        detectTouchdown(s, now)
        prev = s
        val last = points.last()
        // Punkt co 5 s albo po 0,5 NM — wystarcza do rysowania toru, a plik pozostaje mały
        if (now - last.t >= 5_000 || Geo.distanceNm(last.lat, last.lon, lat, lon) >= 0.5) {
            points += TrackPoint(now, lat, lon, alt, gs.toInt(), s.speed.vsFpm?.toInt(), s.speed.iasKt?.toInt())
        }
        if (onGround && gs < 5) {
            val since = stoppedSince ?: now.also { stoppedSince = it }
            if (now - since >= 60_000) return finish(now)
        } else {
            stoppedSince = null
        }
        return null
    }

    private fun detectTouchdown(s: FlightState, now: Long) {
        val td = s.touchdown
        val fromLink = td != null && td.seq != lastTdSeq && td.vsFpm != null
        // Bez danych z Link: przejście z powietrza na ziemię, prędkość pionowa z poprzedniego stanu
        val fromTransition = !fromLink && prev?.aircraft?.onGround == false && s.aircraft.onGround == true
        if (!fromLink && !fromTransition) return
        if (fromLink) lastTdSeq = td!!.seq
        val flownLongEnough = (airborneSince?.let { now - it } ?: 0) >= 60_000
        if (landing != null && now - landingAt <= 15_000) { bounces++; return }
        if (!flownLongEnough && landing == null) return
        val vs = if (fromLink) td!!.vsFpm!! else (prev?.speed?.vsFpm ?: 0.0)
        val lat = (if (fromLink) td!!.lat else null) ?: s.position.lat ?: return
        val lon = (if (fromLink) td!!.lon else null) ?: s.position.lon ?: return
        val hdg = (if (fromLink) td!!.headingTrueDeg else null) ?: s.attitude.hdgTrueDeg
        val rw = RunwayMath.pick(runwaysNear(lat, lon), lat, lon, hdg)
        val off = rw?.let { RunwayMath.offset(it, lat, lon) }
        val cross = rw?.let { r ->
            val wd = s.environment.windDirDeg; val ws = s.environment.windSpdKt
            if (wd != null && ws != null) Wind.components(wd, ws, r.headingTrueDeg).crosswind else null
        }
        landing = TouchdownData(
            vsFpm = vs,
            gForce = if (fromLink) td!!.gForce else null,
            bankDeg = (if (fromLink) td!!.bankDeg else null) ?: prev?.attitude?.bankDeg,
            pitchDeg = (if (fromLink) td!!.pitchDeg else null) ?: prev?.attitude?.pitchDeg,
            iasKt = (if (fromLink) td!!.iasKt else null) ?: prev?.speed?.iasKt,
            runway = rw?.ident, alongFt = off?.alongFt, crossFt = off?.crossFt, runwayLengthFt = rw?.lengthFt,
            crosswindKt = cross,
        )
        landingAt = now
        bounces = 0
        airborneSince = null
        lastLanding = LandingScore.evaluate(landing!!)
    }

    private fun report(): LandingReport? = landing?.let { LandingScore.evaluate(it.copy(bounces = bounces)) }

    /** Kończy nagrywanie (np. po rozłączeniu); krótkie loty (< 2 min) są odrzucane. */
    fun finish(now: Long = System.currentTimeMillis()): FlightLog? {
        if (!recording) return null
        recording = false
        val pts = points.toList()
        points.clear()
        if (pts.size < 2 || now - pts.first().t < 120_000) return null
        val dist = pts.zipWithNext { a, b -> Geo.distanceNm(a.lat, a.lon, b.lat, b.lon) }.sum()
        return FlightLog(
            start = pts.first().t, end = now, aircraft = aircraft,
            from = nearestAirport(pts.first().lat, pts.first().lon),
            to = nearestAirport(pts.last().lat, pts.last().lon),
            maxAltFt = pts.maxOf { it.altFt }, distanceNm = dist.toInt(),
            landing = report(), category = category,
            // Ograniczenie rozmiaru: co n-ty punkt, końce zawsze zostają
            track = if (pts.size <= 2000) pts else pts.filterIndexed { i, _ -> i % (pts.size / 2000 + 1) == 0 } + pts.last(),
        )
    }
}

/** Historia lotów w pliku flights.json (dane aplikacji — przetrwa aktualizację). */
class FlightLogStore(context: Context) {
    private val file = File(context.filesDir, "flights.json")
    private val json = LinkJson

    suspend fun load(): List<FlightLog> = withContext(Dispatchers.IO) {
        if (!file.exists()) emptyList() else runCatching { json.decodeFromString(FlightLogFile.serializer(), file.readText()).flights }.getOrDefault(emptyList())
    }

    suspend fun save(list: List<FlightLog>) = withContext(Dispatchers.IO) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(FlightLogFile.serializer(), FlightLogFile(flights = list)))
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }
}
