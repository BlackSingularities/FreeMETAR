package com.freemetar.app.data

import com.freemetar.app.domain.Geo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parsowanie prawdziwych odpowiedzi aviationweather.gov i Open-Meteo zapisanych w test/resources. */
class WeatherDataTest {
    private fun res(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    @Test
    fun sigmetParsedWithAreaAndLevels() {
        val list = HazardParser.sigmets(res("sigmet.json"))
        assertEquals(3, list.size)
        val s = list.first()
        assertEquals("VA", s.hazard)
        assertEquals(0, s.baseFt)
        assertEquals(14000, s.topFt)
        assertEquals(5, s.area.size)
        assertTrue(s.validTo > s.validFrom)
        assertTrue(s.hazardLabel.startsWith("Pył wulkaniczny"))
        assertEquals("od ziemi do FL140", s.levels)
    }

    @Test
    fun pointInsideAndOutsideSigmet() {
        val s = HazardParser.sigmets(res("sigmet.json")).first()
        // Wnętrze prostokąta ok. 14.05–14.47 N, 90.87–90.98 W
        assertTrue(s.contains(LatLon(14.3, -90.92)))
        assertEquals(0.0, s.distanceNm(LatLon(14.3, -90.92)), 0.0)
        assertFalse(s.contains(LatLon(15.0, -90.92)))
        // 15.0 N to ok. 32 NM na północ od krawędzi 14.467 N
        assertEquals(32.0, s.distanceNm(LatLon(15.0, -90.88)), 2.0)
    }

    @Test
    fun polygonWithConcaveShape() {
        // Litera L: punkt w „wycięciu” jest na zewnątrz
        val l = listOf(LatLon(0.0, 0.0), LatLon(0.0, 2.0), LatLon(1.0, 2.0), LatLon(1.0, 1.0), LatLon(2.0, 1.0), LatLon(2.0, 0.0))
        assertTrue(Geo.pointInPolygon(LatLon(0.5, 1.5), l))
        assertTrue(Geo.pointInPolygon(LatLon(1.5, 0.5), l))
        assertFalse(Geo.pointInPolygon(LatLon(1.5, 1.5), l))
    }

    @Test
    fun pirepNegativeTurbulenceIsNotAHazard() {
        val list = HazardParser.pireps(res("pirep.json"))
        assertEquals(4, list.size)
        val p = list.first()
        assertEquals("B737", p.aircraft)
        assertEquals(65, p.flightLevel)
        assertFalse(p.reportsHazard)
        assertEquals("bez turbulencji", p.summary)
    }

    @Test
    fun pirepSevereTurbulenceSummary() {
        val p = Pirep("UUA /OV DEN/TB SEV", 39.8, -104.6, 210, "A320", 0, "SEV", "MOD", urgent = true)
        assertTrue(p.reportsHazard)
        assertEquals("turbulencja silna, oblodzenie umiarkowane", p.summary)
    }

    @Test
    fun openMeteoLevelsConvertedToFlightLevels() {
        val a = OpenMeteo.parse(res("openmeteo.json"))
        assertNotNull(a)
        a!!
        assertEquals(5, a.levels.size)
        // Poziomy rosną: 850 hPa ok. FL050, 250 hPa ok. FL340
        assertTrue(a.levels.zipWithNext().all { (x, y) -> y.flightLevel > x.flightLevel })
        val low = a.levels.first(); val high = a.levels.last()
        assertEquals(850, low.pressureHpa)
        assertTrue(low.flightLevel in 40..60)
        assertEquals(250, high.pressureHpa)
        assertTrue(high.flightLevel in 330..360)
        assertTrue((high.cells.first().tempC ?: 0) < -40)
        assertTrue(a.freezingLevelFt.first() != null)
        assertEquals(high, a.levels.minByOrNull { kotlin.math.abs(it.flightLevel - 350) })
        assertNotNull(a.windAt(340))
    }

    @Test
    fun openMeteoUrlHasAllLevels() {
        val u = OpenMeteo.url(52.17, 20.97)
        OpenMeteo.PRESSURE_LEVELS.forEach { assertTrue(u.contains("wind_speed_${it}hPa")) }
        assertTrue(u.contains("latitude=52.170"))
        assertTrue(u.contains("wind_speed_unit=kn"))
    }
}
