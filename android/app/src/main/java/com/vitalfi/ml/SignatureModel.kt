package com.vitalfi.ml

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.sqrt

data class SignatureInference(
    val breathingScore: Double,
    val activityScore: Double,
    val signatureNorm: Double,
)

class SignatureModel(
    context: Context,
    private val modelAssetName: String = "signature_model.tflite",
) {
    private val lock = Any()
    private var modelBuffer: ByteBuffer? = null
    private var interpreter: Interpreter? = null
    val isReady: Boolean
    val statusLabel: String

    init {
        val loaded = tryLoadModel(context)
        isReady = loaded
        statusLabel = if (loaded) "ML activo (TFLite)" else "ML desactivado (sin modelo)"
    }

    fun infer(signal: DoubleArray): SignatureInference? {
        if (signal.size < 8) return null
        synchronized(lock) {
            val tf = interpreter ?: return null
            return try {
                runInference(tf, signal)
            } catch (_: Throwable) {
                // Nunca tumbar la app por un fallo de ML: la detección de vida sigue con DSP.
                null
            }
        }
    }

    private fun runInference(tf: Interpreter, signal: DoubleArray): SignatureInference? {
        val inputTensor = tf.getInputTensor(0)
        val inputShape = inputTensor.shape()
        // Modelo actual: [1, 64]. Siempre respetar el tamaño del tensor.
        val window = when {
            inputShape.size >= 3 -> inputShape[1].coerceAtLeast(8)
            inputShape.size == 2 -> inputShape[1].coerceAtLeast(8)
            inputShape.size == 1 -> inputShape[0].coerceAtLeast(8)
            else -> 64
        }
        val input = buildInput(signal, window)

        val outputCount = tf.outputTensorCount
        if (outputCount <= 0) return null
        val outputs = HashMap<Int, Any>(outputCount)
        for (i in 0 until outputCount) {
            val outShape = tf.getOutputTensor(i).shape()
            val outSize = outShape.fold(1) { acc, v -> acc * v.coerceAtLeast(1) }
            outputs[i] = FloatArray(outSize.coerceAtLeast(1))
        }
        tf.runForMultipleInputsOutputs(arrayOf(input), outputs)

        val primary = outputs[0] as? FloatArray ?: return null
        if (primary.isEmpty()) return null

        val signatureNorm = sqrt(primary.fold(0.0) { acc, v -> acc + v * v })
        val breathing = when {
            primary.size >= 3 -> sigmoid(primary[2].toDouble())
            primary.size >= 2 -> sigmoid(primary[1].toDouble())
            else -> sigmoid(primary[0].toDouble())
        }
        val activity = when {
            primary.size >= 2 -> sigmoid(primary[1].toDouble())
            else -> breathing
        }

        return SignatureInference(
            breathingScore = breathing.coerceIn(0.0, 1.0),
            activityScore = activity.coerceIn(0.0, 1.0),
            signatureNorm = signatureNorm,
        )
    }

    private fun buildInput(signal: DoubleArray, window: Int): ByteBuffer {
        val input = ByteBuffer.allocateDirect(window * 4).order(ByteOrder.nativeOrder())
        val start = (signal.size - window).coerceAtLeast(0)
        val slice = signal.copyOfRange(start, signal.size)
        val mean = if (slice.isNotEmpty()) slice.average() else 0.0
        val variance = if (slice.isNotEmpty()) {
            slice.fold(0.0) { acc, v ->
                val d = v - mean
                acc + d * d
            } / slice.size
        } else {
            1.0
        }
        val std = sqrt(variance).coerceAtLeast(1e-4)

        // Rellenar siempre exactamente [window] floats (padding a la izquierda si faltan).
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

    private fun tryLoadModel(context: Context): Boolean {
        return try {
            // Copiar a ByteBuffer propio: cerrar el AssetFileDescriptor no invalida el modelo.
            val bytes = context.assets.open(modelAssetName).use { it.readBytes() }
            if (bytes.isEmpty()) return false
            val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
            buffer.put(bytes)
            buffer.rewind()
            modelBuffer = buffer
            interpreter = Interpreter(buffer)
            true
        } catch (_: Throwable) {
            interpreter = null
            modelBuffer = null
            false
        }
    }

    private fun sigmoid(x: Double): Double = 1.0 / (1.0 + exp(-x))
}
