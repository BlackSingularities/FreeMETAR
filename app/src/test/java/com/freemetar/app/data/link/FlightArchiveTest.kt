package com.freemetar.app.data.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

class FlightArchiveTest {
    /** Godzina lotu z punktem co sekundę: wznoszenie, przelot, zniżanie. */
    private fun hourFlight(id: String = "a", start: Long = 1_700_000_000_000L): FlightLog {
        val pts = (0 until 3600).map { i ->
            val alt = when { i < 900 -> i * 40; i < 2700 -> 36_000; else -> 36_000 - (i - 2700) * 40 }
            TrackPoint(
                t = start + i * 1000L, lat = 52.16 + i * 0.0012, lon = 20.96 + i * 0.0009 + sin(i / 300.0) * 0.01,
                altFt = alt, gsKt = 250 + (i % 7), vsFpm = if (i < 900) 2400 else if (i < 2700) 0 else -2400,
                iasKt = 250, hdg = 30 + (i / 600),
            )
        }
        return FlightLog(id = id, start = start, end = start + 3_600_000, aircraft = "A320", from = "EPWA", to = "EPGD", maxAltFt = 36_000, distanceNm = 380, track = pts)
    }

    @Test
    fun roundTripKeepsEveryPointWithinOneMetre() {
        val f = hourFlight()
        val back = FlightArchive.decode(FlightArchive.encode(listOf(f))).single()
        assertEquals(f.track.size, back.track.size)
        f.track.zip(back.track).forEach { (a, b) ->
            assertEquals(a.t, b.t)
            assertTrue(abs(a.lat - b.lat) < 1e-5 && abs(a.lon - b.lon) < 1e-5)
            assertEquals(a.altFt, b.altFt); assertEquals(a.gsKt, b.gsKt); assertEquals(a.vsFpm, b.vsFpm); assertEquals(a.hdg, b.hdg)
        }
        assertEquals(f.copy(track = emptyList()), back.copy(track = emptyList()))
    }

    @Test
    fun hourAtOneHertzIsSmall() {
        val bytes = FlightArchive.encode(listOf(hourFlight())).size
        println("1 h @ 1 Hz: $bytes B")
        assertTrue("archiwum $bytes B", bytes < 60_000)
    }

    @Test
    fun readsLegacyJsonAndMergesById() {
        val legacy = """{"app":"OpenAirBook","version":1,"flights":[{"id":"x","start":1,"end":200000,"aircraft":null,"from":"EPWA","to":"EPKK","maxAltFt":9000,"distanceNm":130,
            "track":[{"t":1,"lat":52.1,"lon":20.9,"altFt":300,"gsKt":140}]}]}"""
        val old = FlightArchive.decode(legacy.toByteArray())
        assertEquals("x", old.single().id)
        val merged = FlightArchive.merge(old, old + hourFlight("y"))
        assertEquals(listOf("y", "x"), merged.map { it.id })
    }
}
