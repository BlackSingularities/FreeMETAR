package com.freemetar.app.data.nav

import com.freemetar.app.data.link.LinkClient
import com.freemetar.app.data.link.LinkJson
import com.freemetar.app.data.link.LinkStatus

/**
 * Dane nawigacyjne „najpierw lokalnie”: zwraca zapisaną kopię, a gdy jej brak (albo [refresh])
 * i telefon jest połączony z OpenTakeoff Link — pobiera z bazy MSFS i zapisuje.
 */
class NavRepository(private val link: LinkClient, val store: NavDataStore) {
    val connected: Boolean get() = link.status.value is LinkStatus.Connected

    suspend fun cached(icao: String): AirportNav? = store.get(icao)

    /** Lotnisko i lista procedur. Bez połączenia zwraca to, co jest w pamięci (może być null). */
    suspend fun airport(icao: String, refresh: Boolean = false): AirportNav? {
        val cur = store.get(icao)
        if (!connected || (!refresh && cur?.airport != null && cur.procedures != null)) return cur
        val ap = runCatching { LinkJson.decodeFromJsonElement(NavAirport.serializer(), link.navdata("airport", mapOf("icao" to icao))) }.getOrNull()
        val pr = runCatching { LinkJson.decodeFromJsonElement(NavProcedures.serializer(), link.navdata("procedures", mapOf("icao" to icao))) }.getOrNull()
        if (ap == null && pr == null) return cur
        // Nowa lista procedur unieważnia zapisane odcinki (mogła się zmienić baza MSFS)
        return store.update(icao) { it.copy(airport = ap ?: it.airport, procedures = pr ?: it.procedures, legs = if (pr != null) emptyMap() else it.legs) }
    }

    /** Odcinki wybranej procedury (SID/STAR/podejścia) z przejściem. */
    suspend fun legs(icao: String, procType: String, name: String, runway: String?, transition: String?): ProcLegs? {
        val key = AirportNav.legsKey(procType, name, runway, transition)
        store.get(icao)?.legs?.get(key)?.let { return it }
        if (!connected) return null
        val params = mapOf("icao" to icao, "procType" to procType, "name" to name, "runway" to runway, "transition" to transition)
        val l = runCatching { LinkJson.decodeFromJsonElement(ProcLegs.serializer(), link.navdata("procedure_legs", params)) }.getOrNull() ?: return null
        store.update(icao) { it.copy(legs = it.legs + (key to l)) }
        return l
    }

    /** Lotniska, VOR-y, NDB i punkty trasy z bazy MSFS w promieniu od pozycji (dla mapy ND). */
    suspend fun nearby(lat: Double, lon: Double, radiusNm: Int): List<FixResult> {
        if (!connected) return emptyList()
        return runCatching {
            LinkJson.decodeFromJsonElement(FixResults.serializer(), link.navdata("nearby", mapOf("lat" to lat, "lon" to lon, "radiusNm" to radiusNm))).results
        }.getOrDefault(emptyList())
    }

    /** Punkty, VOR-y, NDB o danym identyfikatorze, najbliższe podanej pozycji. */
    suspend fun fixes(ident: String, nearLat: Double?, nearLon: Double?): List<FixResult> {
        if (!connected) return emptyList()
        return runCatching {
            LinkJson.decodeFromJsonElement(FixResults.serializer(), link.navdata("fix", mapOf("ident" to ident, "nearLat" to nearLat, "nearLon" to nearLon))).results
        }.getOrDefault(emptyList())
    }
}
