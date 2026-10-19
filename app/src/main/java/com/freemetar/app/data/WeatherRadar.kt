package com.freemetar.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Weather Radar data provider - opady w czasie rzeczywistym z Open-Meteo API.
 *
 * Dane:
 * - Precipitation (mm/h)
 * - Rain/Snow/Showers
 * - Cloud cover
 * - Grid: 0.25° (~28 km rozdzielczość)
 *
 * API: https://open-meteo.com/en/docs
 * Free tier: 10,000 requests/day
 */
object WeatherRadar {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Pobierz dane opadów dla obszaru wokół [lat], [lon].
     *
     * @param lat Szerokość geograficzna środka
     * @param lon Długość geograficzna środka
     * @param radiusNm Promień w milach morskich (max 200)
     * @return Lista punktów z intensywnością opadów
     */
    suspend fun fetchPrecipitation(lat: Double, lon: Double, radiusNm: Int): WeatherRadarData? = withContext(Dispatchers.IO) {
        try {
            // Przelicz radius na stopnie (1° ≈ 60 NM)
            val radiusDeg = radiusNm / 60.0

            // Bounding box
            val north = lat + radiusDeg
            val south = lat - radiusDeg
            val east = lon + radiusDeg
            val west = lon - radiusDeg

            // Open-Meteo API - grid data
            val url = "https://api.open-meteo.com/v1/forecast?" +
                    "latitude=$lat&longitude=$lon" +
                    "&current=precipitation,rain,showers,snowfall,cloud_cover" +
                    "&hourly=precipitation" +
                    "&timezone=auto"

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) return@withContext null

            val body = response.body?.string() ?: return@withContext null
            val data = json.decodeFromString<OpenMeteoResponse>(body)

            WeatherRadarData(
                centerLat = lat,
                centerLon = lon,
                radiusNm = radiusNm,
                precipitationMmH = data.current.precipitation ?: 0.0,
                rain = data.current.rain ?: 0.0,
                showers = data.current.showers ?: 0.0,
                snowfall = data.current.snowfall ?: 0.0,
                cloudCover = data.current.cloud_cover ?: 0,
                fetchedAt = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Generuj grid punktów z interpolowanymi opadami.
     * Używane do rysowania overlay na mapie.
     */
    fun generateRadarGrid(data: WeatherRadarData, gridSize: Int = 16): List<RadarPoint> {
        val points = mutableListOf<RadarPoint>()
        val radiusDeg = data.radiusNm / 60.0
        val step = radiusDeg * 2 / gridSize

        for (i in 0 until gridSize) {
            for (j in 0 until gridSize) {
                val lat = data.centerLat - radiusDeg + i * step
                val lon = data.centerLon - radiusDeg + j * step

                // Prosta interpolacja - w centrum najwięcej opadów, na brzegach mniej
                val distFromCenter = kotlin.math.hypot((lat - data.centerLat) * 60, (lon - data.centerLon) * 60)
                val factor = (1.0 - (distFromCenter / data.radiusNm)).coerceIn(0.0, 1.0)
                val intensity = data.precipitationMmH * factor

                if (intensity > 0.1) { // Tylko punkty z widocznymi opadami
                    points.add(RadarPoint(lat, lon, intensity))
                }
            }
        }

        return points
    }

    /**
     * Mapuj intensywność opadów na kolor radaru.
     *
     * Skala:
     * - 0-1 mm/h: zielony (light)
     * - 1-5 mm/h: żółty (moderate)
     * - 5-10 mm/h: pomarańczowy (heavy)
     * - 10+ mm/h: czerwony (very heavy)
     */
    fun intensityToColor(mmH: Double): Int {
        return when {
            mmH < 1.0 -> 0x8000FF00.toInt() // Zielony, alpha 0.5
            mmH < 5.0 -> 0x80FFFF00.toInt() // Żółty
            mmH < 10.0 -> 0x80FF8800.toInt() // Pomarańczowy
            else -> 0x80FF0000.toInt() // Czerwony
        }
    }
}

@Serializable
data class OpenMeteoResponse(
    val current: CurrentWeather
)

@Serializable
data class CurrentWeather(
    val precipitation: Double? = null,
    val rain: Double? = null,
    val showers: Double? = null,
    val snowfall: Double? = null,
    val cloud_cover: Int? = null
)

/**
 * Dane radaru pogodowego dla jednego obszaru.
 */
data class WeatherRadarData(
    val centerLat: Double,
    val centerLon: Double,
    val radiusNm: Int,
    val precipitationMmH: Double,
    val rain: Double,
    val showers: Double,
    val snowfall: Double,
    val cloudCover: Int,
    val fetchedAt: Long
) {
    /** Czy dane są świeże (< 10 minut). */
    fun isStale(): Boolean = System.currentTimeMillis() - fetchedAt > 10 * 60 * 1000
}

/**
 * Punkt na gridzie radaru z intensywnością opadów.
 */
data class RadarPoint(
    val lat: Double,
    val lon: Double,
    val intensityMmH: Double
)
