package com.whofi.vitalfi

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.whofi.vitalfi.dsp.MultiVictimTracker
import com.whofi.vitalfi.dsp.TrappedVictim
import com.whofi.vitalfi.dsp.HeatPoint
import com.whofi.vitalfi.dsp.PositionEstimate
import com.whofi.vitalfi.dsp.RubbleConfidenceTracker
import com.whofi.vitalfi.dsp.VitalSignalDetector
import com.whofi.vitalfi.dsp.polarToXY
import com.whofi.vitalfi.audio.VictimAlertPlayer
import com.whofi.vitalfi.export.CsvExporter
import com.whofi.vitalfi.export.SessionLogEntry
import com.whofi.vitalfi.ml.SignatureModel
import com.whofi.vitalfi.sensor.CompassReader
import com.whofi.vitalfi.settings.SettingsStore
import com.whofi.vitalfi.ui.RadarViewport
import com.whofi.vitalfi.ui.TrailPoint3D
import com.whofi.vitalfi.wifi.RssiSample
import com.whofi.vitalfi.wifi.ScannedNetwork
import com.whofi.vitalfi.wifi.WifiCollectMode
import com.whofi.vitalfi.wifi.WifiCoverageEstimator
import com.whofi.vitalfi.wifi.WifiRssiCollector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.hypot
import kotlin.math.max

data class VitalFiUiState(
    val ssid: String = "",
    val rssiDbm: Int? = null,
    val connected: Boolean = false,
    val monitoring: Boolean = false,
    val wifiWithoutInternet: Boolean = false,
    val wifiEnabled: Boolean = true,
    val sampleCount: Int = 0,
    val maxSamples: Int = 180,
    val status: String = "Iniciando...",
    val hint: String = "Solo señal Wi-Fi local — no requiere internet",
    val isBreathing: Boolean = false,
    val isActivity: Boolean = false,
    val respRate: Double = 0.0,
    val confidence: Double = 0.0,
    val rubbleConfidence: Double = 0.0,
    val calibrating: Boolean = true,
    val calibRemainingSec: Int = 30,
    val proximity: String = "—",
    val bearingDeg: Double = 0.0,
    val compassLabel: String = "Brújula",
    val position: PositionEstimate? = null,
    val victims: List<TrappedVictim> = emptyList(),
    val selectedVictim: TrappedVictim? = null,
    val heartBeating: Boolean = false,
    val heartRateBpm: Double = 0.0,
    val heatmap: List<HeatPoint> = emptyList(),
    val recentQuality: List<Double> = emptyList(),
    val collectMode: WifiCollectMode = WifiCollectMode.PASSIVE_SCAN,
    val scannedNetworks: List<ScannedNetwork> = emptyList(),
    val showNetworkPicker: Boolean = false,
    val isScanningNetworks: Boolean = false,
    val showRadar3D: Boolean = true,
    val viewAzim: Double = -55.0,
    val viewElev: Double = 28.0,
    val trail: List<TrailPoint3D> = emptyList(),
    val radarViewport: RadarViewport = RadarViewport(),
    val radarFullscreen: Boolean = false,
    val mlReady: Boolean = false,
    val mlScore: Double = 0.0,
    val mlStatus: String = "ML desactivado",
    val wifiCoverageRadiusM: Double = 0.0,
    val alertSoundEnabled: Boolean = true,
    val toastMessage: String? = null,
)

class VitalFiViewModel(app: Application) : AndroidViewModel(app) {

    private val collector = WifiRssiCollector(app, sampleRateHz = 2.0, maxSamples = 180)
    private val compass = CompassReader(app)
    private val victimTracker = MultiVictimTracker()
    private val rubbleTracker = RubbleConfidenceTracker()
    private val signatureModel = SignatureModel(app)
    private val settingsStore = SettingsStore(app)
    private val alertPlayer = VictimAlertPlayer()
    private val sessionLog = mutableListOf<SessionLogEntry>()
    private val positionTrail = mutableListOf<TrailPoint3D>()
    private val alertedVictimIds = mutableSetOf<Int>()

    private val _uiState = MutableStateFlow(
        VitalFiUiState(
            maxSamples = collector.maxSamples,
            mlReady = signatureModel.isReady,
            mlStatus = signatureModel.statusLabel,
            alertSoundEnabled = settingsStore.alertSoundEnabled,
        ),
    )
    val uiState: StateFlow<VitalFiUiState> = _uiState.asStateFlow()

    private var analysisJob: Job? = null
    private var running = false
    private var pickerDismissedByUser = false
    private var hasAutoOpenedPicker = false
    private var cachedRecentQuality = emptyList<Double>()
    private var connectedNoSamplesSinceMs: Long = 0L
    private var suggestedPassiveFallback = false
    private var compassJob: Job? = null

    fun start() {
        if (running) return
        running = true

        compass.start()
        startCompassUpdates()
        if (collector.mode == WifiCollectMode.CONNECTED || !collector.targetSsid.isNullOrBlank()) {
            collector.start(viewModelScope)
        }
        if (rubbleTracker.calibrating) {
            rubbleTracker.begin(System.currentTimeMillis())
        }

        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            while (isActive) {
                runAnalysisTick()
                delay(500)
            }
        }

        if (collector.mode == WifiCollectMode.PASSIVE_SCAN &&
            collector.targetSsid.isNullOrBlank() &&
            !pickerDismissedByUser &&
            !hasAutoOpenedPicker
        ) {
            hasAutoOpenedPicker = true
            openPassiveMode()
        }
    }

    fun stop() {
        running = false
        analysisJob?.cancel()
        compassJob?.cancel()
        collector.stop()
        compass.stop()
    }

    override fun onCleared() {
        stop()
        alertPlayer.release()
        super.onCleared()
    }

    fun setAlertSoundEnabled(enabled: Boolean) {
        settingsStore.alertSoundEnabled = enabled
        _uiState.value = _uiState.value.copy(alertSoundEnabled = enabled)
    }

    fun reset() {
        collector.clear()
        victimTracker.reset()
        rubbleTracker.reset(System.currentTimeMillis())
        compass.resetManual()
        sessionLog.clear()
        positionTrail.clear()
        alertedVictimIds.clear()
        cachedRecentQuality = emptyList()
        _uiState.value = _uiState.value.copy(
            trail = emptyList(),
            victims = emptyList(),
            selectedVictim = null,
            heartBeating = false,
            heartRateBpm = 0.0,
            position = null,
            heatmap = emptyList(),
            isBreathing = false,
            isActivity = false,
            status = "Reiniciado",
            hint = "Calibrando de nuevo. Permanece quieto 30 s.",
            recentQuality = emptyList(),
            sampleCount = 0,
            mlReady = signatureModel.isReady,
            mlScore = 0.0,
            mlStatus = signatureModel.statusLabel,
            wifiCoverageRadiusM = 0.0,
        )
    }

    fun bearingLeft() {
        compass.adjustBearing(-10.0)
    }

    fun bearingRight() {
        compass.adjustBearing(10.0)
    }

    fun rotateViewAzim(delta: Double) {
        val azim = _uiState.value.viewAzim + delta
        _uiState.value = _uiState.value.copy(viewAzim = ((azim % 360) + 360) % 360)
    }

    fun rotateViewElev(delta: Double) {
        val newElev = (_uiState.value.viewElev + delta).coerceIn(8.0, 82.0)
        _uiState.value = _uiState.value.copy(viewElev = newElev)
    }

    fun resetView3D() {
        _uiState.value = _uiState.value.copy(viewAzim = -55.0, viewElev = 28.0)
    }

    fun toggleRadar3D() {
        _uiState.value = _uiState.value.copy(showRadar3D = !_uiState.value.showRadar3D)
    }

    fun setConnectedMode() {
        pickerDismissedByUser = false
        suggestedPassiveFallback = false
        connectedNoSamplesSinceMs = 0L
        collector.setConnectedMode()
        restartCollector()
        _uiState.value = _uiState.value.copy(
            collectMode = WifiCollectMode.CONNECTED,
            showNetworkPicker = false,
            hint = "Conectado al router — funciona sin internet (~2 Hz).",
        )
    }

    fun openPassiveMode() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isScanningNetworks = true,
                showNetworkPicker = true,
            )
            val networks = collector.scanNetworksAwait()
            _uiState.value = _uiState.value.copy(
                scannedNetworks = networks,
                isScanningNetworks = false,
                showNetworkPicker = true,
                toastMessage = if (networks.isEmpty()) {
                    "No se encontraron redes. Activa Wi-Fi y ubicación."
                } else {
                    _uiState.value.toastMessage
                },
            )
        }
    }

    fun selectPassiveNetwork(network: ScannedNetwork) {
        pickerDismissedByUser = false
        collector.selectPassiveTarget(network)
        restartCollector()
        rubbleTracker.reset(System.currentTimeMillis())
        sessionLog.clear()
        positionTrail.clear()
        alertedVictimIds.clear()
        victimTracker.reset()
        cachedRecentQuality = emptyList()
        _uiState.value = _uiState.value.copy(
            collectMode = WifiCollectMode.PASSIVE_SCAN,
            showNetworkPicker = false,
            ssid = network.ssid,
            hint = "Escaneo pasivo de «${network.ssid}» (~1 muestra/8 s). Acerca el router al escombro.",
        )
    }

    fun dismissNetworkPicker() {
        pickerDismissedByUser = true
        _uiState.value = _uiState.value.copy(showNetworkPicker = false)
    }

    fun dismissToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }

    fun selectVictim(id: Int) {
        val victim = _uiState.value.victims.find { it.id == id }
        _uiState.value = _uiState.value.copy(selectedVictim = victim)
    }

    fun clearVictimSelection() {
        _uiState.value = _uiState.value.copy(selectedVictim = null)
    }

    fun setRadarViewport(viewport: RadarViewport) {
        _uiState.value = _uiState.value.copy(radarViewport = viewport)
    }

    fun zoomRadarIn() {
        _uiState.value = _uiState.value.copy(radarViewport = _uiState.value.radarViewport.zoomIn())
    }

    fun zoomRadarOut() {
        _uiState.value = _uiState.value.copy(radarViewport = _uiState.value.radarViewport.zoomOut())
    }

    fun resetRadarView() {
        _uiState.value = _uiState.value.copy(radarViewport = RadarViewport())
    }

    fun toggleRadarFullscreen() {
        _uiState.value = _uiState.value.copy(radarFullscreen = !_uiState.value.radarFullscreen)
    }

    fun closeRadarFullscreen() {
        _uiState.value = _uiState.value.copy(radarFullscreen = false)
    }

    fun exportCsv(): File? {
        val samples = collector.samplesSnapshot()
        if (samples.isEmpty()) {
            _uiState.value = _uiState.value.copy(toastMessage = "No hay datos para exportar")
            return null
        }
        return try {
            val (ssid, _) = collector.connectionInfo()
            val mode = collector.mode.name.lowercase()
            val file = CsvExporter.exportSession(
                getApplication(),
                samples,
                sessionLog.toList(),
                ssid.ifBlank { "unknown" },
                mode,
            )
            _uiState.value = _uiState.value.copy(
                toastMessage = "CSV exportado: ${file.name} (${samples.size} muestras)",
            )
            file
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                toastMessage = "Error al exportar: ${e.message ?: "desconocido"}",
            )
            null
        }
    }

    private fun restartCollector() {
        collector.stop()
        if (running) collector.start(viewModelScope)
    }

    private fun startCompassUpdates() {
        compassJob?.cancel()
        compassJob = viewModelScope.launch {
            while (isActive && running) {
                val bearing = compass.effectiveBearing()
                val label = compass.sourceLabel()
                val prev = _uiState.value
                if (prev.bearingDeg != bearing || prev.compassLabel != label) {
                    _uiState.value = prev.copy(bearingDeg = bearing, compassLabel = label)
                }
                delay(80)
            }
        }
    }

    private suspend fun runAnalysisTick() {
        val snapshot = collector.samplesSnapshot()
        val series = collector.qualitySeriesSnapshot()
        val bearing = compass.effectiveBearing()
        val compassLabel = compass.sourceLabel()
        val now = System.currentTimeMillis()
        val mode = collector.mode
        val (ssid, _) = collector.connectionInfo()
        val wifiEnabled = collector.isWifiEnabled()
        val monitoring = collector.isMonitoring()
        val connected = collector.isWifiConnected()
        val wifiWithoutInternet = collector.usesWifiWithoutInternet()
        val rssi = collector.currentRssi()
        val wifiCoverageRadiusM = WifiCoverageEstimator.estimateRadiusM(rssi)
        val effectiveFs = collector.estimateSampleRateHz(snapshot)
        val analysisMinSamples = if (mode == WifiCollectMode.PASSIVE_SCAN) 6 else 10
        val collectMinSamples = analysisMinSamples

        maybeSuggestPassiveFallback(mode, series.size, wifiWithoutInternet, monitoring, ssid, now)

        if (!wifiEnabled) {
            _uiState.value = _uiState.value.copy(
                wifiEnabled = false,
                status = "Wi-Fi desactivado",
                hint = "Activa Wi-Fi en ajustes. No hace falta internet.",
                bearingDeg = bearing,
                compassLabel = compassLabel,
                wifiCoverageRadiusM = wifiCoverageRadiusM,
            )
            return
        }

        cachedRecentQuality = if (series.size <= 80) {
            series.toList()
        } else {
            series.copyOfRange(series.size - 80, series.size).toList()
        }

        if (series.size < collectMinSamples) {
            val hint = buildCollectingHint(
                mode, ssid, series.size, collectMinSamples, monitoring, wifiWithoutInternet,
            )
            _uiState.value = _uiState.value.copy(
                ssid = ssid,
                rssiDbm = rssi,
                connected = connected,
                monitoring = monitoring,
                wifiWithoutInternet = wifiWithoutInternet,
                wifiEnabled = wifiEnabled,
                sampleCount = series.size,
                bearingDeg = bearing,
                compassLabel = compassLabel,
                collectMode = mode,
                status = when {
                    !monitoring && mode == WifiCollectMode.CONNECTED ->
                        "Wi-Fi sin lectura — prueba escaneo pasivo"
                    monitoring && rssi != null -> "Leyendo señal local (sin internet)..."
                    monitoring -> "Conectado al router — funciona sin internet"
                    else -> "Esperando señal Wi-Fi..."
                },
                hint = hint,
                recentQuality = cachedRecentQuality,
                calibrating = rubbleTracker.calibrating,
                calibRemainingSec = rubbleTracker.calibRemainingSec(now),
                trail = positionTrail.toList(),
                wifiCoverageRadiusM = wifiCoverageRadiusM,
            )
            return
        }

        val detection = withContext(Dispatchers.Default) {
            VitalSignalDetector.analyze(
                signal = series,
                fs = effectiveFs.coerceAtLeast(0.05),
                sensitivity = 2.0,
                rubbleMode = true,
                minSamples = analysisMinSamples,
            )
        }
        val ml = withContext(Dispatchers.Default) { signatureModel.infer(series) }

        val rubbleConf = rubbleTracker.update(detection, now)
        val mlBreathing = ml?.breathingScore ?: 0.0
        val mlActivity = ml?.activityScore ?: 0.0
        val isBreathing = detection.isBreathing || mlBreathing > 0.72
        val isActivity = detection.isActivity || mlActivity > 0.62
        val hasSignal = !detection.signalFlat &&
            (detection.variance > 0.01 || detection.uniqueValues >= 2)

        val (proximity, distance, _) = VitalSignalDetector.proximityFromVariance(
            detection.variance, isBreathing, isActivity,
        )

        val bearingStrength = maxOf(
            0.08,
            detection.confidence * 0.6,
            rubbleConf * 0.5,
            detection.variance * 0.4,
            if (isBreathing) 0.35 else 0.0,
            if (isActivity) 0.2 else 0.0,
        ).coerceAtMost(1.0)

        if (hasSignal || isBreathing || isActivity || rubbleConf > 0.08) {
            victimTracker.update(
                bearingDeg = bearing,
                distanceM = distance,
                strength = bearingStrength,
                detection = detection,
                rubbleConf = rubbleConf,
                proximity = proximity,
                nowMs = now,
            )
        }

        val victims = victimTracker.locateVictims(minScore = 0.025, nowMs = now)
        maybePlayTrappedAlert(victims)
        val primary = victims.firstOrNull()

        val best = victimTracker.bestEstimate(minScore = 0.03)
        val position = best?.let { (estBearing, estDist, _) ->
            buildPosition(estBearing, estDist, rubbleConf)
        } ?: run {
            if ((isBreathing || isActivity || rubbleConf > 0.12) && hasSignal) {
                val fallback = victimTracker.strongestBearing()
                val (estBearing, estDist) = fallback ?: (bearing to distance)
                buildPosition(estBearing, estDist, rubbleConf)
            } else {
                primary?.let {
                    PositionEstimate(it.x, it.y, it.bearing, it.distance, it.depth)
                }
            }
        }

        if (position != null) {
            positionTrail.add(TrailPoint3D(position.x, position.y, position.depth))
            if (positionTrail.size > 80) positionTrail.removeAt(0)
        }

        sessionLog.add(
            SessionLogEntry(
                timestampMs = now,
                rssiDbm = rssi,
                quality = series.lastOrNull(),
                bearingDeg = bearing,
                status = detection.status,
                isBreathing = isBreathing,
                isActivity = isActivity,
                respRate = detection.respRate,
                confidence = detection.confidence,
                rubbleConfidence = rubbleConf,
                posX = position?.x,
                posY = position?.y,
                posDepth = position?.depth,
                mode = mode.name,
            ),
        )
        if (sessionLog.size > 500) sessionLog.removeAt(0)

        val prev = _uiState.value
        val statusWithRubble = formatStatus(detection.status, rubbleConf, isBreathing, isActivity)
        val hintWithOffline = if (wifiWithoutInternet && mode == WifiCollectMode.CONNECTED) {
            "${detection.hint} Funciona sin internet."
        } else {
            detection.hint
        }
        _uiState.value = prev.copy(
            ssid = ssid,
            rssiDbm = rssi,
            connected = connected,
            monitoring = monitoring,
            wifiWithoutInternet = wifiWithoutInternet,
            wifiEnabled = wifiEnabled,
            sampleCount = series.size,
            status = statusWithRubble,
            hint = hintWithOffline,
            isBreathing = primary?.isBreathing ?: isBreathing,
            isActivity = primary?.isActivity ?: isActivity,
            respRate = primary?.respRate?.takeIf { it > 0 } ?: detection.respRate,
            confidence = primary?.confidence?.takeIf { it > 0 }
                ?: max(detection.confidence, mlBreathing * 0.85),
            rubbleConfidence = primary?.rubbleConfidence?.takeIf { it > 0 } ?: rubbleConf,
            heartBeating = primary?.heartBeating ?: detection.heartBeating,
            heartRateBpm = primary?.heartRateBpm?.takeIf { it > 0 } ?: detection.heartRateBpm,
            calibrating = rubbleTracker.calibrating,
            calibRemainingSec = rubbleTracker.calibRemainingSec(now),
            proximity = primary?.proximity ?: proximity,
            bearingDeg = bearing,
            compassLabel = compassLabel,
            position = position,
            victims = victims,
            selectedVictim = prev.selectedVictim?.let { sel ->
                victims.find { it.id == sel.id } ?: sel
            },
            heatmap = victimTracker.heatmapPoints(),
            recentQuality = cachedRecentQuality,
            collectMode = mode,
            trail = positionTrail.toList(),
            mlReady = signatureModel.isReady,
            mlScore = mlBreathing,
            mlStatus = if (signatureModel.isReady) {
                "ML ${"%.0f".format(mlBreathing * 100)}% | firma ${"%.2f".format(ml?.signatureNorm ?: 0.0)}"
            } else {
                signatureModel.statusLabel
            },
            wifiCoverageRadiusM = wifiCoverageRadiusM,
            toastMessage = prev.toastMessage,
        )
    }

    private fun isTrappedVictim(victim: TrappedVictim): Boolean =
        victim.isBreathing ||
            (victim.rubbleConfidence >= 0.22 && (victim.isActivity || victim.isBreathing))

    private fun maybePlayTrappedAlert(victims: List<TrappedVictim>) {
        if (!settingsStore.alertSoundEnabled) return

        val newlyTrapped = victims.filter { victim ->
            victim.id !in alertedVictimIds && isTrappedVictim(victim)
        }
        if (newlyTrapped.isEmpty()) return

        newlyTrapped.forEach { alertedVictimIds.add(it.id) }
        viewModelScope.launch {
            repeat(3) {
                alertPlayer.playTrappedAlert()
                delay(450)
            }
        }
    }

    private fun buildPosition(estBearing: Double, estDist: Double, rubbleConf: Double): PositionEstimate {
        val (x, y) = polarToXY(estDist, estBearing)
        val depth = victimTracker.depthUnderRubble(hypot(x, y), rubbleConf.coerceAtLeast(0.2))
        return PositionEstimate(x, y, estBearing, estDist, depth)
    }

    private fun formatStatus(
        base: String,
        rubbleConf: Double,
        isBreathing: Boolean,
        isActivity: Boolean,
    ): String {
        if (rubbleConf <= 0.08 || isBreathing) return base
        val pct = (rubbleConf * 100).toInt()
        return when {
            isActivity -> "$base — escombros $pct%"
            rubbleConf > 0.15 -> "Posible señal bajo escombros — $pct%"
            else -> base
        }
    }

    private fun maybeSuggestPassiveFallback(
        mode: WifiCollectMode,
        sampleCount: Int,
        wifiWithoutInternet: Boolean,
        monitoring: Boolean,
        ssid: String,
        nowMs: Long,
    ) {
        if (mode != WifiCollectMode.CONNECTED || sampleCount > 0 || suggestedPassiveFallback) {
            if (sampleCount > 0) connectedNoSamplesSinceMs = 0L
            return
        }
        if (!wifiWithoutInternet && monitoring) {
            connectedNoSamplesSinceMs = 0L
            return
        }
        if (connectedNoSamplesSinceMs == 0L) {
            connectedNoSamplesSinceMs = nowMs
            return
        }
        if (nowMs - connectedNoSamplesSinceMs < 12_000L) return

        suggestedPassiveFallback = true
        val label = ssid.ifBlank { "el router" }
        _uiState.value = _uiState.value.copy(
            toastMessage = "Router sin internet: «Escaneo pasivo» suele funcionar mejor con $label.",
            hint = "Sin muestras en modo conectado. Pulsa «Escaneo pasivo» y elige $label.",
        )
    }

    private fun buildCollectingHint(
        mode: WifiCollectMode,
        ssid: String,
        count: Int,
        minSamples: Int,
        monitoring: Boolean,
        wifiWithoutInternet: Boolean,
    ): String = when (mode) {
        WifiCollectMode.PASSIVE_SCAN -> when {
            ssid.isBlank() -> "Selecciona una red del router para escanear."
            count < 2 -> "Esperando primer barrido Wi-Fi (~8 s)..."
            else -> {
                val remaining = max(0, minSamples - count)
                val sec = remaining * 8
                "Escaneo pasivo: ~${sec}s más ($count/$minSamples barridos)"
            }
        }
        else -> when {
            monitoring && wifiWithoutInternet ->
                "Funciona sin internet — leyendo señal local. Espera ~${max(0, (minSamples - count) / 2)}s"
            monitoring ->
                "Funciona sin internet. Espera ~${max(0, (minSamples - count) / 2)}s más de datos"
            ssid.isNotBlank() ->
                "Unido a «$ssid» pero sin lectura RSSI. Prueba «Escaneo pasivo»."
            else ->
                "Conéctate al Wi-Fi del router (sin internet) o usa «Escaneo pasivo»."
        }
    }
}
