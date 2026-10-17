package com.freemetar.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutesTest {
    /** Udawana baza: dwa lotniska i radiolatarnie, w tym dwie o tym samym kodzie w różnych miejscach. */
    private val resolver = FixResolver { ident, near ->
        val known = mapOf(
            "EPWA" to RoutePoint("EPWA", 52.165, 20.967, "airport"),
            "EPGD" to RoutePoint("EPGD", 54.377, 18.466, "airport"),
            "WAR" to RoutePoint("WAR", 52.25, 21.0, "vor-dme"),
        )
        if (ident == "DUP") {
            // Wybór bliższej z dwóch
            val a = RoutePoint("DUP", 53.0, 20.0, "ndb"); val b = RoutePoint("DUP", -30.0, 150.0, "ndb")
            return@FixResolver if (near == null || near.lat > 0) a else b
        }
        known[ident]
    }

    @Test
    fun textRouteSkipsAirwaysAndProcedures() {
        val r = RouteImport.fromText("epwa SOXER2G WAR N871 BIMPA DCT DUP EPGD", null, resolver)
        assertEquals("EPWA", r.origin)
        assertEquals("EPGD", r.destination)
        assertEquals(listOf("EPWA", "WAR", "BIMPA", "DUP", "EPGD"), r.points.map { it.ident })
        assertEquals("N871", r.points.first { it.ident == "BIMPA" }.airway)
        assertFalse(r.points.first { it.ident == "BIMPA" }.hasPosition)
        assertEquals(53.0, r.points.first { it.ident == "DUP" }.lat!!, 0.0)
        assertEquals("airport", r.points.last().type)
        assertTrue(r.distanceNm!! in 150..260)
    }

    @Test
    fun textRouteReadsSpeedLevelGroup() {
        val r = RouteImport.fromText("EPWA N0450F350 WAR EPGD", null, resolver)
        assertEquals(350, r.cruiseFl)
        assertEquals(listOf("EPWA", "WAR", "EPGD"), r.points.map { it.ident })
    }

    @Test(expected = IllegalArgumentException::class)
    fun textRouteNeedsTwoAirports() {
        RouteImport.fromText("EPWA", null, resolver)
    }

    private val simbrief = """
    {
      "fetch": {"status": "Success"},
      "params": {"units": "kgs"},
      "general": {"icao_airline": "LOT", "flight_number": "123", "initial_altitude": "35000",
                  "route": "SOXER N871 BIMPA", "route_distance": "201"},
      "origin": {"icao_code": "EPWA", "pos_lat": "52.165833", "pos_long": "20.967222", "elevation": "362"},
      "destination": {"icao_code": "EPGD", "pos_lat": "54.377569", "pos_long": "18.466222", "elevation": "489"},
      "alternate": {"icao_code": "EPBY"},
      "aircraft": {"icaocode": "A20N", "reg": "SP-LWA"},
      "times": {"est_time_enroute": "2700"},
      "fuel": {"plan_ramp": "5400", "enroute_burn": "2100", "reserve": "1200", "alternate_burn": "900", "plan_landing": "3200"},
      "navlog": {"fix": [
        {"ident": "SOXER", "type": "wpt", "pos_lat": "52.6", "pos_long": "20.6", "altitude_feet": "18000", "wind_dir": "250", "wind_spd": "35", "oat": "-21", "via_airway": "SID"},
        {"ident": "TOC", "type": "ltlg", "pos_lat": "53.0", "pos_long": "20.2", "altitude_feet": "35000", "via_airway": "N871"},
        {"ident": "EPGD", "type": "apt", "pos_lat": "54.377569", "pos_long": "18.466222", "altitude_feet": "489", "via_airway": "DCT"}
      ]}
    }
    """.trimIndent()

    @Test
    fun simbriefPlanParsed() {
        val r = SimBrief.parse(simbrief)
        assertEquals("LOT123 EPWA → EPGD", r.name)
        assertEquals(listOf("EPBY"), r.alternates)
        assertEquals(350, r.cruiseFl)
        assertEquals(201, r.distanceNm)
        assertEquals(45, r.eteMin)
        assertEquals("A20N SP-LWA", r.aircraft)
        assertEquals(2100, r.fuel!!.trip)
        assertEquals("kgs", r.fuel!!.unit)
        // Lotniska na końcach tylko raz, punkty z navlogu w środku
        assertEquals(listOf("EPWA", "SOXER", "TOC", "EPGD"), r.points.map { it.ident })
        assertEquals(35, r.points[1].windKt)
        assertEquals(35000, r.points[2].altitudeFt)
        assertEquals("simbrief", r.source)
    }

    @Test(expected = IllegalStateException::class)
    fun simbriefErrorStatus() {
        SimBrief.parse("""{"fetch": {"status": "Error: Unknown UserID"}}""")
    }

    @Test
    fun simbriefUrlUsesUserIdForDigits() {
        assertTrue(SimBrief.url("123456").contains("userid=123456"))
        assertTrue(SimBrief.url("Jan Kowalski").contains("username=Jan+Kowalski"))
        assertTrue(SimBrief.url("x").endsWith("&json=v2"))
    }

    @Test
    fun exportRoundTripAndFormatDetection() {
        val a = RouteImport.fromText("EPWA WAR EPGD", 120, resolver)
        val b = SimBrief.parse(simbrief)
        val text = RouteJson.json.encodeToString(RouteExport.serializer(), RouteExport(routes = listOf(a, b)))
        val back = RouteImport.parseAny(text, resolver)
        assertEquals(listOf(a, b), back)
        // Plan SimBrief rozpoznany bez opakowania
        assertEquals("EPGD", RouteImport.parseAny(simbrief, resolver).single().destination)
        // Zwykły tekst
        assertEquals("EPWA", RouteImport.parseAny("EPWA EPGD", resolver).single().origin)
    }

    @Test
    fun measuredDistanceNeedsTwoPositions() {
        assertNull(SavedRoute(name = "x", origin = "A", destination = "B", points = listOf(RoutePoint("A"), RoutePoint("B"))).measuredDistanceNm())
    }

    @Test
    fun simbriefSidAndStarRecognised() {
        val body = """
        {"fetch":{"status":"Success"},"general":{"initial_altitude":"35000"},
         "origin":{"icao_code":"EPWA","pos_lat":"52.16","pos_long":"20.96","plan_rwy":"29"},
         "destination":{"icao_code":"EPGD","pos_lat":"54.37","pos_long":"18.46","plan_rwy":"11"},
         "navlog":{"fix":[
           {"ident":"WA401","pos_lat":"52.2","pos_long":"20.8","is_sid_star":"1","via_airway":"LAPS1G"},
           {"ident":"LAPSO","pos_lat":"52.4","pos_long":"20.6","is_sid_star":"1","via_airway":"LAPS1G"},
           {"ident":"BIMPA","pos_lat":"53.2","pos_long":"20.1","is_sid_star":"0","via_airway":"N871"},
           {"ident":"GD452","pos_lat":"54.0","pos_long":"18.9","is_sid_star":"1","via_airway":"BIMP2A"},
           {"ident":"EPGD","pos_lat":"54.37","pos_long":"18.46","is_sid_star":"0"}]}}
        """.trimIndent()
        val r = SimBrief.parse(body)
        assertEquals("29", r.depRunway); assertEquals("11", r.arrRunway)
        assertEquals("LAPS1G", r.departure!!.name)
        assertEquals(listOf("WA401", "LAPSO"), r.departure!!.points.map { it.ident })
        assertEquals("BIMP2A", r.arrival!!.name)
        assertEquals(listOf("BIMPA"), r.enroute.map { it.ident })
        assertEquals(listOf("EPWA", "WA401", "LAPSO", "BIMPA", "GD452", "EPGD"), r.fullPath().map { it.ident })
    }
}
