package com.whofi.vitalfi.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

class SignalFiltersTest {

    @Test
    fun nextPowerOfTwo_roundsUp() {
        assertEquals(1, FftUtils.nextPowerOfTwo(1))
        assertEquals(8, FftUtils.nextPowerOfTwo(5))
        assertEquals(16, FftUtils.nextPowerOfTwo(16))
        assertEquals(32, FftUtils.nextPowerOfTwo(17))
    }

    @Test
    fun variance_constantSignal_isZero() {
        val signal = DoubleArray(20) { 42.0 }
        assertEquals(0.0, SignalFilters.variance(signal), 1e-12)
    }

    @Test
    fun variance_empty_isZero() {
        assertEquals(0.0, SignalFilters.variance(doubleArrayOf()), 0.0)
    }

    @Test
    fun hampelFilter_removesSpike() {
        val signal = doubleArrayOf(1.0, 1.0, 1.0, 50.0, 1.0, 1.0, 1.0)
        val cleaned = SignalFilters.hampelFilter(signal, windowSize = 2, threshold = 3.0)
        assertTrue("spike should be reduced", abs(cleaned[3] - 1.0) < 5.0)
        assertTrue(cleaned[3] < 50.0)
    }

    @Test
    fun uniqueRounded_countsDistinct() {
        val signal = doubleArrayOf(1.11, 1.12, 1.19, 2.0)
        assertEquals(3, SignalFilters.uniqueRounded(signal, decimals = 1))
    }

    @Test
    fun dominantFrequency_findsSineNearExpected() {
        val fs = 10.0
        val f0 = 0.25
        val n = 256
        val signal = DoubleArray(n) { i -> sin(2.0 * Math.PI * f0 * i / fs) }
        val (freq, mag) = FftUtils.dominantFrequency(signal, fs, 0.1 to 0.5)
        assertTrue("mag should be positive", mag > 0)
        assertEquals(f0, freq, 0.05)
    }

    @Test
    fun respiratoryPeriodicity_shortSignal_returnsZero() {
        val short = DoubleArray(10) { it.toDouble() }
        assertEquals(0.0, SignalFilters.respiratoryPeriodicity(short, fs = 2.0), 0.0)
    }
}
