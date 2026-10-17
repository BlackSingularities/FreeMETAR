package com.freemetar.app.domain

import com.freemetar.app.data.CloudLayer
import com.freemetar.app.data.Metar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AtisTest {

    private fun metar(
        raw: String,
        time: String = "2026-10-17T12:50:00Z",
        windDir: Int? = 290, windVariable: Boolean = false, windKt: Int? = 15, gustKt: Int? = null,
        wx: String? = null, clouds: List<CloudLayer> = emptyList(),
        tempC: Double? = 15.0, dewpointC: Double? = 8.0, altimHpa: Double? = 1013.0,
    ) = Metar(
        station = raw.substringBefore(' '), raw = raw, obsTimeEpoch = Instant.parse(time).epochSecond,
        tempC = tempC, dewpointC = dewpointC, windDir = windDir, windVariable = windVariable, windKt = windKt, gustKt = gustKt,
        visibility = null, altimHpa = altimHpa, wx = wx, clouds = clouds, flightCategory = null,
        lat = null, lon = null, elevationM = null, name = null,
    )

    @Test
    fun `spell uzywa wymowy ICAO`() {
        assertEquals("one two tree four fife six seven eight niner zero", Atis.spell("1234567890"))
    }

    @Test
    fun `litera zmienia sie co godzine`() {
        assertEquals("Alfa", Atis.letter(metar("X", time = "2026-10-17T00:20:00Z")))
        assertEquals("Mike", Atis.letter(metar("X", time = "2026-10-17T12:50:00Z")))
        assertEquals("X-ray", Atis.letter(metar("X", time = "2026-10-17T23:50:00Z")))
    }

    @Test
    fun `pelny komunikat`() {
        val m = metar(
            "EPWA 171250Z 29015G25KT 9999 -SHRA BKN025CB 15/08 Q1013 NOSIG",
            gustKt = 25, wx = "-SHRA", clouds = listOf(CloudLayer("BKN", 2500, "CB")),
        )
        val text = Atis.text("Warsaw", m, listOf(runway("11R", "29L"), runway("15", "33")))
        assertEquals(
            "Warsaw information Mike, time one two fife zero zulu. " +
                "Runway in use two niner left. " +
                "Wind two niner zero degrees, one fife knots, gusting two fife. " +
                "Visibility one zero kilometers or more. " +
                "Light showers of rain. " +
                "Clouds broken two thousand fife hundred feet cumulonimbus. " +
                "Temperature one fife, dew point eight. " +
                "QNH one zero one tree. " +
                "Advise on initial contact you have information Mike.",
            text,
        )
    }

    @Test
    fun `cisza, CAVOK i ujemne temperatury`() {
        val m = metar("ESSA 171250Z 00000KT CAVOK M05/M07 Q0985", windDir = 0, windKt = 0, tempC = -5.0, dewpointC = -7.0, altimHpa = 985.0)
        val text = Atis.text("Arlanda", m, emptyList())
        assertTrue(text, text.contains("Wind calm."))
        assertTrue(text, text.contains("CAVOK."))
        assertTrue(text, text.contains("Temperature minus fife, dew point minus seven."))
        assertTrue(text, text.contains("QNH niner eight fife."))
        assertFalse(text, text.contains("Runway"))
        assertFalse(text, text.contains("Clouds"))
    }

    @Test
    fun `wiatr zmienny`() {
        val m = metar("LOWW 171250Z VRB03KT 9999 SCT045 18/09 Q1020", windDir = null, windVariable = true, windKt = 3)
        assertTrue(Atis.text("Wien", m, emptyList()).contains("Wind variable, tree knots."))
    }

    @Test
    fun `widzialnosc w metrach i kilometrach`() {
        val fog = metar("LFPG 170800Z 00000KT 0800 FG VV002 08/08 Q1021", windKt = 0, wx = "FG", clouds = listOf(CloudLayer("VV", 200)))
        val t1 = Atis.text("Charles de Gaulle", fog, emptyList())
        assertTrue(t1, t1.contains("Visibility eight zero zero meters."))
        assertTrue(t1, t1.contains("Fog."))
        assertTrue(t1, t1.contains("Clouds vertical visibility two hundred feet."))

        val haze = metar("ZBAA 171200Z 36008KT 6000 HZ NSC 14/06 Q1022", windDir = 360, windKt = 8, wx = "HZ")
        assertTrue(Atis.text("Beijing", haze, emptyList()).contains("Visibility six kilometers."))
    }

    @Test
    fun `9999NDV i widzialnosc z trendu nie myli ATIS`() {
        val m = metar("EGLL 171250Z AUTO 24015KT 9999NDV BKN012 12/10 Q0998 TEMPO 4000 RA", windDir = 240, clouds = listOf(CloudLayer("BKN", 1200)))
        val text = Atis.text("Heathrow", m, emptyList())
        assertTrue(text, text.contains("Visibility one zero kilometers or more."))
        assertFalse(text, text.contains("four zero zero zero"))
    }

    @Test
    fun `pasy z literami C i bez litery`() {
        val m = metar("KDEN 171253Z 36012KT 10SM FEW100 10/M02 A3012", windDir = 360, windKt = 12)
        assertTrue(Atis.text("Denver", m, listOf(runway("17C", "35C"))).contains("Runway in use tree fife center."))
        val e = metar("EPKK 171250Z 07010KT 9999 FEW030 15/08 Q1013", windDir = 70, windKt = 10)
        assertTrue(Atis.text("Krakow", e, listOf(runway("07", "25"))).contains("Runway in use zero seven."))
    }

    @Test
    fun `chmury powyzej 10000 ft i kilka warstw`() {
        val m = metar(
            "KJFK 171251Z 31015KT 10SM FEW050 SCT120 18/04 A2992",
            windDir = 310, clouds = listOf(CloudLayer("FEW", 5000), CloudLayer("SCT", 12000)), altimHpa = 1013.2,
        )
        val text = Atis.text("Kennedy", m, emptyList())
        assertTrue(text, text.contains("Clouds few fife thousand feet, scattered one two thousand feet."))
        assertTrue(text, text.contains("QNH one zero one tree."))
    }
}
