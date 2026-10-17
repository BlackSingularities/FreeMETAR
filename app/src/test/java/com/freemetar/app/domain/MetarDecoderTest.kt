package com.freemetar.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetarDecoderTest {

    private fun group(raw: String, token: String) =
        MetarDecoder.decode(raw).firstOrNull { it.token == token } ?: throw AssertionError("Brak grupy $token w: $raw")

    private fun assertGroup(raw: String, token: String, category: Category, text: String) {
        val g = group(raw, token)
        assertEquals("kategoria $token", category, g.category)
        assertEquals("opis $token", text, g.text)
    }

    @Test
    fun `wszystkie grupy poza uwagami sa rozpoznane`() {
        for (raw in Samples.metars + Samples.tafs) {
            val unknown = MetarDecoder.decode(raw).filter { it.category != Category.REMARK && (it.text == "—" || it.text.isBlank()) }
            assertTrue("Nierozpoznane ${unknown.map { it.token }} w: $raw", unknown.isEmpty())
        }
    }

    @Test
    fun `europejski METAR`() {
        val r = Samples.EPWA
        assertGroup(r, "METAR", Category.OTHER, "Rutynowa depesza METAR")
        assertGroup(r, "EPWA", Category.STATION, "Lotnisko EPWA")
        assertGroup(r, "171200Z", Category.TIME, "Dzień 17, godz. 12:00 UTC")
        assertGroup(r, "27012KT", Category.WIND, "Wiatr z 270°, 12 kt")
        assertGroup(r, "240V300", Category.WIND, "Kierunek zmienny od 240° do 300°")
        assertGroup(r, "9999", Category.VISIBILITY, "Widzialność 10 km lub więcej")
        assertGroup(r, "FEW040", Category.CLOUDS, "Nieliczne (1–2/8) na 4000 ft")
        assertGroup(r, "SCT100", Category.CLOUDS, "Rozproszone (3–4/8) na 10000 ft")
        assertGroup(r, "15/08", Category.TEMP, "Temperatura 15 °C, punkt rosy 8 °C (różnica 7°)")
        assertTrue(group(r, "Q1013").text.startsWith("QNH 1013 hPa"))
        assertEquals(Category.TREND, group(r, "NOSIG").category)
    }

    @Test
    fun `porywy, AUTO i trend z widzialnoscia`() {
        val r = Samples.EGLL
        assertGroup(r, "AUTO", Category.OTHER, "Depesza automatyczna (bez obserwatora)")
        assertGroup(r, "24015G27KT", Category.WIND, "Wiatr z 240°, 15 kt, w porywach 27 kt")
        assertGroup(r, "-RA", Category.WEATHER, "Słaby deszcz")
        assertGroup(r, "BKN012", Category.CLOUDS, "Poprzerywane (5–7/8) na 1200 ft")
        assertTrue(group(r, "Q0998").text.startsWith("QNH 998 hPa"))
        assertGroup(r, "TEMPO", Category.TREND, "Okresowo:")
        // Widzialność w trendzie po temperaturze to nadal widzialność
        assertGroup(r, "4000", Category.VISIBILITY, "Widzialność 4000 m")
        assertGroup(Samples.OMDB, "5000", Category.VISIBILITY, "Widzialność 5 km")
    }

    @Test
    fun `mgla, RVR i widzialnosc pionowa`() {
        val r = Samples.LFPG
        assertGroup(r, "00000KT", Category.WIND, "Cisza")
        assertGroup(r, "0300", Category.VISIBILITY, "Widzialność 300 m")
        assertGroup(r, "R27L/0450N", Category.RVR, "RVR pas 27L: 450 m, bez zmian")
        assertGroup(r, "R26R/0550U", Category.RVR, "RVR pas 26R: 550 m, rośnie")
        assertGroup(r, "FG", Category.WEATHER, "Mgła")
        assertGroup(r, "VV001", Category.CLOUDS, "Widzialność pionowa na 100 ft")
        assertGroup(r, "08/08", Category.TEMP, "Temperatura 8 °C, punkt rosy 8 °C (różnica 0°)")
        assertGroup(r, "1500", Category.VISIBILITY, "Widzialność 1500 m")
        assertGroup(Samples.ESSA, "R01L/1100D", Category.RVR, "RVR pas 01L: 1100 m, maleje")
        assertEquals(Category.CLOUDS, group(Samples.EPGD, "VV///").category)
    }

    @Test
    fun `ujemne temperatury`() {
        assertGroup(Samples.ESSA, "M05/M07", Category.TEMP, "Temperatura -5 °C, punkt rosy -7 °C (różnica 2°)")
        assertGroup(Samples.PANC, "M18/M22", Category.TEMP, "Temperatura -18 °C, punkt rosy -22 °C (różnica 4°)")
        assertGroup(Samples.FAOR, "24/M01", Category.TEMP, "Temperatura 24 °C, punkt rosy -1 °C (różnica 25°)")
        assertGroup(Samples.EPGD, "M02/M02", Category.TEMP, "Temperatura -2 °C, punkt rosy -2 °C (różnica 0°)")
    }

    @Test
    fun `wiatr w MPS, zmienny i stan nawierzchni`() {
        assertGroup(Samples.UUEE, "33005MPS", Category.WIND, "Wiatr z 330°, 5 m/s")
        assertGroup(Samples.UUEE, "R24L/290050", Category.OTHER, "Stan nawierzchni pasa 24L")
        assertGroup(Samples.LOWW, "VRB02KT", Category.WIND, "Wiatr zmienny, 2 kt")
    }

    @Test
    fun `NIL, SPECI i COR`() {
        assertGroup(Samples.EPKK_NIL, "EPKK", Category.STATION, "Lotnisko EPKK")
        assertGroup(Samples.EPKK_NIL, "NIL", Category.OTHER, "Brak depeszy")
        assertGroup(Samples.KATL, "SPECI", Category.OTHER, "Depesza specjalna (zmiana warunków)")
        assertGroup(Samples.KATL, "KATL", Category.STATION, "Lotnisko KATL")
        assertGroup(Samples.LKPR_COR, "COR", Category.OTHER, "Depesza skorygowana")
        assertGroup(Samples.LKPR_COR, "LKPR", Category.STATION, "Lotnisko LKPR")
    }

    @Test
    fun `CAVOK i brak chmur`() {
        assertEquals(Category.VISIBILITY, group(Samples.EDDF, "CAVOK").category)
        assertGroup(Samples.LKPR, "NCD", Category.CLOUDS, "Nie wykryto chmur (stacja automatyczna)")
        assertGroup(Samples.OMDB, "NSC", Category.CLOUDS, "Brak znaczących chmur")
        assertGroup(Samples.KLAX, "CLR", Category.CLOUDS, "Niebo bezchmurne")
        assertGroup(Samples.PANC, "SKC", Category.CLOUDS, "Niebo bezchmurne")
    }

    @Test
    fun `9999NDV to widzialnosc 10 km bez zmiennosci kierunkowej`() {
        val g = group(Samples.LKPR, "9999NDV")
        assertEquals(Category.VISIBILITY, g.category)
        assertTrue(g.text, g.text.startsWith("Widzialność 10 km lub więcej"))
    }

    @Test
    fun `chmury konwekcyjne`() {
        assertGroup(Samples.EHAM, "BKN015CB", Category.CLOUDS, "Poprzerywane (5–7/8) na 1500 ft — cumulonimbus!")
        assertGroup(Samples.CYYZ, "FEW030TCU", Category.CLOUDS, "Nieliczne (1–2/8) na 3000 ft — cumulus wypiętrzony")
        assertGroup(Samples.EHAM, "RETSRA", Category.WEATHER, "Niedawno: Burza deszcz")
    }

    @Test
    fun `amerykanskie mile i altymetr`() {
        val r = Samples.KJFK
        assertGroup(r, "10SM", Category.VISIBILITY, "Widzialność 10 mil lądowych")
        assertGroup(r, "A2992", Category.PRESSURE, "Altymetr 29.92 inHg (1013 hPa)")
        assertGroup(Samples.KSFO, "1/2SM", Category.VISIBILITY, "Widzialność 1/2 mil lądowych")
        assertGroup(Samples.KSEA, "M1/4SM", Category.VISIBILITY, "Widzialność 1/4 mil lądowych (mniej niż)")
        assertGroup(Samples.KORD, "1 1/2SM", Category.VISIBILITY, "Widzialność 1 1/2 mil lądowych")
        assertGroup(Samples.TAF_KJFK, "P6SM", Category.VISIBILITY, "Widzialność powyżej 6 mil lądowych")
    }

    @Test
    fun `uwagi RMK`() {
        val r = Samples.KJFK
        assertGroup(r, "RMK", Category.REMARK, "Uwagi:")
        assertGroup(r, "AO2", Category.REMARK, "Stacja automatyczna z czujnikiem opadów")
        assertTrue(group(r, "SLP132").text.startsWith("Ciśnienie zredukowane: 1013"))
        assertGroup(r, "T01830039", Category.REMARK, "Dokładnie: temp. 18.3 °C, rosa 3.9 °C")
        // Po RMK wszystko jest uwagą, nawet grupy wyglądające jak wiatr
        assertEquals(Category.REMARK, group(r, "PK").category)
    }

    @Test
    fun `grupy TAF`() {
        val r = Samples.TAF_EPWA
        assertGroup(r, "TAF", Category.OTHER, "Prognoza lotniskowa")
        assertGroup(r, "EPWA", Category.STATION, "Lotnisko EPWA")
        assertGroup(r, "1712/1812", Category.TIME, "Ważna od dnia 17 12:00 do dnia 18 12:00 UTC")
        assertGroup(r, "PROB30", Category.TREND, "Prawdopodobieństwo 30%")
        assertGroup(r, "0800", Category.VISIBILITY, "Widzialność 800 m")
        assertGroup(Samples.TAF_KJFK, "FM171800", Category.TREND, "Od dnia 17, 18:00 UTC:")
        assertGroup(Samples.TAF_LFPG, "AMD", Category.OTHER, "Prognoza poprawiona")
        assertGroup(Samples.TAF_LFPG, "LFPG", Category.STATION, "Lotnisko LFPG")
        assertGroup(Samples.TAF_ESSA, "TX02/1714Z", Category.TEMP, "Maks. temperatura 02 °C")
        assertGroup(Samples.TAF_ESSA, "TNM08/1806Z", Category.TEMP, "Min. temperatura -08 °C")
    }

    @Test
    fun `znak rownosci na koncu jest pomijany`() {
        val d = MetarDecoder.decode("EPWA 171200Z 27012KT 9999 FEW040 15/08 Q1013=")
        assertEquals("Q1013", d.last().token)
    }

    @Test
    fun `weatherText po polsku`() {
        assertEquals("Słaby przelotny deszcz", MetarDecoder.weatherText("-SHRA"))
        assertEquals("Silny burza deszcz", MetarDecoder.weatherText("+TSRA"))
        assertEquals("Marznący mgła", MetarDecoder.weatherText("FZFG"))
        assertEquals("Zamglenie", MetarDecoder.weatherText("BR"))
        assertEquals("W pobliżu burza", MetarDecoder.weatherText("VCTS"))
        assertEquals("Zamieć wysoka śnieg", MetarDecoder.weatherText("BLSN"))
        assertEquals("Słaby deszcz śnieg", MetarDecoder.weatherText("-RASN"))
        assertEquals("Pył", MetarDecoder.weatherText("DU"))
    }

    @Test
    fun `weatherTextEn dla ATIS`() {
        assertEquals("Light showers of rain, mist", MetarDecoder.weatherTextEn("-SHRA BR"))
        assertEquals("Heavy thunderstorm with rain", MetarDecoder.weatherTextEn("+TSRA"))
        assertEquals("Thunderstorm", MetarDecoder.weatherTextEn("TS"))
        assertEquals("Freezing fog", MetarDecoder.weatherTextEn("FZFG"))
        assertEquals("Patches of fog", MetarDecoder.weatherTextEn("BCFG"))
        assertEquals("Light snow, blowing snow", MetarDecoder.weatherTextEn("-SN BLSN"))
        assertEquals("Haze", MetarDecoder.weatherTextEn("HZ"))
    }

    @Test
    fun `isWeather`() {
        assertTrue(MetarDecoder.isWeather("-SHRA"))
        assertTrue(MetarDecoder.isWeather("TS"))
        assertTrue(MetarDecoder.isWeather("VCTS"))
        assertTrue(!MetarDecoder.isWeather("NSW"))
        assertTrue(!MetarDecoder.isWeather("BKN012"))
        assertTrue(!MetarDecoder.isWeather("9999"))
    }

    @Test
    fun `splitTaf dzieli na okresy`() {
        assertEquals(
            listOf(
                "TAF EPWA 171100Z 1712/1812 27012KT 9999 SCT030",
                "TEMPO 1712/1718 4000 SHRA BKN012",
                "BECMG 1800/1802 VRB03KT",
                "PROB30 TEMPO 1803/1807 0800 FG BKN002",
            ),
            MetarDecoder.splitTaf(Samples.TAF_EPWA),
        )
        val us = MetarDecoder.splitTaf(Samples.TAF_KJFK)
        assertEquals(4, us.size)
        assertTrue(us.drop(1).all { it.startsWith("FM") })
        assertEquals(listOf("PROB40 1800/1806 3000 BR"), MetarDecoder.splitTaf(Samples.TAF_EGLL).takeLast(1))
        assertEquals(1, MetarDecoder.splitTaf(Samples.TAF_ESSA).size)
    }
}
