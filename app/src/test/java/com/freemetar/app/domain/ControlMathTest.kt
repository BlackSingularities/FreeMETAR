package com.freemetar.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ControlMathTest {
    @Test
    fun headingWrapsAround() {
        assertEquals(5, ControlMath.heading(355.0, 10))
        assertEquals(355, ControlMath.heading(5.0, -10))
        assertEquals(0, ControlMath.heading(359.4, 1))
        assertEquals(1, ControlMath.heading(null, 1))
    }

    @Test
    fun altitudeRoundedAndClamped() {
        assertEquals(12100, ControlMath.altitude(12040.0, 100))
        assertEquals(0, ControlMath.altitude(300.0, -1000))
        assertEquals(50000, ControlMath.altitude(49800.0, 1000))
    }

    @Test
    fun verticalSpeedAndSpeedLimits() {
        assertEquals(-8000, ControlMath.verticalSpeed(-7950.0, -100))
        assertEquals(1900, ControlMath.verticalSpeed(1800.0, 100))
        assertEquals(0, ControlMath.speed(5.0, -10))
        assertEquals(260, ControlMath.speed(250.0, 10))
    }

    @Test
    fun frequenciesParsedLikeRadioPanel() {
        assertEquals(121.5, ControlMath.parseMhz("121,5")!!, 1e-9)
        assertEquals(121.5, ControlMath.parseMhz("1215")!!, 1e-9)
        assertEquals(118.305, ControlMath.parseMhz("118305")!!, 1e-9)
        assertEquals(128.805, ControlMath.parseMhz("128.805")!!, 1e-9)
        assertNull(ControlMath.parseMhz("abc"))
        assertEquals(121_500_000L, ControlMath.comHz(121.5))
        assertEquals(118_305_000L, ControlMath.comHz(118.305))
        assertNull(ControlMath.comHz(113.45))
        assertEquals(113_450_000L, ControlMath.navHz(113.45))
        assertNull(ControlMath.navHz(121.5))
    }

    @Test
    fun squawkOnlyOctalDigits() {
        assertEquals(7000, ControlMath.squawk("7000"))
        assertEquals(1200, ControlMath.squawk(" 1200 "))
        assertNull(ControlMath.squawk("7800"))
        assertNull(ControlMath.squawk("700"))
    }

    @Test
    fun axisDeadZoneSensitivityAndLimit() {
        assertEquals(0.0, ControlMath.axis(1.5, 35.0, 2.0, 1.0, 0.8), 0.0)
        assertEquals(0.5, ControlMath.axis(18.5, 35.0, 2.0, 1.0, 0.8), 1e-9)
        assertEquals(-0.5, ControlMath.axis(-18.5, 35.0, 2.0, 1.0, 0.8), 1e-9)
        // Pełne przechylenie przycięte do limitu serwera
        assertEquals(0.8, ControlMath.axis(60.0, 35.0, 2.0, 1.0, 0.8), 0.0)
        assertEquals(0.75, ControlMath.axis(18.5, 35.0, 2.0, 1.5, 0.8), 1e-9)
    }
}
