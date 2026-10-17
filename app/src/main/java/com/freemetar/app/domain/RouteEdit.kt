package com.freemetar.app.domain

import com.freemetar.app.data.ProcSelection
import com.freemetar.app.data.RoutePoint
import com.freemetar.app.data.SavedRoute
import com.freemetar.app.data.nav.Leg

/**
 * Operacje edytora planu lotu — czyste funkcje na [SavedRoute], testowalne bez Androida.
 * Lotniska na końcach (`points.first()` / `points.last()`) zawsze zostają na miejscu.
 */
object RouteEdit {
    private fun SavedRoute.withEnroute(list: List<RoutePoint>): SavedRoute {
        val origin = points.firstOrNull() ?: RoutePoint(this.origin, type = "airport")
        val dest = points.lastOrNull()?.takeIf { points.size > 1 } ?: RoutePoint(destination, type = "airport")
        return copy(points = listOf(origin) + list + dest).remeasured()
    }

    /** Po zmianie trasy dystans z planu SimBrief przestaje być aktualny — liczymy z punktów. */
    private fun SavedRoute.remeasured() = copy(distanceNm = measuredDistanceNm() ?: distanceNm)

    fun insert(r: SavedRoute, index: Int, p: RoutePoint): SavedRoute {
        val e = r.enroute.toMutableList()
        e.add(index.coerceIn(0, e.size), p.copy(segment = "enroute"))
        return r.withEnroute(e)
    }

    fun remove(r: SavedRoute, index: Int): SavedRoute {
        val e = r.enroute.toMutableList()
        if (index in e.indices) e.removeAt(index)
        return r.withEnroute(e)
    }

    fun move(r: SavedRoute, from: Int, to: Int): SavedRoute {
        val e = r.enroute.toMutableList()
        if (from !in e.indices || to !in e.indices) return r
        e.add(to, e.removeAt(from))
        return r.withEnroute(e)
    }

    /** „Direct to”: usuwa punkty trasy przed wskazanym (i odlot, bo samolot leci już prosto). */
    fun directTo(r: SavedRoute, index: Int): SavedRoute {
        val e = r.enroute
        if (index !in e.indices) return r
        return r.copy(departure = null).withEnroute(e.drop(index))
    }

    fun setDeparture(r: SavedRoute, runway: String?, proc: ProcSelection?) = r.copy(depRunway = runway, departure = proc).remeasured()
    fun setArrival(r: SavedRoute, proc: ProcSelection?) = r.copy(arrival = proc).remeasured()
    fun setApproach(r: SavedRoute, runway: String?, proc: ProcSelection?) = r.copy(arrRunway = runway ?: proc?.runway, approach = proc).remeasured()

    fun rename(r: SavedRoute, name: String) = r.copy(name = name.ifBlank { "${r.origin} → ${r.destination}" })
    fun setCruise(r: SavedRoute, fl: Int?) = r.copy(cruiseFl = fl)
    fun setAlternates(r: SavedRoute, list: List<String>) = r.copy(alternates = list.map { it.uppercase().trim() }.filter { it.length in 3..4 }.distinct())

    /** Odcinki procedury z MSFS → punkty trasy (tylko te z pozycją; wektory bez punktu są pomijane). */
    fun legsToPoints(legs: List<Leg>, segment: String): List<RoutePoint> = legs.mapNotNull { l ->
        val lat = l.lat ?: return@mapNotNull null
        val lon = l.lon ?: return@mapNotNull null
        RoutePoint(
            ident = l.fix ?: "(%.2f,%.2f)".format(java.util.Locale.US, lat, lon),
            lat = lat, lon = lon, type = "proc",
            altitudeFt = (if (l.altDesc == "BETWEEN") l.alt2Ft else l.alt1Ft)?.toInt(),
            constraint = l.constraint,
            segment = segment,
        )
    }
}
