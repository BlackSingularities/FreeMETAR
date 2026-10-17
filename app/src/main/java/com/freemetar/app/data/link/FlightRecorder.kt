package com.freemetar.app.data.link

import android.content.Context
import com.freemetar.app.domain.Geo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID

@Serializable
data class TrackPoint(val t: Long, val lat: Double, val lon: Double, val altFt: Int, val gsKt: Int)

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
) {
    val durationMin get() = ((end - start) / 60_000).toInt()
}

@Serializable
private data class FlightLogFile(val app: String = "OpenAirBook", val version: Int = 1, val flights: List<FlightLog>)

/**
 * Rejestrator lotu oparty wyłącznie na kolejnych stanach — bez zależności od Androida, więc testowalny.
 * Start: w powietrzu i GS > 40 kt. Koniec: na ziemi i GS < 5 kt przez 60 s (albo [finish] po rozłączeniu).
 */
class FlightRecorder(private val nearestAirport: (Double, Double) -> String?) {
    private val points = ArrayList<TrackPoint>()
    private var aircraft: String? = null
    private var stoppedSince: Long? = null
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
                stoppedSince = null
                points += TrackPoint(now, lat, lon, alt, gs.toInt())
            }
            return null
        }
        val last = points.last()
        // Punkt co 5 s albo po 0,5 NM — wystarcza do rysowania toru, a plik pozostaje mały
        if (now - last.t >= 5_000 || Geo.distanceNm(last.lat, last.lon, lat, lon) >= 0.5) {
            points += TrackPoint(now, lat, lon, alt, gs.toInt())
        }
        if (onGround && gs < 5) {
            val since = stoppedSince ?: now.also { stoppedSince = it }
            if (now - since >= 60_000) return finish(now)
        } else {
            stoppedSince = null
        }
        return null
    }

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
