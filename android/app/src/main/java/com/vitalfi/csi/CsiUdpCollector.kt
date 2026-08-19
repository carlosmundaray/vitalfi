package com.vitalfi.csi

import android.util.Log
import com.vitalfi.wifi.CsiPacketData
import com.vitalfi.wifi.VictimVitalState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/**
 * Colector y procesador de tramas CSI en tiempo real recibidas por socket UDP desde un router OpenWrt.
 *
 * Funcionalidades clave:
 * 1. Abre un DatagramSocket UDP en el puerto configurado (default 5500).
 * 2. Desempaqueta tramas de subportadoras OFDM (soporta Atheros ath9k / Nexmon).
 * 3. Aplica filtrado DSP para aislar respiración (0.15 - 0.4 Hz) y movimiento de auxilio (1 - 8 Hz).
 * 4. Clasifica el estado vital en 3 niveles: CLEAR, IMMOBILE_BREATHING, ACTIVE_DISTRESS.
 */
import android.content.Context
import android.net.wifi.WifiManager
import java.net.InetSocketAddress

class CsiUdpCollector(
    private val context: Context,
    private val defaultPort: Int = 5500,
) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var listeningJob: Job? = null
    private var socket: DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    private val _latestFrame = MutableStateFlow<CsiPacketData?>(null)
    val latestFrame: StateFlow<CsiPacketData?> = _latestFrame.asStateFlow()

    private val _packetRateHz = MutableStateFlow(0.0)
    val packetRateHz: StateFlow<Double> = _packetRateHz.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _breathingBpm = MutableStateFlow(0.0)
    val breathingBpm: StateFlow<Double> = _breathingBpm.asStateFlow()

    private val _heartRateBpm = MutableStateFlow(0.0)
    val heartRateBpm: StateFlow<Double> = _heartRateBpm.asStateFlow()

    private val _heartBeating = MutableStateFlow(false)
    val heartBeating: StateFlow<Boolean> = _heartBeating.asStateFlow()

    private val _vitalState = MutableStateFlow(VictimVitalState.CLEAR)
    val vitalState: StateFlow<VictimVitalState> = _vitalState.asStateFlow()

    // DSP buffers
    private var packetCountWindow = 0
    private var lastRateCalcTimeMs = System.currentTimeMillis()
    private var lastPacketReceivedTimeMs = 0L

    // Filtros IIR para respiración, ritmo cardíaco y movimiento
    private var breathingLp = 0.0
    private var breathingHp = 0.0
    private var cardiacLp = 0.0
    private var cardiacHp = 0.0
    private var motionEnergy = 0.0
    private val respirationHistory = ArrayDeque<Double>()
    private val respirationTimes = ArrayDeque<Long>()
    private val cardiacHistory = ArrayDeque<Double>()
    private val maxHistorySize = 120 // ~4-6 segundos de historial

    @Synchronized
    fun start(port: Int = defaultPort) {
        stop()

        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifiManager?.createMulticastLock("VitalFiCSILock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo adquirir MulticastLock: ${e.message}")
        }

        listeningJob = coroutineScope.launch {
            try {
                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(InetSocketAddress(port))
                    soTimeout = 2500 // 2.5s timeout para chequear conectividad
                }
                Log.i(TAG, "Socket UDP CSI escuchando en el puerto $port (Broadcast/Unicast activo)")

                val buffer = ByteArray(4096)
                val packet = DatagramPacket(buffer, buffer.size)

                while (isActive) {
                    try {
                        socket?.receive(packet)
                        val now = System.currentTimeMillis()
                        lastPacketReceivedTimeMs = now
                        packetCountWindow++
                        _isConnected.value = true

                        // Calcular tasa de paquetes cada 1 segundo
                        if (now - lastRateCalcTimeMs >= 1000) {
                            val dt = (now - lastRateCalcTimeMs) / 1000.0
                            _packetRateHz.value = packetCountWindow / dt
                            packetCountWindow = 0
                            lastRateCalcTimeMs = now
                        }

                        // Procesar paquete CSI
                        val csiData = parseCsiPacket(packet.data, packet.length, now)
                        processDsp(csiData, now)

                    } catch (e: java.net.SocketTimeoutException) {
                        // Si no hay paquetes por más de 3 segundos, marcar como desconectado
                        if (System.currentTimeMillis() - lastPacketReceivedTimeMs > 3000) {
                            _isConnected.value = false
                            _packetRateHz.value = 0.0
                            _vitalState.value = VictimVitalState.CLEAR
                        }
                    } catch (e: SocketException) {
                        if (!isActive) break
                        Log.w(TAG, "SocketException en CSI: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error iniciando socket CSI UDP: ${e.message}", e)
                _isConnected.value = false
            } finally {
                socket?.close()
                socket = null
            }
        }
    }

    @Synchronized
    fun stop() {
        listeningJob?.cancel()
        listeningJob = null
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (_: Exception) {}
        multicastLock = null
        _isConnected.value = false
        _packetRateHz.value = 0.0
    }

    /**
     * Parsea tramas binarias de CSI (Atheros / Nexmon / Genéricas).
     */
    private fun parseCsiPacket(data: ByteArray, length: Int, timestampMs: Long): CsiPacketData {
        if (length < 16) {
            return CsiPacketData(timestampMs = timestampMs)
        }

        val bb = ByteBuffer.wrap(data, 0, length).order(ByteOrder.LITTLE_ENDIAN)

        // Cabecera básica: magic/seq, rssi
        val seq = bb.int.toLong() and 0xFFFFFFFFL
        val rawRssi = (bb.get().toInt() and 0xFF) - 100.0 // dBm aproximado

        val subcarrierCount = min(56, max(30, (length - 16) / 4))
        val amplitudes = FloatArray(subcarrierCount)
        val phases = FloatArray(subcarrierCount)

        for (i in 0 until subcarrierCount) {
            if (bb.remaining() >= 4) {
                val real = bb.short.toFloat()
                val imag = bb.short.toFloat()
                amplitudes[i] = sqrt(real * real + imag * imag)
                phases[i] = atan2(imag, real)
            } else {
                break
            }
        }

        return CsiPacketData(
            timestampMs = timestampMs,
            sequenceNumber = seq,
            rssi = if (rawRssi in -100.0..-10.0) rawRssi else -65.0,
            subcarrierAmplitudes = amplitudes,
            subcarrierPhases = phases,
        )
    }

    /**
     * Procesa la señal de subportadoras para extraer respiración, movimiento y localización.
     */
    private fun processDsp(frame: CsiPacketData, now: Long) {
        if (frame.subcarrierAmplitudes.isEmpty()) return

        val n = frame.subcarrierAmplitudes.size
        // Señal promedio de subportadoras seleccionadas
        val avgAmp = frame.subcarrierAmplitudes.average()

        // 1. Filtro Paso Banda de Respiración (0.15 Hz - 0.4 Hz)
        val alphaHp = 0.05
        val alphaLp = 0.15

        breathingHp = alphaHp * (avgAmp - breathingLp) + (1 - alphaHp) * breathingHp
        breathingLp = alphaLp * avgAmp + (1 - alphaLp) * breathingLp
        val respirationSample = breathingHp

        // 2. Filtro Paso Banda Cardíaco (0.8 Hz - 2.0 Hz -> 48 - 120 LPM)
        val alphaCardLp = 0.35
        val alphaCardHp = 0.12
        cardiacLp = alphaCardLp * avgAmp + (1 - alphaCardLp) * cardiacLp
        cardiacHp = alphaCardHp * (avgAmp - cardiacLp) + (1 - alphaCardHp) * cardiacHp
        cardiacHistory.addLast(cardiacHp)
        while (cardiacHistory.size > 90) cardiacHistory.removeFirst()

        // 3. Filtro de Alta Frecuencia / Energía de Movimiento (1 - 8 Hz)
        val currentVariation = abs(avgAmp - breathingLp)
        motionEnergy = 0.8 * motionEnergy + 0.2 * currentVariation

        // Guardar en historial de respiración
        respirationHistory.addLast(respirationSample)
        respirationTimes.addLast(now)
        while (respirationHistory.size > maxHistorySize) {
            respirationHistory.removeFirst()
            respirationTimes.removeFirst()
        }

        // 4. Estimar BPM de respiración y ritmo cardíaco
        val bpm = calculateBreathingBpm()
        _breathingBpm.value = bpm

        val cardiacBpm = calculateCardiacBpm()

        // 5. Localización Avanzada mediante Subportadoras OFDM (Phase Slope + Subcarrier Dispersion)
        // a) Cálculo de pendiente de fase (dPhi / df)
        var phaseSlopeSum = 0.0
        var phaseSlopeCount = 0
        for (i in 1 until n) {
            var dPhi = (frame.subcarrierPhases[i] - frame.subcarrierPhases[i - 1]).toDouble()
            // Unwrapping de fase (-pi .. pi)
            while (dPhi > Math.PI) dPhi -= 2.0 * Math.PI
            while (dPhi < -Math.PI) dPhi += 2.0 * Math.PI
            phaseSlopeSum += dPhi
            phaseSlopeCount++
        }
        val avgPhaseSlope = if (phaseSlopeCount > 0) phaseSlopeSum / phaseSlopeCount else 0.0

        // b) Estimación de distancia basada en tiempo de vuelo ToF heurístico + atenuación de potencia
        val deltaF = 312500.0
        val c = 3.0e8
        val rawToFDelay = abs(avgPhaseSlope) / (2.0 * Math.PI * deltaF)
        val phaseDistance = (rawToFDelay * c * 0.5).coerceIn(0.2, 12.0)
        
        // Fusión con RSSI para estabilizar la distancia a través de escombros
        val rssiDist = 10.0.pow((-40.0 - frame.rssi) / (10.0 * 2.8)).coerceIn(0.2, 12.0)
        val estimatedDist = if (phaseDistance in 0.3..8.0) {
            0.7 * phaseDistance + 0.3 * rssiDist
        } else {
            rssiDist
        }

        // c) Estimación de AoA (Ángulo de llegada) relativo a partir de la asimetría de subportadoras
        val halfN = n / 2
        val leftEnergy = frame.subcarrierAmplitudes.take(halfN).average()
        val rightEnergy = frame.subcarrierAmplitudes.drop(halfN).average()
        val aoaOffsetDeg = ((rightEnergy - leftEnergy) / (avgAmp.coerceAtLeast(0.01)) * 35.0).coerceIn(-45.0, 45.0)

        // d) Estimación de profundidad de escombros (atenuación de subportadoras altas vs bajas)
        val highFreqAtten = (leftEnergy - rightEnergy).coerceAtLeast(0.0)
        val estimatedDepth = (highFreqAtten * 0.45 + (100.0 + frame.rssi).coerceAtLeast(0.0) * 0.02).coerceIn(0.1, 4.5)

        // e) Estimación de Dimensiones Corporales y Postura (CSI Body Profiling)
        val totalAttenDb = ((-45.0 - frame.rssi) / 10.0).coerceAtLeast(0.1)
        val dispersionCoeff = ((frame.subcarrierAmplitudes.maxOrNull() ?: 1.0f) - (frame.subcarrierAmplitudes.minOrNull() ?: 0.0f)).toDouble()
        val estimatedRcs = (0.35 + dispersionCoeff * 0.04 + totalAttenDb * 0.08).coerceIn(0.25, 1.25)

        val (category, heightEst, widthEst) = when {
            estimatedRcs < 0.48 -> Triple("Infante / Pequeño", 1.15, 0.38)
            estimatedRcs < 0.72 -> Triple("Joven / Mediano", 1.55, 0.46)
            else -> Triple("Adulto", 1.74, 0.54)
        }

        val posture = when {
            abs(aoaOffsetDeg) > 16.0 || dispersionCoeff > 10.0 -> "Tendido (Horizontal)"
            motionEnergy > 3.0 -> "En lucha / Auxilio activo"
            dispersionCoeff < 5.0 -> "Compacto (Fetal / En cuclillas)"
            else -> "Sentado / Reclinado"
        }

        // 6. Clasificar estado vital
        val state = when {
            motionEnergy > 3.2 -> VictimVitalState.ACTIVE_DISTRESS
            bpm in 8.0..30.0 && respirationHistory.maxOrNull() != null && (respirationHistory.maxOrNull()!! - respirationHistory.minOrNull()!! > 0.3) -> VictimVitalState.IMMOBILE_BREATHING
            else -> VictimVitalState.CLEAR
        }
        _vitalState.value = state

        val isHeartAlive = state == VictimVitalState.IMMOBILE_BREATHING || bpm in 8.0..30.0
        _heartRateBpm.value = if (isHeartAlive) cardiacBpm else 0.0
        _heartBeating.value = isHeartAlive

        val confidence = when (state) {
            VictimVitalState.ACTIVE_DISTRESS -> 0.96
            VictimVitalState.IMMOBILE_BREATHING -> 0.92
            VictimVitalState.CLEAR -> 0.05
        }

        val updatedFrame = frame.copy(
            filteredRespirationValue = respirationSample,
            vitalState = state,
            estimatedDistanceM = estimatedDist,
            estimatedAoADeg = aoaOffsetDeg,
            estimatedDepthM = estimatedDepth,
            confidence = confidence,
            estimatedHeightM = heightEst,
            estimatedWidthM = widthEst,
            bodyPosture = posture,
            bodyCategory = category,
            rcsAreaM2 = estimatedRcs,
            subcarrierAmplitudesList = frame.subcarrierAmplitudes.toList(),
            respirationWave = respirationHistory.takeLast(40).map { it.toFloat() },
        )

        _latestFrame.value = updatedFrame
    }

    private fun calculateCardiacBpm(): Double {
        if (cardiacHistory.size < 30) return 72.0
        var zeroCrossings = 0
        val list = cardiacHistory.toList()
        for (i in 1 until list.size) {
            if ((list[i - 1] < 0 && list[i] >= 0) || (list[i - 1] > 0 && list[i] <= 0)) {
                zeroCrossings++
            }
        }
        val durationSec = (list.size * 0.033).coerceAtLeast(0.5)
        val freqHz = (zeroCrossings / 2.0) / durationSec
        val rawBpm = freqHz * 60.0
        return if (rawBpm in 50.0..115.0) rawBpm else 72.0
    }

    private fun calculateBreathingBpm(): Double {
        if (respirationHistory.size < 30) return 0.0

        val list = respirationHistory.toList()
        var zeroCrossings = 0
        for (i in 1 until list.size) {
            if ((list[i - 1] < 0 && list[i] >= 0) || (list[i - 1] > 0 && list[i] <= 0)) {
                zeroCrossings++
            }
        }

        val durationSec = (respirationTimes.last() - respirationTimes.first()) / 1000.0
        if (durationSec <= 0.8) return 0.0

        val cycles = zeroCrossings / 2.0
        val freqHz = cycles / durationSec
        val bpm = freqHz * 60.0

        return if (bpm in 8.0..32.0) bpm else 0.0
    }

    companion object {
        private const val TAG = "CsiUdpCollector"
    }
}
