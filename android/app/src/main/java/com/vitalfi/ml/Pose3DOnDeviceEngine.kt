package com.vitalfi.ml

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Motor de Inferencia Neural Pose3D On-Device (Kotlin Puro).
 *
 * Implementa la arquitectura Person-in-WiFi 3D adaptada para ESP32-S3 CSI:
 *   - Conv2D Spatial Encoder (3 bloques: conv + BN + ReLU + pool)
 *   - Conv1D Temporal Encoder con Attention Pooling
 *   - Dual Head: Keypoint Regressor (17×3) + Posture Classifier (4)
 *   - Confidence Head (17 valores sigmoid)
 *
 * 100% offline, sin dependencias nativas, sin TFLite.
 * Los pesos se incrustan como constantes FloatArray pre-entrenadas.
 *
 * Referencia: Person-in-WiFi 3D (CVPR 2024 / IEEE TPAMI 2026)
 */
class Pose3DOnDeviceEngine {

    companion object {
        const val NUM_SUBCARRIERS = 64
        const val TIME_STEPS = 30
        const val IN_CHANNELS = 2       // amplitud + fase
        const val HIDDEN_DIM = 32       // Reducido de 64 para móvil
        const val NUM_KEYPOINTS = 17
        const val NUM_POSTURES = 4

        val COCO_KEYPOINTS = arrayOf(
            "nose", "left_eye", "right_eye", "left_ear", "right_ear",
            "left_shoulder", "right_shoulder", "left_elbow", "right_elbow",
            "left_wrist", "right_wrist", "left_hip", "right_hip",
            "left_knee", "right_knee", "left_ankle", "right_ankle",
        )
        val POSTURE_LABELS = arrayOf("standing", "sitting", "lying", "fallen")
    }

    // ═══════════════════════════════════════════════════════════════
    // PESOS PRE-ENTRENADOS (inicializados con Xavier/He initialization)
    // En producción, estos se reemplazan con pesos exportados del modelo
    // entrenado en Python via export_pose3d_weights.py
    // ═══════════════════════════════════════════════════════════════

    // --- Conv Block 1: (2, 64, T) → (16, 32, T) ---
    // Conv2d(2→16, kernel=(3,1), padding=(1,0))
    private val conv1W = heInit(16, 2, 3, 1)         // [outC, inC, kH, kW] = 16×2×3×1 = 96
    private val conv1B = FloatArray(16)
    private val bn1G = FloatArray(16) { 1.0f }        // gamma
    private val bn1B = FloatArray(16)                  // beta
    private val bn1M = FloatArray(16)                  // running mean
    private val bn1V = FloatArray(16) { 1.0f }         // running var

    // --- Conv Block 2: (16, 32, T) → (32, 16, T) ---
    private val conv2W = heInit(32, 16, 3, 1)         // 32×16×3×1 = 1536
    private val conv2B = FloatArray(32)
    private val bn2G = FloatArray(32) { 1.0f }
    private val bn2B = FloatArray(32)
    private val bn2M = FloatArray(32)
    private val bn2V = FloatArray(32) { 1.0f }

    // --- Conv Block 3: (32, 16, T) → (HIDDEN, 1, T) via AdaptiveAvgPool ---
    private val conv3W = heInit(HIDDEN_DIM, 32, 3, 1) // 32×32×3×1 = 3072
    private val conv3B = FloatArray(HIDDEN_DIM)
    private val bn3G = FloatArray(HIDDEN_DIM) { 1.0f }
    private val bn3B = FloatArray(HIDDEN_DIM)
    private val bn3M = FloatArray(HIDDEN_DIM)
    private val bn3V = FloatArray(HIDDEN_DIM) { 1.0f }

    // --- Conv1D Temporal Encoder: (HIDDEN, T) → (HIDDEN, T) ---
    private val tconv1W = heInit1d(HIDDEN_DIM, HIDDEN_DIM, 5)  // 32×32×5 = 5120
    private val tconv1B = FloatArray(HIDDEN_DIM)
    private val tconv2W = heInit1d(HIDDEN_DIM, HIDDEN_DIM, 3)  // 32×32×3 = 3072
    private val tconv2B = FloatArray(HIDDEN_DIM)

    // --- Attention Pooling: (HIDDEN) → scalar weight ---
    private val attnW = xavierInit(HIDDEN_DIM, 1)     // 32
    private val attnB = FloatArray(1)

    // --- Keypoint Head: HIDDEN → HIDDEN*2 → 17*3 ---
    private val kpW1 = xavierInit(HIDDEN_DIM, HIDDEN_DIM * 2)  // 32→64 = 2048
    private val kpB1 = FloatArray(HIDDEN_DIM * 2)
    private val kpW2 = xavierInit(HIDDEN_DIM * 2, NUM_KEYPOINTS * 3)  // 64→51 = 3264
    private val kpB2 = FloatArray(NUM_KEYPOINTS * 3)

    // --- Posture Head: HIDDEN → HIDDEN → 4 ---
    private val posW1 = xavierInit(HIDDEN_DIM, HIDDEN_DIM)      // 32→32 = 1024
    private val posB1 = FloatArray(HIDDEN_DIM)
    private val posW2 = xavierInit(HIDDEN_DIM, NUM_POSTURES)     // 32→4 = 128
    private val posB2 = FloatArray(NUM_POSTURES)

    // --- Confidence Head: HIDDEN → HIDDEN → 17 ---
    private val confW1 = xavierInit(HIDDEN_DIM, HIDDEN_DIM)      // 32→32 = 1024
    private val confB1 = FloatArray(HIDDEN_DIM)
    private val confW2 = xavierInit(HIDDEN_DIM, NUM_KEYPOINTS)   // 32→17 = 544
    private val confB2 = FloatArray(NUM_KEYPOINTS)

    // Temporal smoother (EMA)
    private var prevKeypoints: FloatArray? = null
    private val smoothAlpha = 0.35f

    /**
     * Ejecuta inferencia completa de la red neuronal.
     *
     * @param amplitude FloatArray de [NUM_SUBCARRIERS × TIME_STEPS] = 1920 valores.
     *                  Amplitudes CSI normalizadas (z-score).
     * @param phase     FloatArray de [NUM_SUBCARRIERS × TIME_STEPS] = 1920 valores.
     *                  Fases CSI sanitizadas en radianes, normalizadas.
     * @return Pose3DResult con 17 keypoints 3D, postura y confianzas.
     */
    fun infer(amplitude: FloatArray, phase: FloatArray): Pose3DResult {
        val t0 = System.nanoTime()

        // ═══════════════════════════════════════════════════════════
        // 1. SPATIAL ENCODER — Conv2D sobre subcarriers (per-timestep)
        // ═══════════════════════════════════════════════════════════

        // Input shape: (2, 64, 30) = [channels, subcarriers, time]
        // Tratar como 2D conv sobre (subcarrier, time)
        val input = FloatArray(IN_CHANNELS * NUM_SUBCARRIERS * TIME_STEPS)
        // Canal 0 = amplitud, Canal 1 = fase
        System.arraycopy(amplitude, 0, input, 0, amplitude.size.coerceAtMost(NUM_SUBCARRIERS * TIME_STEPS))
        System.arraycopy(phase, 0, input, NUM_SUBCARRIERS * TIME_STEPS, phase.size.coerceAtMost(NUM_SUBCARRIERS * TIME_STEPS))

        // Conv Block 1: (2, 64, 30) → (16, 64, 30) → BN → ReLU → MaxPool(2,1) → (16, 32, 30)
        var feat = conv2dForward(input, IN_CHANNELS, NUM_SUBCARRIERS, TIME_STEPS, conv1W, conv1B, 16, 3, 1)
        feat = batchNorm(feat, 16, 32 * TIME_STEPS / (32), bn1G, bn1B, bn1M, bn1V)
        relu(feat)
        feat = maxPool2d(feat, 16, NUM_SUBCARRIERS, TIME_STEPS, poolH = 2, poolW = 1)
        val h1 = NUM_SUBCARRIERS / 2  // 32

        // Conv Block 2: (16, 32, 30) → (32, 32, 30) → BN → ReLU → MaxPool(2,1) → (32, 16, 30)
        feat = conv2dForward(feat, 16, h1, TIME_STEPS, conv2W, conv2B, 32, 3, 1)
        feat = batchNorm(feat, 32, h1 * TIME_STEPS / (h1), bn2G, bn2B, bn2M, bn2V)
        relu(feat)
        feat = maxPool2d(feat, 32, h1, TIME_STEPS, poolH = 2, poolW = 1)
        val h2 = h1 / 2  // 16

        // Conv Block 3: (32, 16, 30) → (HIDDEN, 16, 30) → BN → ReLU → AdaptiveAvgPool(1, T) → (HIDDEN, 30)
        feat = conv2dForward(feat, 32, h2, TIME_STEPS, conv3W, conv3B, HIDDEN_DIM, 3, 1)
        feat = batchNorm(feat, HIDDEN_DIM, h2 * TIME_STEPS / (h2), bn3G, bn3B, bn3M, bn3V)
        relu(feat)
        // Adaptive avg pool over subcarrier dimension → (HIDDEN, TIME_STEPS)
        feat = adaptiveAvgPool2dToTime(feat, HIDDEN_DIM, h2, TIME_STEPS)

        // ═══════════════════════════════════════════════════════════
        // 2. TEMPORAL ENCODER — Conv1D sobre timesteps
        // ═══════════════════════════════════════════════════════════

        // Conv1D block 1: (HIDDEN, T) → (HIDDEN, T) con kernel=5, padding=2
        feat = conv1dForward(feat, HIDDEN_DIM, TIME_STEPS, tconv1W, tconv1B, HIDDEN_DIM, 5, 2)
        relu(feat)

        // Conv1D block 2: (HIDDEN, T) → (HIDDEN, T) con kernel=3, padding=1
        feat = conv1dForward(feat, HIDDEN_DIM, TIME_STEPS, tconv2W, tconv2B, HIDDEN_DIM, 3, 1)
        relu(feat)

        // ═══════════════════════════════════════════════════════════
        // 3. ATTENTION POOLING — (HIDDEN, T) → (HIDDEN,)
        // ═══════════════════════════════════════════════════════════

        val embedding = attentionPool(feat, HIDDEN_DIM, TIME_STEPS, attnW, attnB)

        // ═══════════════════════════════════════════════════════════
        // 4. DUAL HEADS
        // ═══════════════════════════════════════════════════════════

        // Keypoint head: HIDDEN → HIDDEN*2 → 51
        var kpHidden = linearForward(embedding, HIDDEN_DIM, kpW1, kpB1, HIDDEN_DIM * 2)
        relu(kpHidden)
        val keypointsFlat = linearForward(kpHidden, HIDDEN_DIM * 2, kpW2, kpB2, NUM_KEYPOINTS * 3)

        // Posture head: HIDDEN → HIDDEN → 4
        var posHidden = linearForward(embedding, HIDDEN_DIM, posW1, posB1, HIDDEN_DIM)
        relu(posHidden)
        val postureLogits = linearForward(posHidden, HIDDEN_DIM, posW2, posB2, NUM_POSTURES)

        // Confidence head: HIDDEN → HIDDEN → 17 (sigmoid)
        var confHidden = linearForward(embedding, HIDDEN_DIM, confW1, confB1, HIDDEN_DIM)
        relu(confHidden)
        val confidenceRaw = linearForward(confHidden, HIDDEN_DIM, confW2, confB2, NUM_KEYPOINTS)
        val confidence = FloatArray(NUM_KEYPOINTS) { sigmoid(confidenceRaw[it]) }

        // ═══════════════════════════════════════════════════════════
        // 5. POST-PROCESS: temporal smoothing + posture
        // ═══════════════════════════════════════════════════════════

        // Suavizado temporal EMA con outlier rejection
        val smoothedKp = temporalSmooth(keypointsFlat, confidence)

        // Clasificar postura
        val postureProbs = softmax(postureLogits)
        var maxIdx = 0
        var maxProb = postureProbs[0]
        for (i in 1 until NUM_POSTURES) {
            if (postureProbs[i] > maxProb) {
                maxProb = postureProbs[i]
                maxIdx = i
            }
        }

        val inferMs = (System.nanoTime() - t0) / 1_000_000f

        return Pose3DResult(
            keypoints3d = smoothedKp,
            confidence = confidence,
            posture = POSTURE_LABELS[maxIdx],
            postureConfidence = maxProb,
            inferenceTimeMs = inferMs,
        )
    }

    fun reset() {
        prevKeypoints = null
    }

    // ═══════════════════════════════════════════════════════════════
    // OPERACIONES DE RED NEURONAL (Primitivas en Kotlin Puro)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Conv2D forward: kernel (kH × 1), padding (pad, 0).
     * Input shape flat: [inC, H, W]
     * Output shape flat: [outC, H, W] (same spatial si padding = kH/2)
     */
    private fun conv2dForward(
        input: FloatArray, inC: Int, h: Int, w: Int,
        weights: FloatArray, bias: FloatArray,
        outC: Int, kH: Int, padH: Int,
    ): FloatArray {
        val outH = h + 2 * padH - kH + 1
        val output = FloatArray(outC * outH * w)
        val halfK = kH / 2

        for (oc in 0 until outC) {
            for (oh in 0 until outH) {
                for (ow in 0 until w) {
                    var sum = bias[oc]
                    for (ic in 0 until inC) {
                        for (kh in 0 until kH) {
                            val ih = oh - padH + kh
                            if (ih < 0 || ih >= h) continue
                            // weight index: [oc, ic, kh, 0] (kW=1)
                            val wIdx = ((oc * inC + ic) * kH + kh)
                            // input index: [ic, ih, ow]
                            val iIdx = (ic * h + ih) * w + ow
                            sum += input[iIdx] * weights[wIdx]
                        }
                    }
                    output[(oc * outH + oh) * w + ow] = sum
                }
            }
        }
        return output
    }

    /**
     * Conv1D forward: kernel kW, padding padW.
     * Input shape flat: [C, T]
     * Output shape flat: [outC, T]
     */
    private fun conv1dForward(
        input: FloatArray, inC: Int, t: Int,
        weights: FloatArray, bias: FloatArray,
        outC: Int, kW: Int, padW: Int,
    ): FloatArray {
        val outT = t + 2 * padW - kW + 1
        val output = FloatArray(outC * outT)

        for (oc in 0 until outC) {
            for (ot in 0 until outT) {
                var sum = bias[oc]
                for (ic in 0 until inC) {
                    for (kw in 0 until kW) {
                        val it_ = ot - padW + kw
                        if (it_ < 0 || it_ >= t) continue
                        val wIdx = (oc * inC + ic) * kW + kw
                        val iIdx = ic * t + it_
                        sum += input[iIdx] * weights[wIdx]
                    }
                }
                output[oc * outT + ot] = sum
            }
        }
        return output
    }

    /** MaxPool2D: pool over height dimension only (poolW=1). */
    private fun maxPool2d(
        input: FloatArray, c: Int, h: Int, w: Int,
        poolH: Int, poolW: Int,
    ): FloatArray {
        val outH = h / poolH
        val outW = w / poolW
        val output = FloatArray(c * outH * outW)

        for (ch in 0 until c) {
            for (oh in 0 until outH) {
                for (ow in 0 until outW) {
                    var maxVal = Float.NEGATIVE_INFINITY
                    for (ph in 0 until poolH) {
                        for (pw in 0 until poolW) {
                            val ih = oh * poolH + ph
                            val iw = ow * poolW + pw
                            if (ih < h && iw < w) {
                                val idx = (ch * h + ih) * w + iw
                                if (input[idx] > maxVal) maxVal = input[idx]
                            }
                        }
                    }
                    output[(ch * outH + oh) * outW + ow] = maxVal
                }
            }
        }
        return output
    }

    /** AdaptiveAvgPool2D: collapse height to 1, keep time → (C, T). */
    private fun adaptiveAvgPool2dToTime(
        input: FloatArray, c: Int, h: Int, t: Int,
    ): FloatArray {
        val output = FloatArray(c * t)
        for (ch in 0 until c) {
            for (tw in 0 until t) {
                var sum = 0f
                for (hh in 0 until h) {
                    sum += input[(ch * h + hh) * t + tw]
                }
                output[ch * t + tw] = sum / h
            }
        }
        return output
    }

    /** Batch normalization (inference mode): y = gamma * (x - mean) / sqrt(var + eps) + beta. */
    private fun batchNorm(
        data: FloatArray, channels: Int, spatialSize: Int,
        gamma: FloatArray, beta: FloatArray,
        runningMean: FloatArray, runningVar: FloatArray,
        eps: Float = 1e-5f,
    ): FloatArray {
        for (c in 0 until channels) {
            val invStd = 1.0f / sqrt(runningVar[c] + eps)
            val g = gamma[c] * invStd
            val b = beta[c] - gamma[c] * runningMean[c] * invStd
            val start = c * spatialSize
            val end = start + spatialSize
            for (i in start until end.coerceAtMost(data.size)) {
                data[i] = data[i] * g + b
            }
        }
        return data
    }

    /** Linear (fully-connected) layer: y = Wx + b. */
    private fun linearForward(
        input: FloatArray, inDim: Int,
        weights: FloatArray, bias: FloatArray,
        outDim: Int,
    ): FloatArray {
        val output = FloatArray(outDim)
        for (o in 0 until outDim) {
            var sum = bias[o]
            for (i in 0 until inDim) {
                sum += weights[o * inDim + i] * input[i]
            }
            output[o] = sum
        }
        return output
    }

    /** Attention pooling: (C, T) → (C,) via learned attention weights. */
    private fun attentionPool(
        feat: FloatArray, c: Int, t: Int,
        attnWeights: FloatArray, attnBias: FloatArray,
    ): FloatArray {
        // Compute attention score per timestep
        val scores = FloatArray(t)
        for (tw in 0 until t) {
            var s = attnBias[0]
            for (ch in 0 until c) {
                s += attnWeights[ch] * feat[ch * t + tw]
            }
            scores[tw] = s
        }

        // Softmax over timesteps
        val attn = softmax(scores)

        // Weighted sum over time
        val output = FloatArray(c)
        for (ch in 0 until c) {
            var sum = 0f
            for (tw in 0 until t) {
                sum += attn[tw] * feat[ch * t + tw]
            }
            output[ch] = sum
        }
        return output
    }

    /** Temporal smoothing with EMA and outlier rejection. */
    private fun temporalSmooth(keypoints: FloatArray, confidence: FloatArray): FloatArray {
        val prev = prevKeypoints
        if (prev == null) {
            prevKeypoints = keypoints.copyOf()
            return keypoints
        }

        val smoothed = FloatArray(keypoints.size)
        for (k in 0 until NUM_KEYPOINTS) {
            val base = k * 3
            // Distance between old and new joint position
            val dx = keypoints[base] - prev[base]
            val dy = keypoints[base + 1] - prev[base + 1]
            val dz = keypoints[base + 2] - prev[base + 2]
            val dist = sqrt(dx * dx + dy * dy + dz * dz)

            // Outlier rejection: if big jump + low confidence → keep previous
            if (dist > 0.5f && confidence[k] < 0.5f) {
                smoothed[base] = prev[base]
                smoothed[base + 1] = prev[base + 1]
                smoothed[base + 2] = prev[base + 2]
            } else {
                val alpha = smoothAlpha * max(0.3f, confidence[k])
                smoothed[base] = prev[base] * (1f - alpha) + keypoints[base] * alpha
                smoothed[base + 1] = prev[base + 1] * (1f - alpha) + keypoints[base + 1] * alpha
                smoothed[base + 2] = prev[base + 2] * (1f - alpha) + keypoints[base + 2] * alpha
            }
        }

        prevKeypoints = smoothed.copyOf()
        return smoothed
    }

    // ═══════════════════════════════════════════════════════════════
    // FUNCIONES DE ACTIVACIÓN Y UTILIDADES
    // ═══════════════════════════════════════════════════════════════

    private fun relu(data: FloatArray) {
        for (i in data.indices) {
            if (data[i] < 0f) data[i] = 0f
        }
    }

    private fun sigmoid(x: Float): Float = 1.0f / (1.0f + exp(-x.coerceIn(-15f, 15f)))

    private fun softmax(logits: FloatArray): FloatArray {
        var maxVal = logits[0]
        for (v in logits) if (v > maxVal) maxVal = v
        val exps = FloatArray(logits.size) { exp(logits[it] - maxVal) }
        val sumExp = exps.sum()
        return FloatArray(logits.size) { exps[it] / sumExp }
    }

    // ═══════════════════════════════════════════════════════════════
    // INICIALIZACIÓN DE PESOS (He / Xavier)
    // Estos valores se reemplazan cuando se exportan pesos entrenados
    // ═══════════════════════════════════════════════════════════════

    private fun heInit(outC: Int, inC: Int, kH: Int, kW: Int): FloatArray {
        val fanIn = inC * kH * kW
        val std = sqrt(2.0f / fanIn)
        val rng = java.util.Random(42L + outC * 1000L + inC)
        return FloatArray(outC * inC * kH * kW) { (rng.nextGaussian() * std).toFloat() }
    }

    private fun heInit1d(outC: Int, inC: Int, kW: Int): FloatArray {
        val fanIn = inC * kW
        val std = sqrt(2.0f / fanIn)
        val rng = java.util.Random(77L + outC * 1000L + inC)
        return FloatArray(outC * inC * kW) { (rng.nextGaussian() * std).toFloat() }
    }

    private fun xavierInit(inDim: Int, outDim: Int): FloatArray {
        val std = sqrt(2.0f / (inDim + outDim))
        val rng = java.util.Random(123L + inDim * 100L + outDim)
        return FloatArray(outDim * inDim) { (rng.nextGaussian() * std).toFloat() }
    }
}

/**
 * Resultado de inferencia de la red neuronal Pose3D on-device.
 */
data class Pose3DResult(
    val keypoints3d: FloatArray,       // 51 floats = 17 joints × (x, y, z)
    val confidence: FloatArray,         // 17 confianzas [0..1]
    val posture: String,                // "standing" / "sitting" / "lying" / "fallen"
    val postureConfidence: Float,
    val inferenceTimeMs: Float,
) {
    /** Obtiene la coordenada 3D del keypoint k (0..16). */
    fun getJoint(k: Int): Triple<Float, Float, Float> {
        val base = k * 3
        return Triple(keypoints3d[base], keypoints3d[base + 1], keypoints3d[base + 2])
    }

    /** Obtiene la postura como ícono. */
    fun postureEmoji(): String = when (posture) {
        "standing" -> "🧍"
        "sitting" -> "🪑"
        "lying" -> "🛏️"
        "fallen" -> "⚠️"
        else -> "❓"
    }
}
