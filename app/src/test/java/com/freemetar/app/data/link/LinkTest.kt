package com.freemetar.app.data.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkTest {
    // Przykład z docs/link-protocol.md (sekcja 3, `state`)
    private val stateJson = """
    {
      "v": 1, "type": "state", "seq": 1234, "t": 1792231234567,
      "data": {
        "source": "msfs",
        "aircraft": { "title": "Airbus A320neo", "onGround": false, "paused": false },
        "simTimeUtc": "2026-10-17T12:34:56Z",
        "position": { "lat": 52.1657, "lon": 20.9671, "altMslFt": 12000, "altAglFt": 11630, "altIndFt": 12010 },
        "speed": { "iasKt": 250, "tasKt": 298, "gsKt": 310, "mach": 0.46, "vsFpm": 1800 },
        "attitude": { "pitchDeg": 5.2, "bankDeg": -3.0, "hdgTrueDeg": 331.0, "hdgMagDeg": 325.6, "trackTrueDeg": 333.1 },
        "autopilot": { "master": true, "fd": true, "athr": true, "hdgDeg": 330, "altFt": 24000, "vsFpm": 1800, "spdKt": 250,
          "modes": { "hdg": true, "nav": false, "apr": false, "loc": false, "alt": false, "vs": false, "flc": true } },
        "radios": { "com1": { "activeMhz": 121.5, "standbyMhz": 118.3 }, "transponder": { "code": "7000", "mode": "alt", "ident": false } },
        "controls": { "gearDown": true, "gearPct": 100, "flapsIndex": 1, "flapsPositions": 5, "flapsPct": 25,
          "spoilersPct": 0, "spoilersArmed": true, "parkingBrake": false, "elevatorTrimPct": 2.5 },
        "lights": { "beacon": true, "strobe": true },
        "fuel": { "totalKg": 5400, "tanks": { "leftKg": 2500, "centerKg": 400, "rightKg": 2500 } },
        "engines": [ { "n1Pct": 85.1, "n2Pct": 93.0, "rpm": 0, "egtC": 620, "fuelFlowKgH": 1250 } ],
        "environment": { "windDirDeg": 270, "windSpdKt": 25, "oatC": -9.5, "qnhHpa": 1013.2 },
        "axes": { "active": false },
        "futureField": { "x": 1 }
      }
    }
    """.trimIndent()

    @Test
    fun decodesDocumentedState() {
        val m = LinkJson.decodeFromString(Incoming.serializer(), stateJson)
        assertEquals("state", m.type)
        val s = m.data!!
        assertEquals("Airbus A320neo", s.aircraft.title)
        assertEquals(12010.0, s.position.altIndFt!!, 0.0)
        assertEquals(250.0, s.speed.iasKt!!, 0.0)
        assertTrue(s.autopilot.modes.flc)
        assertEquals(118.3, s.radios.com1!!.standbyMhz!!, 0.0)
        assertNull(s.radios.com2)
        assertEquals("7000", s.radios.transponder!!.code)
        assertEquals(3, s.fuel.tanks.size)
        assertEquals(1250.0, s.engines.single().fuelFlowKgH!!, 0.0)
    }

    @Test
    fun decodesHelloOkAndErrors() {
        val ok = LinkJson.decodeFromString(Incoming.serializer(),
            """{"v":1,"type":"hello_ok","id":"1","token":"abc","server":"OpenAirBook Link","limits":{"maxDeflection":0.8,"axisTimeoutMs":500}}""")
        assertEquals("abc", ok.token)
        assertEquals(0.8, ok.limits!!.maxDeflection, 0.0)
        val err = LinkJson.decodeFromString(Incoming.serializer(), """{"v":1,"type":"error","id":null,"code":"pairing_failed","message":"Zły kod"}""")
        assertEquals("pairing_failed", err.code)
    }

    @Test
    fun encodesHelloAndCommandsPerProtocol() {
        val hello = LinkJson.encodeToString(Hello.serializer(), Hello(code = "482913", client = ClientInfo("Pixel 8")))
        assertTrue(hello.contains("\"type\":\"hello\""))
        assertTrue(hello.contains("\"code\":\"482913\""))
        assertFalse("brak tokenu nie może być wysłany jako null", hello.contains("token"))
        val cmd = LinkJson.encodeToString(Command.serializer(), Command(id = "c1", name = "HEADING_BUG_SET", value = 270.0))
        assertTrue(cmd.contains("\"name\":\"HEADING_BUG_SET\"") && cmd.contains("\"value\":270.0"))
        val rel = LinkJson.encodeToString(AxisMessage.serializer(), AxisMessage(release = true))
        assertTrue(rel.contains("\"release\":true"))
        assertFalse(rel.contains("aileron"))
    }

    @Test
    fun announceParsed() {
        val a = LinkJson.decodeFromString(Announce.serializer(),
            """{"v":1,"type":"announce","name":"OpenAirBook Link","version":"1.0.12","host":"PC","port":47810,"ips":["192.168.1.20"],"source":"msfs"}""")
        assertEquals(47810, a.port)
        assertEquals("msfs", a.source)
    }

    private fun st(lat: Double, lon: Double, gs: Double, ground: Boolean, alt: Double = 3000.0) = FlightState(
        aircraft = AircraftInfo("C172", ground, false),
        position = Position(lat, lon, alt),
        speed = Speed(gsKt = gs),
    )

    @Test
    fun recorderCapturesFlightUntilStoppedOnGround() {
        val r = FlightRecorder { lat, _ -> if (lat < 53) "EPWA" else "EPGD" }
        var t = 0L
        // Kołowanie — nie nagrywa
        assertNull(r.onState(st(52.16, 20.96, 15.0, true), t)); assertFalse(r.recording)
        // Start
        t += 1000; r.onState(st(52.17, 20.0, 90.0, false), t); assertTrue(r.recording)
        // 40 min lotu na północ
        var lat = 52.17
        repeat(480) { t += 5000; lat += 0.0046; r.onState(st(lat, 20.0, 110.0, false, 4500.0), t) }
        // Lądowanie i postój: zapis dopiero po 60 s bez ruchu
        t += 5000; assertNull(r.onState(st(lat, 20.0, 3.0, true), t))
        t += 30_000; assertNull(r.onState(st(lat, 20.0, 0.0, true), t))
        t += 31_000; val log = r.onState(st(lat, 20.0, 0.0, true), t)
        assertNotNull(log); log!!
        assertEquals("EPWA", log.from); assertEquals("EPGD", log.to)
        assertEquals(4500, log.maxAltFt)
        assertTrue(log.distanceNm in 120..140)
        assertTrue(log.durationMin in 40..42)
        assertFalse(r.recording)
    }

    @Test
    fun restartInSimulatorStartsANewFlight() {
        val r = FlightRecorder { lat, _ -> if (lat < 53) "EPWA" else "EPGD" }
        var t = 0L
        var lat = 52.17
        r.onState(st(lat, 20.0, 120.0, false), t)
        repeat(60) { t += 5000; lat += 0.003; r.onState(st(lat, 20.0, 120.0, false, 3000.0), t) }   // 5 min lotu
        // Restart: samolot nagle znów nad EPWA (teleport 10 NM wstecz w 1 s) — pierwszy lot zamknięty
        t += 1000
        val first = r.onState(st(52.17, 20.0, 120.0, false), t)
        assertNotNull(first)
        assertTrue(first!!.durationMin in 4..6)
        // Drugi lot nagrywa się od nowa
        assertTrue(r.recording)
        repeat(30) { t += 5000; r.onState(st(52.17 + it * 0.003, 20.0, 120.0, false), t) }
        assertTrue(r.currentTrack.first().t > first.end)
    }

    @Test
    fun snapshotHasTheSameIdAsTheFinishedFlight() {
        val r = FlightRecorder { _, _ -> null }
        var t = 0L
        repeat(40) { t += 5000; r.onState(st(52.0 + it * 0.003, 20.0, 120.0, false), t) }
        val draft = r.snapshot(t)
        assertNotNull(draft)
        assertTrue(r.recording)  // migawka nie kończy nagrania
        val done = r.finish(t + 5000)
        assertEquals(draft!!.id, done!!.id)
    }

    @Test
    fun otherAircraftStartsANewFlight() {
        val r = FlightRecorder { _, _ -> null }
        var t = 0L
        repeat(40) { t += 5000; r.onState(st(52.0 + it * 0.003, 20.0, 120.0, false), t) }
        t += 5000
        val done = r.onState(st(52.0 + 40 * 0.003, 20.0, 120.0, false).copy(aircraft = AircraftInfo("A320", false, false)), t)
        assertNotNull(done)
        assertTrue(r.recording)
    }

    @Test
    fun shortHopIsDiscarded() {
        val r = FlightRecorder { _, _ -> null }
        r.onState(st(52.0, 20.0, 80.0, false), 0)
        r.onState(st(52.01, 20.0, 80.0, false), 30_000)
        assertNull(r.finish(60_000))
    }

    @Test
    fun pausedStatesAreIgnored() {
        val r = FlightRecorder { _, _ -> null }
        assertNull(r.onState(st(52.0, 20.0, 200.0, false).copy(aircraft = AircraftInfo("x", false, true)), 0))
        assertFalse(r.recording)
    }
}
