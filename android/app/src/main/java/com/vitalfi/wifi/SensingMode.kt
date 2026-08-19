package com.vitalfi.wifi

/**
 * Modo del motor de detección en VitalFi:
 * - RSSI_NATIVE: Escaneo local en el smartphone (2 estados: Despejado / Movimiento).
 * - CSI_OPENWRT: Captura de tramas OFDM CSI por socket UDP desde un router OpenWrt (3 estados: Despejado / Respiración / Auxilio).
 */
enum class SensingEngineMode {
    RSSI_NATIVE,
    CSI_OPENWRT,
}

/**
 * Estados vitales clasificados para la víctima o entorno.
 */
enum class VictimVitalState {
    CLEAR,               // Despejado / Solo ruido de fondo
    IMMOBILE_BREATHING,  // Persona inmóvil / Respiración detectada (CSI)
    ACTIVE_DISTRESS,     // Intento de auxilio / Movimiento / Golpes
}

/**
 * Trama CSI procesada a partir de los paquetes UDP del router OpenWrt.
 */
data class CsiPacketData(
    val timestampMs: Long = System.currentTimeMillis(),
    val sequenceNumber: Long = 0L,
    val rssi: Double = 0.0,
    val subcarrierAmplitudes: FloatArray = FloatArray(0),
    val subcarrierPhases: FloatArray = FloatArray(0),
    val filteredRespirationValue: Double = 0.0,
    val vitalState: VictimVitalState = VictimVitalState.CLEAR,
    val estimatedDistanceM: Double = 0.0,
    val estimatedAoADeg: Double = 0.0,
    val estimatedDepthM: Double = 0.0,
    val confidence: Double = 0.0,
    val subcarrierAmplitudesList: List<Float> = emptyList(),
    val respirationWave: List<Float> = emptyList(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CsiPacketData

        if (timestampMs != other.timestampMs) return false
        if (sequenceNumber != other.sequenceNumber) return false
        if (rssi != other.rssi) return false
        if (!subcarrierAmplitudes.contentEquals(other.subcarrierAmplitudes)) return false
        if (!subcarrierPhases.contentEquals(other.subcarrierPhases)) return false
        if (filteredRespirationValue != other.filteredRespirationValue) return false
        if (vitalState != other.vitalState) return false
        if (estimatedDistanceM != other.estimatedDistanceM) return false
        if (estimatedAoADeg != other.estimatedAoADeg) return false
        if (estimatedDepthM != other.estimatedDepthM) return false
        if (confidence != other.confidence) return false
        if (subcarrierAmplitudesList != other.subcarrierAmplitudesList) return false
        if (respirationWave != other.respirationWave) return false

        return true
    }

    override fun hashCode(): Int {
        var result = timestampMs.hashCode()
        result = 31 * result + sequenceNumber.hashCode()
        result = 31 * result + rssi.hashCode()
        result = 31 * result + subcarrierAmplitudes.contentHashCode()
        result = 31 * result + subcarrierPhases.contentHashCode()
        result = 31 * result + filteredRespirationValue.hashCode()
        result = 31 * result + vitalState.hashCode()
        result = 31 * result + estimatedDistanceM.hashCode()
        result = 31 * result + estimatedAoADeg.hashCode()
        result = 31 * result + estimatedDepthM.hashCode()
        result = 31 * result + confidence.hashCode()
        result = 31 * result + subcarrierAmplitudesList.hashCode()
        result = 31 * result + respirationWave.hashCode()
        return result
    }
}
