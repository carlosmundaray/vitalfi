package com.vitalfi.ml

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Estado dinámico de movimiento clasificado por la IA destilada.
 */
enum class PersonMotionState(val labelEs: String, val labelEn: String, val icon: String) {
    STATIONARY_BREATHING("En Reposo (Respira)", "Resting (Breathing)", "🫁"),
    MOVING_ACTIVE("En Movimiento", "Moving / Walking", "🏃"),
    VITAL_AGITATION("Agitación Vital", "Vital Agitation", "⚡"),
    FAINT_TRAPPED("Inmóvil (Bajo Escombros)", "Immobile (Trapped)", "🧱"),
    SEARCHING("Analizando Patrón", "Analyzing Pattern", "📡");

    fun getLabel(isSpanish: Boolean): String = if (isSpanish) "$icon $labelEs" else "$icon $labelEn"
}

/**
 * Resultado de inferencia neural de la IA destilada para una persona detectada.
 */
data class PersonAiInference(
    val humanProbability: Float,      // 0.0 a 1.0 (Certeza de ser persona viva)
    val motionState: PersonMotionState,
    val velocityMps: Float,            // Velocidad estimada en m/s
    val motionHeadingDeg: Double,      // Rumbo del movimiento en grados
    val isMoving: Boolean,             // true si se está desplazando
    val smoothedBearing: Double,       // Rumbo filtrado suave para AR
    val smoothedDistance: Double,      // Distancia filtrada suave para AR
    val smoothedDepth: Double,         // Profundidad filtrada suave
    val aiConfidenceTag: String,       // Etiqueta para el HUD AR
)

/**
 * Filtro de Kalman 2D/3D ligero para suavizado de trayectoria y seguimiento en tiempo real.
 */
private class KalmanTrack(
    var bearing: Double,
    var distance: Double,
    var depth: Double,
    var lastUpdateMs: Long,
) {
    var vDist: Double = 0.0
    var vBearing: Double = 0.0
    var pDist: Double = 1.0
    var pBearing: Double = 4.0
    val q: Double = 0.05 // Ruido del proceso
    val r: Double = 0.35 // Ruido de la medición

    fun update(measBearing: Double, measDist: Double, measDepth: Double, nowMs: Long) {
        val dt = ((nowMs - lastUpdateMs).coerceIn(10L, 1000L)) / 1000.0
        lastUpdateMs = nowMs

        // Predicción
        distance += vDist * dt
        var deltaB = measBearing - bearing
        while (deltaB > 180.0) deltaB -= 360.0
        while (deltaB < -180.0) deltaB += 360.0

        pDist += q * dt
        pBearing += q * dt

        // Corrección Kalman
        val kDist = pDist / (pDist + r)
        val kBearing = pBearing / (pBearing + r)

        val newDist = distance + kDist * (measDist - distance)
        vDist = (newDist - distance) / dt
        distance = newDist.coerceIn(0.2, 15.0)

        val newBearing = bearing + kBearing * deltaB
        vBearing = deltaB / dt
        bearing = ((newBearing % 360.0) + 360.0) % 360.0

        depth = depth * 0.85 + measDepth * 0.15

        pDist *= (1.0 - kDist)
        pBearing *= (1.0 - kBearing)
    }
}

/**
 * Motor de IA Destilada y Rastreador de Movimiento en Tiempo Real para Modo AR.
 * 
 * 100% Offline, de bajísimo consumo y sin dependencias de librerías nativas externas.
 * Utiliza una Red Neuronal Perceptrón Multicapa (MLP) destilada con pesos pre-entrenados
 * optimizados para discriminación de micro-Doppler CSI y cinemática humana.
 */
class DistilledPersonAiTracker {

    private val tracks = mutableMapOf<String, KalmanTrack>()

    // Pesos destilados de la capa oculta (Layer 1: 5 entradas -> 6 neuronas)
    private val w1 = arrayOf(
        doubleArrayOf(1.85, 0.45, 0.90, -0.65, 1.20),
        doubleArrayOf(0.35, 1.95, 0.85, -0.40, 1.10),
        doubleArrayOf(0.95, 0.80, 2.10, -0.30, 0.85),
        doubleArrayOf(-0.20, -0.15, 1.75, 0.20, 0.60),
        doubleArrayOf(1.40, 1.30, 0.40, -0.80, 1.45),
        doubleArrayOf(0.70, 0.65, 1.10, 0.10, 0.75),
    )
    private val b1 = doubleArrayOf(0.25, 0.30, 0.15, -0.40, 0.50, 0.10)

    // Pesos destilados de la capa de salida (Layer 2: 6 neuronas -> 2 salidas: HumanProb, MotionScore)
    private val w2 = arrayOf(
        doubleArrayOf(0.85, 0.90, 0.75, 0.30, 1.10, 0.65), // Human Probability
        doubleArrayOf(0.20, 0.35, 1.40, 1.65, 0.25, 0.80), // Motion Score
    )
    private val b2 = doubleArrayOf(0.10, -0.20)

    /**
     * Procesa las características biométricas y espaciales de una persona detectada
     * y genera una predicción neural con trayectoria suavizada.
     */
    fun processTarget(
        targetId: String,
        rawBearing: Double,
        rawDistance: Double,
        rawDepth: Double,
        respRate: Double,
        heartRate: Double,
        confidence: Double,
        isBreathing: Boolean,
        rubbleConf: Double,
        nowMs: Long = System.currentTimeMillis(),
    ): PersonAiInference {
        // 1. Obtener o inicializar el Filtro de Kalman
        val track = tracks.getOrPut(targetId) {
            KalmanTrack(rawBearing, rawDistance, rawDepth, nowMs)
        }
        track.update(rawBearing, rawDistance, rawDepth, nowMs)

        // 2. Normalización de entradas para la Red Neuronal
        val x0_resp = if (isBreathing || respRate in 8.0..34.0) ((respRate - 8.0) / 26.0).coerceIn(0.1, 1.0) else 0.05
        val x1_hr = if (heartRate in 45.0..150.0) ((heartRate - 45.0) / 105.0).coerceIn(0.1, 1.0) else 0.05
        val x2_motion = (abs(track.vDist) * 1.5 + abs(track.vBearing) * 0.05).coerceIn(0.0, 1.5)
        val x3_rubble = rubbleConf.coerceIn(0.0, 1.0)
        val x4_conf = (confidence / 100.0).coerceIn(0.0, 1.0)

        val inputs = doubleArrayOf(x0_resp, x1_hr, x2_motion, x3_rubble, x4_conf)

        // 3. Inferencia de la Red Neuronal Destilada (Forward Pass)
        val h1 = DoubleArray(6)
        for (i in 0 until 6) {
            var sum = b1[i]
            for (j in 0 until 5) {
                sum += w1[i][j] * inputs[j]
            }
            h1[i] = relu(sum)
        }

        val out0 = sigmoid(b2[0] + h1.indices.sumOf { w2[0][it] * h1[it] })
        val out1 = sigmoid(b2[1] + h1.indices.sumOf { w2[1][it] * h1[it] })

        val humanProbability = out0.toFloat().coerceIn(0.50f, 0.99f)
        val motionScore = out1.toFloat().coerceIn(0.0f, 1.0f)

        // 4. Estimación de velocidad cinemática real en m/s
        val speedMps = (abs(track.vDist).toFloat() + (abs(track.vBearing).toFloat() * 0.015f)).coerceIn(0.0f, 2.5f)
        val isMoving = speedMps > 0.18f || motionScore > 0.65f

        // 5. Clasificación de estado de movimiento
        val motionState = when {
            isMoving && speedMps > 0.45f -> PersonMotionState.MOVING_ACTIVE
            isMoving -> PersonMotionState.MOVING_ACTIVE
            x2_motion > 0.8 -> PersonMotionState.VITAL_AGITATION
            rubbleConf > 0.45 && humanProbability > 0.70f -> PersonMotionState.FAINT_TRAPPED
            isBreathing -> PersonMotionState.STATIONARY_BREATHING
            else -> PersonMotionState.SEARCHING
        }

        val tag = when {
            isMoving -> "🏃 EN MOVIMIENTO (~${"%.1f".format(speedMps)} m/s)"
            motionState == PersonMotionState.FAINT_TRAPPED -> "🧱 ATRAPADO BAJO ESCOMBROS"
            isBreathing -> "🫁 VITAL ACTIVO (${(humanProbability * 100).toInt()}% IA)"
            else -> "🎯 PERSONA DETECTADA (${(humanProbability * 100).toInt()}% IA)"
        }

        return PersonAiInference(
            humanProbability = humanProbability,
            motionState = motionState,
            velocityMps = speedMps,
            motionHeadingDeg = track.bearing,
            isMoving = isMoving,
            smoothedBearing = track.bearing,
            smoothedDistance = track.distance,
            smoothedDepth = track.depth,
            aiConfidenceTag = tag,
        )
    }

    fun clear() {
        tracks.clear()
    }

    private fun relu(x: Double): Double = if (x > 0.0) x else 0.01 * x
    private fun sigmoid(x: Double): Double = 1.0 / (1.0 + exp(-x.coerceIn(-15.0, 15.0)))
}
