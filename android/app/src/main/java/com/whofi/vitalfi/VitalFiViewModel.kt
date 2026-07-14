package com.whofi.vitalfi

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.whofi.vitalfi.dsp.MultiVictimTracker
import com.whofi.vitalfi.dsp.RescuerExclusionTracker
import com.whofi.vitalfi.dsp.TrappedPersonFilter
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
import com.whofi.vitalfi.ui.RadarLayers
import com.whofi.vitalfi.ui.RadarViewport
import com.whofi.vitalfi.ui.TrailPoint3D
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
    val viewAzim: Double = 0.0,
    val viewElev: Double = 68.0,
    val trail: List<TrailPoint3D> = emptyList(),
    val radarViewport: RadarViewport = RadarViewport(),
    val radarLayers: RadarLayers = RadarLayers(),
    val radarFullscreen: Boolean = false,
    val mlReady: Boolean = false,
    val mlScore: Double = 0.0,
    val mlStatus: String = "ML desactivado",
    val wifiCoverageRadiusM: Double = 0.0,
    val alertSoundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val keepScreenOn: Boolean = true,
    val detectionSensitivity: Double = 2.0,
    val toastMessage: String? = null,
    val trappedOnlyMode: Boolean = true,
    val rubbleZoneLabel: String? = null,
    val filterStatus: String? = null,
    val excludedRescuerCount: Int = 0,
    val scanningElevationM: Double = 0.0,
    val hasBarometer: Boolean = false,
    val autoElevationActive: Boolean = true,
    val rubbleZoneRadiusDeg: Double = 55.0,
    val defaultWifiMode: WifiCollectMode = WifiCollectMode.PASSIVE_SCAN,
    val colorblindMode: Boolean = true,
)

class VitalFiViewModel(app: Application) : AndroidViewModel(app) {

    private val collector = WifiRssiCollector(app, sampleRateHz = 2.0, maxSamples = 180)
    private val compass = CompassReader(app)
    private val barometer = com.whofi.vitalfi.sensor.BarometerReader(app)
    private val victimTracker = MultiVictimTracker()
    private val rubbleTracker = RubbleConfidenceTracker()
    private val settingsStore = SettingsStore(app)
    private val exclusionTracker = RescuerExclusionTracker().also { tracker ->
        settingsStore.rubbleZoneCenterDeg?.let { center ->
            tracker.setRubbleZone(center, settingsStore.rubbleZoneRadiusDeg)
        }
    }
    private val signatureModel = SignatureModel(app)
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
            vibrationEnabled = settingsStore.vibrationEnabled,
            keepScreenOn = settingsStore.keepScreenOn,
            detectionSensitivity = settingsStore.detectionSensitivity,
            trappedOnlyMode = settingsStore.trappedOnlyMode,
            rubbleZoneLabel = loadRubbleZoneLabel(),
            hasBarometer = barometer.hasSensor,
            autoElevationActive = settingsStore.autoElevationActive && barometer.hasSensor,
            scanningElevationM = if (settingsStore.autoElevationActive && barometer.hasSensor) {
                0.0
            } else {
                settingsStore.manualElevationM
            },
            radarLayers = settingsStore.loadRadarLayers(),
            showRadar3D = settingsStore.showRadar3D,
            rubbleZoneRadiusDeg = settingsStore.rubbleZoneRadiusDeg,
            defaultWifiMode = if (settingsStore.defaultWifiMode == "CONNECTED") {
                WifiCollectMode.CONNECTED
            } else {
                WifiCollectMode.PASSIVE_SCAN
            },
            colorblindMode = settingsStore.colorblindMode,
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
        barometer.start()
        if (collector.mode == WifiCollectMode.CONNECTED || !collector.targetSsid.isNullOrBlank()) {
            collector.start(viewModelScope)
        }
        if (rubbleTracker.calibrating) {
            rubbleTracker.begin(System.currentTimeMillis())
            barometer.calibrateBaseline()
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
        barometer.stop()
    }

    override fun onCleared() {
        stop()
        alertPlayer.release()
        super.onCleared()
    }

    private fun loadRubbleZoneLabel(): String? = exclusionTracker.rubbleZoneLabel()

    fun setAlertSoundEnabled(enabled: Boolean) {
        settingsStore.alertSoundEnabled = enabled
        _uiState.value = _uiState.value.copy(alertSoundEnabled = enabled)
    }

    fun setTrappedOnlyMode(enabled: Boolean) {
        settingsStore.trappedOnlyMode = enabled
        _uiState.value = _uiState.value.copy(trappedOnlyMode = enabled)
    }

    fun setVibrationEnabled(enabled: Boolean) {
        settingsStore.vibrationEnabled = enabled
        _uiState.value = _uiState.value.copy(vibrationEnabled = enabled)
    }

    fun setKeepScreenOn(enabled: Boolean) {
        settingsStore.keepScreenOn = enabled
        _uiState.value = _uiState.value.copy(keepScreenOn = enabled)
    }

    fun setDetectionSensitivity(value: Double) {
        val clamped = value.coerceIn(1.0, 3.5)
        settingsStore.detectionSensitivity = clamped
        _uiState.value = _uiState.value.copy(detectionSensitivity = clamped)
    }

    fun setRubbleZoneRadiusDeg(value: Double) {
        val clamped = value.coerceIn(20.0, 120.0)
        settingsStore.rubbleZoneRadiusDeg = clamped
        settingsStore.rubbleZoneCenterDeg?.let { center ->
            exclusionTracker.setRubbleZone(center, clamped)
        }
        _uiState.value = _uiState.value.copy(
            rubbleZoneRadiusDeg = clamped,
            rubbleZoneLabel = exclusionTracker.rubbleZoneLabel(),
        )
    }

    fun setShowRadar3D(enabled: Boolean) {
        settingsStore.showRadar3D = enabled
        _uiState.value = _uiState.value.copy(showRadar3D = enabled)
    }

    fun setColorblindMode(enabled: Boolean) {
        settingsStore.colorblindMode = enabled
        _uiState.value = _uiState.value.copy(colorblindMode = enabled)
    }

    fun setDefaultWifiMode(mode: WifiCollectMode) {
        settingsStore.defaultWifiMode = when (mode) {
            WifiCollectMode.CONNECTED -> "CONNECTED"
            WifiCollectMode.PASSIVE_SCAN -> "PASSIVE_SCAN"
        }
        _uiState.value = _uiState.value.copy(defaultWifiMode = mode)
    }

    fun markRubbleZoneAtCurrentBearing() {
        val bearing = _uiState.value.bearingDeg
        exclusionTracker.setRubbleZone(bearing, settingsStore.rubbleZoneRadiusDeg)
        settingsStore.rubbleZoneCenterDeg = bearing
        _uiState.value = _uiState.value.copy(
            rubbleZoneLabel = exclusionTracker.rubbleZoneLabel(),
            toastMessage = "Zona de escombros marcada hacia ${bearing.toInt()}°",
        )
    }

    fun clearRubbleZone() {
        exclusionTracker.clearRubbleZone()
        settingsStore.rubbleZoneCenterDeg = null
        _uiState.value = _uiState.value.copy(
            rubbleZoneLabel = null,
            toastMessage = "Zona de escombros eliminada",
        )
    }

    fun excludeRescuerAtCurrentBearing() {
        val bearing = _uiState.value.bearingDeg
        exclusionTracker.excludeSector(bearing)
        _uiState.value = _uiState.value.copy(
            toastMessage = "Sector ${bearing.toInt()}° excluido (rescatista)",
            excludedRescuerCount = exclusionTracker.excludedSectorCount(),
        )
    }

    fun clearRescuerExclusions() {
        exclusionTracker.clearExclusions()
        _uiState.value = _uiState.value.copy(
            excludedRescuerCount = 0,
            toastMessage = "Exclusiones de rescatistas borradas",
        )
    }

    fun reset() {
        collector.clear()
        victimTracker.reset()
        exclusionTracker.reset()
        settingsStore.rubbleZoneCenterDeg?.let { center ->
            exclusionTracker.setRubbleZone(center, settingsStore.rubbleZoneRadiusDeg)
        }
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
        _uiState.value = _uiState.value.copy(viewAzim = 0.0, viewElev = 68.0)
    }

    fun toggleRadar3D() {
        setShowRadar3D(!_uiState.value.showRadar3D)
    }

    fun setConnectedMode() {
        pickerDismissedByUser = false
        suggestedPassiveFallback = false
        connectedNoSamplesSinceMs = 0L
        collector.setConnectedMode()
        restartCollector()
        settingsStore.defaultWifiMode = "CONNECTED"
        _uiState.value = _uiState.value.copy(
            collectMode = WifiCollectMode.CONNECTED,
            defaultWifiMode = WifiCollectMode.CONNECTED,
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
        settingsStore.defaultWifiMode = "PASSIVE_SCAN"
        _uiState.value = _uiState.value.copy(
            collectMode = WifiCollectMode.PASSIVE_SCAN,
            defaultWifiMode = WifiCollectMode.PASSIVE_SCAN,
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

    fun setRadarLayers(layers: RadarLayers) {
        settingsStore.saveRadarLayers(layers)
        _uiState.value = _uiState.value.copy(radarLayers = layers)
    }

    fun toggleRadarLayer(layer: String) {
        val cur = _uiState.value.radarLayers
        val next = when (layer) {
            "grid" -> cur.copy(grid = !cur.grid)
            "compass" -> cur.copy(compass = !cur.compass)
            "wifi" -> cur.copy(wifi = !cur.wifi)
            "heatmap" -> cur.copy(heatmap = !cur.heatmap)
            "guide" -> cur.copy(guide = !cur.guide)
            "rubble" -> cur.copy(rubble = !cur.rubble)
            "trail" -> cur.copy(trail = !cur.trail)
            "labels" -> cur.copy(labels = !cur.labels)
            "depthRays", "rayas" -> cur.copy(depthRays = !cur.depthRays)
            else -> cur
        }
        setRadarLayers(next)
    }

    fun radarLayersSoloPersona() {
        setRadarLayers(_uiState.value.radarLayers.soloPersona())
    }

    fun radarLayersModoRescate() {
        setRadarLayers(_uiState.value.radarLayers.modoRescate())
    }

    fun radarLayersTodo() {
        setRadarLayers(RadarLayers().todoVisible())
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
                sensitivity = settingsStore.detectionSensitivity,
                rubbleMode = true,
                minSamples = analysisMinSamples,
            )
        }
        val ml = withContext(Dispatchers.Default) { signatureModel.infer(series) }

        val rubbleConf = rubbleTracker.update(detection, now)
        val mlBreathing = ml?.breathingScore ?: 0.0
        val mlActivity = ml?.activityScore ?: 0.0
        // Solo datos reales de Wi-Fi RSSI: el ML no inventa víctimas sin señal real.
        val hasSignal = !detection.signalFlat &&
            (detection.variance > 0.01 || detection.uniqueValues >= 2)
        val isBreathing = detection.isBreathing || (hasSignal && mlBreathing > 0.78)
        val isActivity = detection.isActivity || (hasSignal && mlActivity > 0.68)

        val trappedOnly = settingsStore.trappedOnlyMode
        exclusionTracker.record(bearing, detection, now)
        val movementScore = exclusionTracker.movementAt(bearing)
        val classification = TrappedPersonFilter.classify(
            detection = detection.copy(
                isBreathing = isBreathing,
                isActivity = isActivity,
            ),
            rubbleConf = rubbleConf,
            movementScore = movementScore,
            strictMode = trappedOnly,
        )

        val (proximity, distance, _) = VitalSignalDetector.proximityFromVariance(
            detection.variance, isBreathing, isActivity, rssi,
        )

        val bearingStrength = maxOf(
            0.08,
            detection.confidence * 0.6,
            rubbleConf * 0.5,
            detection.variance * 0.4,
            if (isBreathing) 0.35 else 0.0,
            if (isActivity && !trappedOnly) 0.2 else 0.0,
            if (isActivity && trappedOnly && isBreathing) 0.08 else 0.0,
        ).coerceAtMost(1.0)
        // Reduce bearing strength for surface-like signals (rescuer near router)
        val surfaceAttenuation = 1.0 - detection.surfaceSignalLikelihood * 0.7
        val adjustedBearingStrength = (bearingStrength * surfaceAttenuation).coerceAtLeast(0.02)

        val shouldUpdateTracker = when {
            !hasSignal && !isBreathing && rubbleConf <= 0.08 -> false
            trappedOnly && classification.isLikelyRescuer -> false
            trappedOnly && !classification.isLikelyTrapped && !isBreathing -> false
            trappedOnly && !exclusionTracker.isInRubbleZone(bearing) -> false
            exclusionTracker.isExcluded(bearing) -> false
            // High surface signal likelihood without breathing → reject
            trappedOnly && detection.surfaceSignalLikelihood > 0.55 && !isBreathing -> false
            else -> hasSignal || isBreathing || isActivity || rubbleConf > 0.08
        }

        val elevation = if (_uiState.value.autoElevationActive && barometer.hasSensor) {
            barometer.relativeElevationM
        } else {
            _uiState.value.scanningElevationM
        }

        if (shouldUpdateTracker) {
            victimTracker.update(
                bearingDeg = bearing,
                distanceM = distance,
                strength = if (classification.isLikelyTrapped) adjustedBearingStrength else adjustedBearingStrength * 0.45,
                detection = detection.copy(isBreathing = isBreathing, isActivity = isActivity),
                rubbleConf = rubbleConf,
                proximity = proximity,
                nowMs = now,
            )
        }

        val allVictims = victimTracker.locateVictims(minScore = if (trappedOnly) 0.035 else 0.025, nowMs = now)
        val victims = if (trappedOnly) {
            filterTrappedVictims(allVictims)
        } else {
            allVictims
        }
        // Profundidad = solo entierro bajo el suelo (Z≤0). La altura del rescatista
        // (scanningElevationM) no se mezcla aquí: eso generaba "−X m bajo escombros".
        val mappedVictims = victims
        maybePlayTrappedAlert(mappedVictims)
        val primary = mappedVictims.firstOrNull()

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
        val statusWithRubble = formatStatus(
            detection.status, rubbleConf, isBreathing, isActivity, classification, trappedOnly,
        )
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
            victims = mappedVictims,
            selectedVictim = prev.selectedVictim?.let { sel ->
                mappedVictims.find { it.id == sel.id } ?: sel
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
            trappedOnlyMode = trappedOnly,
            rubbleZoneLabel = exclusionTracker.rubbleZoneLabel(),
            filterStatus = if (trappedOnly) classification.reason else null,
            excludedRescuerCount = exclusionTracker.excludedSectorCount(),
            scanningElevationM = elevation,
        )
    }

    private fun filterTrappedVictims(all: List<TrappedVictim>): List<TrappedVictim> =
        all.filter { victim ->
            !exclusionTracker.isExcluded(victim.bearing) &&
                exclusionTracker.isInRubbleZone(victim.bearing) &&
                TrappedPersonFilter.isLikelyTrappedVictim(
                    victim,
                    exclusionTracker.movementAt(victim.bearing),
                    strictMode = true,
                )
        }

    private fun isTrappedVictim(victim: TrappedVictim): Boolean =
        TrappedPersonFilter.isLikelyTrappedVictim(
            victim,
            exclusionTracker.movementAt(victim.bearing),
            strictMode = settingsStore.trappedOnlyMode,
        )

    private fun maybePlayTrappedAlert(victims: List<TrappedVictim>) {
        val soundOn = settingsStore.alertSoundEnabled
        val vibrateOn = settingsStore.vibrationEnabled
        if (!soundOn && !vibrateOn) return

        val newlyTrapped = victims.filter { victim ->
            victim.id !in alertedVictimIds && isTrappedVictim(victim)
        }
        if (newlyTrapped.isEmpty()) return

        newlyTrapped.forEach { alertedVictimIds.add(it.id) }
        if (vibrateOn) vibrateAlert()
        if (soundOn) {
            viewModelScope.launch {
                repeat(3) {
                    alertPlayer.playTrappedAlert()
                    delay(450)
                }
            }
        }
    }

    private fun vibrateAlert() {
        try {
            val app = getApplication<Application>()
            val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val manager = app.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE)
                    as android.os.VibratorManager
                manager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                app.getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
            }
            val pattern = longArrayOf(0, 300, 150, 300, 150, 500)
            vibrator.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1))
        } catch (_: Exception) {
            // Dispositivo sin vibrador.
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
        classification: TrappedPersonFilter.Classification,
        trappedOnly: Boolean,
    ): String {
        if (trappedOnly && classification.isLikelyRescuer) {
            return "Rescatista filtrado — ${classification.reason}"
        }
        if (trappedOnly && !classification.isLikelyTrapped && isActivity && !isBreathing) {
            return "Movimiento detectado — no es víctima bajo escombros"
        }
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

    fun setAutoElevationActive(active: Boolean) {
        val hasBar = barometer.hasSensor
        val useAuto = active && hasBar
        settingsStore.autoElevationActive = useAuto
        val elev = if (useAuto) barometer.relativeElevationM else settingsStore.manualElevationM
        _uiState.value = _uiState.value.copy(
            autoElevationActive = useAuto,
            scanningElevationM = elev,
        )
    }

    fun adjustManualElevation(deltaM: Double) {
        if (_uiState.value.autoElevationActive) return
        val newElev = (_uiState.value.scanningElevationM + deltaM).coerceIn(-10.0, 10.0)
        settingsStore.manualElevationM = newElev
        _uiState.value = _uiState.value.copy(scanningElevationM = newElev)
    }

    fun resetElevation() {
        barometer.calibrateBaseline()
        settingsStore.manualElevationM = 0.0
        _uiState.value = _uiState.value.copy(
            scanningElevationM = 0.0,
        )
    }
}
