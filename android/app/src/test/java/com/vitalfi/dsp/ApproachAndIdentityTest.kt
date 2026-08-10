package com.vitalfi.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApproachAndIdentityTest {

    @Test
    fun personIdentity_assignsStableLettersAndRename() {
        val reg = PersonIdentityRegistry()
        assertEquals("Persona A", reg.displayLabel(1))
        assertEquals("Persona B", reg.displayLabel(2))
        assertEquals("PA", reg.shortCode(1))
        reg.rename(1, "Niño norte")
        assertEquals("Niño norte", reg.displayLabel(1))
        reg.rename(1, "  ")
        assertEquals("Persona A", reg.displayLabel(1))
    }

    @Test
    fun approachLock_keepsVictimWhileWalkingCloser() {
        val lock = ApproachVictimLock()
        val now = 1_000L
        val victim = TrappedVictim(
            id = 1,
            label = "P1",
            x = 1.0,
            y = 2.0,
            bearing = 90.0,
            distance = 3.0,
            depth = -0.5,
            isBreathing = true,
            isActivity = false,
            heartBeating = true,
            heartRateBpm = 70.0,
            respRate = 14.0,
            confidence = 0.7,
            rubbleConfidence = 0.5,
            proximity = "CERCA",
            status = "Viva",
            score = 0.8,
            lastSeenMs = now,
        )
        lock.remember(victim, now, reflectionVar = 0.10)
        // Aunque la distancia Wi‑Fi live empeore al caminar, sigue en modo acercamiento por rumbo
        val approaching = lock.approaching(bearingDeg = 95.0, distanceM = 5.5, nowMs = now + 2_000L)
        assertNotNull(approaching)
        assertTrue(lock.isHeld(1, now + 5_000L))

        val closer = lock.updateWhileApproaching(
            id = 1,
            liveDistanceM = 5.5,
            reflectionVar = 0.08,
            bearingDeg = 92.0,
            nowMs = now + 3_000L,
            stepsDelta = 3,
            headingAligned = true,
            walking = true,
        )
        assertTrue("steps should pull toward person/point zero, got $closer", closer < 3.0)
        assertTrue(closer <= 3.0 - 1.2)

        // Ruido Wi‑Fi lejos no debe alejar el punto
        val still = lock.updateWhileApproaching(
            id = 1,
            liveDistanceM = 6.0,
            reflectionVar = 0.05,
            bearingDeg = 92.0,
            nowMs = now + 4_000L,
            stepsDelta = 0,
            headingAligned = true,
            walking = false,
        )
        assertTrue(still <= closer + 0.01)
    }

    @Test
    fun trappedFilter_approachHold_notRescuer() {
        val detection = DetectionResult(
            isBreathing = false,
            isActivity = true,
            heartBeating = false,
            heartRateBpm = 0.0,
            respFreq = 0.0,
            respRate = 0.0,
            variance = 0.2,
            snr = 1.0,
            noiseFloor = 0.1,
            uniqueValues = 4,
            signalFlat = false,
            status = "Actividad",
            hint = "",
            confidence = 0.4,
            periodicity = 0.05,
            surfaceSignalLikelihood = 0.5,
        )
        val c = TrappedPersonFilter.classify(
            detection = detection,
            rubbleConf = 0.3,
            movementScore = 0.5,
            strictMode = true,
            approachingLockedVictim = true,
        )
        assertTrue(c.isLikelyTrapped)
        assertFalse(c.isLikelyRescuer)
        assertTrue(c.reason.contains("Acercándote"))
    }

    @Test
    fun trappedFilter_walkingPerson_isDetected() {
        val detection = DetectionResult(
            isBreathing = false,
            isActivity = true,
            heartBeating = false,
            heartRateBpm = 0.0,
            respFreq = 0.0,
            respRate = 0.0,
            variance = 0.25,
            snr = 1.2,
            noiseFloor = 0.4,
            uniqueValues = 6,
            signalFlat = false,
            status = "Actividad",
            hint = "",
            confidence = 0.5,
            periodicity = 0.06,
            surfaceSignalLikelihood = 0.4,
        )
        val open = TrappedPersonFilter.classify(
            detection = detection,
            rubbleConf = 0.1,
            movementScore = 0.35,
            strictMode = false,
        )
        assertTrue(open.isLikelyTrapped)
        assertFalse(open.isLikelyRescuer)
        assertTrue(open.reason.contains("movimiento") || open.reason.contains("actividad"))
    }
}
