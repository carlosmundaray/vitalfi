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
 * 5. Estimación de distancia por fusión adaptativa: MUSIC ToF + Phase Slope + RSSI.
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

    // Procesadores avanzados de distancia CSI
    private val phaseProcessor = CsiPhaseProcessor()
    private val musicRanging = CsiMusicRanging()
    private val distanceKalman = CsiDistanceKalman()

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
        phaseProcessor.reset()
        musicRanging.reset()
        distanceKalman.reset()
    }

    /**
     * Parsea tramas binarias CSI con detección automática de protocolo:
     * - Protocolo VF v2 (magic 0x5646 en offset 14-15): cabecera 24 bytes + N subportadoras
     * - Protocolo legacy (sin magic): cabecera 16 bytes + subportadoras fijas
     */
    private fun parseCsiPacket(data: ByteArray, length: Int, timestampMs: Long): CsiPacketData {
        if (length < 16) {
            return CsiPacketData(timestampMs = timestampMs)
        }

        // Detectar protocolo VF v2 por magic number en bytes 14-15
        val isEnhanced = length >= 24 &&
            (data[14].toInt() and 0xFF) == 0x46 &&
            (data[15].toInt() and 0xFF) == 0x56

        return if (isEnhanced) {
            parseEnhancedPacket(data, length, timestampMs)
        } else {
            parseLegacyPacket(data, length, timestampMs)
        }
    }

    /**
     * Parsea paquetes del protocolo VF v2 (cabecera 24 bytes con metadatos reales del router).
     */
    private fun parseEnhancedPacket(data: ByteArray, length: Int, timestampMs: Long): CsiPacketData {
        val bb = ByteBuffer.wrap(data, 0, length).order(ByteOrder.LITTLE_ENDIAN)

        val seq        = bb.int.toLong() and 0xFFFFFFFFL         // [0..3]
        val rawRssi    = (bb.get().toInt() and 0xFF) - 100.0     // [4]
        val flags      = bb.get().toInt() and 0xFF               // [5]
        val noiseFloor = (bb.get().toInt() and 0xFF) - 100.0     // [6]
        val chanUtil   = (bb.get().toInt() and 0xFF).toDouble()  // [7]
        val mcsRate    = bb.get().toInt() and 0xFF               // [8]
        val stationCnt = bb.get().toInt() and 0xFF               // [9]
        val snr        = (bb.get().toInt() and 0xFF).toDouble()  // [10]
        val chanFreq   = bb.short.toInt() and 0xFFFF             // [11..12]
        val bw         = bb.get().toInt() and 0xFF               // [13]
        bb.short  // [14..15] magic (ya validado)
        val numSc      = bb.get().toInt() and 0xFF               // [16]
        val routerId   = bb.get().toInt() and 0xFF               // [17]
        val routerTs   = bb.int.toLong() and 0xFFFFFFFFL         // [18..21]
        bb.short  // [22..23] reserved

        val isRealCsi  = (flags and 0x01) != 0
        val hasNoise   = (flags and 0x02) != 0

        // Parsear subportadoras reales (si las hay)
        val actualSc = min(numSc, max(0, (length - 24) / 4))
        val amplitudes = FloatArray(actualSc)
        val phases = FloatArray(actualSc)

        for (i in 0 until actualSc) {
            if (bb.remaining() >= 4) {
                val real = bb.short.toFloat()
                val imag = bb.short.toFloat()
                amplitudes[i] = sqrt(real * real + imag * imag)
                phases[i] = atan2(imag, real)
            } else break
        }

        return CsiPacketData(
            timestampMs = timestampMs,
            sequenceNumber = seq,
            rssi = if (rawRssi in -100.0..-10.0) rawRssi else -65.0,
            subcarrierAmplitudes = amplitudes,
            subcarrierPhases = phases,
            noiseFloorDbm = if (hasNoise) noiseFloor else -95.0,
            channelUtilization = chanUtil,
            snrDb = snr,
            connectedStations = stationCnt,
            isRealCsi = isRealCsi,
            channelFreqMhz = if (chanFreq > 0) chanFreq else 2437,
            channelBwMhz = if (bw > 0) bw else 20,
            mcsIndex = mcsRate,
            routerId = routerId,
            routerUptimeMs = routerTs,
        )
    }

    /**
     * Parsea paquetes del protocolo legacy (16 bytes cabecera + subportadoras fijas).
     */
    private fun parseLegacyPacket(data: ByteArray, length: Int, timestampMs: Long): CsiPacketData {
        val bb = ByteBuffer.wrap(data, 0, length).order(ByteOrder.LITTLE_ENDIAN)

        val seq = bb.int.toLong() and 0xFFFFFFFFL
        val rawRssi = (bb.get().toInt() and 0xFF) - 100.0

        // Saltar al offset 16 donde realmente comienzan las subportadoras
        bb.position(16)
        val subcarrierCount = min(56, max(0, (length - 16) / 4))
        val amplitudes = FloatArray(subcarrierCount)
        val phases = FloatArray(subcarrierCount)

        for (i in 0 until subcarrierCount) {
            if (bb.remaining() >= 4) {
                val real = bb.short.toFloat()
                val imag = bb.short.toFloat()
                amplitudes[i] = sqrt(real * real + imag * imag)
                phases[i] = atan2(imag, real)
            } else break
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
        if (frame.subcarrierAmplitudes.isEmpty()) {
            // Modo RSSI-only: estimación básica de distancia, SIN datos inventados
            val rssiDist = if (frame.snrDb > 0) {
                10.0.pow((40.0 - frame.snrDb) / (10.0 * 2.0)).coerceIn(0.2, 12.0)
            } else {
                10.0.pow((-40.0 - frame.rssi) / (10.0 * 2.8)).coerceIn(0.2, 12.0)
            }
            val smoothedDist = distanceKalman.update(rssiDist, 0.15, now).coerceIn(0.2, 12.0)
            // RSSI-only: confianza baja, sin signos vitales, sin body profiling
            _breathingBpm.value = 0.0
            _heartRateBpm.value = 0.0
            _heartBeating.value = false
            _vitalState.value = VictimVitalState.CLEAR
            _latestFrame.value = frame.copy(
                estimatedDistanceM = smoothedDist,
                confidence = 0.10,
            )
            return
        }

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

        // 5. Localización Avanzada: Fusión Adaptativa MUSIC + Phase Slope + RSSI

        // a) Limpieza de fases con CsiPhaseProcessor (CPE/SFO removal + unwrap)
        val cleanedPhases = phaseProcessor.sanitizePhases(frame.subcarrierPhases)
        val phaseCoherence = phaseProcessor.phaseCoherence(cleanedPhases)

        // b) Distancia por pendiente de fase robusta (RANSAC-lite sobre fases limpias)
        val robustSlope = phaseProcessor.phaseSlope(cleanedPhases)
        val phaseDistance = phaseProcessor.distanceFromPhaseSlope(robustSlope)
            .coerceIn(0.2, 12.0)

        // c) Distancia por MUSIC ToF (super-resolución multipath)
        musicRanging.addSnapshot(frame.subcarrierAmplitudes, cleanedPhases)
        val musicResult = musicRanging.estimateDistance()
        val musicDistance = musicResult?.primaryDistanceM?.coerceIn(0.2, 12.0) ?: phaseDistance
        val musicConfidence = musicResult?.confidence ?: 0.0
        val multipathCount = musicResult?.multipathCount ?: 0

        // d) Distancia por RSSI log-distance (fallback estable)
        val rssiDist = if (frame.snrDb > 0) {
            10.0.pow((40.0 - frame.snrDb) / (10.0 * 2.0)).coerceIn(0.2, 12.0)
        } else {
            10.0.pow((-40.0 - frame.rssi) / (10.0 * 2.8)).coerceIn(0.2, 12.0)
        }

        // e) Fusión adaptativa multi-estimador con pesos basados en confianza
        //    MUSIC: domina cuando hay suficientes snapshots y buena separación eigenvalores
        //    Phase Slope: complementa cuando las fases son coherentes (bajo multipath)
        //    RSSI: fallback siempre presente para estabilidad
        val wMusic = 0.50 * musicConfidence.coerceIn(0.1, 1.0) *
            (if (musicRanging.snapshotCount() >= 12) 1.0 else 0.4)
        val wPhase = 0.30 * phaseCoherence.coerceIn(0.1, 1.0)
        val wRssi = 0.20 // Siempre presente como ancla
        val wTotal = wMusic + wPhase + wRssi

        val fusedDistance = (wMusic * musicDistance + wPhase * phaseDistance + wRssi * rssiDist) / wTotal

        // f) Confianza global de la fusión
        val fusionConfidence = ((wMusic * musicConfidence + wPhase * phaseCoherence + wRssi * 0.3) / wTotal)
            .coerceIn(0.0, 1.0)

        // g) Suavizado temporal con Kalman 1D dedicado a distancia CSI
        val estimatedDist = distanceKalman.update(fusedDistance, fusionConfidence, now)
            .coerceIn(0.2, 12.0)

        // 6. Clasificar estado vital — SOLO con datos CSI reales
        val state = if (!frame.isRealCsi) {
            // Sin CSI real: SOLO detectar movimiento brusco por variación de amplitud
            if (motionEnergy > 5.0) VictimVitalState.ACTIVE_DISTRESS
            else VictimVitalState.CLEAR
        } else {
            when {
                motionEnergy > 3.2 -> VictimVitalState.ACTIVE_DISTRESS
                bpm in 8.0..30.0 && respirationHistory.maxOrNull() != null && (respirationHistory.maxOrNull()!! - respirationHistory.minOrNull()!! > 0.3) -> VictimVitalState.IMMOBILE_BREATHING
                else -> VictimVitalState.CLEAR
            }
        }
        _vitalState.value = state

        // Ritmo cardíaco: SOLO reportar si hay datos reales suficientes, NUNCA inventar
        val isHeartAlive = frame.isRealCsi && (state == VictimVitalState.IMMOBILE_BREATHING || bpm in 8.0..30.0)
        _heartRateBpm.value = if (isHeartAlive && cardiacBpm > 0.0) cardiacBpm else 0.0
        _heartBeating.value = isHeartAlive && cardiacBpm > 0.0

        // Confianza basada en calidad REAL de datos, NO hardcoded
        val snrQuality = (frame.snrDb / 40.0).coerceIn(0.0, 1.0)  // SNR normalizado
        val csiQuality = if (frame.isRealCsi) 0.6 else 0.1          // Bonus por CSI real
        val baseConfidence = (snrQuality * 0.4 + csiQuality + fusionConfidence * 0.2).coerceIn(0.0, 1.0)
        val confidence = when (state) {
            VictimVitalState.ACTIVE_DISTRESS -> (baseConfidence * 0.95).coerceIn(0.05, 0.85)
            VictimVitalState.IMMOBILE_BREATHING -> (baseConfidence * 0.80).coerceIn(0.05, 0.70)
            VictimVitalState.CLEAR -> 0.05
        }

        // 7. Inferir postura corporal a partir de la señal CSI
        //    - ACTIVE_DISTRESS + alta energía → Auxilio / lucha
        //    - IMMOBILE_BREATHING + baja varianza de amplitud → Tendido
        //    - IMMOBILE_BREATHING + alta varianza → Sentado / Compacto
        val inferredPosture = when {
            state == VictimVitalState.ACTIVE_DISTRESS -> "Auxilio Activo"
            state == VictimVitalState.IMMOBILE_BREATHING -> {
                // Varianza de amplitud entre subportadoras indica la dispersión espacial
                val ampVariance = if (n > 1) {
                    val mean = avgAmp
                    frame.subcarrierAmplitudes.map { (it - mean) * (it - mean) }.average()
                } else 0.0
                when {
                    ampVariance > 80.0 -> "Compacto (Fetal)"
                    ampVariance > 30.0 -> "Sentado / Reclinado"
                    else -> "Tendido (Horizontal)"
                }
            }
            else -> "Indeterminado"
        }

        // 8. Categoría corporal por RCS (Radar Cross Section) estimado
        //    RCS ≈ amplitud media normalizada × factor de ancho de banda
        //    Adulto: RCS 0.5–1.2 m², Niño: 0.15–0.5 m²
        val rcsEstimate = (avgAmp / 40.0 * (frame.channelBwMhz / 20.0))
            .coerceIn(0.05, 2.0)
        val inferredCategory = when {
            rcsEstimate < 0.30 -> "Niño / Mascota"
            rcsEstimate < 0.60 -> "Adolescente / Persona menuda"
            rcsEstimate < 1.30 -> "Adulto"
            else -> "Adulto corpulento"
        }

        // 9. Dimensiones corporales estimadas a partir de la dispersión de amplitud
        //    La distribución de energía entre subportadoras correlaciona con el tamaño del reflector
        val ampRange = if (n > 2) {
            val sorted = frame.subcarrierAmplitudes.sorted()
            val p10 = sorted[(n * 0.1).toInt().coerceIn(0, n - 1)]
            val p90 = sorted[(n * 0.9).toInt().coerceIn(0, n - 1)]
            (p90 - p10).toDouble().coerceAtLeast(1.0)
        } else 10.0
        val heightEstimate = (0.8 + ampRange / 120.0).coerceIn(0.40, 2.10)
        val widthEstimate = (heightEstimate * 0.30).coerceIn(0.20, 0.80)

        // 10. Profundidad bajo escombros (atenuación extra más allá de distancia en espacio libre)
        //     Modelo: la diferencia entre RSSI esperado en espacio libre y RSSI real
        //     indica la atenuación del material de escombros.
        val expectedFreeSpaceRssi = -40.0 - 20.0 * kotlin.math.log10(estimatedDist.coerceAtLeast(0.3))
        val excessAttenuation = (expectedFreeSpaceRssi - frame.rssi).coerceAtLeast(0.0)
        // ~10 dB por capa de concreto de 15 cm → profundidad ≈ excessAtt / 10 * 0.15
        val depthEstimate = (excessAttenuation / 10.0 * 0.15).coerceIn(0.0, 5.0)

        // 11. Ángulo de llegada (AoA) — estimación simplificada por asimetría de amplitud
        //     Con una sola antena no hay AoA real, pero la asimetría espectral
        //     entre mitad inferior y superior de subportadoras da una pista
        val aoaEstimate = if (n >= 4) {
            val half = n / 2
            val lowerHalf = frame.subcarrierAmplitudes.take(half).average()
            val upperHalf = frame.subcarrierAmplitudes.takeLast(half).average()
            val asymmetry = (upperHalf - lowerHalf) / (lowerHalf + upperHalf + 1e-6)
            (asymmetry * 45.0).coerceIn(-60.0, 60.0)
        } else 0.0

        val updatedFrame = frame.copy(
            filteredRespirationValue = respirationSample,
            vitalState = state,
            estimatedDistanceM = estimatedDist,
            estimatedDepthM = depthEstimate,
            estimatedAoADeg = aoaEstimate,
            confidence = confidence,
            estimatedHeightM = heightEstimate,
            estimatedWidthM = widthEstimate,
            bodyPosture = inferredPosture,
            bodyCategory = inferredCategory,
            rcsAreaM2 = rcsEstimate,
            subcarrierAmplitudesList = frame.subcarrierAmplitudes.toList(),
            respirationWave = respirationHistory.takeLast(40).map { it.toFloat() },
            musicDistanceM = musicDistance,
            phaseDistanceM = phaseDistance,
            distanceConfidence = fusionConfidence,
            multipathCount = multipathCount,
        )

        _latestFrame.value = updatedFrame
    }

    private fun calculateCardiacBpm(): Double {
        // NUNCA inventar un valor cardíaco — vidas dependen de esto
        if (cardiacHistory.size < 30) return 0.0  // Datos insuficientes → desconocido
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
        return if (rawBpm in 50.0..115.0) rawBpm else 0.0  // Fuera de rango → desconocido
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
