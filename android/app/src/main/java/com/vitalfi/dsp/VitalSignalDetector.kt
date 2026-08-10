package com.vitalfi.dsp

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
    /**
     * Varianza solo de la banda respiratoria (proxy de la onda modulada / reflejada por la persona).
     * Se usa para estimar distancia; el RSSI absoluto mide más el router que a la víctima.
     */
    val reflectionVariance: Double = 0.0,
)

class RubbleConfidenceTracker(
    private val calibSeconds: Double = 20.0,
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

    /** Umbral de proximidad horizontal muy cerca (no implica profundidad real). */
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
                status = "Posible persona — verifica el sitio"
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
            reflectionVariance = diffVar,
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
        val signalFlat = uniqueValues <= 1 || rawStd < 0.28

        val combined = SignalFilters.preprocess(signal, fs, rubbleMode)
        val calibLen = minOf(combined.size, maxOf(15, (fs * if (rubbleMode) 8 else 6).toInt()))
        val noiseFloor = kotlin.math.sqrt(SignalFilters.variance(combined.copyOfRange(0, calibLen))).let {
            maxOf(0.05, it)
        }

        val cleaned = SignalFilters.hampelFilter(
            combined,
            windowSize = if (rubbleMode) 5 else 3,
            threshold = if (rubbleMode) 3.0 else 2.5,
        )
        val detrended = cleaned.map { it - cleaned.average() }.toDoubleArray()
        val motionVar = SignalFilters.variance(detrended)

        val respBand = if (rubbleMode) 0.06 to 0.65 else 0.08 to 0.55
        val filtered = if (rubbleMode && fs >= 2.0) {
            SignalFilters.bandpass(detrended, 0.06, 0.65, fs)
        } else {
            SignalFilters.bandpass(detrended, 0.1, 0.5, fs)
        }

        val spectral = FftUtils.spectralMetrics(filtered, fs, respBand)
        val respFreq = spectral.dominantFreq
        val respRate = respFreq * 60.0
        val reflectionVariance = SignalFilters.variance(filtered)
        val snr = spectral.snr
        val periodicity = SignalFilters.respiratoryPeriodicity(filtered, fs)
        val specEnergy = spectral.spectralEnergy

        val sens = maxOf(0.5, sensitivity)
        val varThreshold = maxOf(
            if (rubbleMode) 0.012 else 0.04,
            noiseFloor * noiseFloor * 0.35,
        ) / sens
        val snrThreshold = maxOf(if (rubbleMode) 0.08 else 0.22, 0.35 / sens)
        val activityThreshold = maxOf(if (rubbleMode) 0.20 else 0.32, noiseFloor * 0.40) / sens
        val periodicityThreshold = if (rubbleMode) 0.11 else 0.26

        // Movimiento de persona (caminar): σ / ΔRSSI reales — NO basta uniqueValues=2 (ruido Wi‑Fi)
        val isActivity = !signalFlat && (
            rawStd > activityThreshold ||
                motionVar > activityThreshold * 0.85 ||
                reflectionVariance > varThreshold * 0.35 ||
                (uniqueValues >= 4 && rawStd > activityThreshold * 0.55)
            )

        val classicBreathing = reflectionVariance > varThreshold &&
            snr > snrThreshold &&
            respFreq >= respBand.first &&
            respFreq <= respBand.second &&
            periodicity >= periodicityThreshold * 0.55

        val periodicBreathing = rubbleMode &&
            periodicity > periodicityThreshold &&
            (reflectionVariance > varThreshold * 0.15 || specEnergy > 0.008)

        val weakBreathing = rubbleMode &&
            periodicity > periodicityThreshold * 0.70 &&
            (reflectionVariance > varThreshold * 0.12 || isActivity)

        val isBreathing = classicBreathing || periodicBreathing || weakBreathing

        val heartRateBpm = estimateHeartRate(detrended, fs, respRate, isBreathing, filtered)
        val heartBeating = heartRateBpm >= 45.0 && (isBreathing || periodicity > 0.15)

        val confidence = computeConfidence(
            isBreathing, isActivity, reflectionVariance, snr, periodicity, specEnergy, rubbleMode, rawStd,
        )

        val status: String
        val hint: String
        when {
            isBreathing -> {
                status = "Persona detectada — patrón vital"
                hint = "Gira para ubicarla en el radar. Camina hacia el punto. Distancia = a la persona."
            }
            isActivity -> {
                status = "Persona en movimiento"
                hint = "Actividad Wi‑Fi de persona. Ubícala en el radar y acércate al punto."
            }
            signalFlat -> {
                status = "Señal plana — sin persona"
                hint = "Acerca el router 2.4 GHz. Gira 360° buscando variación."
            }
            else -> {
                status = "Buscando persona..."
                hint = "σ=${"%.2f".format(rawStd)} | Per=${"%.2f".format(periodicity)} | Vida=${"%.3f".format(reflectionVariance)}"
            }
        }

        val surfaceLikelihood = computeSurfaceLikelihood(
            variance = maxOf(reflectionVariance, motionVar),
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
            // Energía de persona (movimiento + vida) para distancia/tracking
            variance = maxOf(reflectionVariance, motionVar, rawStd * rawStd * 0.35),
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
            reflectionVariance = reflectionVariance,
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
     * Distancia a la **persona detectada** (vida o movimiento), no al router.
     *
     * Usa la energía de la señal vital/movimiento (reflexión + varianza de actividad).
     * El RSSI absoluto al AP se ignora cuando hay persona: mide el router, no al sujeto.
     */
    fun proximityFromVariance(
        variance: Double,
        isBreathing: Boolean,
        isActivity: Boolean,
        rssiDbm: Int? = null,
        reflectionVariance: Double? = null,
        periodicity: Double = 0.0,
        noiseFloor: Double = 0.0,
    ): Triple<String, Double, Long> {
        val refl = (reflectionVariance ?: 0.0).coerceAtLeast(0.0)
        val motion = variance.coerceAtLeast(0.0)
        // Señal de la persona: vida (banda) o movimiento (varianza / σ)
        val personSignal = when {
            isBreathing -> maxOf(refl, motion * 0.85)
            isActivity -> maxOf(motion, refl * 1.15, noiseFloor * noiseFloor * 0.9)
            else -> maxOf(refl, motion * 0.5)
        }
        val color = when {
            isBreathing && personSignal > 0.3 -> 0xFFFF3366
            isBreathing -> 0xFFFF6699
            isActivity -> 0xFFFFCC00
            else -> 0xFF444444
        }

        // Solo si hay persona: distancia = f(fuerza de su señal). Más fuerte ⇒ más cerca.
        val personDetected = isBreathing || isActivity
        var distance = if (personDetected) {
            val weight = when {
                isBreathing && periodicity >= 0.18 -> 1.05
                isBreathing -> 1.0
                isActivity && personSignal > 0.15 -> 1.08
                isActivity -> 0.95
                else -> 0.9
            }
            distanceFromVariance(personSignal * weight)
        } else {
            // Sin persona: no inventar ubicación; RSSI solo como referencia débil de cobertura
            rssiDbm?.let { distanceFromRssi(it) } ?: 5.0
        }

        if (isBreathing && periodicity > 0.18 && personSignal > 0.06) {
            distance *= 0.90
        }
        // Persona en movimiento fuerte ⇒ suele estar más cerca
        if (isActivity && !isBreathing && personSignal > 0.20) {
            distance *= 0.88
        }
        distance = distance.coerceIn(0.15, 8.0)

        val label = when {
            !personDetected -> "—"
            distance < PUNTO_CERO_M -> "MUY CERCA"
            distance < 1.0 -> "MUY CERCA"
            distance < 2.0 -> "CERCA"
            distance < 3.5 -> "MEDIA"
            else -> "LEJOS (persona)"
        }
        return Triple(label, distance, color)
    }

    /** Interpolación entre anclas empiricas: más varianza de reflexión ⇒ más cerca en el radar. */
    fun distanceFromVariance(variance: Double): Double {
        // Curva un poco más sensible en el tramo medio para que al caminar
        // hacia la persona el punto se acerque de forma perceptible.
        val anchors = listOf(
            2.20 to 0.15,
            1.40 to 0.28,
            0.95 to 0.48,
            0.65 to 0.68,
            0.42 to 0.92,
            0.28 to 1.20,
            0.18 to 1.50,
            0.11 to 1.85,
            0.06 to 2.40,
            0.03 to 3.10,
            0.015 to 3.80,
            0.005 to 4.50,
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
        // Solo heurística suave: NO debe ocultar personas caminando en el radar.
        var score = 0.0
        score += (variance * 1.5).coerceAtMost(0.35)
        score -= periodicity * 1.8
        score -= (snr * 0.12).coerceAtMost(0.25)
        if (isBreathing) score -= 0.25
        if (isActivity && !isBreathing) score += 0.10
        return score.coerceIn(0.0, 1.0)
    }
}
