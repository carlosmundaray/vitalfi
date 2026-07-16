package com.whofi.vitalfi.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiVictimTrackerTest {

    @Test
    fun depthUnderRubble_isNegativeAndBounded() {
        val tracker = MultiVictimTracker()
        val depth = tracker.depthUnderRubble(distanceM = 2.0, confidence = 0.8)
        assertTrue(depth < 0)
        assertTrue(depth in -1.8..-0.25)
    }

    @Test
    fun depthUnderRubble_higherConfidence_deeper() {
        val tracker = MultiVictimTracker()
        val shallow = tracker.depthUnderRubble(1.0, 0.1)
        val deep = tracker.depthUnderRubble(1.0, 0.95)
        assertTrue(deep < shallow) // more negative
    }

    @Test
    fun update_andLocate_producesVictimNearBearing() {
        val tracker = MultiVictimTracker()
        val now = System.currentTimeMillis()
        val detection = DetectionResult(
            isBreathing = true,
            isActivity = true,
            heartBeating = true,
            heartRateBpm = 72.0,
            respFreq = 0.25,
            respRate = 15.0,
            variance = 0.8,
            snr = 3.0,
            noiseFloor = 0.05,
            uniqueValues = 8,
            signalFlat = false,
            status = "Respiración detectada",
            hint = "",
            confidence = 0.85,
            periodicity = 0.55,
            spectralEnergy = 0.2,
            surfaceSignalLikelihood = 0.1,
        )
        repeat(12) { i ->
            tracker.update(
                bearingDeg = 90.0,
                distanceM = 2.0,
                strength = 1.0,
                detection = detection,
                rubbleConf = 0.7,
                nowMs = now + i * 500L,
            )
        }
        val victims = tracker.locateVictims(minScore = 0.01, nowMs = now + 10_000L)
        assertTrue(victims.isNotEmpty())
        val best = victims.first()
        assertTrue(absAngleDiff(best.bearing, 90.0) < 20.0)
        assertTrue(best.distance in 0.5..5.0)
        assertTrue(best.isBreathing)
    }

    @Test
    fun bestEstimate_nullWhenEmpty() {
        val tracker = MultiVictimTracker()
        assertNull(tracker.bestEstimate())
    }

    private fun absAngleDiff(a: Double, b: Double): Double {
        var d = ((a - b) % 360.0 + 360.0) % 360.0
        if (d > 180) d = 360 - d
        return d
    }
}
