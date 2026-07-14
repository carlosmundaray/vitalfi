package com.whofi.vitalfi.dsp

/**
 * Rastrea sectores con movimiento (rescatistas) y zonas marcadas manualmente.
 */
class RescuerExclusionTracker(
    private val numBins: Int = 36,
    private val decay: Double = 0.88,
) {
    private val movementScore = DoubleArray(numBins)
    private val excludedBins = mutableSetOf<Int>()
    private val bearingHistory = ArrayDeque<BearingSample>()
    /** Cumulative activity hits per sector — does not decay, reset only manually. */
    private val cumulativeActivity = IntArray(numBins)

    var rubbleZoneCenterDeg: Double? = null
        private set
    var rubbleZoneRadiusDeg: Double = 55.0
        private set

    private data class BearingSample(val bearingDeg: Double, val activity: Boolean, val timeMs: Long)

    fun reset() {
        movementScore.fill(0.0)
        excludedBins.clear()
        bearingHistory.clear()
        cumulativeActivity.fill(0)
    }

    fun setRubbleZone(centerBearingDeg: Double, radiusDeg: Double = 55.0) {
        rubbleZoneCenterDeg = centerBearingDeg
        rubbleZoneRadiusDeg = radiusDeg.coerceIn(20.0, 120.0)
    }

    fun clearRubbleZone() {
        rubbleZoneCenterDeg = null
    }

    fun excludeSector(bearingDeg: Double, spreadBins: Int = 2) {
        val idx = binIndex(bearingDeg)
        excludedBins.add(idx)
        for (d in 1..spreadBins) {
            excludedBins.add((idx + d) % numBins)
            excludedBins.add((idx - d + numBins) % numBins)
        }
    }

    fun clearExclusions() {
        excludedBins.clear()
        movementScore.fill(0.0)
        bearingHistory.clear()
        cumulativeActivity.fill(0)
    }

    fun excludedSectorCount(): Int = excludedBins.size

    fun record(
        bearingDeg: Double,
        detection: DetectionResult,
        nowMs: Long,
    ) {
        for (i in movementScore.indices) {
            movementScore[i] *= decay
        }

        pruneHistory(nowMs)
        if (detection.isActivity || detection.isBreathing) {
            bearingHistory.addLast(BearingSample(bearingDeg, detection.isActivity, nowMs))
        }

        val spread = recentBearingSpread(nowMs)
        if (detection.isActivity) {
            val idx = binIndex(bearingDeg)
            cumulativeActivity[idx]++

            val boost = when {
                spread > 50 && !detection.isBreathing -> 0.50
                spread > 30 && detection.isActivity && !detection.isBreathing -> 0.38
                detection.variance > 0.08 && !detection.isBreathing -> 0.28
                detection.surfaceSignalLikelihood > 0.4 -> 0.30
                else -> 0.18
            }
            movementScore[idx] = minOf(1.0, movementScore[idx] + boost)

            // Auto-exclude sectors with strong rescuer-like activity patterns
            val shouldAutoExclude = when {
                spread > 30 && detection.isActivity && !detection.isBreathing && detection.periodicity < 0.18 -> true
                detection.variance > 0.08 && !detection.isBreathing && detection.surfaceSignalLikelihood > 0.35 -> true
                cumulativeActivity[idx] >= 8 && !detection.isBreathing -> true
                else -> false
            }
            if (shouldAutoExclude) {
                excludeSector(bearingDeg, spreadBins = 1)
            }
        }
    }

    fun movementAt(bearingDeg: Double): Double = movementScore[binIndex(bearingDeg)]

    fun isExcluded(bearingDeg: Double): Boolean {
        val idx = binIndex(bearingDeg)
        if (idx in excludedBins) return true
        if (movementScore[idx] >= 0.55) return true
        // Sectors with high cumulative activity are very likely rescuer paths
        if (cumulativeActivity[idx] >= 12) return true
        return false
    }

    fun isInRubbleZone(bearingDeg: Double): Boolean {
        val center = rubbleZoneCenterDeg ?: return true
        return angularDistance(bearingDeg, center) <= rubbleZoneRadiusDeg
    }

    fun rubbleZoneLabel(): String? {
        val center = rubbleZoneCenterDeg ?: return null
        return "Zona escombros: ${kotlin.math.round(center).toInt()}° ±${kotlin.math.round(rubbleZoneRadiusDeg).toInt()}°"
    }

    private fun pruneHistory(nowMs: Long) {
        while (bearingHistory.isNotEmpty() && nowMs - bearingHistory.first().timeMs > 25_000L) {
            bearingHistory.removeFirst()
        }
        while (bearingHistory.size > 24) {
            bearingHistory.removeFirst()
        }
    }

    private fun recentBearingSpread(nowMs: Long): Double {
        val recent = bearingHistory.filter { nowMs - it.timeMs <= 20_000L && it.activity }
        if (recent.size < 3) return 0.0
        val bearings = recent.map { it.bearingDeg }
        var maxSpan = 0.0
        for (i in bearings.indices) {
            for (j in i + 1 until bearings.size) {
                maxSpan = maxOf(maxSpan, angularDistance(bearings[i], bearings[j]))
            }
        }
        return maxSpan
    }

    private fun binIndex(bearingDeg: Double): Int {
        val norm = ((bearingDeg % 360.0) + 360.0) % 360.0
        return ((norm / 360.0 * numBins).toInt()) % numBins
    }

    private fun angularDistance(a: Double, b: Double): Double {
        var d = kotlin.math.abs(a - b) % 360.0
        if (d > 180.0) d = 360.0 - d
        return d
    }
}
