package com.vitalfi.dsp

import kotlin.math.min

data class TrappedVictim(
    val id: Int,
    val label: String,
    val x: Double,
    val y: Double,
    val bearing: Double,
    val distance: Double,
    val depth: Double,
    val isBreathing: Boolean,
    val isActivity: Boolean,
    val heartBeating: Boolean,
    val heartRateBpm: Double,
    val respRate: Double,
    val confidence: Double,
    val rubbleConfidence: Double,
    val proximity: String,
    val status: String,
    val score: Double,
    val lastSeenMs: Long,
    /** Firma automática Persona A/B… (sin nombre civil). */
    val signatureCode: String = "",
)

/**
 * Acumula señal por sector de rumbo y estima varias personas atrapadas en el sitio.
 * 100 % offline — solo datos locales de RSSI + brújula.
 */
class MultiVictimTracker(
    private val numBins: Int = 36,
    private val decay: Double = 0.92,
) {
    private val scores = DoubleArray(numBins)
    private val distances = DoubleArray(numBins)
    private val bearings = DoubleArray(numBins) { Double.NaN }
    private val breathing = DoubleArray(numBins)
    private val activity = DoubleArray(numBins)
    private val confidence = DoubleArray(numBins)
    private val respRate = DoubleArray(numBins)
    private val heartRate = DoubleArray(numBins)
    private val rubble = DoubleArray(numBins)
    private val lastSeen = LongArray(numBins)
    private val binIds = mutableMapOf<Int, Int>()
    private var nextVictimId = 1

    fun reset() {
        scores.fill(0.0)
        distances.fill(0.0)
        bearings.fill(Double.NaN)
        breathing.fill(0.0)
        activity.fill(0.0)
        confidence.fill(0.0)
        respRate.fill(0.0)
        heartRate.fill(0.0)
        rubble.fill(0.0)
        lastSeen.fill(0L)
        binIds.clear()
        nextVictimId = 1
    }

    private fun binIndex(bearingDeg: Double): Int {
        val norm = ((bearingDeg % 360.0) + 360.0) % 360.0
        return ((norm / 360.0 * numBins).toInt()) % numBins
    }

    fun update(
        bearingDeg: Double,
        distanceM: Double,
        strength: Double,
        detection: DetectionResult,
        rubbleConf: Double,
        nowMs: Long,
    ) {
        val idx = binIndex(bearingDeg)
        for (i in scores.indices) {
            scores[i] *= decay
            breathing[i] *= decay
            activity[i] *= decay
            confidence[i] *= decay
            respRate[i] *= decay
            heartRate[i] *= decay
            rubble[i] *= decay
            // No decaer distancias: eso las acercaba artificialmente a 0 m.
            if (scores[i] < 0.002) {
                distances[i] = 0.0
                bearings[i] = Double.NaN
            }
        }

        scores[idx] += strength
        val clampedDist = distanceM.coerceIn(0.15, 8.0)
        distances[idx] = if (distances[idx] > 0) {
            // Asimétrico: al ACERCARTE la lectura baja rápido (sigue tu avance);
            // al alejarse sube lento (evita saltos por ruido).
            val alpha = if (clampedDist < distances[idx]) 0.60 else 0.25
            distances[idx] * (1 - alpha) + clampedDist * alpha
        } else {
            clampedDist
        }
        bearings[idx] = if (!bearings[idx].isNaN()) {
            smoothBearing(bearings[idx], bearingDeg, 0.35)
        } else {
            bearingDeg
        }

        if (detection.isBreathing) breathing[idx] = min(1.0, breathing[idx] + 0.35)
        if (detection.isActivity) activity[idx] = min(1.0, activity[idx] + 0.25)
        confidence[idx] = min(1.0, confidence[idx] + detection.confidence * 0.3)
        if (detection.respRate > 0) {
            respRate[idx] = if (respRate[idx] > 0) {
                respRate[idx] * 0.7 + detection.respRate * 0.3
            } else {
                detection.respRate
            }
        }
        if (detection.heartRateBpm > 0) {
            heartRate[idx] = if (heartRate[idx] > 0) {
                heartRate[idx] * 0.7 + detection.heartRateBpm * 0.3
            } else {
                detection.heartRateBpm
            }
        }
        rubble[idx] = min(1.0, rubble[idx] + rubbleConf * 0.25)
        lastSeen[idx] = nowMs
    }

    fun bestEstimate(minScore: Double = 0.04): Triple<Double, Double, Double>? {
        val peak = scores.maxOrNull() ?: return null
        if (peak < minScore) return null
        val idx = scores.indices.maxByOrNull { scores[it] } ?: return null
        val bearing = bearingForBin(idx)
        val distance = if (distances[idx] > 0) distances[idx] else 2.5
        return Triple(bearing, distance, peak)
    }

    fun strongestBearing(): Pair<Double, Double>? {
        val idx = scores.indices.maxByOrNull { scores[it] } ?: return null
        if (scores[idx] < 0.005) return null
        val bearing = bearingForBin(idx)
        val distance = if (distances[idx] > 0) distances[idx] else 2.5
        return bearing to distance
    }

    fun heatmapPoints(): List<HeatPoint> {
        val points = mutableListOf<HeatPoint>()
        val peak = scores.maxOrNull() ?: 0.0
        for (i in scores.indices) {
            if (scores[i] < maxOf(0.005, peak * 0.1)) continue
            val bearing = bearingForBin(i)
            val dist = if (distances[i] > 0) distances[i] else 2.0
            val (x, y) = polarToXY(dist, bearing)
            points.add(HeatPoint(x, y, scores[i]))
        }
        return points
    }

    /**
     * Profundidad bajo el suelo (Z negativo). Depende más de la confianza de escombros
     * que de la distancia horizontal (antes: depth ≈ distance×0.25, poco fiable).
     */
    fun depthUnderRubble(distanceM: Double, confidence: Double): Double {
        val conf = confidence.coerceIn(0.0, 1.0)
        val base = 0.30 + conf * 1.15
        val distFactor = (distanceM * 0.06).coerceIn(0.0, 0.30)
        val depth = (base + distFactor).coerceIn(0.25, 1.8)
        return -depth
    }

    fun locateVictims(minScore: Double = 0.025, nowMs: Long): List<TrappedVictim> {
        val peakBins = findPeakBins(minScore)
        return peakBins.map { bin ->
            buildVictim(bin, nowMs)
        }.sortedByDescending { it.score }
    }

    private fun buildVictim(bin: Int, nowMs: Long): TrappedVictim {
        val bearing = bearingForBin(bin)
        val dist = if (distances[bin] > 0) distances[bin] else 2.0
        val (x, y) = polarToXY(dist, bearing)
        val rubbleConf = rubble[bin].coerceIn(0.0, 1.0)
        val depth = depthUnderRubble(dist, rubbleConf.coerceAtLeast(0.2))
        val isBreathing = breathing[bin] > 0.18
        val isActivity = activity[bin] > 0.15
        val hr = heartRate[bin]
        val rr = respRate[bin]
        val conf = confidence[bin].coerceIn(0.0, 1.0)
        val heartBeating = hr >= 45.0 && (isBreathing || conf > 0.2)
        val proximity = proximityLabel(dist, isBreathing, isActivity)
        val status = victimStatus(isBreathing, isActivity, heartBeating, conf)
        val id = victimIdForBin(bin)

        return TrappedVictim(
            id = id,
            label = "P$id",
            x = x,
            y = y,
            bearing = bearing,
            distance = dist,
            depth = depth,
            isBreathing = isBreathing,
            isActivity = isActivity,
            heartBeating = heartBeating,
            heartRateBpm = hr,
            respRate = rr,
            confidence = conf,
            rubbleConfidence = rubbleConf,
            proximity = proximity,
            status = status,
            score = scores[bin],
            lastSeenMs = if (lastSeen[bin] > 0) lastSeen[bin] else nowMs,
        )
    }

    private fun bearingForBin(bin: Int): Double {
        val stored = bearings[bin]
        if (!stored.isNaN()) return ((stored % 360.0) + 360.0) % 360.0
        return (bin + 0.5) * 360.0 / numBins
    }

    private fun smoothBearing(previous: Double, sample: Double, alpha: Double): Double {
        var delta = sample - previous
        while (delta > 180.0) delta -= 360.0
        while (delta < -180.0) delta += 360.0
        return ((previous + alpha * delta) % 360.0 + 360.0) % 360.0
    }

    private fun victimStatus(
        isBreathing: Boolean,
        isActivity: Boolean,
        heartBeating: Boolean,
        confidence: Double,
    ): String = when {
        isBreathing && heartBeating -> "Viva — respira y pulso detectado"
        isBreathing -> "Viva — respiración detectada"
        heartBeating -> "Posible pulso cardíaco"
        isActivity -> "Actividad / movimiento"
        confidence > 0.2 -> "Señal vital débil"
        else -> "Señal en análisis"
    }

    private fun proximityLabel(distance: Double, isBreathing: Boolean, isActivity: Boolean): String =
        when {
            distance < 0.6 && (isBreathing || isActivity) -> "MUY CERCA — verifica (profundidad no confirmada)"
            distance < 1.0 -> "MUY CERCA"
            distance < 2.0 -> "CERCA"
            distance < 3.5 -> "MEDIA"
            isActivity || isBreathing -> "LEJOS (señal)"
            else -> "LEJOS"
        }

    private fun findPeakBins(minScore: Double): List<Int> {
        val peak = scores.maxOrNull() ?: return emptyList()
        val threshold = maxOf(minScore, peak * 0.12)
        val peaks = mutableListOf<Int>()
        for (i in scores.indices) {
            if (scores[i] < threshold) continue
            val left = scores[(i - 1 + numBins) % numBins]
            val right = scores[(i + 1) % numBins]
            if (scores[i] >= left && scores[i] >= right) peaks.add(i)
        }
        return mergeAdjacentPeaks(peaks)
    }

    private fun mergeAdjacentPeaks(peaks: List<Int>): List<Int> {
        if (peaks.isEmpty()) return emptyList()
        val groups = peaks.map { mutableListOf(it) }.toMutableList()
        var changed = true
        while (changed) {
            changed = false
            var i = 0
            while (i < groups.size) {
                var j = i + 1
                while (j < groups.size) {
                    val adjacent = groups[i].any { p1 ->
                        groups[j].any { p2 ->
                            val diff = kotlin.math.abs(p1 - p2) % numBins
                            val dist = if (diff > numBins / 2) numBins - diff else diff
                            dist <= 2
                        }
                    }
                    if (adjacent) {
                        groups[i].addAll(groups[j])
                        groups.removeAt(j)
                        changed = true
                    } else {
                        j++
                    }
                }
                i++
            }
        }
        return groups.map { group -> group.maxBy { scores[it] } }
    }

    private fun victimIdForBin(bin: Int): Int {
        binIds[bin]?.let { return it }
        for (d in -2..2) {
            val adj = (bin + d + numBins) % numBins
            binIds[adj]?.let { id ->
                binIds[bin] = id
                return id
            }
        }
        val id = nextVictimId++
        binIds[bin] = id
        return id
    }
}
