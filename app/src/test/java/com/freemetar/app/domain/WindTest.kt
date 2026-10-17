package com.freemetar.app.domain

import com.freemetar.app.data.Runway
import com.freemetar.app.data.RunwayEnd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

fun runway(le: String, he: String, leHdg: Double? = null, heHdg: Double? = null) = Runway(
    lengthFt = 9000, widthFt = 150, surface = "ASP", lighted = true, closed = false,
    le = RunwayEnd(le, leHdg, null, null, null, null),
    he = RunwayEnd(he, heHdg, null, null, null, null),
)

class WindTest {
    private val eps = 0.01

    @Test
    fun `wiatr wzdluz pasa`() {
        val c = Wind.components(270.0, 20.0, 270.0)
        assertEquals(20.0, c.headwind, eps)
        assertEquals(0.0, c.crosswind, eps)
    }

    @Test
    fun `wiatr boczny z prawej i z lewej`() {
        val r = Wind.components(300.0, 20.0, 270.0)
        assertEquals(17.32, r.headwind, eps)
        assertEquals(10.0, r.crosswind, eps)
        assertEquals("z prawej", r.crossSide)

        val l = Wind.components(240.0, 20.0, 270.0)
        assertEquals(-10.0, l.crosswind, eps)
        assertEquals("z lewej", l.crossSide)
    }

    @Test
    fun `wiatr w ogon`() {
        val c = Wind.components(90.0, 10.0, 270.0)
        assertEquals(-10.0, c.headwind, eps)
    }

    @Test
    fun `kurs pasa z oznaczenia lub z bazy`() {
        assertEquals(290.0, Wind.headingOf("29L", null)!!, eps)
        assertEquals(90.0, Wind.headingOf("09", null)!!, eps)
        assertEquals(293.5, Wind.headingOf("29L", 293.5)!!, eps)
        assertEquals(null, Wind.headingOf("H1", null))
    }

    @Test
    fun `analyze sortuje od najwiekszego wiatru czolowego`() {
        val rwys = listOf(runway("09", "27"), runway("04", "22"))
        val a = Wind.analyze(rwys, 250, 15, 25)
        assertEquals(listOf("27", "22", "04", "09"), a.map { it.runwayEnd })
        assertEquals(15 * kotlin.math.cos(Math.toRadians(20.0)), a[0].steady.headwind, eps)
        assertEquals(25 * kotlin.math.cos(Math.toRadians(20.0)), a[0].gust!!.headwind, eps)
        assertTrue(a[0].steady.crosswind < 0) // 250° przy kursie 270° — z lewej
    }

    @Test
    fun `analyze bez kierunku wiatru`() {
        assertTrue(Wind.analyze(listOf(runway("09", "27")), null, 5, null).isEmpty())
        assertTrue(Wind.analyze(listOf(runway("09", "27")), 90, null, null).isEmpty())
    }

    @Test
    fun `analyze uzywa kursu rzeczywistego i pomija pasy bez kursu`() {
        val a = Wind.analyze(listOf(runway("11", "29", 112.0, 292.0), runway("H1", "H2")), 290, 10, null)
        assertEquals(listOf("29", "11"), a.map { it.runwayEnd })
        assertEquals(292.0, a[0].heading, eps)
        assertEquals(null, a[0].gust)
    }
}
