package com.vitalfi.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RfEnvironmentMapTest {

    @Test
    fun record_buildsCellsOnlyWhereMeasured() {
        val map = RfEnvironmentMap(numBins = 36)
        val now = 1_000L
        map.record(0.0, -50, 0.1, now)
        map.record(10.0, -52, 0.12, now + 100)
        map.record(180.0, -80, 0.02, now + 200)

        val cells = map.snapshot(now + 300)
        assertEquals(3, cells.size)
        assertTrue(cells.all { it.samples >= 1 })
        assertTrue(cells.none { it.meanRssi.isNaN() })
    }

    @Test
    fun attenuation_marksWeakerSectorVsNeighbors() {
        val map = RfEnvironmentMap(numBins = 36)
        val now = 5_000L
        // Vecinos fuertes, centro débil (sombra RF real)
        repeat(4) {
            map.record(0.0, -45, 0.05, now + it)
            map.record(20.0, -46, 0.05, now + it)
            map.record(10.0, -70, 0.02, now + it) // bin del medio más atenuado
        }
        val cells = map.snapshot(now + 50)
        val weak = cells.minBy { it.meanRssi }
        assertTrue(weak.attenuationScore > 0.3)
    }
}
