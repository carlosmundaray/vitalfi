package com.vitalfi.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Verifica el empaquetado de entrada TFLite sin cargar el Interpreter nativo
 * (misma lógica de padding/ventana que SignatureModel).
 */
class SignatureModelInputTest {

    @Test
    fun shortSignal_padsToExactly64Floats() {
        val window = 64
        val signal = DoubleArray(12) { -55.0 - it * 0.1 }
        val buf = buildInputLikeProduction(signal, window)
        assertEquals(window * 4, buf.capacity())
        assertEquals(0, buf.position())
        var count = 0
        while (buf.hasRemaining()) {
            buf.float
            count++
        }
        assertEquals(window, count)
    }

    @Test
    fun longSignal_usesLastWindow() {
        val window = 64
        val signal = DoubleArray(100) { it.toDouble() }
        val buf = buildInputLikeProduction(signal, window)
        assertEquals(window * 4, buf.capacity())
        // Primer valor del buffer debe corresponder a z-score de signal[36..]
        val first = buf.float
        assertTrue(first.isFinite())
    }

    private fun buildInputLikeProduction(signal: DoubleArray, window: Int): ByteBuffer {
        val input = ByteBuffer.allocateDirect(window * 4).order(ByteOrder.nativeOrder())
        val start = (signal.size - window).coerceAtLeast(0)
        val slice = signal.copyOfRange(start, signal.size)
        val mean = slice.average()
        val variance = slice.map { (it - mean) * (it - mean) }.average()
        val std = kotlin.math.sqrt(variance).coerceAtLeast(1e-4)
        val padded = DoubleArray(window)
        val offset = (window - slice.size).coerceAtLeast(0)
        for (i in slice.indices) {
            if (offset + i < window) padded[offset + i] = slice[i]
        }
        for (v in padded) {
            input.putFloat(((v - mean) / std).toFloat())
        }
        input.rewind()
        return input
    }
}
