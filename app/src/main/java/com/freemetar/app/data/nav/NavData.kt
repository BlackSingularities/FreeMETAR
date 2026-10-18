package com.freemetar.app.data.nav

import android.content.Context
import com.freemetar.app.data.link.LinkJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File

/*
 * Dane nawigacyjne z bazy MSFS 2024 przez OpenTakeoff Link (wiadomości navdata_request / navdata).
 * Kształty zgodne z docs/link-protocol.md; telefon zapisuje je lokalnie, więc działają też bez symulatora.
 */

@Serializable data class Ils(val ident: String? = null, val freqMhz: Double? = null, val locCourseDeg: Double? = null, val gsAngleDeg: Double? = null)

@Serializable
data class NavRunway(
    val ident: String,
    val headingTrueDeg: Double? = null,
    val lengthFt: Double? = null,
    val widthFt: Double? = null,
    val thresholdLat: Double? = null,
    val thresholdLon: Double? = null,
    val elevFt: Double? = null,
    val ils: Ils? = null,
)

@Serializable data class NavFreq(val type: String = "OTHER", val name: String? = null, val mhz: Double)

@Serializable
data class NavAirport(
    val icao: String,
    val name: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val elevFt: Double? = null,
    val runways: List<NavRunway> = emptyList(),
    val frequencies: List<NavFreq> = emptyList(),
)

@Serializable data class ProcRef(val name: String, val runways: List<String> = emptyList(), val transitions: List<String> = emptyList())

@Serializable
data class ApproachRef(
    val name: String,
    val type: String = "OTHER",
    val runway: String? = null,
    val suffix: String? = null,
    val transitions: List<String> = emptyList(),
) {
    /** Czytelna nazwa, np. „ILS 29”, „RNAV 11 Z”. */
    val label: String get() = listOfNotNull(type.takeIf { it != "OTHER" }, runway, suffix).joinToString(" ").ifBlank { name }
}

@Serializable
data class NavProcedures(
    val icao: String,
    val departures: List<ProcRef> = emptyList(),
    val arrivals: List<ProcRef> = emptyList(),
    val approaches: List<ApproachRef> = emptyList(),
)

@Serializable
data class Leg(
    val fix: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val legType: String? = null,
    val altDesc: String? = null,
    val alt1Ft: Double? = null,
    val alt2Ft: Double? = null,
    val speedKt: Double? = null,
    val courseDeg: Double? = null,
    val distNm: Double? = null,
    val flyOver: Boolean = false,
) {
    /** Ograniczenie wysokości w zapisie jak na chartach: „5000A”, „FL100B”, „3000–5000”. */
    val constraint: String?
        get() {
            fun f(v: Double?) = v?.let { if (it >= 18000) "FL%03d".format((it / 100).toInt()) else it.toInt().toString() }
            return when (altDesc) {
                "AT" -> f(alt1Ft)
                "ABOVE" -> f(alt1Ft)?.plus("A")
                "BELOW" -> f(alt1Ft)?.plus("B")
                "BETWEEN" -> listOfNotNull(f(alt2Ft), f(alt1Ft)).joinToString("–")
                else -> null
            }
        }
}

@Serializable
data class ProcLegs(
    val icao: String,
    val procType: String,
    val name: String,
    val runway: String? = null,
    val transition: String? = null,
    val legs: List<Leg> = emptyList(),
    val missed: List<Leg> = emptyList(),
)

@Serializable data class FixResult(val ident: String, val type: String = "waypoint", val lat: Double, val lon: Double, val freqMhz: Double? = null, val region: String? = null)
@Serializable data class FixResults(val results: List<FixResult> = emptyList())

/** Wszystko, co telefon wie o lotnisku z MSFS — jeden plik na lotnisko. */
@Serializable
data class AirportNav(
    val icao: String,
    val airport: NavAirport? = null,
    val procedures: NavProcedures? = null,
    /** Odcinki pobranych procedur, klucz: typ|nazwa|pas|przejście. */
    val legs: Map<String, ProcLegs> = emptyMap(),
    val fetchedAt: Long = System.currentTimeMillis(),
    val source: String = "msfs",
) {
    fun ilsFor(runway: String): Ils? = airport?.runways?.firstOrNull { it.ident.equals(runway, true) }?.ils

    companion object {
        fun legsKey(procType: String, name: String, runway: String?, transition: String?) =
            listOf(procType, name, runway.orEmpty(), transition.orEmpty()).joinToString("|")
    }
}

/** Lokalny magazyn danych nawigacyjnych w filesDir/navdata/<ICAO>.json (przetrwa aktualizację aplikacji). */
class NavDataStore(context: Context) {
    private val dir = File(context.filesDir, "navdata").apply { mkdirs() }
    private val memory = HashMap<String, AirportNav>()

    private fun file(icao: String) = File(dir, icao.uppercase().filter { it.isLetterOrDigit() } + ".json")

    suspend fun get(icao: String): AirportNav? = withContext(Dispatchers.IO) {
        val key = icao.uppercase()
        memory[key] ?: file(key).takeIf { it.exists() }?.let { f ->
            runCatching { LinkJson.decodeFromString(AirportNav.serializer(), f.readText()) }.getOrNull()
        }?.also { memory[key] = it }
    }

    suspend fun put(nav: AirportNav) = withContext(Dispatchers.IO) {
        val key = nav.icao.uppercase()
        memory[key] = nav
        val f = file(key)
        val tmp = File(dir, f.name + ".tmp")
        tmp.writeText(LinkJson.encodeToString(AirportNav.serializer(), nav))
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    suspend fun update(icao: String, change: (AirportNav) -> AirportNav): AirportNav {
        val cur = get(icao) ?: AirportNav(icao.uppercase())
        return change(cur).copy(fetchedAt = System.currentTimeMillis()).also { put(it) }
    }

    /** Lista lotnisk z danymi (do ekranu „Dane nawigacyjne” w ustawieniach). */
    suspend fun list(): List<String> = withContext(Dispatchers.IO) { dir.listFiles()?.filter { it.extension == "json" }?.map { it.nameWithoutExtension }.orEmpty().sorted() }

    suspend fun clear() = withContext(Dispatchers.IO) { memory.clear(); dir.listFiles()?.forEach { it.delete() } }
}
