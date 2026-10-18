package com.freemetar.app.data

import android.content.Context
import android.net.Uri
import com.freemetar.app.data.link.FlightLog
import com.freemetar.app.data.link.TrackPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

/**
 * Import logbooków z innych aplikacji:
 * - SimToolkitPro (JSON + GPX)
 * - Volanta (JSON)
 * - Generic CSV
 */
object LogbookImporter {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Import z SimToolkitPro export (ZIP z JSON + GPX).
     */
    suspend fun importFromSimToolkit(context: Context, uri: Uri): List<FlightLog> = withContext(Dispatchers.IO) {
        val flights = mutableListOf<FlightLog>()

        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.name.endsWith(".json")) {
                            val jsonText = BufferedReader(InputStreamReader(zip)).use { it.readText() }
                            val stkFlight = json.decodeFromString<SimToolkitFlight>(jsonText)
                            flights.add(convertSimToolkit(stkFlight))
                        }
                        entry = zip.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        flights
    }

    /**
     * Import z Volanta export (JSON).
     */
    suspend fun importFromVolanta(context: Context, uri: Uri): List<FlightLog> = withContext(Dispatchers.IO) {
        val flights = mutableListOf<FlightLog>()

        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val jsonText = BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
                val volantaExport = json.decodeFromString<VolantaExport>(jsonText)

                volantaExport.flights.forEach { volantaFlight ->
                    flights.add(convertVolanta(volantaFlight))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        flights
    }

    /**
     * Import z generic CSV.
     */
    suspend fun importFromCsv(context: Context, uri: Uri): List<FlightLog> = withContext(Dispatchers.IO) {
        val flights = mutableListOf<FlightLog>()

        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    val header = reader.readLine()?.split(",") ?: return@withContext flights

                    reader.forEachLine { line ->
                        val values = line.split(",")
                        if (values.size >= 6) {
                            flights.add(convertCsv(header, values))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        flights
    }

    /**
     * Konwersja SimToolkit → FlightLog.
     */
    private fun convertSimToolkit(stk: SimToolkitFlight): FlightLog {
        return FlightLog(
            id = java.util.UUID.randomUUID().toString(),
            start = stk.departureTime ?: System.currentTimeMillis(),
            end = stk.arrivalTime ?: System.currentTimeMillis(),
            from = stk.departure,
            to = stk.arrival,
            aircraft = stk.aircraft,
            distanceNm = stk.distance?.toInt() ?: 0,
            durationMin = ((stk.arrivalTime ?: 0) - (stk.departureTime ?: 0)) / 60000,
            maxAltFt = stk.maxAltitude?.toInt() ?: 0,
            track = stk.track?.map { TrackPoint(it.lat, it.lon, it.alt.toInt(), it.timestamp) } ?: emptyList(),
            landing = null // SimToolkit nie ma landing score
        )
    }

    /**
     * Konwersja Volanta → FlightLog.
     */
    private fun convertVolanta(volanta: VolantaFlight): FlightLog {
        return FlightLog(
            id = volanta.id ?: java.util.UUID.randomUUID().toString(),
            start = volanta.departureTime ?: System.currentTimeMillis(),
            end = volanta.arrivalTime ?: System.currentTimeMillis(),
            from = volanta.origin,
            to = volanta.destination,
            aircraft = volanta.aircraft,
            distanceNm = volanta.distance?.toInt() ?: 0,
            durationMin = volanta.duration ?: 0,
            maxAltFt = volanta.maxAltitude ?: 0,
            track = emptyList(), // Volanta zwykle nie ma pełnego tracka
            landing = null
        )
    }

    /**
     * Konwersja CSV → FlightLog.
     */
    private fun convertCsv(header: List<String>, values: List<String>): FlightLog {
        val map = header.zip(values).toMap()

        return FlightLog(
            id = java.util.UUID.randomUUID().toString(),
            start = map["date"]?.toLongOrNull() ?: System.currentTimeMillis(),
            end = map["date"]?.toLongOrNull() ?: System.currentTimeMillis(),
            from = map["from"] ?: map["departure"] ?: "",
            to = map["to"] ?: map["arrival"] ?: "",
            aircraft = map["aircraft"] ?: "",
            distanceNm = map["distance"]?.toIntOrNull() ?: 0,
            durationMin = map["duration"]?.toIntOrNull() ?: 0,
            maxAltFt = map["altitude"]?.toIntOrNull() ?: 0,
            track = emptyList(),
            landing = null
        )
    }
}

// SimToolkitPro format
@Serializable
data class SimToolkitFlight(
    val departure: String? = null,
    val arrival: String? = null,
    val aircraft: String? = null,
    val departureTime: Long? = null,
    val arrivalTime: Long? = null,
    val distance: Double? = null,
    val maxAltitude: Double? = null,
    val track: List<SimToolkitTrackPoint>? = null
)

@Serializable
data class SimToolkitTrackPoint(
    val lat: Double,
    val lon: Double,
    val alt: Double,
    val timestamp: Long
)

// Volanta format
@Serializable
data class VolantaExport(
    val flights: List<VolantaFlight>
)

@Serializable
data class VolantaFlight(
    val id: String? = null,
    val origin: String? = null,
    val destination: String? = null,
    val aircraft: String? = null,
    val departureTime: Long? = null,
    val arrivalTime: Long? = null,
    val distance: Double? = null,
    val duration: Int? = null,
    val maxAltitude: Int? = null
)
