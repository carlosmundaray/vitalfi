package com.vitalfi.dsp

/**
 * Mapa del entorno RF construido solo con mediciones reales
 * (RSSI + rumbo de brújula + varianza de reflexión).
 *
 * No reconstruye geometría física (paredes) como un LiDAR: pinta el campo
 * electromagnético medido y marca atenuaciones relativas entre sectores.
 */
data class EnvironmentCell(
    /** Centro del sector (grados brújula). */
    val bearingDeg: Double,
    /** RSSI medio medido en este sector (dBm). */
    val meanRssi: Double,
    /** Varianza de banda respiratoria medida mirando este sector. */
    val reflectionVar: Double,
    /** Número de muestras reales acumuladas. */
    val samples: Int,
    /**
     * 0–1: atenuación relativa vs vecinos (dato real).
     * Alto = la señal en esta dirección es más débil que a los lados (sombra / obstáculo RF).
     */
    val attenuationScore: Double,
)

class RfEnvironmentMap(
    private val numBins: Int = 72,
    private val emaAlpha: Double = 0.28,
) {
    private val meanRssi = DoubleArray(numBins) { Double.NaN }
    private val meanReflection = DoubleArray(numBins) { 0.0 }
    private val samples = IntArray(numBins)
    private val lastUpdateMs = LongArray(numBins)

    fun reset() {
        meanRssi.fill(Double.NaN)
        meanReflection.fill(0.0)
        samples.fill(0)
        lastUpdateMs.fill(0L)
    }

    fun record(
        bearingDeg: Double,
        rssiDbm: Int?,
        reflectionVariance: Double,
        nowMs: Long,
    ) {
        if (rssiDbm == null) return
        val idx = binIndex(bearingDeg)
        val rssi = rssiDbm.toDouble()
        val prev = meanRssi[idx]
        meanRssi[idx] = if (prev.isNaN()) rssi else prev * (1.0 - emaAlpha) + rssi * emaAlpha
        meanReflection[idx] =
            meanReflection[idx] * (1.0 - emaAlpha) + reflectionVariance.coerceAtLeast(0.0) * emaAlpha
        samples[idx] = (samples[idx] + 1).coerceAtMost(10_000)
        lastUpdateMs[idx] = nowMs
    }

    fun scannedSectorCount(): Int = samples.count { it > 0 }

    fun snapshot(nowMs: Long = System.currentTimeMillis()): List<EnvironmentCell> {
        val cells = ArrayList<EnvironmentCell>(numBins)
        val binWidth = 360.0 / numBins
        for (i in 0 until numBins) {
            if (samples[i] <= 0 || meanRssi[i].isNaN()) continue
            // Ignorar sectores muy viejos sin re-medición (>90 s) para no “inventar” pasado
            if (nowMs - lastUpdateMs[i] > 90_000L) continue
            cells.add(
                EnvironmentCell(
                    bearingDeg = i * binWidth + binWidth / 2.0,
                    meanRssi = meanRssi[i],
                    reflectionVar = meanReflection[i],
                    samples = samples[i],
                    attenuationScore = attenuationScore(i),
                ),
            )
        }
        return cells
    }

    private fun attenuationScore(idx: Int): Double {
        val self = meanRssi[idx]
        if (self.isNaN() || samples[idx] < 2) return 0.0
        val neighbors = mutableListOf<Double>()
        for (d in 1..3) {
            val a = meanRssi[(idx + d) % numBins]
            val b = meanRssi[(idx - d + numBins) % numBins]
            if (!a.isNaN() && samples[(idx + d) % numBins] > 0) neighbors.add(a)
            if (!b.isNaN() && samples[(idx - d + numBins) % numBins] > 0) neighbors.add(b)
        }
        if (neighbors.size < 2) return 0.0
        val neighMean = neighbors.average()
        // self más negativo que vecinos ⇒ más atenuación (dB)
        val dipDb = (neighMean - self).coerceAtLeast(0.0)
        return (dipDb / 12.0).coerceIn(0.0, 1.0)
    }

    private fun binIndex(bearingDeg: Double): Int {
        val norm = ((bearingDeg % 360.0) + 360.0) % 360.0
        return ((norm / 360.0 * numBins).toInt()) % numBins
    }
}
