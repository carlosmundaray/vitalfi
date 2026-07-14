package com.whofi.vitalfi.dsp

/**
 * Distingue señales de persona atrapada bajo escombros vs rescatista en movimiento.
 *
 * Rescatista: alta actividad, varianza RSSI grande, rumbo inestable, sin respiración periódica,
 *             alta surfaceSignalLikelihood (señal fuerte sin atenuación de escombros).
 * Atrapada: respiración periódica, poca actividad de desplazamiento, señal más estable,
 *           baja surfaceSignalLikelihood.
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
    ): Classification {
        val periodicBreathing = detection.isBreathing &&
            detection.periodicity >= 0.14
        val weakBreathing = detection.isBreathing && detection.periodicity >= 0.08
        val highMovement = movementScore >= 0.38
        val moderateMovement = movementScore >= 0.22

        val activityOnly = detection.isActivity && !detection.isBreathing
        val highVariance = detection.variance > 0.10 || detection.noiseFloor > 0.50
        val walkingPattern = activityOnly && highVariance && detection.periodicity < 0.18
        val closeAndActive = detection.isActivity && detection.variance > 0.06 && !detection.isBreathing

        val surfaceLikely = detection.surfaceSignalLikelihood > 0.35

        val isLikelyRescuer = when {
            // Strong movement in sector + activity only → rescuer
            highMovement && activityOnly -> true
            // Walking pattern with any movement → rescuer
            walkingPattern && moderateMovement -> true
            // Activity + high variance + no breathing periodicity → rescuer
            activityOnly && highVariance && detection.periodicity < 0.14 -> true
            // Close & active with moderate movement and low rubble confidence → rescuer
            closeAndActive && moderateMovement && rubbleConf < 0.22 -> true
            // Surface signal likelihood is high + activity without breathing → rescuer
            surfaceLikely && activityOnly -> true
            // Moderate movement + surface signal + no periodic breathing → rescuer
            moderateMovement && surfaceLikely && !periodicBreathing -> true
            // High variance without any breathing periodicity → likely surface person
            detection.variance > 0.15 && detection.periodicity < 0.10 && activityOnly -> true
            else -> false
        }

        var trappedScore = 0.0
        if (periodicBreathing) trappedScore += 0.45
        else if (weakBreathing) trappedScore += 0.28
        if (rubbleConf >= 0.2) trappedScore += 0.2
        if (detection.periodicity >= 0.16) trappedScore += 0.15
        if (movementScore < 0.20) trappedScore += 0.18
        else if (movementScore < 0.30) trappedScore += 0.10
        if (detection.isBreathing && !detection.isActivity) trappedScore += 0.1
        if (detection.heartBeating) trappedScore += 0.08
        // Surface signal penalty — the stronger the surface likelihood, the less likely trapped
        trappedScore -= detection.surfaceSignalLikelihood * 0.4
        trappedScore -= movementScore * 0.40
        if (activityOnly) trappedScore -= 0.30
        trappedScore = trappedScore.coerceIn(0.0, 1.0)

        val trappedThreshold = if (strictMode) 0.45 else 0.30
        val isLikelyTrapped = when {
            isLikelyRescuer -> false
            strictMode -> (periodicBreathing || (weakBreathing && rubbleConf >= 0.18)) &&
                movementScore < 0.30 &&
                trappedScore >= trappedThreshold &&
                detection.surfaceSignalLikelihood < 0.50
            else -> detection.isBreathing || (rubbleConf >= 0.22 && weakBreathing && movementScore < 0.40)
        }

        val reason = when {
            isLikelyRescuer && walkingPattern -> "Movimiento alto — posible rescatista"
            isLikelyRescuer && highMovement -> "Sector con desplazamiento — excluido"
            isLikelyRescuer && surfaceLikely -> "Señal de superficie — rescatista filtrado"
            isLikelyRescuer && activityOnly -> "Actividad sin respiración — no es víctima"
            isLikelyTrapped && periodicBreathing -> "Respiración periódica bajo escombros"
            isLikelyTrapped -> "Señal vital estacionaria"
            activityOnly -> "Solo movimiento — ignorado en modo estricto"
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
    ): Boolean {
        // Activity without breathing and low rubble confidence → reject
        if (victim.isActivity && !victim.isBreathing && victim.rubbleConfidence < 0.22) {
            return false
        }
        // High movement without breathing → reject
        if (movementScore >= 0.40 && !victim.isBreathing) return false
        // Activity without breathing and high movement → reject even more aggressively
        if (victim.isActivity && !victim.isBreathing && movementScore >= 0.30) return false
        // Breathing with low movement → accept
        if (victim.isBreathing && movementScore < 0.35) return true

        return if (strictMode) {
            victim.isBreathing &&
                victim.rubbleConfidence >= 0.15 &&
                movementScore < 0.30 &&
                !(victim.isActivity && !victim.isBreathing)
        } else {
            victim.isBreathing ||
                (victim.rubbleConfidence >= 0.25 && victim.isBreathing)
        }
    }
}
