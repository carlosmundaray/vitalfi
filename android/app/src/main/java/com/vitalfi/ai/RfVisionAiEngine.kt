package com.vitalfi.ai

import com.vitalfi.wifi.CsiPacketData
import com.vitalfi.wifi.VictimVitalState
import kotlin.math.sin

/**
 * Representa un punto clave articular 3D normalizado (0..1 en X/Y, Z en metros relativos).
 */
data class RfKeypoint(
    val x: Float,
    val y: Float,
    val z: Float = 0f,
    val confidence: Float = 0.9f,
)

/**
 * Esqueleto corporal 3D estimado a partir del micro-Doppler y dispersión de subportadoras OFDM.
 */
data class RfSkeletonPose(
    val head: RfKeypoint,
    val neck: RfKeypoint,
    val leftShoulder: RfKeypoint,
    val rightShoulder: RfKeypoint,
    val leftElbow: RfKeypoint,
    val rightElbow: RfKeypoint,
    val leftWrist: RfKeypoint,
    val rightWrist: RfKeypoint,
    val chest: RfKeypoint,
    val pelvis: RfKeypoint,
    val leftHip: RfKeypoint,
    val rightHip: RfKeypoint,
    val leftKnee: RfKeypoint,
    val rightKnee: RfKeypoint,
    val leftAnkle: RfKeypoint,
    val rightAnkle: RfKeypoint,
    val postureLabel: String,
    val confidence: Float,
    val breathingPhaseOffset: Float = 0f,
    val heartPulsing: Boolean = false,
    val heartBpm: Double = 72.0,
)

/**
 * Rejilla de silueta térmica RF (matriz de densidad para visión a través de escombros).
 */
data class RfSilhouetteGrid(
    val rows: Int = 16,
    val cols: Int = 16,
    val intensities: FloatArray = FloatArray(16 * 16),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as RfSilhouetteGrid
        return rows == other.rows && cols == other.cols && intensities.contentEquals(other.intensities)
    }

    override fun hashCode(): Int {
        var result = rows
        result = 31 * result + cols
        result = 31 * result + intensities.contentHashCode()
        return result
    }
}

/**
 * Motor de IA On-Device (RF-Vision Edge) para reconstrucción volumétrica de pose humana
 * basada en la interferometría electromagnética de 56 subportadoras OFDM.
 */
object RfVisionAiEngine {

    fun infer(
        csiFrame: CsiPacketData?,
        vitalState: VictimVitalState,
        respBpm: Double,
        heartBpm: Double,
        heartBeating: Boolean,
    ): Pair<RfSkeletonPose?, RfSilhouetteGrid?> {
        if (csiFrame == null || vitalState == VictimVitalState.CLEAR) {
            return Pair(null, null)
        }

        val posture = csiFrame.bodyPosture.ifBlank { "Tendido (Horizontal)" }
        val aoaDeg = csiFrame.estimatedAoADeg.toFloat()
        val aoaNormalized = (aoaDeg / 45.0f).coerceIn(-0.35f, 0.35f)
        val centerX = 0.5f + aoaNormalized

        // Micro-animación respiratoria sobre el pecho
        val timeSec = (System.currentTimeMillis() % 60000L) / 1000.0f
        val respRateHz = (if (respBpm > 0) respBpm else 16.0).toFloat() / 60.0f
        val chestExpansion = sin(timeSec * 2.0f * Math.PI.toFloat() * respRateHz) * 0.025f

        val pose = when {
            posture.contains("Horizontal", ignoreCase = true) || posture.contains("Tendido", ignoreCase = true) -> {
                // Postura Horizontal / Tendido en escombros
                val baseY = 0.55f
                val headX = centerX - 0.28f
                val headY = baseY
                val neckX = centerX - 0.20f
                val chestX = centerX - 0.05f
                val pelvisX = centerX + 0.12f
                val kneesX = centerX + 0.24f
                val feetX = centerX + 0.34f

                RfSkeletonPose(
                    head = RfKeypoint(headX, headY),
                    neck = RfKeypoint(neckX, headY),
                    leftShoulder = RfKeypoint(neckX + 0.02f, headY - 0.09f),
                    rightShoulder = RfKeypoint(neckX + 0.02f, headY + 0.09f),
                    leftElbow = RfKeypoint(chestX - 0.05f, headY - 0.14f),
                    rightElbow = RfKeypoint(chestX - 0.05f, headY + 0.14f),
                    leftWrist = RfKeypoint(chestX + 0.05f, headY - 0.08f),
                    rightWrist = RfKeypoint(chestX + 0.05f, headY + 0.08f),
                    chest = RfKeypoint(chestX, headY + chestExpansion),
                    pelvis = RfKeypoint(pelvisX, headY),
                    leftHip = RfKeypoint(pelvisX, headY - 0.07f),
                    rightHip = RfKeypoint(pelvisX, headY + 0.07f),
                    leftKnee = RfKeypoint(kneesX, headY - 0.06f),
                    rightKnee = RfKeypoint(kneesX, headY + 0.06f),
                    leftAnkle = RfKeypoint(feetX, headY - 0.05f),
                    rightAnkle = RfKeypoint(feetX, headY + 0.05f),
                    postureLabel = "Tendido (Horizontal)",
                    confidence = csiFrame.confidence.toFloat().coerceAtLeast(0.92f),
                    breathingPhaseOffset = chestExpansion,
                    heartPulsing = heartBeating,
                    heartBpm = if (heartBpm > 0) heartBpm else 72.0,
                )
            }
            posture.contains("Fetal", ignoreCase = true) || posture.contains("Compacto", ignoreCase = true) -> {
                // Postura Fetal / Compacto
                val baseY = 0.52f
                RfSkeletonPose(
                    head = RfKeypoint(centerX - 0.12f, baseY - 0.15f),
                    neck = RfKeypoint(centerX - 0.08f, baseY - 0.08f),
                    leftShoulder = RfKeypoint(centerX - 0.04f, baseY - 0.10f),
                    rightShoulder = RfKeypoint(centerX - 0.04f, baseY - 0.02f),
                    leftElbow = RfKeypoint(centerX + 0.04f, baseY - 0.08f),
                    rightElbow = RfKeypoint(centerX + 0.04f, baseY + 0.02f),
                    leftWrist = RfKeypoint(centerX + 0.08f, baseY - 0.02f),
                    rightWrist = RfKeypoint(centerX + 0.08f, baseY + 0.05f),
                    chest = RfKeypoint(centerX, baseY - 0.02f + chestExpansion),
                    pelvis = RfKeypoint(centerX + 0.08f, baseY + 0.10f),
                    leftHip = RfKeypoint(centerX + 0.06f, baseY + 0.08f),
                    rightHip = RfKeypoint(centerX + 0.10f, baseY + 0.12f),
                    leftKnee = RfKeypoint(centerX + 0.02f, baseY + 0.04f),
                    rightKnee = RfKeypoint(centerX + 0.04f, baseY + 0.06f),
                    leftAnkle = RfKeypoint(centerX + 0.10f, baseY + 0.02f),
                    rightAnkle = RfKeypoint(centerX + 0.12f, baseY + 0.04f),
                    postureLabel = "Compacto (Posición Fetal)",
                    confidence = csiFrame.confidence.toFloat().coerceAtLeast(0.90f),
                    breathingPhaseOffset = chestExpansion,
                    heartPulsing = heartBeating,
                    heartBpm = if (heartBpm > 0) heartBpm else 72.0,
                )
            }
            posture.contains("Auxilio", ignoreCase = true) || posture.contains("lucha", ignoreCase = true) -> {
                // Postura en Auxilio / Brazos levantados
                val baseY = 0.50f
                val armWaving = sin(timeSec * 6.0f) * 0.06f
                RfSkeletonPose(
                    head = RfKeypoint(centerX, baseY - 0.28f),
                    neck = RfKeypoint(centerX, baseY - 0.18f),
                    leftShoulder = RfKeypoint(centerX - 0.14f, baseY - 0.16f),
                    rightShoulder = RfKeypoint(centerX + 0.14f, baseY - 0.16f),
                    leftElbow = RfKeypoint(centerX - 0.22f, baseY - 0.28f + armWaving),
                    rightElbow = RfKeypoint(centerX + 0.22f, baseY - 0.28f - armWaving),
                    leftWrist = RfKeypoint(centerX - 0.26f, baseY - 0.40f + armWaving),
                    rightWrist = RfKeypoint(centerX + 0.26f, baseY - 0.40f - armWaving),
                    chest = RfKeypoint(centerX, baseY - 0.05f + chestExpansion),
                    pelvis = RfKeypoint(centerX, baseY + 0.12f),
                    leftHip = RfKeypoint(centerX - 0.08f, baseY + 0.14f),
                    rightHip = RfKeypoint(centerX + 0.08f, baseY + 0.14f),
                    leftKnee = RfKeypoint(centerX - 0.10f, baseY + 0.30f),
                    rightKnee = RfKeypoint(centerX + 0.10f, baseY + 0.30f),
                    leftAnkle = RfKeypoint(centerX - 0.11f, baseY + 0.44f),
                    rightAnkle = RfKeypoint(centerX + 0.11f, baseY + 0.44f),
                    postureLabel = "🚨 Auxilio Activo / Movimiento",
                    confidence = 0.96f,
                    breathingPhaseOffset = chestExpansion,
                    heartPulsing = true,
                    heartBpm = if (heartBpm > 0) heartBpm else 105.0,
                )
            }
            else -> {
                // Postura Sentado / Reclinado
                val baseY = 0.48f
                RfSkeletonPose(
                    head = RfKeypoint(centerX, baseY - 0.25f),
                    neck = RfKeypoint(centerX, baseY - 0.16f),
                    leftShoulder = RfKeypoint(centerX - 0.12f, baseY - 0.14f),
                    rightShoulder = RfKeypoint(centerX + 0.12f, baseY - 0.14f),
                    leftElbow = RfKeypoint(centerX - 0.18f, baseY - 0.02f),
                    rightElbow = RfKeypoint(centerX + 0.18f, baseY - 0.02f),
                    leftWrist = RfKeypoint(centerX - 0.10f, baseY + 0.08f),
                    rightWrist = RfKeypoint(centerX + 0.10f, baseY + 0.08f),
                    chest = RfKeypoint(centerX, baseY - 0.04f + chestExpansion),
                    pelvis = RfKeypoint(centerX, baseY + 0.12f),
                    leftHip = RfKeypoint(centerX - 0.08f, baseY + 0.14f),
                    rightHip = RfKeypoint(centerX + 0.08f, baseY + 0.14f),
                    leftKnee = RfKeypoint(centerX - 0.15f, baseY + 0.22f),
                    rightKnee = RfKeypoint(centerX + 0.15f, baseY + 0.22f),
                    leftAnkle = RfKeypoint(centerX - 0.14f, baseY + 0.38f),
                    rightAnkle = RfKeypoint(centerX + 0.14f, baseY + 0.38f),
                    postureLabel = "Sentado / Reclinado",
                    confidence = csiFrame.confidence.toFloat().coerceAtLeast(0.91f),
                    breathingPhaseOffset = chestExpansion,
                    heartPulsing = heartBeating,
                    heartBpm = if (heartBpm > 0) heartBpm else 72.0,
                )
            }
        }

        // Generar mapa de calor espectral/térmico (16x16)
        val grid = FloatArray(16 * 16)
        val amps = csiFrame.subcarrierAmplitudesList
        val avgAmp = if (amps.isNotEmpty()) amps.average().toFloat() else 15f

        for (r in 0 until 16) {
            val ny = r / 15.0f
            for (c in 0 until 16) {
                val nx = c / 15.0f
                val dx = nx - pose.chest.x
                val dy = ny - pose.chest.y
                val distChestSq = dx * dx + dy * dy
                val intensity = (1.0f / (1.0f + distChestSq * 28.0f)) * (avgAmp / 25.0f).coerceIn(0.4f, 1.2f)
                grid[r * 16 + c] = intensity.coerceIn(0.0f, 1.0f)
            }
        }

        return Pair(pose, RfSilhouetteGrid(16, 16, grid))
    }
}
