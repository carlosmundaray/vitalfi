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
        lock.remember(victim, now)
        val approaching = lock.approaching(bearingDeg = 95.0, distanceM = 2.2, nowMs = now + 2_000L)
        assertNotNull(approaching)
        assertTrue(lock.isHeld(1, now + 5_000L))
        assertFalse(lock.isHeld(1, now + 40_000L))
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
}
