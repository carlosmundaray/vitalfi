package com.vitalfi.csi

import kotlin.math.*

/**
 * Procesador avanzado de fase CSI (Channel State Information).
 *
 * Aplica una cadena de limpieza robusta a las fases crudas de subportadoras OFDM
 * recibidas de un router OpenWrt, eliminando artefactos de hardware y ruido
 * para obtener estimaciones precisas de distancia por ToF (Time of Flight).
 *
 * Pipeline: Raw → Unwrap → CPE Removal → SFO Removal → Hampel → Phase Slope
 */
class CsiPhaseProcessor {

    // Historial de fases anteriores para unwrapping temporal (por subportadora)
    private var prevPhases: DoubleArray? = null

    // Historial de pendientes de fase para detección de outliers
    private val slopeHistory = ArrayDeque<Double>()
    private val maxSlopeHistory = 30

    // Constantes OFDM 802.11n/ac
    companion object {
        /** Espaciado entre subportadoras OFDM (Hz). */
        const val SUBCARRIER_SPACING_HZ = 312500.0

        /** Velocidad de la luz (m/s). */
        const val SPEED_OF_LIGHT = 3.0e8

        /** Máximo número de subportadoras soportadas. */
        const val MAX_SUBCARRIERS = 114

        /** Umbral de Hampel (desviaciones MAD) para rechazo de outliers de fase. */
        const val HAMPEL_THRESHOLD = 3.0

        /** Ventana de Hampel para filtrado de fase. */
        const val HAMPEL_WINDOW = 3
    }

    /**
     * Pipeline completo de limpieza de fases CSI.
     *
     * @param rawPhases Fases crudas de las subportadoras (radianes, rango [-π, π]).
     * @return Fases limpias con CPE y SFO removidos, listas para estimación de distancia.
     */
    fun sanitizePhases(rawPhases: FloatArray): DoubleArray {
        if (rawPhases.isEmpty()) return DoubleArray(0)

        val n = rawPhases.size
        val phases = DoubleArray(n) { rawPhases[it].toDouble() }

        // 1. Phase unwrapping espacial (entre subportadoras adyacentes)
        spatialUnwrap(phases)

        // 2. Phase unwrapping temporal (entre tramas consecutivas)
        temporalUnwrap(phases)

        // 3. Eliminación de Common Phase Error (CPE)
        removeCpe(phases)

        // 4. Eliminación de Sampling Frequency Offset (SFO)
        removeSfo(phases)

        // 5. Filtro de Hampel por subportadora (rechazo de outliers)
        hampelFilter(phases)

        // Actualizar historial temporal
        prevPhases = phases.copyOf()

        return phases
    }

    /**
     * Calcula la pendiente de fase robusta (RANSAC-lite) sobre las fases limpias.
     *
     * La pendiente de fase entre subportadoras está directamente relacionada con
     * el retardo de propagación (ToF): slope = 2π × τ × Δf, donde τ = d/c.
     *
     * @param cleanedPhases Fases después de sanitizePhases().
     * @return Pendiente de fase promedio (radianes por subportadora).
     */
    fun phaseSlope(cleanedPhases: DoubleArray): Double {
        val n = cleanedPhases.size
        if (n < 4) return 0.0

        // Calcular diferencias de fase entre subportadoras adyacentes
        val diffs = DoubleArray(n - 1) { i ->
            wrapPhase(cleanedPhases[i + 1] - cleanedPhases[i])
        }

        // RANSAC-lite: Usar mediana en lugar de media para robustez
        val sorted = diffs.sorted()
        val median = sorted[sorted.size / 2]

        // Rechazar outliers a >2σ de la mediana (inlier selection)
        val mad = sorted.map { abs(it - median) }.sorted()[sorted.size / 2] * 1.4826
        val threshold = max(0.15, mad * 2.5)

        var sum = 0.0
        var count = 0
        for (d in diffs) {
            if (abs(d - median) <= threshold) {
                sum += d
                count++
            }
        }

        val robustSlope = if (count > 0) sum / count else median

        // Registro en historial para detección de saltos
        slopeHistory.addLast(robustSlope)
        while (slopeHistory.size > maxSlopeHistory) slopeHistory.removeFirst()

        // Suavizado con historial si hay suficientes datos
        return if (slopeHistory.size >= 5) {
            val recent = slopeHistory.toList().takeLast(5)
            val slopeSorted = recent.sorted()
            // Mediana de las últimas 5 pendientes (rechazo de saltos)
            slopeSorted[slopeSorted.size / 2]
        } else {
            robustSlope
        }
    }

    /**
     * Convierte la pendiente de fase (OFDM phase slope) a distancia en metros.
     *
     * Modelo: d = |slope| × c / (2π × Δf × 2)
     * El factor 2 es por el viaje ida y vuelta (Tx → reflector → Rx).
     * Para CSI con un solo AP (no round-trip), se usa factor 1.
     *
     * @param slope Pendiente de fase robusta (rad/subportadora).
     * @return Distancia estimada en metros.
     */
    fun distanceFromPhaseSlope(slope: Double): Double {
        // d = |slope| * c / (2π * Δf)
        // Nota: En CSI de un AP, la fase mide el camino de ida (one-way),
        // así que no dividimos por 2 como en radar clásico.
        val rawDelay = abs(slope) / (2.0 * PI * SUBCARRIER_SPACING_HZ)
        val distance = rawDelay * SPEED_OF_LIGHT

        return distance.coerceIn(0.1, 15.0)
    }

    /**
     * Calcula la coherencia de fase como métrica de calidad (0.0 - 1.0).
     * Alta coherencia = fases limpias, buena estimación. Baja = multipath severo.
     */
    fun phaseCoherence(cleanedPhases: DoubleArray): Double {
        if (cleanedPhases.size < 4) return 0.0

        val diffs = DoubleArray(cleanedPhases.size - 1) { i ->
            wrapPhase(cleanedPhases[i + 1] - cleanedPhases[i])
        }
        val mean = diffs.average()
        val variance = diffs.map { (it - mean) * (it - mean) }.average()

        // Coherencia alta cuando la varianza de las diferencias es baja
        // (la fase cambia linealmente = un solo camino dominante)
        return exp(-variance * 3.0).coerceIn(0.0, 1.0)
    }

    /**
     * Resetea el estado temporal (para cambios de escenario / reinicio de sesión).
     */
    fun reset() {
        prevPhases = null
        slopeHistory.clear()
    }

    // ─── Métodos internos de limpieza de fase ───

    /**
     * Phase unwrapping espacial: elimina saltos de ±2π entre subportadoras adyacentes.
     */
    private fun spatialUnwrap(phases: DoubleArray) {
        for (i in 1 until phases.size) {
            var diff = phases[i] - phases[i - 1]
            while (diff > PI) diff -= 2.0 * PI
            while (diff < -PI) diff += 2.0 * PI
            phases[i] = phases[i - 1] + diff
        }
    }

    /**
     * Phase unwrapping temporal: corrige saltos entre tramas consecutivas.
     */
    private fun temporalUnwrap(phases: DoubleArray) {
        val prev = prevPhases ?: return
        if (prev.size != phases.size) return

        // Calcular offset medio entre trama actual y anterior
        var offsetSum = 0.0
        var offsetCount = 0
        for (i in phases.indices) {
            var diff = phases[i] - prev[i]
            while (diff > PI) diff -= 2.0 * PI
            while (diff < -PI) diff += 2.0 * PI
            offsetSum += diff
            offsetCount++
        }

        // Solo corregir si hay un offset temporal consistente (salto de fase global)
        if (offsetCount > 0) {
            val meanOffset = offsetSum / offsetCount
            // Si el offset medio es grande, es un salto de hardware; no corregir
            // Si es pequeño, es drift natural del reloj; corregir
            if (abs(meanOffset) > PI * 0.5) {
                // Salto de hardware: re-alinear fase a la trama anterior
                val correction = round(meanOffset / (2 * PI)) * 2 * PI
                for (i in phases.indices) {
                    phases[i] -= correction
                }
            }
        }
    }

    /**
     * Eliminación de Common Phase Error (CPE).
     *
     * El CPE es un offset de fase constante en todas las subportadoras causado
     * por el oscilador local del receptor. Se elimina restando la media de fase.
     */
    private fun removeCpe(phases: DoubleArray) {
        if (phases.isEmpty()) return
        val mean = phases.average()
        for (i in phases.indices) {
            phases[i] -= mean
        }
    }

    /**
     * Eliminación de Sampling Frequency Offset (SFO).
     *
     * El SFO introduce una pendiente lineal artificial en la fase de las subportadoras.
     * Se estima por regresión lineal y se sustrae.
     *
     * Nota: Solo se remueve el componente SFO estimado. La pendiente residual
     * es la pendiente de propagación real (ToF).
     */
    private fun removeSfo(phases: DoubleArray) {
        val n = phases.size
        if (n < 4) return

        // Regresión lineal: phases[i] = a + b*i
        // Donde b contiene SFO + ToF. El SFO se estima como la componente
        // que no cambia entre tramas (si hay historial).
        // Sin historial temporal del SFO: usamos un estimador basado en
        // la pendiente de los extremos vs. la pendiente interior (heurístico).

        // Método: fit lineal y remover solo si la pendiente es "demasiado grande"
        // para ser ToF real (d > 15m implica slope > cierto umbral).
        val sumX = n * (n - 1.0) / 2.0
        val sumX2 = n * (n - 1.0) * (2.0 * n - 1.0) / 6.0
        var sumY = 0.0
        var sumXY = 0.0
        for (i in 0 until n) {
            sumY += phases[i]
            sumXY += i.toDouble() * phases[i]
        }
        val slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX)

        // Distancia equivalente de la pendiente total
        val totalDist = abs(slope) / (2.0 * PI * SUBCARRIER_SPACING_HZ) * SPEED_OF_LIGHT

        // Si la distancia implícita es > 20m, hay SFO significativo; remover parcialmente.
        // Conservar la porción que corresponde a ~8m máximo (rango útil de VitalFi).
        if (totalDist > 20.0) {
            val maxReasonableSlope = 8.0 * 2.0 * PI * SUBCARRIER_SPACING_HZ / SPEED_OF_LIGHT
            val sfoSlope = slope - slope.sign * maxReasonableSlope
            val intercept = (sumY - sfoSlope * sumX) / n
            for (i in 0 until n) {
                phases[i] -= (intercept + sfoSlope * i)
            }
        }
    }

    /**
     * Filtro de Hampel para rechazar outliers de fase en subportadoras individuales.
     */
    private fun hampelFilter(phases: DoubleArray) {
        val n = phases.size
        if (n < 5) return
        val k = 1.4826 // Factor de escala MAD → σ
        val window = HAMPEL_WINDOW

        val original = phases.copyOf()
        for (i in phases.indices) {
            val lo = max(0, i - window)
            val hi = min(n, i + window + 1)
            val windowValues = original.slice(lo until hi).sorted()
            val median = windowValues[windowValues.size / 2]
            val mad = k * windowValues.map { abs(it - median) }.sorted()[windowValues.size / 2]

            if (mad > 1e-10 && abs(original[i] - median) > HAMPEL_THRESHOLD * mad) {
                phases[i] = median
            }
        }
    }

    /**
     * Normaliza un ángulo al rango [-π, π].
     */
    private fun wrapPhase(phase: Double): Double {
        var p = phase
        while (p > PI) p -= 2.0 * PI
        while (p < -PI) p += 2.0 * PI
        return p
    }

    /** Signo de un Double (-1, 0, +1). */
    private val Double.sign: Double
        get() = when {
            this > 0 -> 1.0
            this < 0 -> -1.0
            else -> 0.0
        }
}
