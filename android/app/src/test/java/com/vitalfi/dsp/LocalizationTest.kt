package com.vitalfi.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class LocalizationTest {

    @Test
    fun polarToXY_northIsPositiveY() {
        val (x, y) = polarToXY(2.0, 0.0)
        assertEquals(0.0, x, 1e-9)
        assertEquals(2.0, y, 1e-9)
    }

    @Test
    fun polarToXY_eastIsPositiveX() {
        val (x, y) = polarToXY(3.0, 90.0)
        assertEquals(3.0, x, 1e-9)
        assertEquals(0.0, y, 1e-9)
    }

    @Test
    fun describePersonPosition_frontRight_whenPhoneFacingNorth() {
        val pos = describePersonPosition(x = 1.0, y = 2.0, z = -0.5, phoneBearingDeg = 0.0)
        assertTrue(pos.horizontal.contains("Frente"))
        assertTrue(pos.horizontal.contains("Derecha"))
        assertTrue(pos.vertical.contains("estimada") || pos.vertical.contains("no confirmada"))
        assertEquals(0.5, pos.depthM, 1e-9)
        assertEquals(hypot(1.0, 2.0), pos.distanceM, 1e-9)
        assertTrue(pos.forwardM > 0)
        assertTrue(pos.rightM > 0)
    }

    @Test
    fun describePersonPosition_nearby_whenDirectlyBelow_doesNotOrderDigging() {
        val pos = describePersonPosition(x = 0.1, y = 0.1, z = -0.8, phoneBearingDeg = 0.0)
        val h = pos.horizontal.lowercase()
        val v = pos.vertical.lowercase()
        assertTrue(h.contains("cerca") || h.contains("verifica"))
        assertFalse(h.contains("cavar"))
        assertFalse(v.contains("cavar"))
        assertTrue(v.contains("estimada") || v.contains("no confirmada"))
        assertEquals(0.8, pos.depthM, 1e-9)
    }

    @Test
    fun describePersonPosition_aboveGround() {
        val pos = describePersonPosition(x = 0.0, y = 1.0, z = 0.5, phoneBearingDeg = 0.0)
        assertTrue(pos.vertical.lowercase().contains("altura") || pos.vertical.lowercase().contains("estimada"))
        assertEquals(0.0, pos.depthM, 1e-9)
    }
}
