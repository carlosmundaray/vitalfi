package com.vitalfi.dsp

import kotlin.math.abs

/**
 * Ancla a la persona detectada mientras te orientas y caminas hacia ella.
 * Distancia del radar = ubicación de ESA persona (señal Wi‑Fi + pasos reales alineados).
 */
class ApproachVictimLock(
    private val holdMs: Long = 40_000L,
    private val bearingWindowDeg: Double = 65.0,
    private val metersPerStep: Double = 0.55,
) {
    data class Lock(
        val id: Int,
        val bearing: Double,
        val distance: Double,
        val respRate: Double,
        val rubbleConf: Double,
        val lastGoodMs: Long,
        val wasBreathing: Boolean,
        val peakPersonSignal: Double = 0.0,
    )

    private val locks = linkedMapOf<Int, Lock>()

    fun reset() = locks.clear()

    fun remember(victim: TrappedVictim, nowMs: Long, reflectionVar: Double = 0.0) {
        val prev = locks[victim.id]
        if (prev == null && !victim.isBreathing && !victim.isActivity && victim.confidence < 0.18) {
            return
        }

        val peak = maxOf(prev?.peakPersonSignal ?: 0.0, reflectionVar)
        val dist = if (prev != null) minOf(prev.distance, victim.distance) else victim.distance
        locks[victim.id] = Lock(
            id = victim.id,
            bearing = if (prev != null) {
                smoothBearing(prev.bearing, victim.bearing, 0.18)
            } else {
                victim.bearing
            },
            distance = dist.coerceIn(0.15, 8.0),
            respRate = victim.respRate,
            rubbleConf = maxOf(prev?.rubbleConf ?: 0.0, victim.rubbleConfidence),
            lastGoodMs = nowMs,
            wasBreathing = victim.isBreathing || victim.heartBeating || (prev?.wasBreathing == true),
            peakPersonSignal = peak,
        )
    }

    fun prune(nowMs: Long) {
        locks.filterValues { nowMs - it.lastGoodMs > holdMs }.keys.forEach { locks.remove(it) }
    }

    fun get(id: Int): Lock? = locks[id]

    fun approaching(bearingDeg: Double, distanceM: Double, nowMs: Long): Lock? {
        prune(nowMs)
        return locks.values
            .filter {
                angularDistance(bearingDeg, it.bearing) <= bearingWindowDeg &&
                    (distanceM <= 0.0 || kotlin.math.abs(it.distance - distanceM) <= 15.0)
            }
            .minByOrNull { angularDistance(bearingDeg, it.bearing) }
    }

    fun isHeld(id: Int, nowMs: Long): Boolean {
        val lock = locks[id] ?: return false
        return nowMs - lock.lastGoodMs <= holdMs
    }

    fun headingAligned(phoneBearingDeg: Double, id: Int, maxDeg: Double = 55.0): Boolean {
        val lock = locks[id] ?: return false
        return angularDistance(phoneBearingDeg, lock.bearing) <= maxDeg
    }

    /**
     * Actualiza distancia a la persona:
     * 1) Si la señal de persona indica más cerca → aceptar inmediatamente
     * 2) Proyección de pasos reales en dirección a la víctima → acerca el punto al centro
     * 3) Nunca aleja por ruido
     */
    fun updateWhileApproaching(
        id: Int,
        liveDistanceM: Double,
        reflectionVar: Double,
        bearingDeg: Double,
        nowMs: Long,
        stepsDelta: Int = 0,
        headingAligned: Boolean = false,
        walking: Boolean = false,
    ): Double {
        val prev = locks[id] ?: return liveDistanceM.coerceIn(0.15, 8.0)
        val personSig = reflectionVar.coerceAtLeast(0.0)
        val live = liveDistanceM.coerceIn(0.15, 8.0)

        var d = minOf(prev.distance, live)

        // Señal de persona más fuerte → está más cerca (dato Wi‑Fi real)
        if (prev.peakPersonSignal > 0.004 && personSig > prev.peakPersonSignal * 1.05) {
            val gain = ((personSig / prev.peakPersonSignal) - 1.0).coerceIn(0.0, 2.0)
            d = (d * (1.0 - 0.25 * gain)).coerceAtLeast(0.15)
        }

        // Proyección de movimiento según ángulo hacia la víctima
        val angleDiff = angularDistance(bearingDeg, prev.bearing)
        val rad = Math.toRadians(angleDiff.coerceIn(0.0, 180.0))
        val cosFactor = Math.cos(rad).coerceAtLeast(0.0) // 1.0 si camina directo, 0.707 a 45°, 0 si camina de espaldas

        if (cosFactor > 0.15) {
            if (stepsDelta > 0) {
                val stepDist = stepsDelta * metersPerStep * cosFactor
                d = (d - stepDist).coerceAtLeast(0.15)
            } else if (walking) {
                // Avance continuo mientras camina hacia el objetivo (~0.12m cada 500ms)
                d = (d - 0.12 * cosFactor).coerceAtLeast(0.15)
            }
        }

        d = minOf(d, prev.distance).coerceIn(0.15, 8.0)

        locks[id] = prev.copy(
            distance = d,
            bearing = smoothBearing(prev.bearing, bearingDeg, if (cosFactor > 0.5) 0.10 else 0.04),
            peakPersonSignal = maxOf(prev.peakPersonSignal, personSig),
            lastGoodMs = nowMs,
        )
        return d
    }

    private fun smoothBearing(prev: Double, next: Double, alpha: Double): Double {
        var delta = next - prev
        while (delta > 180) delta -= 360
        while (delta < -180) delta += 360
        return ((prev + alpha * delta) % 360.0 + 360.0) % 360.0
    }

    fun angularDistance(a: Double, b: Double): Double {
        var d = abs(a - b) % 360.0
        if (d > 180.0) d = 360.0 - d
        return d
    }
}
