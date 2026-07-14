package com.whofi.vitalfi.dsp

data class DetectionResult(
    val isBreathing: Boolean,
    val isActivity: Boolean,
    val heartBeating: Boolean,
    val heartRateBpm: Double,
    val respFreq: Double,
    val respRate: Double,
    val variance: Double,
    val snr: Double,
    val noiseFloor: Double,
    val uniqueValues: Int,
    val signalFlat: Boolean,
    val status: String,
    val hint: String,
    val confidence: Double = 0.0,
    val periodicity: Double = 0.0,
    val spectralEnergy: Double = 0.0,
    /** 0-1 score: high = signal likely from surface (rescuer), low = likely under rubble. */
    val surfaceSignalLikelihood: Double = 0.0,
)

class RubbleConfidenceTracker(
    private val calibSeconds: Double = 30.0,
    private val decay: Double = 0.985,
) {
    var score: Double = 0.0
        private set
    var calibrating: Boolean = true
        private set
    var hits: Int = 0
        private set

    private var startTimeMs: Long = 0L

    fun calibRemainingSec(nowMs: Long): Int {
        if (!calibrating) return 0
        val elapsed = (nowMs - startTimeMs) / 1000.0
        return maxOf(0, (calibSeconds - elapsed).toInt())
    }

    fun begin(nowMs: Long) {
        startTimeMs = nowMs
        calibrating = true
        score = 0.0
        hits = 0
    }

    fun update(detection: DetectionResult, nowMs: Long): Double {
        if (startTimeMs <= 0L) begin(nowMs)

        val elapsedSec = (nowMs - startTimeMs) / 1000.0
        if (calibrating && elapsedSec >= calibSeconds) {
            calibrating = false
        }

        val boost = 0.06 + detection.confidence * 0.2
        when {
            detection.isBreathing -> {
                score = minOf(1.0, score * decay + boost * 2.0)
                hits++
            }
            detection.isActivity -> {
                score = minOf(1.0, score * decay + boost * 1.2)
                hits++
            }
            detection.periodicity > 0.18 -> {
                score = minOf(1.0, score * decay + boost * 0.6)
            }
            detection.variance > 0.04 -> {
                score = minOf(1.0, score * decay + boost * 0.3)
            }
            else -> score *= if (calibrating) 0.995 else decay
        }
        return score
    }

    fun likelyPresent(minScore: Double = 0.28): Boolean = score >= minScore

    fun reset(nowMs: Long) = begin(nowMs)
}

object VitalSignalDetector {

    /** Umbral de "punto cero": la persona está prácticamente debajo de ti. */
    const val PUNTO_CERO_M = 0.6

    fun analyze(
        signal: DoubleArray,
        fs: Double,
        sensitivity: Double = 2.0,
        rubbleMode: Boolean = true,
        minSamples: Int = 15,
    ): DetectionResult {
        if (signal.size < minSamples) {
            return collectingResult(signal)
        }

        if (fs < 0.8) {
            return analyzeLowRate(signal, fs, sensitivity)
        }

        return analyzeFullRate(signal, fs, sensitivity, rubbleMode)
    }

    private fun collectingResult(signal: DoubleArray): DetectionResult =
        DetectionResult(
            isBreathing = false,
            isActivity = false,
            heartBeating = false,
            heartRateBpm = 0.0,
            respFreq = 0.0,
            respRate = 0.0,
            variance = 0.0,
            snr = 0.0,
            noiseFloor = 0.0,
            uniqueValues = signal.toSet().size,
            signalFlat = true,
            status = "Recolectando muestras...",
            hint = "Espera más muestras. Gira lentamente alrededor del escombro.",
        )

    private fun analyzeLowRate(
        signal: DoubleArray,
        fs: Double,
        sensitivity: Double,
    ): DetectionResult {
        val uniqueValues = SignalFilters.uniqueRounded(signal, decimals = 0)
        val rawStd = kotlin.math.sqrt(SignalFilters.variance(signal))
        val signalFlat = uniqueValues <= 1 || rawStd < 0.35

        val diffs = DoubleArray(signal.size - 1) { signal[it + 1] - signal[it] }
        val diffVar = SignalFilters.variance(diffs)
        val sens = maxOf(0.5, sensitivity)
        val activityThreshold = maxOf(0.08, 0.25 / sens)
        val breathingThreshold = maxOf(0.2, 0.6 / sens)

        val isActivity = !signalFlat && (diffVar > activityThreshold || uniqueValues >= 3 || rawStd > 0.5)
        val oscillation = countPeaks(signal, minDeviation = 0.4)
        val isBreathing = isActivity &&
            (diffVar > breathingThreshold || oscillation >= 2) &&
            uniqueValues >= 2

        val confidence = when {
            isBreathing -> minOf(1.0, 0.5 + diffVar * 0.15 + oscillation * 0.08)
            isActivity -> minOf(0.75, 0.3 + diffVar * 0.12 + rawStd * 0.1)
            else -> minOf(0.4, diffVar * 0.1)
        }

        val respFreqEst = if (oscillation > 0) fs / oscillation.coerceAtLeast(1) else 0.0
        val respRateEst = respFreqEst * 60.0
        val heartRateBpm = estimateHeartRate(signal, fs, respRateEst, isBreathing, null)
        val heartBeating = heartRateBpm >= 45.0 && (isBreathing || confidence > 0.25)

        val status: String
        val hint: String
        when {
            isBreathing -> {
                status = "Posible persona bajo escombros"
                hint = "Señal detectada. Gira 360° lentamente para ubicar en el radar."
            }
            isActivity -> {
                status = "Actividad detectada"
                hint = "Variación de señal. Sigue girando el teléfono alrededor del montículo."
            }
            signalFlat -> {
                status = "Señal plana — sin variación"
                hint = "Acerca el router a 0.5 m del escombro. Usa 2.4 GHz."
            }
            else -> {
                status = "Analizando escombros..."
                hint = "ΔRSSI=${"%.2f".format(diffVar)} dBm² | σ=${"%.2f".format(rawStd)} | Picos=$oscillation"
            }
        }

        val surfaceLikelihood = computeSurfaceLikelihood(
            variance = maxOf(diffVar, rawStd * rawStd),
            periodicity = minOf(1.0, oscillation / 3.0),
            snr = if (rawStd > 0) diffVar / rawStd else 0.0,
            isActivity = isActivity,
            isBreathing = isBreathing,
        )

        return DetectionResult(
            isBreathing = isBreathing,
            isActivity = isActivity,
            heartBeating = heartBeating,
            heartRateBpm = heartRateBpm,
            respFreq = respFreqEst,
            respRate = respRateEst,
            variance = maxOf(diffVar, rawStd * rawStd),
            snr = if (rawStd > 0) diffVar / rawStd else 0.0,
            noiseFloor = rawStd,
            uniqueValues = uniqueValues,
            signalFlat = signalFlat,
            status = status,
            hint = hint,
            confidence = confidence,
            periodicity = minOf(1.0, oscillation / 3.0),
            spectralEnergy = 0.0,
            surfaceSignalLikelihood = surfaceLikelihood,
        )
    }

    private fun countPeaks(signal: DoubleArray, minDeviation: Double = 0.15): Int {
        if (signal.size < 4) return 0
        val mean = signal.average()
        var peaks = 0
        for (i in 1 until signal.size - 1) {
            val rising = signal[i] > signal[i - 1] && signal[i] >= signal[i + 1]
            if (rising && kotlin.math.abs(signal[i] - mean) > minDeviation) peaks++
        }
        return peaks
    }

    private fun analyzeFullRate(
        signal: DoubleArray,
        fs: Double,
        sensitivity: Double,
        rubbleMode: Boolean,
    ): DetectionResult {
        val uniqueValues = SignalFilters.uniqueRounded(signal, decimals = 0)
        val rawStd = kotlin.math.sqrt(SignalFilters.variance(signal))
        val signalFlat = uniqueValues <= 1 || rawStd < 0.35

        val combined = SignalFilters.preprocess(signal, fs, rubbleMode)
        val calibLen = minOf(combined.size, maxOf(15, (fs * if (rubbleMode) 10 else 8).toInt()))
        val noiseFloor = kotlin.math.sqrt(SignalFilters.variance(combined.copyOfRange(0, calibLen))).let {
            maxOf(0.05, it)
        }

        val cleaned = SignalFilters.hampelFilter(
            combined,
            windowSize = if (rubbleMode) 5 else 3,
            threshold = if (rubbleMode) 3.0 else 2.5,
        )
        val detrended = cleaned.map { it - cleaned.average() }.toDoubleArray()

        val respBand = if (rubbleMode) 0.06 to 0.65 else 0.08 to 0.55
        val filtered = if (rubbleMode && fs >= 2.0) {
            SignalFilters.bandpass(detrended, 0.06, 0.65, fs)
        } else {
            SignalFilters.bandpass(detrended, 0.1, 0.5, fs)
        }

        val spectral = FftUtils.spectralMetrics(filtered, fs, respBand)
        val respFreq = spectral.dominantFreq
        val respRate = respFreq * 60.0
        val variance = SignalFilters.variance(filtered)
        val snr = spectral.snr
        val periodicity = SignalFilters.respiratoryPeriodicity(filtered, fs)
        val specEnergy = spectral.spectralEnergy

        val sens = maxOf(0.5, sensitivity)
        val varThreshold = maxOf(
            if (rubbleMode) 0.015 else 0.05,
            noiseFloor * noiseFloor * 0.4,
        ) / sens
        val snrThreshold = maxOf(if (rubbleMode) 0.1 else 0.25, 0.4 / sens)
        val activityThreshold = maxOf(if (rubbleMode) 0.35 else 0.5, noiseFloor * 0.6) / sens
        val periodicityThreshold = if (rubbleMode) 0.12 else 0.28

        val isActivity = rawStd > activityThreshold ||
            uniqueValues >= 2 ||
            variance > varThreshold * 0.25 ||
            periodicity > periodicityThreshold * 0.7

        val classicBreathing = variance > varThreshold &&
            snr > snrThreshold &&
            respFreq >= respBand.first &&
            (isActivity || uniqueValues >= 2)

        val periodicBreathing = rubbleMode &&
            periodicity > periodicityThreshold &&
            (variance > varThreshold * 0.2 || specEnergy > 0.01)

        val weakBreathing = rubbleMode &&
            isActivity &&
            (periodicity > periodicityThreshold * 0.75 || rawStd > activityThreshold * 1.2)

        val isBreathing = classicBreathing || periodicBreathing || weakBreathing

        val heartRateBpm = estimateHeartRate(detrended, fs, respRate, isBreathing, filtered)
        val heartBeating = heartRateBpm >= 45.0 && (isBreathing || periodicity > 0.15)

        val confidence = computeConfidence(
            isBreathing, isActivity, variance, snr, periodicity, specEnergy, rubbleMode, rawStd,
        )

        val status: String
        val hint: String
        when {
            isBreathing -> {
                status = "Persona detectada bajo escombros"
                hint = "Patrón vital detectado. Gira 360° para triangular en el radar."
            }
            isActivity -> {
                status = "Actividad / posible movimiento"
                hint = "Variación detectada. Gira el teléfono lentamente alrededor del escombro."
            }
            signalFlat -> {
                status = "Señal plana — sin variación"
                hint = "Acerca el router a 0.5 m del escombro. Usa 2.4 GHz."
            }
            else -> {
                status = "Analizando escombros..."
                hint = "σ=${"%.2f".format(rawStd)} dBm | Per=${"%.2f".format(periodicity)} | Var=${"%.3f".format(variance)}"
            }
        }

        val surfaceLikelihood = computeSurfaceLikelihood(
            variance = maxOf(variance, rawStd * rawStd * 0.5),
            periodicity = periodicity,
            snr = snr,
            isActivity = isActivity,
            isBreathing = isBreathing,
        )

        return DetectionResult(
            isBreathing = isBreathing,
            isActivity = isActivity,
            heartBeating = heartBeating,
            heartRateBpm = heartRateBpm,
            respFreq = respFreq,
            respRate = respRate,
            variance = maxOf(variance, rawStd * rawStd * 0.5),
            snr = snr,
            noiseFloor = noiseFloor,
            uniqueValues = uniqueValues,
            signalFlat = signalFlat,
            status = status,
            hint = hint,
            confidence = confidence,
            periodicity = periodicity,
            spectralEnergy = specEnergy,
            surfaceSignalLikelihood = surfaceLikelihood,
        )
    }

    private fun computeConfidence(
        isBreathing: Boolean,
        isActivity: Boolean,
        variance: Double,
        snr: Double,
        periodicity: Double,
        spectralEnergy: Double,
        rubbleMode: Boolean,
        rawStd: Double,
    ): Double {
        var score = 0.0
        if (isBreathing) score += 0.5
        if (isActivity) score += 0.2
        score += minOf(0.25, variance * 2.0)
        score += minOf(0.2, rawStd * 0.4)
        score += minOf(0.2, snr * 0.06)
        score += minOf(0.25, periodicity * 0.5)
        score += minOf(0.1, spectralEnergy * 1.5)
        if (rubbleMode && (isActivity || rawStd > 0.1)) score += 0.1
        return minOf(1.0, score)
    }

    /**
     * Estima distancia horizontal continua (metros) a partir de la amplitud de señal (varianza)
     * y, si hay RSSI, un modelo log-distancia. Antes solo devolvía 5 valores fijos (0.8/1.5/…).
     */
    fun proximityFromVariance(
        variance: Double,
        isBreathing: Boolean,
        isActivity: Boolean,
        rssiDbm: Int? = null,
    ): Triple<String, Double, Long> {
        val color = when {
            isBreathing && variance > 0.3 -> 0xFFFF3366
            isBreathing -> 0xFFFF6699
            isActivity -> 0xFFFFCC00
            else -> 0xFF444444
        }
        val fromVariance = distanceFromVariance(variance)
        val fromRssi = rssiDbm?.let { distanceFromRssi(it) }
        // El RSSI mide distancia al router, no a la víctima: cuanto más fuerte es la
        // señal vital (varianza), menos peso le damos para que la lectura sí baje al acercarte.
        var distance = when {
            fromRssi != null && variance >= 0.60 -> fromVariance * 0.90 + fromRssi * 0.10
            fromRssi != null && variance >= 0.20 -> fromVariance * 0.75 + fromRssi * 0.25
            fromRssi != null && variance >= 0.02 -> fromVariance * 0.55 + fromRssi * 0.45
            fromRssi != null -> fromRssi * 0.65 + fromVariance * 0.35
            else -> fromVariance
        }
        if (isBreathing && variance > 0.12) distance *= 0.94
        if (isActivity && !isBreathing && variance < 0.08) distance = maxOf(distance, 2.2)
        distance = distance.coerceIn(0.15, 8.0)

        val label = when {
            distance < PUNTO_CERO_M && (isBreathing || variance > 0.8) -> "PUNTO CERO"
            distance < 1.0 -> "MUY CERCA"
            distance < 2.0 -> "CERCA"
            distance < 3.5 -> "MEDIA"
            isActivity || isBreathing -> "LEJOS (señal)"
            else -> "LEJOS"
        }
        return Triple(label, distance, color)
    }

    /** Interpolación entre anclas empiricas: más varianza ⇒ más cerca. */
    fun distanceFromVariance(variance: Double): Double {
        val anchors = listOf(
            2.20 to 0.15,
            1.60 to 0.30,
            1.20 to 0.50,
            0.80 to 0.70,
            0.50 to 0.95,
            0.30 to 1.25,
            0.20 to 1.55,
            0.12 to 1.95,
            0.06 to 2.55,
            0.03 to 3.20,
            0.015 to 3.90,
            0.005 to 4.60,
            0.0 to 5.20,
        )
        val v = variance.coerceAtLeast(0.0)
        if (v >= anchors.first().first) return anchors.first().second
        for (i in 0 until anchors.lastIndex) {
            val (vHi, dNear) = anchors[i]
            val (vLo, dFar) = anchors[i + 1]
            if (v in vLo..vHi) {
                val t = if (vHi > vLo) (vHi - v) / (vHi - vLo) else 0.0
                return dNear + (dFar - dNear) * t
            }
        }
        return anchors.last().second
    }

    /** Modelo log-distancia indoor típico (1 m ≈ -40 dBm, n ≈ 2.7). */
    fun distanceFromRssi(rssiDbm: Int): Double {
        val rssi = rssiDbm.coerceIn(-95, -25).toDouble()
        val pathLossExp = 2.7
        val txAt1m = -40.0
        val meters = Math.pow(10.0, (txAt1m - rssi) / (10.0 * pathLossExp))
        return meters.coerceIn(0.5, 8.0)
    }

    private fun estimateHeartRate(
        signal: DoubleArray,
        fs: Double,
        respRate: Double,
        isBreathing: Boolean,
        filtered: DoubleArray?,
    ): Double {
        val src = filtered ?: signal
        if (fs >= 0.8 && src.size >= 12) {
            val cardiac = FftUtils.spectralMetrics(src, fs, 0.85 to 2.5)
            if (cardiac.dominantFreq in 0.72..2.4 && cardiac.spectralEnergy > 0.008) {
                return cardiac.dominantFreq * 60.0
            }
        }
        if (isBreathing && respRate in 6.0..35.0) {
            return (respRate * 4.2).coerceIn(48.0, 115.0)
        }
        return 0.0
    }

    /**
     * Estimates how likely the detected signal comes from a person on the surface
     * (rescuer) rather than someone trapped under rubble.
     *
     * Surface signals: high variance, low periodicity, low SNR in breathing band.
     * Trapped signals: low variance, high periodicity, higher SNR.
     */
    private fun computeSurfaceLikelihood(
        variance: Double,
        periodicity: Double,
        snr: Double,
        isActivity: Boolean,
        isBreathing: Boolean,
    ): Double {
        // Rescuer on surface: strong RSSI variance (close to router, no rubble attenuation),
        // irregular pattern (walking/moving), low periodicity in breathing band.
        var score = 0.0
        score += (variance * 3.0).coerceAtMost(0.5)
        score -= periodicity * 2.0
        score -= (snr * 0.15).coerceAtMost(0.3)
        if (isActivity && !isBreathing) score += 0.25
        if (!isActivity) score -= 0.15
        return score.coerceIn(0.0, 1.0)
    }
}
