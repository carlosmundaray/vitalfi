package com.whofi.vitalfi.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class VitalSignalDetectorTest {

    @Test
    fun analyze_fewSamples_returnsCollecting() {
        val result = VitalSignalDetector.analyze(doubleArrayOf(1.0, 2.0), fs = 2.0)
        assertFalse(result.isBreathing)
        assertTrue(result.status.contains("Recolectando"))
    }

    @Test
    fun analyze_flatSignal_notBreathing() {
        val signal = DoubleArray(40) { -55.0 }
        val result = VitalSignalDetector.analyze(signal, fs = 2.0, sensitivity = 2.0, rubbleMode = true)
        assertFalse(result.isBreathing)
        assertTrue(result.signalFlat || !result.isActivity)
    }

    @Test
    fun analyze_oscillatingSignal_detectsActivityOrBreathing() {
        val fs = 2.0
        val f0 = 0.25 // 15 rpm
        val signal = DoubleArray(80) { i ->
            -60.0 + 3.0 * sin(2.0 * Math.PI * f0 * i / fs)
        }
        val result = VitalSignalDetector.analyze(signal, fs = fs, sensitivity = 3.0, rubbleMode = true)
        assertTrue(
            "expected activity or breathing, got status=${result.status} var=${result.variance}",
            result.isActivity || result.isBreathing || result.periodicity > 0.1,
        )
    }

    @Test
    fun distanceFromRssi_strongerMeansCloser() {
        val near = VitalSignalDetector.distanceFromRssi(-45)
        val far = VitalSignalDetector.distanceFromRssi(-80)
        assertTrue(near < far)
        assertTrue(near in 0.5..8.0)
        assertTrue(far in 0.5..8.0)
    }

    @Test
    fun distanceFromVariance_higherVarianceMeansCloser() {
        val close = VitalSignalDetector.distanceFromVariance(1.5)
        val far = VitalSignalDetector.distanceFromVariance(0.01)
        assertTrue(close < far)
    }

    @Test
    fun proximityFromVariance_puntoCeroLabel() {
        val (label, distance, _) = VitalSignalDetector.proximityFromVariance(
            variance = 2.5,
            isBreathing = true,
            isActivity = true,
            rssiDbm = -40,
        )
        assertTrue(distance < VitalSignalDetector.PUNTO_CERO_M || label == "PUNTO CERO" || label == "MUY CERCA")
        assertTrue(distance in 0.15..8.0)
    }

    @Test
    fun rubbleConfidenceTracker_buildsScoreOnBreathing() {
        val tracker = RubbleConfidenceTracker(calibSeconds = 1.0)
        val now = 1_000L
        tracker.begin(now)
        val breathing = DetectionResult(
            isBreathing = true,
            isActivity = true,
            heartBeating = false,
            heartRateBpm = 0.0,
            respFreq = 0.25,
            respRate = 15.0,
            variance = 0.5,
            snr = 2.0,
            noiseFloor = 0.1,
            uniqueValues = 5,
            signalFlat = false,
            status = "ok",
            hint = "",
            confidence = 0.8,
            periodicity = 0.5,
        )
        // After calibration window
        tracker.update(breathing, now + 2_000L)
        repeat(8) { i ->
            tracker.update(breathing, now + 3_000L + i * 500L)
        }
        assertTrue(tracker.score > 0.2)
        assertTrue(tracker.hits >= 1)
        assertTrue(tracker.likelyPresent(0.2))
    }

    @Test
    fun wifiCoverageEstimator_strongerRssi_largerRadius() {
        val weak = com.whofi.vitalfi.wifi.WifiCoverageEstimator.estimateRadiusM(-90)
        val strong = com.whofi.vitalfi.wifi.WifiCoverageEstimator.estimateRadiusM(-40)
        assertTrue(strong > weak)
        assertEquals(0.0, com.whofi.vitalfi.wifi.WifiCoverageEstimator.estimateRadiusM(null), 0.0)
    }
}
