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

    /** Wszystkie METAR-y w prostokącie (lat0,lon0,lat1,lon1) — do kolorowania mapy jednym zapytaniem. */
    suspend fun metarsInBox(south: Double, west: Double, north: Double, east: Double): List<Metar> {
        val box = "%.2f,%.2f,%.2f,%.2f".format(java.util.Locale.US, south, west, north, east)
        val body = Http.get("$BASE/metar?bbox=$box&format=json")
        if (body.isBlank()) return emptyList()
        return json.parseToJsonElement(body).jsonArray.mapNotNull { runCatching { parseMetar(it.jsonObject) }.getOrNull() }
    }

    private var sigmetCache: Pair<Long, List<Sigmet>>? = null

    /** Globalna lista SIGMET (krajowe USA i międzynarodowe); pamiętana 10 minut, żeby nie przekraczać limitu API. */
    suspend fun sigmets(): List<Sigmet> {
        sigmetCache?.let { (t, list) -> if (System.currentTimeMillis() - t < 10 * 60_000) return list }
        val list = HazardParser.sigmets(Http.get("$BASE/sigmet?format=json"))
        val now = System.currentTimeMillis() / 1000
        val valid = list.filter { it.validTo == 0L || it.validTo >= now }
        sigmetCache = System.currentTimeMillis() to valid
        return valid
    }

    /** Raporty pilotów w promieniu od lotniska z ostatnich 3 godzin (głównie USA i Północny Atlantyk). */
    suspend fun pireps(id: String, distanceNm: Int = 100): List<Pirep> =
        HazardParser.pireps(Http.get("$BASE/pirep?id=$id&distance=$distanceNm&format=json&age=3"))

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
