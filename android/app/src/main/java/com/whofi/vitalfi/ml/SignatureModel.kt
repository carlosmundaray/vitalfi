package com.whofi.vitalfi.ml

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
    private var interpreter: Interpreter? = null
    val isReady: Boolean
    val statusLabel: String

    init {
        val loaded = tryLoadModel(context)
        isReady = loaded
        statusLabel = if (loaded) "ML activo (TFLite)" else "ML desactivado (sin modelo)"
    }

    fun infer(signal: DoubleArray): SignatureInference? {
        val tf = interpreter ?: return null
        if (signal.size < 8) return null

        val inputTensor = tf.getInputTensor(0)
        val inputShape = inputTensor.shape()
        val window = when {
            inputShape.size >= 3 -> inputShape[1].coerceAtLeast(8)
            signal.size >= 64 -> 64
            else -> signal.size
        }
        val input = buildInput(signal, window)

        val outputCount = tf.outputTensorCount
        if (outputCount <= 0) return null
        val outputs = HashMap<Int, Any>(outputCount)
        for (i in 0 until outputCount) {
            val outShape = tf.getOutputTensor(i).shape()
            val outSize = outShape.fold(1) { acc, v -> acc * v.coerceAtLeast(1) }
            outputs[i] = FloatArray(outSize)
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

        val padded = if (slice.size == window) {
            slice
        } else {
            DoubleArray(window).also { out ->
                val offset = window - slice.size
                for (i in slice.indices) out[offset + i] = slice[i]
            }
        }

        for (v in padded) {
            val z = ((v - mean) / std).toFloat()
            input.putFloat(z)
        }
        input.rewind()
        return input
    }

    private fun tryLoadModel(context: Context): Boolean {
        return try {
            context.assets.openFd(modelAssetName).use { afd ->
                afd.createInputStream().channel.use { channel ->
                    val mapped = channel.map(
                        java.nio.channels.FileChannel.MapMode.READ_ONLY,
                        afd.startOffset,
                        afd.declaredLength,
                    )
                    interpreter = Interpreter(mapped)
                }
            }
            true
        } catch (_: Exception) {
            interpreter = null
            false
        }
    }

    private fun sigmoid(x: Double): Double = 1.0 / (1.0 + exp(-x))
}
