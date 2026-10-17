package com.freemetar.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TafAnalyzerTest {

    @Test
    fun `TAF europejski - TEMPO, BECMG, PROB TEMPO`() {
        val p = TafAnalyzer.periods(Samples.TAF_EPWA)
        assertEquals(listOf(PeriodKind.BASE, PeriodKind.TEMPO, PeriodKind.BECOMING, PeriodKind.PROB), p.map { it.kind })

        with(p[0]) {
            assertEquals(listOf(17, 12, 18, 12), listOf(fromDay, fromHour, toDay, toHour))
            assertEquals("270° 12 kt", windText)
            assertEquals(9999, visM)
            assertNull(ceilingFt)
            assertEquals("VFR", category)
            assertEquals("Prognoza główna 12–12Z", title)
        }
        with(p[1]) {
            assertEquals(4000, visM)
            assertEquals(1200, ceilingFt)
            assertEquals("przelotny deszcz", weather)
            assertEquals("IFR", category)
            assertEquals("Okresowo 12–18Z", title)
        }
        with(p[2]) {
            // Brak widzialności i chmur — dziedziczy z prognozy głównej
            assertNull(visM)
            assertEquals("zmienny 03 kt", windText)
            assertEquals("VFR", category)
        }
        with(p[3]) {
            assertEquals(30, probability)
            assertEquals(800, visM)
            assertEquals(200, ceilingFt)
            assertEquals("LIFR", category)
            assertEquals("Szansa 30% 03–07Z", title)
        }
    }

    @Test
    fun `TAF amerykanski - FM, P6SM, 5SM, 1 1_2SM`() {
        val p = TafAnalyzer.periods(Samples.TAF_KJFK)
        assertEquals(listOf(PeriodKind.BASE, PeriodKind.FROM, PeriodKind.FROM, PeriodKind.FROM), p.map { it.kind })
        assertEquals("310° 12 kt (porywy 22)", p[0].windText)
        assertEquals(9654, p[0].visM)
        assertEquals("VFR", p[0].category)

        assertEquals(17, p[1].fromDay)
        assertEquals(18, p[1].fromHour)
        assertNull(p[1].toHour)
        assertEquals("Od 18Z", p[1].title)
        assertEquals("VFR", p[1].category)

        // 5 SM to jeszcze MVFR
        assertEquals("MVFR", p[2].category)
        // 1 1/2 SM ≈ 2414 m → IFR (nie LIFR jak dla 1/2 SM)
        assertEquals(2414, p[3].visM)
        assertEquals(1500, p[3].ceilingFt)
        assertEquals("IFR", p[3].category)
    }

    @Test
    fun `BECMG z chmurami bez pulapu kasuje pulap z prognozy glownej`() {
        val p = TafAnalyzer.periods(Samples.TAF_EGLL)
        assertEquals("IFR", p[0].category)
        assertEquals(PeriodKind.BECOMING, p[1].kind)
        assertNull(p[1].ceilingFt)
        assertEquals("VFR", p[1].category)
        assertEquals(PeriodKind.PROB, p[2].kind)
        assertEquals(40, p[2].probability)
        assertEquals(3000, p[2].visM)
        assertEquals("Szansa 40% 00–06Z", p[2].title)
        assertEquals("IFR", p[2].category)
    }

    @Test
    fun `okres z samym zjawiskiem dziedziczy kategorie`() {
        val p = TafAnalyzer.periods("TAF EPKK 171100Z 1712/1812 22010KT 6000 BKN025 TEMPO 1714/1718 SHRA")
        assertEquals("MVFR", p[0].category)
        assertEquals("MVFR", p[1].category)
        assertEquals("przelotny deszcz", p[1].weather)
    }

    @Test
    fun `CAVOK i NSC`() {
        val p = TafAnalyzer.periods(Samples.TAF_LFPG)
        assertEquals(10000, p[0].visM)
        assertEquals("VFR", p[0].category)
        assertEquals(3000, p[1].visM)
        assertNull(p[1].ceilingFt)
        assertEquals("IFR", p[1].category)
    }

    @Test
    fun `VV jako pulap i grupy TX TN`() {
        val p = TafAnalyzer.periods(Samples.TAF_ESSA)
        assertEquals(1, p.size)
        assertEquals(800, p[0].ceilingFt)
        assertEquals(2000, p[0].visM)
        assertEquals("śnieg", p[0].weather)
        assertEquals("IFR", p[0].category)
    }

    @Test
    fun `TEMPO z CB i wiatr w MPS`() {
        val p = TafAnalyzer.periods(Samples.TAF_UUEE)
        assertNull(p[0].windText)
        assertEquals("MVFR", p[0].category)
        assertEquals(1000, p[1].ceilingFt)
        assertEquals("LIFR", p[1].category)
    }

    @Test
    fun `wszystkie probki TAF daja kategorie dla kazdego okresu`() {
        for (raw in Samples.tafs) {
            TafAnalyzer.periods(raw).forEach { assertEquals(raw, true, it.category != null) }
        }
    }
}
