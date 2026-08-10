package com.vitalfi.dsp

/**
 * Distingue señales de persona (quieta / caminando) vs ruido extremo de rescatista.
 *
 * Importante: una persona caminando DEBE poder aparecer en el radar.
 * Solo se marca “rescatista” en casos muy claros de desplazamiento propio del operador.
 */
object TrappedPersonFilter {

    data class Classification(
        val isLikelyTrapped: Boolean,
        val isLikelyRescuer: Boolean,
        val trappedScore: Double,
        val reason: String,
    )

    fun classify(
        detection: DetectionResult,
        rubbleConf: Double,
        movementScore: Double,
        strictMode: Boolean,
        approachingLockedVictim: Boolean = false,
    ): Classification {
        val periodicBreathing = detection.isBreathing && detection.periodicity >= 0.14
        val weakBreathing = detection.isBreathing && detection.periodicity >= 0.08
        val activityOnly = detection.isActivity && !detection.isBreathing
        val highMovement = movementScore >= 0.55
        val surfaceLikely = detection.surfaceSignalLikelihood > 0.55

        if (approachingLockedVictim) {
            return Classification(
                isLikelyTrapped = true,
                isLikelyRescuer = false,
                trappedScore = maxOf(0.55, if (detection.isBreathing) 0.75 else 0.55),
                reason = "Acercándote a víctima anclada — mantén rumbo",
            )
        }

        // Solo filtrar rescatista en modo estricto y con evidencia fuerte
        val isLikelyRescuer = strictMode && highMovement && activityOnly && surfaceLikely &&
            detection.periodicity < 0.08 && rubbleConf < 0.12

        var trappedScore = 0.0
        if (periodicBreathing) trappedScore += 0.45
        else if (weakBreathing) trappedScore += 0.28
        if (detection.isActivity) trappedScore += 0.25
        if (rubbleConf >= 0.2) trappedScore += 0.15
        if (detection.periodicity >= 0.16) trappedScore += 0.12
        if (detection.heartBeating) trappedScore += 0.08
        if (isLikelyRescuer) trappedScore *= 0.2
        trappedScore = trappedScore.coerceIn(0.0, 1.0)

        val isLikelyTrapped = when {
            isLikelyRescuer -> false
            // Modo amplio / normal: respiración O actividad (persona caminando)
            !strictMode -> detection.isBreathing || detection.isActivity
            // Modo estricto: prioriza respiración, pero no oculta actividad clara
            detection.isBreathing -> true
            detection.isActivity && movementScore < 0.60 -> true
            else -> rubbleConf >= 0.18 && detection.isActivity
        }

        val reason = when {
            isLikelyRescuer -> "Movimiento extremo — posible rescatista"
            detection.isBreathing && periodicBreathing -> "Respiración periódica detectada"
            detection.isBreathing -> "Persona detectada (patrón vital)"
            detection.isActivity -> "Persona en movimiento / actividad"
            else -> "Analizando..."
        }

        return Classification(
            isLikelyTrapped = isLikelyTrapped,
            isLikelyRescuer = isLikelyRescuer,
            trappedScore = trappedScore,
            reason = reason,
        )
    }

    fun isLikelyTrappedVictim(
        victim: TrappedVictim,
        movementScore: Double,
        strictMode: Boolean,
        heldByApproachLock: Boolean = false,
    ): Boolean {
        if (heldByApproachLock) return true
        if (victim.isBreathing) return true
        // Persona caminando / actividad: mostrar en radar
        if (victim.isActivity) {
            return if (strictMode) movementScore < 0.70 else true
        }
        return if (strictMode) {
            victim.rubbleConfidence >= 0.15 && movementScore < 0.50
        } else {
            victim.rubbleConfidence >= 0.20
        }
    }
}
