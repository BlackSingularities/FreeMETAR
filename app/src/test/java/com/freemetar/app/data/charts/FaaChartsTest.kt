package com.freemetar.app.data.charts

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate

class FaaChartsTest {
    private fun sample(): DtppIndex =
        javaClass.classLoader!!.getResourceAsStream("dtpp_metafile_sample.xml")!!.use { DtppMetafile.parse(it) }

    @Test
    fun metafileParsedIntoIndex() {
        val idx = sample()
        assertEquals("2610", idx.cycle)
        assertEquals("2026-10-01", idx.from)
        assertEquals("2026-10-29", idx.to)
        assertEquals(listOf("JNU", "79J", "JFK"), idx.airports.map { it.ident })

        val jfk = idx.airport("KJFK")!!
        assertEquals("JOHN F KENNEDY INTL", jfk.name)
        assertEquals("NY", jfk.state)
        assertEquals("NEW YORK", jfk.city)
        val types = jfk.charts.groupBy { it.type }.mapValues { (_, l) -> l.map { it.name } }
        assertEquals(listOf("AIRPORT DIAGRAM"), types[ChartType.APD])
        assertEquals(listOf("ILS OR LOC RWY 04L"), types[ChartType.IAP])
        assertEquals(listOf("CAMRN FIVE"), types[ChartType.STAR])
        assertEquals(listOf("HOT SPOT"), types[ChartType.HOT])
        // LAHSO trafia do minimów
        assertEquals(listOf("TAKEOFF MINIMUMS", "LAHSO"), types[ChartType.MIN])
    }

    @Test
    fun continuationPagesMergedIntoOneChart() {
        val dps = sample().charts("KJFK").filter { it.type == ChartType.DP }
        assertEquals(1, dps.size)
        assertEquals("KENNEDY FIVE", dps[0].name)
        assertEquals(listOf("00610KENNEDY.PDF", "00610KENNEDY_C.PDF"), dps[0].pdfs)
        assertEquals("2610", dps[0].cycle)
    }

    @Test
    fun deletedRecordsSkippedAndOdpIsDeparture() {
        val idx = sample()
        assertEquals(listOf("RNAV (GPS) RWY 11"), idx.charts("79J").map { it.name })
        assertEquals(listOf("JUNEAU SEVEN (OBSTACLE)"), idx.charts("PAJN").filter { it.type == ChartType.DP }.map { it.name })
    }

    @Test
    fun amendmentAndChangeFlag() {
        val ils = sample().charts("JFK").first { it.type == ChartType.IAP }
        assertTrue(ils.changed)
        assertEquals("Amdt 5 10/01/2026", ils.amendment)
        val apd = sample().charts("JFK").first { it.type == ChartType.APD }
        assertFalse(apd.changed)
        assertNull(apd.amendment)
    }

    @Test
    fun icaoAndFaaIdentMapping() {
        val idx = sample()
        assertEquals("JFK", idx.faaIdent("KJFK"))
        assertEquals("JNU", idx.faaIdent("PAJN"))
        assertEquals("KJFK", idx.icaoFor("JFK"))
        assertEquals("PAJN", idx.icaoFor("JNU"))
        // Lotnisko bez kodu ICAO: tylko identyfikator FAA, a K+ident też trafia
        assertNull(idx.icaoFor("79J"))
        assertEquals("79J", idx.faaIdent("K79J"))
        assertEquals(idx.charts("kjfk ").size, idx.charts("JFK").size)
        assertTrue(idx.contains("PAJN"))
        assertFalse(idx.contains("EPWA"))
        assertTrue(idx.charts("EGLL").isEmpty())
    }

    @Test
    fun usIcaoHeuristic() {
        listOf("KJFK", "PAJN", "PHNL", "PGUM", "TJSJ", "TIST", "NSTU", "kord").forEach { assertTrue(it, FaaCharts.isUsIcao(it)) }
        listOf("EPWA", "CYYZ", "MMMX", "JFK", "K12", "EGLL").forEach { assertFalse(it, FaaCharts.isUsIcao(it)) }
    }

    @Test
    fun cycleByDate() {
        assertEquals("2610", DtppCycle.idFor(LocalDate.of(2026, 10, 18)))
        assertEquals("2610", DtppCycle.idFor(LocalDate.of(2026, 10, 1)))
        assertEquals("2609", DtppCycle.idFor(LocalDate.of(2026, 9, 30)))
        assertEquals("2611", DtppCycle.idFor(LocalDate.of(2026, 10, 29)))
        assertEquals("2601", DtppCycle.idFor(LocalDate.of(2026, 1, 22)))
        // 2025 miał 13 cykli: 2513 obowiązywał od 25 grudnia
        assertEquals("2513", DtppCycle.idFor(LocalDate.of(2026, 1, 21)))
        assertEquals("2501", DtppCycle.idFor(LocalDate.of(2025, 1, 23)))
        assertEquals("2001", DtppCycle.idFor(LocalDate.of(2020, 1, 2)))
        assertEquals("1913", DtppCycle.idFor(LocalDate.of(2020, 1, 1)))
        assertEquals(LocalDate.of(2026, 10, 1), DtppCycle.effective(LocalDate.of(2026, 10, 28)))
    }

    @Test
    fun cycleSwitchesAt0901Z() {
        assertEquals("2610", DtppCycle.current(Instant.parse("2026-10-29T09:00:00Z")))
        assertEquals("2611", DtppCycle.current(Instant.parse("2026-10-29T09:02:00Z")))
        assertEquals("2609", DtppCycle.previous(Instant.parse("2026-10-18T12:00:00Z")))
        assertEquals(LocalDate.of(2026, 10, 1), DtppCycle.parseEdate("0901Z  10/01/26"))
        assertEquals("https://aeronav.faa.gov/d-tpp/2610/xml_data/d-TPP_Metafile.xml", DtppCycle.metafileUrl("2610"))
        assertEquals("https://aeronav.faa.gov/d-tpp/2610/00610AD.PDF", DtppCycle.pdfUrl("2610", "00610AD.PDF"))
    }

    @Test
    fun indexSurvivesJsonRoundTrip() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }
        val idx = sample()
        val back = json.decodeFromString(DtppIndex.serializer(), json.encodeToString(DtppIndex.serializer(), idx))
        assertEquals(idx.airports, back.airports)
        assertEquals("JNU", back.faaIdent("PAJN"))
    }

    @Test
    fun localIndexUsedOfflineAndOldCyclesCleaned() = kotlinx.coroutines.runBlocking {
        val root = kotlin.io.path.createTempDirectory("charts").toFile()
        try {
            val json = Json { encodeDefaults = false; explicitNulls = false }
            File(root, "2610").apply { mkdirs() }.resolve("index.json").writeText(json.encodeToString(DtppIndex.serializer(), sample()))
            File(root, "2609").mkdirs()
            val repo = FaaCharts(root, clock = { Instant.parse("2026-10-18T12:00:00Z") })
            assertEquals("2610", repo.localCycle())
            assertFalse(repo.isStale())
            // update() nie sięga do sieci, gdy indeks jest z bieżącego cyklu
            assertEquals("2610", repo.update().cycle)
            assertEquals(1, repo.chartsFor("KJFK").count { it.type == ChartType.APD })
            assertTrue(repo.isUsAirport("PAJN"))
            assertFalse(repo.isUsAirport("KXXX"))
            repo.cleanup(keep = "2610")
            assertEquals(listOf("2610"), root.list()!!.toList())
            val later = FaaCharts(root, clock = { Instant.parse("2026-11-01T12:00:00Z") })
            assertTrue(later.isStale())
        } finally {
            root.deleteRecursively()
        }
    }
}
