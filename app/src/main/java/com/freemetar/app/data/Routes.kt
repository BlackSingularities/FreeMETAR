package com.freemetar.app.data

import android.content.Context
import android.net.Uri
import com.freemetar.app.domain.Geo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.util.UUID

@Serializable
data class RoutePoint(
    val ident: String,
    val lat: Double? = null,
    val lon: Double? = null,
    /** airport, vor, ndb, fix, toc, tod… */
    val type: String = "fix",
    val altitudeFt: Int? = null,
    val windDir: Int? = null,
    val windKt: Int? = null,
    val oatC: Int? = null,
    val airway: String? = null,
    /** Ograniczenie wysokości z procedury, np. „5000A”. */
    val constraint: String? = null,
    /** Odcinek trasy: sid / enroute / star / approach (null = enroute dla starszych plików). */
    val segment: String? = null,
) {
    val hasPosition get() = lat != null && lon != null
}

/** Wybrana procedura (SID, STAR, podejście) z przejściem i odcinkami z bazy MSFS. */
@Serializable
data class ProcSelection(
    val type: String,               // departure / arrival / approach
    val name: String,
    val runway: String? = null,
    val transition: String? = null,
    val label: String? = null,      // np. „ILS 29”
    val points: List<RoutePoint> = emptyList(),
) {
    val display: String get() = listOfNotNull(label ?: name, transition?.let { "przez $it" }).joinToString(" ")
}

@Serializable
data class FuelPlan(val unit: String, val ramp: Int?, val trip: Int?, val reserve: Int?, val alternate: Int?, val landing: Int?)

/** Trasa zapisana w telefonie. Plik przetrwa aktualizację aplikacji (dane w filesDir). */
@Serializable
data class SavedRoute(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val origin: String,
    val destination: String,
    val alternates: List<String> = emptyList(),
    val cruiseFl: Int? = null,
    val routeText: String = "",
    val points: List<RoutePoint> = emptyList(),
    val source: String = "manual",
    val aircraft: String? = null,
    val distanceNm: Int? = null,
    val eteMin: Int? = null,
    val fuel: FuelPlan? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val depRunway: String? = null,
    val arrRunway: String? = null,
    val departure: ProcSelection? = null,
    val arrival: ProcSelection? = null,
    val approach: ProcSelection? = null,
) {
    /** Punkty trasy między lotniskami (bez lotnisk na końcach). */
    val enroute: List<RoutePoint> get() = if (points.size <= 2) emptyList() else points.subList(1, points.size - 1)

    /**
     * Pełna ścieżka do mapy i ND: lotnisko → SID → trasa → STAR → podejście → lotnisko.
     * Kolejne powtórzenia tego samego punktu (np. koniec SID = początek trasy) są łączone.
     */
    fun fullPath(): List<RoutePoint> {
        val origin = points.firstOrNull()
        val dest = points.lastOrNull()?.takeIf { points.size > 1 }
        val seq = buildList {
            origin?.let(::add)
            departure?.points?.let(::addAll)
            enroute.forEach { add(if (it.segment == null) it.copy(segment = "enroute") else it) }
            arrival?.points?.let(::addAll)
            approach?.points?.let(::addAll)
            dest?.let(::add)
        }
        return seq.fold(ArrayList<RoutePoint>()) { acc, p ->
            val last = acc.lastOrNull()
            if (last != null && last.ident == p.ident && (p.lat == null || last.lat == null || kotlin.math.abs(last.lat - p.lat) < 0.01)) {
                // Zostaw wersję z ograniczeniem / segmentem procedury
                acc[acc.lastIndex] = if (p.constraint != null || last.segment == "enroute") p.copy(lat = p.lat ?: last.lat, lon = p.lon ?: last.lon) else last
            } else acc += p
            acc
        }
    }

    /** Długość po punktach z pozycją (gdy brak danych z planu). */
    fun measuredDistanceNm(): Int? {
        val p = fullPath().filter { it.hasPosition }
        if (p.size < 2) return null
        return p.zipWithNext { a, b -> Geo.distanceNm(a.lat!!, a.lon!!, b.lat!!, b.lon!!) }.sum().toInt()
    }
}

/** Format pliku eksportu: rozpoznawalny po polu „app”. */
@Serializable
data class RouteExport(val app: String = "OpenAirBook", val version: Int = 1, val exportedAt: Long = System.currentTimeMillis(), val routes: List<SavedRoute>)

object RouteJson {
    val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true; isLenient = true }
}

/** Zapis tras w jednym pliku JSON; zapis atomowy przez plik tymczasowy. */
class RouteStore(context: Context) {
    private val file = File(context.filesDir, "routes.json")

    suspend fun load(): List<SavedRoute> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        runCatching { RouteJson.json.decodeFromString(RouteExport.serializer(), file.readText()).routes }.getOrDefault(emptyList())
    }

    suspend fun save(routes: List<SavedRoute>) = withContext(Dispatchers.IO) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(RouteJson.json.encodeToString(RouteExport.serializer(), RouteExport(routes = routes)))
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    suspend fun exportTo(context: Context, uri: Uri, routes: List<SavedRoute>) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "wt")!!.use {
            it.write(RouteJson.json.encodeToString(RouteExport.serializer(), RouteExport(routes = routes)).toByteArray())
        }
    }

    suspend fun readText(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
    }
}

/** Rozpoznaje identyfikatory punktów trasy: lotniska i radiolatarnie z bazy OurAirports. */
fun interface FixResolver {
    fun resolve(ident: String, near: LatLon?): RoutePoint?
}

object RouteImport {
    private val reAirway = Regex("""^[A-Z]{1,2}\d{1,4}[A-Z]?$""")
    private val reLevel = Regex("""^[NKM]\d{3,4}F(\d{3})$""") // N0450F350 — prędkość i poziom
    private val reSidStar = Regex("""^[A-Z]{3,5}\d[A-Z]$""")    // np. SOXER2G — procedury pomijamy

    /** Rozpoznaje dowolny obsługiwany format: eksport OpenAirBook, plan SimBrief (JSON) albo tekst trasy. */
    fun parseAny(text: String, resolver: FixResolver): List<SavedRoute> {
        val t = text.trim()
        if (t.startsWith("{")) {
            val el = RouteJson.json.parseToJsonElement(t).jsonObject
            if (el["app"] != null && el["routes"] != null) return RouteJson.json.decodeFromString(RouteExport.serializer(), t).routes
            if (el["origin"] != null && el["destination"] != null) return listOf(SimBrief.parse(t))
            error("Nieznany format pliku JSON")
        }
        return listOf(fromText(t, null, resolver))
    }

    /**
     * Trasa z tekstu „EPWA SOXER N871 BIMPA EPGD” (opcjonalnie z grupą N0450F350).
     * Drogi lotnicze i procedury są pomijane; punkty bez pozycji zostają z samą nazwą.
     */
    fun fromText(text: String, cruiseFl: Int?, resolver: FixResolver): SavedRoute {
        val tokens = text.uppercase().split(Regex("[\\s,]+")).filter { it.isNotBlank() && it != "DCT" }
        require(tokens.size >= 2) { "Podaj co najmniej lotnisko startowe i docelowe, np. „EPWA EPGD”." }
        var fl = cruiseFl
        val pts = ArrayList<RoutePoint>()
        var airway: String? = null
        var prev: LatLon? = null
        tokens.forEachIndexed { i, tok ->
            reLevel.find(tok)?.let { fl = fl ?: it.groupValues[1].toInt(); return@forEachIndexed }
            val isEnd = i == 0 || i == tokens.lastIndex
            if (!isEnd && (reAirway.matches(tok) && tok.any { it.isDigit() } && tok.length <= 6 && !tok.all { it.isLetter() }) && pts.isNotEmpty()) {
                airway = tok; return@forEachIndexed
            }
            if (!isEnd && reSidStar.matches(tok)) return@forEachIndexed
            val r = resolver.resolve(tok, prev)
            val p = (r ?: RoutePoint(tok)).copy(type = if (isEnd) "airport" else r?.type ?: "fix", airway = airway)
            airway = null
            pts += p
            if (p.hasPosition) prev = LatLon(p.lat!!, p.lon!!)
        }
        val origin = pts.first().ident
        val dest = pts.last().ident
        val route = SavedRoute(
            name = "$origin → $dest",
            origin = origin, destination = dest,
            cruiseFl = fl,
            routeText = tokens.drop(1).dropLast(1).joinToString(" "),
            points = pts,
            source = "manual",
        )
        return route.copy(distanceNm = route.measuredDistanceNm())
    }
}

/**
 * Plan lotu z SimBrief (API xml.fetcher.php, format json=v2). Pola są tekstami;
 * pojedynczy element bywa obiektem zamiast tablicy, więc obsługujemy oba przypadki.
 */
object SimBrief {
    fun url(user: String): String {
        val u = user.trim()
        val key = if (u.all { it.isDigit() }) "userid" else "username"
        return "https://www.simbrief.com/api/xml.fetcher.php?$key=${java.net.URLEncoder.encode(u, "UTF-8")}&json=v2"
    }

    suspend fun fetch(user: String): SavedRoute = parse(Http.get(url(user)))

    fun parse(body: String): SavedRoute {
        val root = RouteJson.json.parseToJsonElement(body).jsonObject
        root.obj("fetch")?.str("status")?.let { st -> if (!st.equals("Success", true)) error("SimBrief: $st") }
        val origin = root.obj("origin") ?: error("SimBrief: brak lotniska startowego w planie")
        val dest = root.obj("destination") ?: error("SimBrief: brak lotniska docelowego w planie")
        val general = root.obj("general")
        val alternates = root["alternate"].asList().mapNotNull { it.str("icao_code") }
        val fixes = root.obj("navlog")?.get("fix").asList()
        val units = root.obj("params")?.str("units") ?: "kgs"

        val points = buildList {
            add(airportPoint(origin))
            fixes.forEach { f ->
                val ident = f.str("ident") ?: return@forEach
                if (ident == origin.str("icao_code") || ident == dest.str("icao_code")) return@forEach
                add(
                    RoutePoint(
                        ident = ident,
                        lat = f.dbl("pos_lat"), lon = f.dbl("pos_long"),
                        type = f.str("type") ?: "fix",
                        altitudeFt = f.int("altitude_feet"),
                        windDir = f.int("wind_dir"), windKt = f.int("wind_spd"), oatC = f.int("oat"),
                        airway = f.str("via_airway")?.takeIf { it != "DCT" },
                    )
                )
            }
            add(airportPoint(dest))
        }
        val fuel = root.obj("fuel")?.let { fu ->
            FuelPlan(units, fu.int("plan_ramp"), fu.int("enroute_burn"), fu.int("reserve"), fu.int("alternate_burn"), fu.int("plan_landing"))
        }
        val o = origin.str("icao_code")!!; val d = dest.str("icao_code")!!
        val flight = listOfNotNull(general?.str("icao_airline"), general?.str("flight_number")).joinToString("")

        // Punkty SID/STAR z navlogu (is_sid_star=1): przed pierwszym punktem trasy to SID, po ostatnim — STAR.
        // Wyodrębnione jako procedury, żeby edytor mógł je wymienić na inne z bazy MSFS.
        val mid = points.subList(1, points.size - 1)
        val sidFlags = fixes.filter { f -> f.str("ident") != o && f.str("ident") != d }.map { it.str("is_sid_star") == "1" }
        val firstEnroute = sidFlags.indexOfFirst { !it }.let { if (it < 0) sidFlags.size else it }
        val lastEnroute = sidFlags.indexOfLast { !it }
        val sidPts = mid.take(firstEnroute).map { it.copy(segment = "sid") }
        val starPts = if (lastEnroute >= 0 && lastEnroute < mid.lastIndex) mid.drop(lastEnroute + 1).map { it.copy(segment = "star") } else emptyList()
        val enroutePts = mid.drop(sidPts.size).dropLast(starPts.size)
        val sidName = sidPts.firstNotNullOfOrNull { it.airway }
        val starName = starPts.lastOrNull()?.airway ?: starPts.firstNotNullOfOrNull { it.airway }
        val depRwy = origin.str("plan_rwy")
        val arrRwy = dest.str("plan_rwy")
        return SavedRoute(
            depRunway = depRwy,
            arrRunway = arrRwy,
            departure = if (sidPts.isNotEmpty()) ProcSelection("departure", sidName ?: "SID", depRwy, null, sidName, sidPts) else null,
            arrival = if (starPts.isNotEmpty()) ProcSelection("arrival", starName ?: "STAR", arrRwy, null, starName, starPts) else null,
            name = listOf(flight.ifBlank { null }, "$o → $d").filterNotNull().joinToString(" "),
            origin = o, destination = d,
            alternates = alternates,
            cruiseFl = general?.int("initial_altitude")?.let { if (it > 1000) it / 100 else it },
            routeText = general?.str("route").orEmpty(),
            points = listOf(points.first()) + enroutePts + points.last(),
            source = "simbrief",
            aircraft = root.obj("aircraft")?.let { listOfNotNull(it.str("icaocode") ?: it.str("icao_code"), it.str("reg")).joinToString(" ") }?.ifBlank { null },
            distanceNm = general?.int("route_distance") ?: general?.int("air_distance"),
            eteMin = root.obj("times")?.int("est_time_enroute")?.let { it / 60 },
            fuel = fuel,
        )
    }

    private fun airportPoint(o: JsonObject) = RoutePoint(
        ident = o.str("icao_code") ?: "?", lat = o.dbl("pos_lat"), lon = o.dbl("pos_long"),
        type = "airport", altitudeFt = o.int("elevation"),
    )

    private fun JsonElement?.asList(): List<JsonObject> = when (this) {
        is JsonArray -> mapNotNull { it as? JsonObject }
        is JsonObject -> listOf(this)
        else -> emptyList()
    }
    private fun JsonObject.obj(k: String) = this[k] as? JsonObject
    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    private fun JsonObject.dbl(k: String) = str(k)?.toDoubleOrNull()
    private fun JsonObject.int(k: String) = str(k)?.toDoubleOrNull()?.toInt()
}
