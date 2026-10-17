package com.freemetar.app.domain

import com.freemetar.app.data.ProcSelection
import com.freemetar.app.data.RouteExport
import com.freemetar.app.data.RouteJson
import com.freemetar.app.data.RoutePoint
import com.freemetar.app.data.SavedRoute
import com.freemetar.app.data.nav.Leg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteEditTest {
    private val base = SavedRoute(
        name = "EPWA → EPGD", origin = "EPWA", destination = "EPGD",
        points = listOf(
            RoutePoint("EPWA", 52.165, 20.967, "airport"),
            RoutePoint("WAR", 52.25, 21.0, "vor"),
            RoutePoint("BIMPA", 53.2, 20.1),
            RoutePoint("EPGD", 54.377, 18.466, "airport"),
        ),
    )

    @Test
    fun enrouteEditing() {
        var r = RouteEdit.insert(base, 1, RoutePoint("SOXER", 53.6, 19.5))
        assertEquals(listOf("EPWA", "WAR", "SOXER", "BIMPA", "EPGD"), r.points.map { it.ident })
        r = RouteEdit.move(r, 2, 0)
        assertEquals(listOf("BIMPA", "WAR", "SOXER"), r.enroute.map { it.ident })
        r = RouteEdit.remove(r, 1)
        assertEquals(listOf("BIMPA", "SOXER"), r.enroute.map { it.ident })
        // Lotniska końcowe nie ruszają się
        assertEquals("EPWA", r.points.first().ident); assertEquals("EPGD", r.points.last().ident)
        // Dystans przeliczony z punktów
        assertTrue(r.distanceNm!! > 150)
    }

    @Test
    fun directToDropsEarlierPointsAndSid() {
        val withSid = RouteEdit.setDeparture(base, "29", ProcSelection("departure", "LAPS1G", "29", points = listOf(RoutePoint("LAPS", 52.3, 20.8, segment = "sid"))))
        val r = RouteEdit.directTo(withSid, 1)
        assertEquals(listOf("BIMPA"), r.enroute.map { it.ident })
        assertNull(r.departure)
        assertEquals("29", r.depRunway)
    }

    @Test
    fun fullPathIncludesProceduresAndMergesDuplicates() {
        val legs = listOf(
            Leg("GD401", 54.4, 18.9, "IF", "ABOVE", 3000.0),
            Leg("FD29", 54.39, 18.6, "CF", "AT", 2000.0),
            Leg(null, null, null, "VA"), // wektor bez pozycji — pomijany
        )
        val app = ProcSelection("approach", "I29", "29", null, "ILS 29", RouteEdit.legsToPoints(legs, "approach"))
        val star = ProcSelection("arrival", "BIMPA1A", null, null, null, listOf(RoutePoint("BIMPA", 53.2, 20.1, "proc", segment = "star"), RoutePoint("GD401", 54.4, 18.9, "proc", constraint = "FL100B", segment = "star")))
        val r = RouteEdit.setApproach(RouteEdit.setArrival(base, star), null, app)
        val path = r.fullPath()
        assertEquals(listOf("EPWA", "WAR", "BIMPA", "GD401", "FD29", "EPGD"), path.map { it.ident })
        // BIMPA z trasy połączony z początkiem STAR; GD401 z ograniczeniem podejścia
        assertEquals("star", path[2].segment)
        assertEquals("3000A", path[3].constraint)
        assertEquals("29", r.arrRunway)
        assertEquals("ILS 29", r.approach!!.display)
    }

    @Test
    fun oldRoutesFileStillLoads() {
        // Plik z wersji 2.0 — bez pól procedur i segmentów
        val old = """{"app":"OpenAirBook","version":1,"routes":[{"id":"x","name":"A → B","origin":"EPWA","destination":"EPGD",
            "points":[{"ident":"EPWA","lat":52.1,"lon":20.9,"type":"airport"},{"ident":"EPGD","lat":54.3,"lon":18.4,"type":"airport"}]}]}"""
        val r = RouteJson.json.decodeFromString(RouteExport.serializer(), old).routes.single()
        assertNull(r.departure); assertNull(r.approach)
        assertEquals(2, r.fullPath().size)
    }

    @Test
    fun alternatesNormalized() {
        assertEquals(listOf("EPBY", "EPSY"), RouteEdit.setAlternates(base, listOf(" epby", "EPSY", "", "EPBY", "X")).alternates)
    }
}
