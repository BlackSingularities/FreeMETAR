package com.freemetar.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlin.math.roundToInt

/** Wiatr i temperatura na jednym poziomie w jednej godzinie. */
data class AloftCell(val dirDeg: Int?, val speedKt: Int?, val tempC: Int?)

/** Poziom ciśnienia przeliczony na poziom lotu z wysokości geopotencjalnej. */
data class AloftLevel(val pressureHpa: Int, val flightLevel: Int, val cells: List<AloftCell>)

/** Profil pionowy atmosfery nad punktem z Open-Meteo (model globalny, bez klucza). */
data class WindsAloft(
    val hoursUtc: List<String>,       // "HH:mm" dla kolejnych kolumn
    val levels: List<AloftLevel>,      // od najniższego
    val freezingLevelFt: List<Int?>,
    val cloudLow: List<Int?>, val cloudMid: List<Int?>, val cloudHigh: List<Int?>,
) {
    /** Wiatr najbliższy danemu FL w pierwszej godzinie (do planowania zniżania). */
    fun windAt(flightLevel: Int, hourIndex: Int = 0): AloftCell? =
        levels.minByOrNull { kotlin.math.abs(it.flightLevel - flightLevel) }?.cells?.getOrNull(hourIndex)
}

object OpenMeteo {
    /** Poziomy od ok. FL050 do FL390. */
    val PRESSURE_LEVELS = listOf(850, 700, 500, 400, 300, 250, 200)
    private val json = Json { ignoreUnknownKeys = true }

    fun url(lat: Double, lon: Double, hours: Int = 12): String {
        val vars = buildList {
            add("freezing_level_height"); add("cloud_cover_low"); add("cloud_cover_mid"); add("cloud_cover_high")
            PRESSURE_LEVELS.forEach { p ->
                add("wind_speed_${p}hPa"); add("wind_direction_${p}hPa"); add("temperature_${p}hPa"); add("geopotential_height_${p}hPa")
            }
        }.joinToString(",")
        return "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&hourly=%s&wind_speed_unit=kn&forecast_hours=%d&timezone=UTC"
            .format(java.util.Locale.US, lat, lon, vars, hours)
    }

    suspend fun fetch(lat: Double, lon: Double, hours: Int = 12): WindsAloft? = parse(Http.get(url(lat, lon, hours)))

    fun parse(body: String): WindsAloft? {
        if (body.isBlank()) return null
        val h = json.parseToJsonElement(body).jsonObject["hourly"]?.jsonObject ?: return null
        fun arr(k: String): List<Double?> = (h[k] as? JsonArray)?.map { (it as? JsonPrimitive)?.doubleOrNull }.orEmpty()
        val times = (h["time"] as? JsonArray)?.map { (it as JsonPrimitive).contentOrNull.orEmpty().substringAfter("T") }.orEmpty()
        if (times.isEmpty()) return null
        val levels = PRESSURE_LEVELS.mapNotNull { p ->
            val spd = arr("wind_speed_${p}hPa"); val dir = arr("wind_direction_${p}hPa")
            val tmp = arr("temperature_${p}hPa"); val gph = arr("geopotential_height_${p}hPa")
            val heightM = gph.filterNotNull().takeIf { it.isNotEmpty() }?.average() ?: return@mapNotNull null
            // Wysokość geopotencjalna ≈ wysokość w atmosferze wzorcowej → poziom lotu
            val fl = (heightM / 0.3048 / 100).roundToInt()
            AloftLevel(p, fl, times.indices.map { i ->
                AloftCell(dir.getOrNull(i)?.roundToInt(), spd.getOrNull(i)?.roundToInt(), tmp.getOrNull(i)?.roundToInt())
            })
        }.sortedBy { it.flightLevel }
        return WindsAloft(
            hoursUtc = times,
            levels = levels,
            freezingLevelFt = arr("freezing_level_height").map { it?.let { m -> (m / 0.3048).roundToInt() } },
            cloudLow = arr("cloud_cover_low").map { it?.roundToInt() },
            cloudMid = arr("cloud_cover_mid").map { it?.roundToInt() },
            cloudHigh = arr("cloud_cover_high").map { it?.roundToInt() },
        )
    }
}
