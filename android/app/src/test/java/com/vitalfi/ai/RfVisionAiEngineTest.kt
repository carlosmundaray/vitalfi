package com.vitalfi.ai

import com.vitalfi.wifi.CsiPacketData
import com.vitalfi.wifi.VictimVitalState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RfVisionAiEngineTest {

    @Test
    fun testInferClearStateReturnsNull() {
        val (pose, grid) = RfVisionAiEngine.infer(
            csiFrame = null,
            vitalState = VictimVitalState.CLEAR,
            respBpm = 0.0,
            heartBpm = 0.0,
            heartBeating = false,
        )
        assertNull(pose)
        assertNull(grid)
    }

    @Test
    fun testInferHorizontalPosture() {
        val dummyAmps = FloatArray(56) { 20f }
        val dummyPhases = FloatArray(56) { 0f }
        val frame = CsiPacketData(
            timestampMs = System.currentTimeMillis(),
            rssi = -55.0,
            subcarrierAmplitudes = dummyAmps,
            subcarrierPhases = dummyPhases,
            bodyPosture = "Tendido (Horizontal)",
            confidence = 0.95,
        )

        val (pose, grid) = RfVisionAiEngine.infer(
            csiFrame = frame,
            vitalState = VictimVitalState.IMMOBILE_BREATHING,
            respBpm = 16.0,
            heartBpm = 72.0,
            heartBeating = true,
        )

        assertNotNull(pose)
        assertNotNull(grid)
        assertEquals("Tendido (Horizontal)", pose!!.postureLabel)
        assertTrue(pose.confidence >= 0.90f)
        assertTrue(pose.heartPulsing)
        assertEquals(256, grid!!.intensities.size)
    }

    @Test
    fun testInferDistressActivePose() {
        val dummyAmps = FloatArray(56) { 35f }
        val dummyPhases = FloatArray(56) { 0.1f }
        val frame = CsiPacketData(
            timestampMs = System.currentTimeMillis(),
            rssi = -50.0,
            subcarrierAmplitudes = dummyAmps,
            subcarrierPhases = dummyPhases,
            bodyPosture = "En lucha / Auxilio activo",
            confidence = 0.98,
        )

        val (pose, grid) = RfVisionAiEngine.infer(
            csiFrame = frame,
            vitalState = VictimVitalState.ACTIVE_DISTRESS,
            respBpm = 22.0,
            heartBpm = 110.0,
            heartBeating = true,
        )

        assertNotNull(pose)
        assertNotNull(grid)
        assertTrue(pose!!.postureLabel.contains("Auxilio"))
        assertTrue(pose.confidence >= 0.95f)
    }
}
