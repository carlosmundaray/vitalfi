package com.vitalfi.dsp

/**
 * Registro de una víctima rescatada con su coordenada exacta en formato GMS (Grados, Minutos, Segundos),
 * signos vitales, profundidad y fecha/hora del rescate.
 */
data class RescuedVictimRecord(
    val id: String,
    val victimId: Int,
    val label: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val latitudeGms: String,
    val longitudeGms: String,
    val fullGms: String,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitudeM: Double = 0.0,
    val depthM: Double = 0.0,
    val respRate: Double = 0.0,
    val heartRate: Double = 0.0,
    val confidence: Double = 0.0,
    val signatureCode: String = "",
    val rescueNotes: String = "Rescate completado en sitio con radar VitalFi AR",
)
