package com.freemetar.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 * Klient darmowego Data API NOAA/NWS: https://aviationweather.gov/data/api/
 * Nie wymaga klucza; limit ok. 100 zapytań/min — wystarczy z zapasem.
 */
object WeatherApi {
    private const val BASE = "https://aviationweather.gov/api/data"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun metars(ids: Collection<String>, hours: Int = 1): List<Metar> {
        if (ids.isEmpty()) return emptyList()
        val body = Http.get("$BASE/metar?ids=${ids.joinToString(",")}&format=json&hours=$hours")
        if (body.isBlank()) return emptyList()
        return json.parseToJsonElement(body).jsonArray.mapNotNull { runCatching { parseMetar(it.jsonObject) }.getOrNull() }
    }

    suspend fun taf(id: String): Taf? {
        val body = Http.get("$BASE/taf?ids=$id&format=json")
        if (body.isBlank()) return null
        val o = json.parseToJsonElement(body).jsonArray.firstOrNull()?.jsonObject ?: return null
        return Taf(
            station = o.str("icaoId") ?: id,
            raw = o.str("rawTAF") ?: return null,
            issueTime = o.str("issueTime"),
        )
    }

    private fun parseMetar(o: JsonObject): Metar {
        val wdirEl = o["wdir"]
        val wdirStr = (wdirEl as? JsonPrimitive)?.contentOrNull
        val clouds = (o["clouds"] as? JsonArray)?.mapNotNull { c ->
            val co = c.jsonObject
            val cover = co.str("cover") ?: return@mapNotNull null
            CloudLayer(cover, co.num("base")?.toInt(), co.str("type"))
        }.orEmpty()
        return Metar(
            station = o.str("icaoId") ?: "",
            raw = o.str("rawOb") ?: "",
            obsTimeEpoch = (o["obsTime"] as? JsonPrimitive)?.longOrNull ?: 0L,
            tempC = o.num("temp"),
            dewpointC = o.num("dewp"),
            windDir = wdirStr?.toIntOrNull(),
            windVariable = wdirStr == "VRB",
            windKt = o.num("wspd")?.toInt(),
            gustKt = o.num("wgst")?.toInt(),
            visibility = o.str("visib"),
            altimHpa = o.num("altim"),
            wx = o.str("wxString"),
            clouds = clouds,
            flightCategory = o.str("fltCat"),
            lat = o.num("lat"),
            lon = o.num("lon"),
            elevationM = o.num("elev"),
            name = o.str("name"),
        )
    }

    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    private fun JsonObject.num(k: String): Double? {
        val e: JsonElement = this[k] ?: return null
        return (e as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }
    }
}
