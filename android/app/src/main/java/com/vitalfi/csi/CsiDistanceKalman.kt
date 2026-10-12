package com.vitalfi.csi

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Filtro de Kalman 1D especializado para suavizar la distancia estimada por CSI.
 *
 * A diferencia del KalmanTrack en DistilledPersonAiTracker (que filtra posición angular),
 * este filtro está calibrado específicamente para el dominio de distancia RF con CSI:
 * - Ruido de medición alto (multipath, interferencia)
 * - Actualizaciones rápidas (~20-30 Hz desde el router)
 * - Necesidad de detectar y rechazar saltos grandes (outliers por multipath)
 *
 * Estado: [distancia, velocidad_de_cambio]
 * Medición: distancia fusionada de los 3 estimadores
 */
class CsiDistanceKalman(
    /** Distancia inicial estimada (metros). */
    initialDistance: Double = 3.0,
    /** Ruido del proceso (qué tan rápido esperamos que cambie la distancia real). */
    private val processNoise: Double = 0.08,
    /** Ruido de medición base (incertidumbre de cada medición CSI). */
    private val baseMeasurementNoise: Double = 0.60,
) {
    // Estado: [distancia, velocidad]
    private var x = initialDistance   // Distancia estimada (m)
    private var v = 0.0               // Velocidad de cambio (m/s)

    // Covarianza del estado 2x2 (diagonal simplificada)
    private var pDist = 2.0           // Incertidumbre de distancia
    private var pVel = 1.0            // Incertidumbre de velocidad

    // Timestamp del último update
    private var lastUpdateMs = 0L

    // Historial para detección de saltos
    private val recentMeasurements = ArrayDeque<Double>()
    private val maxHistory = 15

    // Contadores de estado
    private var totalUpdates = 0
    private var outlierCount = 0

    /** Distancia filtrada actual (metros). */
    val filteredDistance: Double get() = x.coerceIn(0.1, 15.0)

    /** Velocidad de cambio estimada (m/s, positivo = alejándose). */
    val velocity: Double get() = v

    /** Incertidumbre actual (σ en metros). */
    val uncertainty: Double get() = sqrt(pDist).coerceIn(0.01, 5.0)

    /** True si el filtro está convergido (suficientes updates). */
    val isConverged: Boolean get() = totalUpdates >= 8

    /**
     * Actualiza el filtro con una nueva medición de distancia.
     *
     * @param measurement Distancia medida (fusión de MUSIC + PhaseSlope + RSSI).
     * @param confidence Confianza de la medición (0.0 - 1.0). Baja confianza = más peso al modelo.
     * @param nowMs Timestamp actual en milisegundos.
     * @return Distancia filtrada suavizada.
     */
    fun update(measurement: Double, confidence: Double, nowMs: Long): Double {
        val dt = if (lastUpdateMs > 0) {
            ((nowMs - lastUpdateMs).coerceIn(5, 2000)) / 1000.0
        } else {
            0.033 // ~30 Hz default
        }
        lastUpdateMs = nowMs
        totalUpdates++

        // ─── Predicción ───
        // x_pred = x + v * dt
        val xPred = (x + v * dt).coerceIn(0.05, 20.0)

        // P_pred = P + Q * dt
        pDist += processNoise * dt + abs(v) * dt * 0.5
        pVel += processNoise * dt * 2.0

        // ─── Detección de outliers ───
        val innovation = measurement - xPred
        val innovationSigma = sqrt(pDist + baseMeasurementNoise)
        val isOutlier = abs(innovation) > innovationSigma * 3.5

        if (isOutlier && totalUpdates > 10) {
            outlierCount++
            // Si hay muchos outliers consecutivos, puede ser un cambio real de escenario
            if (outlierCount >= 5) {
                // Re-inicializar: el escenario cambió
                reinitialize(measurement, nowMs)
                return filteredDistance
            }
            // Outlier aislado: reducir su peso drásticamente
            val dampedMeasurement = xPred + innovation * 0.08
            return applyCorrection(dampedMeasurement, confidence * 0.15, xPred, dt)
        }

        outlierCount = 0

        // Registrar para historial
        recentMeasurements.addLast(measurement)
        while (recentMeasurements.size > maxHistory) recentMeasurements.removeFirst()

        return applyCorrection(measurement, confidence, xPred, dt)
    }

    /**
     * Aplica la corrección de Kalman.
     */
    private fun applyCorrection(
        measurement: Double,
        confidence: Double,
        xPred: Double,
        dt: Double,
    ): Double {
        // Ruido de medición adaptativo: menor confianza = más ruido
        val effectiveR = baseMeasurementNoise / confidence.coerceAtLeast(0.05)

        // Ganancia de Kalman
        val kDist = pDist / (pDist + effectiveR)
        val kVel = pVel / (pVel + effectiveR * 4.0)

        // Corrección
        val innovation = measurement - xPred
        val newX = (xPred + kDist * innovation).coerceIn(0.1, 15.0)
        val newV = v + kVel * (innovation / dt.coerceAtLeast(0.01))

        // Actualizar velocidad con damping (la persona no se mueve a >3 m/s típicamente)
        v = (newV * 0.92).coerceIn(-3.0, 3.0)
        x = newX

        // Actualizar covarianza
        pDist *= (1.0 - kDist)
        pVel *= (1.0 - kVel)

        // Floor de covarianza para evitar sobre-confianza
        pDist = pDist.coerceAtLeast(0.005)
        pVel = pVel.coerceAtLeast(0.002)

        return filteredDistance
    }

    /**
     * Re-inicializa el filtro cuando se detecta un cambio de escenario.
     */
    private fun reinitialize(measurement: Double, nowMs: Long) {
        x = measurement.coerceIn(0.2, 15.0)
        v = 0.0
        pDist = 2.0
        pVel = 1.0
        lastUpdateMs = nowMs
        totalUpdates = 3 // No empezar de cero para evitar fase transitoria larga
        outlierCount = 0
        recentMeasurements.clear()
    }

    /**
     * Resetea completamente el filtro.
     */
    fun reset(initialDistance: Double = 3.0) {
        x = initialDistance
        v = 0.0
        pDist = 2.0
        pVel = 1.0
        lastUpdateMs = 0L
        totalUpdates = 0
        outlierCount = 0
        recentMeasurements.clear()
    }

    /**
     * Retorna la tendencia de movimiento (convergiendo/alejándose/estable).
     */
    fun movementTrend(): MovementTrend = when {
        !isConverged -> MovementTrend.UNKNOWN
        v < -0.15 -> MovementTrend.APPROACHING
        v > 0.15 -> MovementTrend.RECEDING
        else -> MovementTrend.STABLE
    }

    enum class MovementTrend {
        APPROACHING,
        STABLE,
        RECEDING,
        UNKNOWN,
    }
}
