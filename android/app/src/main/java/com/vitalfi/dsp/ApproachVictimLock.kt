package com.vitalfi.dsp

import kotlin.math.abs

/**
 * Mantiene a la víctima "anclada" mientras el rescatista camina hacia ella.
 * Evita que el propio movimiento borre la detección (falso positivo de rescatista).
 */
class ApproachVictimLock(
    private val holdMs: Long = 25_000L,
    private val bearingWindowDeg: Double = 50.0,
) {
    data class Lock(
        val id: Int,
        val bearing: Double,
        val distance: Double,
        val respRate: Double,
        val rubbleConf: Double,
        val lastGoodMs: Long,
        val wasBreathing: Boolean,
    )

    private val locks = linkedMapOf<Int, Lock>()

    fun reset() = locks.clear()

    fun remember(victim: TrappedVictim, nowMs: Long) {
        if (!victim.isBreathing && victim.confidence < 0.25) return
        locks[victim.id] = Lock(
            id = victim.id,
            bearing = victim.bearing,
            distance = victim.distance,
            respRate = victim.respRate,
            rubbleConf = victim.rubbleConfidence,
            lastGoodMs = nowMs,
            wasBreathing = victim.isBreathing || victim.heartBeating,
        )
    }

    fun prune(nowMs: Long) {
        val stale = locks.filterValues { nowMs - it.lastGoodMs > holdMs }.keys
        stale.forEach { locks.remove(it) }
    }

    fun activeLocks(nowMs: Long): List<Lock> {
        prune(nowMs)
        return locks.values.toList()
    }

    fun approaching(
        bearingDeg: Double,
        distanceM: Double,
        nowMs: Long,
    ): Lock? {
        prune(nowMs)
        return locks.values
            .filter { angularDistance(bearingDeg, it.bearing) <= bearingWindowDeg }
            .minByOrNull { angularDistance(bearingDeg, it.bearing) }
            ?.takeIf { lock ->
                // Camina hacia la víctima o ya está cerca
                distanceM <= lock.distance + 0.8 || distanceM < 2.5
            }
    }

    fun isHeld(id: Int, nowMs: Long): Boolean {
        val lock = locks[id] ?: return false
        return nowMs - lock.lastGoodMs <= holdMs
    }

    fun updateDistanceIfCloser(id: Int, distanceM: Double, bearingDeg: Double, nowMs: Long) {
        val prev = locks[id] ?: return
        locks[id] = prev.copy(
            distance = minOf(prev.distance, distanceM),
            bearing = bearingDeg,
            lastGoodMs = nowMs,
        )
    }

    private fun angularDistance(a: Double, b: Double): Double {
        var d = abs(a - b) % 360.0
        if (d > 180.0) d = 360.0 - d
        return d
    }
}
