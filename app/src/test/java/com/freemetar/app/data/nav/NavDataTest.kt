package com.freemetar.app.data.nav

import com.freemetar.app.data.link.LinkJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavDataTest {
    @Test
    fun airportWithIlsDecoded() {
        val a = LinkJson.decodeFromString(NavAirport.serializer(), """
            {"icao":"EPWA","name":"Chopin","lat":52.16,"lon":20.96,"elevFt":362,
             "runways":[{"ident":"29","headingTrueDeg":292.4,"lengthFt":9186,"widthFt":164,"thresholdLat":52.16,"thresholdLon":20.99,"elevFt":360,
                         "ils":{"ident":"IWA","freqMhz":110.3,"locCourseDeg":287,"gsAngleDeg":3.0}},
                        {"ident":"11","headingTrueDeg":112.4,"ils":null}],
             "frequencies":[{"type":"ATIS","name":"Okecie ATIS","mhz":128.8},{"type":"TWR","mhz":118.3}],
             "futureField":1}
        """.trimIndent())
        assertEquals(2, a.runways.size)
        assertEquals(110.3, a.runways[0].ils!!.freqMhz!!, 0.0)
        assertNull(a.runways[1].ils)
        assertEquals("TWR", a.frequencies[1].type)
        val nav = AirportNav("EPWA", airport = a)
        assertEquals("IWA", nav.ilsFor("29")!!.ident)
        assertNull(nav.ilsFor("11"))
    }

    @Test
    fun proceduresAndLegsDecoded() {
        val p = LinkJson.decodeFromString(NavProcedures.serializer(), """
            {"icao":"EPGD","departures":[{"name":"LAPS1G","runways":["29"],"transitions":[]}],
             "arrivals":[{"name":"SOXER2A","runways":["29","11"],"transitions":["DCT"]}],
             "approaches":[{"name":"I29","type":"ILS","runway":"29","suffix":null,"transitions":["GD401"]},
                           {"name":"R11-Z","type":"RNAV","runway":"11","suffix":"Z"}]}
        """.trimIndent())
        assertEquals("ILS 29", p.approaches[0].label)
        assertEquals("RNAV 11 Z", p.approaches[1].label)
        val legs = LinkJson.decodeFromString(ProcLegs.serializer(), """
            {"icao":"EPGD","procType":"approach","name":"I29","runway":"29","transition":null,
             "legs":[{"fix":"GD401","lat":54.4,"lon":18.7,"legType":"IF","altDesc":"ABOVE","alt1Ft":3000,"flyOver":false},
                     {"fix":"FD29","lat":54.39,"lon":18.6,"legType":"CF","altDesc":"AT","alt1Ft":2000,"courseDeg":287},
                     {"fix":"RW29","lat":54.38,"lon":18.48,"legType":"CF","altDesc":"BETWEEN","alt1Ft":500,"alt2Ft":700}],
             "missed":[{"fix":"GD","legType":"DF","altDesc":"AT","alt1Ft":19000}]}
        """.trimIndent())
        assertEquals(3, legs.legs.size)
        assertEquals("3000A", legs.legs[0].constraint)
        assertEquals("2000", legs.legs[1].constraint)
        assertEquals("700–500", legs.legs[2].constraint)
        assertEquals("FL190", legs.missed[0].constraint)
    }

    @Test
    fun legsKeyIsStable() {
        assertEquals("approach|I29|29|", AirportNav.legsKey("approach", "I29", "29", null))
    }
}
