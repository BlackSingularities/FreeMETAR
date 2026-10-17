package com.freemetar.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlightCategoryTest {

    @Test
    fun `brak danych`() = assertNull(FlightCategory.of(null, null))

    @Test
    fun `sama widzialnosc`() {
        assertEquals("VFR", FlightCategory.of(9999, null))
        assertEquals("MVFR", FlightCategory.of(8000, null))
        assertEquals("MVFR", FlightCategory.of(8045, null)) // 5 SM
        assertEquals("MVFR", FlightCategory.of(4828, null)) // 3 SM
        assertEquals("IFR", FlightCategory.of(4000, null))
        assertEquals("IFR", FlightCategory.of(1609, null)) // 1 SM
        assertEquals("LIFR", FlightCategory.of(800, null))
    }

    @Test
    fun `sam pulap`() {
        assertEquals("LIFR", FlightCategory.of(null, 400))
        assertEquals("IFR", FlightCategory.of(null, 500))
        assertEquals("IFR", FlightCategory.of(null, 999))
        assertEquals("MVFR", FlightCategory.of(null, 1000))
        assertEquals("MVFR", FlightCategory.of(null, 3000))
        assertEquals("VFR", FlightCategory.of(null, 3100))
    }

    @Test
    fun `gorszy z dwoch warunkow decyduje`() {
        assertEquals("LIFR", FlightCategory.of(9999, 200))
        assertEquals("LIFR", FlightCategory.of(1200, 5000))
        assertEquals("IFR", FlightCategory.of(6000, 800))
        assertEquals("VFR", FlightCategory.of(9654, 3500))
    }
}
