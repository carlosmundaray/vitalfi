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
        // Ahora el label incluye "Ilustrativo" para no engañar rescatistas
        assertTrue(pose!!.postureLabel.contains("Ilustrativo"))
        assertTrue(pose.postureLabel.contains("Tendido"))
        assertTrue(pose.isIllustrative)
        assertTrue(pose.heartPulsing)
        assertEquals(72.0, pose.heartBpm, 0.01)  // Pasa el valor real, no inventa
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
        assertTrue(pose!!.postureLabel.contains("Ilustrativo"))
        assertTrue(pose.postureLabel.contains("Auxilio"))
        assertTrue(pose.isIllustrative)
        // Confianza baja porque es ilustrativo, NO detección real
        assertTrue(pose.confidence <= 0.30f)
    }

    @Test
    fun testInferStandingPosture() {
        val dummyAmps = FloatArray(56) { 25f }
        val dummyPhases = FloatArray(56) { 0.05f }
        val frame = CsiPacketData(
            timestampMs = System.currentTimeMillis(),
            rssi = -52.0,
            subcarrierAmplitudes = dummyAmps,
            subcarrierPhases = dummyPhases,
            bodyPosture = "De Pie (Erguido)",
            confidence = 0.90,
        )

        val (pose, grid) = RfVisionAiEngine.infer(
            csiFrame = frame,
            vitalState = VictimVitalState.IMMOBILE_BREATHING,
            respBpm = 15.0,
            heartBpm = 68.0,
            heartBeating = true,
        )

        assertNotNull(pose)
        assertNotNull(grid)
        assertTrue(pose!!.postureLabel.contains("De Pie") || pose.postureLabel.contains("Erguido"))
        assertEquals(68.0, pose.heartBpm, 0.01)
    }

    @Test
    fun testInferWalkingPosture() {
        val dummyAmps = FloatArray(56) { 30f }
        val dummyPhases = FloatArray(56) { 0.2f }
        val frame = CsiPacketData(
            timestampMs = System.currentTimeMillis(),
            rssi = -48.0,
            subcarrierAmplitudes = dummyAmps,
            subcarrierPhases = dummyPhases,
            bodyPosture = "Caminando / En Movimiento",
            confidence = 0.92,
        )

        val (pose, grid) = RfVisionAiEngine.infer(
            csiFrame = frame,
            vitalState = VictimVitalState.ACTIVE_DISTRESS,
            respBpm = 20.0,
            heartBpm = 85.0,
            heartBeating = true,
        )

        assertNotNull(pose)
        assertNotNull(grid)
        assertTrue(pose!!.postureLabel.contains("Caminando") || pose.postureLabel.contains("Movimiento"))
    }
}
