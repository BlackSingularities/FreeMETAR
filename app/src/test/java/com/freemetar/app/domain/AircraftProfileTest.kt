package com.freemetar.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AircraftProfileTest {
    @Test
    fun detectsByManufacturerFirst() {
        assertEquals(AircraftProfile.AIRBUS, AircraftProfile.detect("FENIX A320 IAE", null, "airliner", "jet"))
        assertEquals(AircraftProfile.AIRBUS, AircraftProfile.detect("Airbus A320neo FlyByWire", null, null, null))
        assertEquals(AircraftProfile.BOEING, AircraftProfile.detect("PMDG 737-800", null, "airliner", "jet"))
        assertEquals(AircraftProfile.BOEING, AircraftProfile.detect(null, "B787", null, "jet"))
    }

    @Test
    fun detectsByCategoryAndEngine() {
        assertEquals(AircraftProfile.BIZJET, AircraftProfile.detect("Cessna Citation CJ4", "C25C", "bizjet", "jet"))
        assertEquals(AircraftProfile.TURBOPROP, AircraftProfile.detect("Beechcraft King Air 350i", "B350", "turboprop", "turboprop"))
        assertEquals(AircraftProfile.TURBOPROP, AircraftProfile.detect("ATR 72-600", null, "airliner", "turboprop"))
        assertEquals(AircraftProfile.PISTON, AircraftProfile.detect("Cessna Skyhawk G1000", "C172", "ga", "piston"))
        assertEquals(AircraftProfile.PISTON, AircraftProfile.detect(null, null, null, null))
    }

    @Test
    fun capabilities() {
        assertTrue(AircraftProfile.AIRBUS.hasAutothrottle)
        assertFalse(AircraftProfile.PISTON.hasSpoilers)
        assertFalse(AircraftProfile.TURBOPROP.hasSpoilers)
        assertEquals(AircraftProfile.BOEING, AircraftProfile.fromName("BOEING"))
        assertNull(AircraftProfile.fromName(null))
    }
}
