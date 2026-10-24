package com.freemetar.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AircraftProfileTest {
    private fun detect(title: String?, model: String? = null, category: String? = null, engine: String? = null) =
        AircraftProfile.detect(title, model, null, category, engine)

    @Test
    fun detectsTypeByName() {
        assertEquals(AircraftProfile.A320, detect("FENIX A320 IAE", category = "airliner", engine = "jet"))
        assertEquals(AircraftProfile.A320, detect("Airbus A320neo FlyByWire"))
        assertEquals(AircraftProfile.B737, detect("PMDG 737-800", category = "airliner", engine = "jet"))
        assertEquals(AircraftProfile.B787, detect(null, "B787", engine = "jet"))
        assertEquals(AircraftProfile.ATR72, detect("ATR 72-600", category = "airliner", engine = "turboprop"))
        assertEquals(AircraftProfile.E190, detect("Embraer E190", category = "airliner", engine = "jet"))
    }

    @Test
    fun detectsByCategoryAndEngine() {
        assertEquals(AircraftProfile.R44, detect("Robinson R44", category = "helicopter", engine = "helicopter"))
        assertEquals(AircraftProfile.GLIDER, detect("Schleicher ASK 21", category = "ga", engine = "none"))
        assertEquals(AircraftProfile.C172, detect("Cessna 152 Classic", category = "ga", engine = "piston"))
        assertEquals(AircraftProfile.G1000, detect("Cessna Skyhawk G1000", "C172", "ga", "piston"))
        assertEquals(AircraftProfile.G1000, detect("Beechcraft King Air 350i", "B350", "turboprop", "turboprop"))
        assertEquals(AircraftProfile.G1000, detect(null))
    }

    @Test
    fun eachProfileHasItsOwnPanels() {
        assertTrue(AircraftProfile.A320.hasAutothrottle)
        assertEquals(AutopilotKind.FCU, AircraftProfile.A320.autopilot)
        assertEquals(2, AircraftProfile.A320.engines)
        assertFalse(AircraftProfile.ATR72.hasAutothrottle)
        assertEquals(listOf(Lever.CONDITION), AircraftProfile.ATR72.levers)
        assertFalse(AircraftProfile.C172.hasSpoilers)
        assertEquals(AutopilotKind.NONE, AircraftProfile.R44.autopilot)
        assertFalse(AircraftProfile.GLIDER.hasThrottle)
        assertFalse(AircraftProfile.GLIDER.com2)
    }

    @Test
    fun savedNames() {
        assertEquals(AircraftProfile.A320, AircraftProfile.fromName("AIRBUS"))
        assertEquals(AircraftProfile.B737, AircraftProfile.fromName("BOEING"))
        assertEquals(AircraftProfile.ATR72, AircraftProfile.fromName("ATR72"))
        assertNull(AircraftProfile.fromName("PISTON"))
        assertNull(AircraftProfile.fromName(null))
    }
}
