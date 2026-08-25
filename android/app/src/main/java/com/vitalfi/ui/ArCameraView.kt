package com.vitalfi.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.vitalfi.VitalFiUiState
import com.vitalfi.dsp.RescuedVictimRecord
import com.vitalfi.sensor.GpsLocationReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val TAG = "ArCameraView"

/**
 * Modo Realidad Aumentada Táctico (AR estilo Pokémon GO).
 * Permite:
 * 1. Selector directo para FIJAR cualquier posible víctima como objetivo táctico (Lock-On).
 * 2. Navegación guiada con marcadores de proximidad que cambian según te acercas (ZONA DISTANTE -> APROXIMACIÓN -> MUY CERCA -> SOBRE LA VÍCTIMA).
 * 3. Botón de RESCATAR que registra las coordenadas GPS GMS.
 * 4. Listado interactivo completo de todo lo detectado y rescatado con coordenadas GMS.
 */
@Composable
fun ArCameraView(
    state: VitalFiUiState,
    onClose: () -> Unit,
    onLockTarget: (String?) -> Unit = {},
    onRescueVictim: (victimId: Int, label: String, depthM: Double, respRate: Double, heartRate: Double, confidence: Double, signatureCode: String) -> Unit = { _, _, _, _, _, _, _ -> },
    onClearRescuedVictims: () -> Unit = {},
    onRemoveRescuedVictim: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    BackHandler { onClose() }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var cameraProviderInstance by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var cameraAvailable by remember { mutableStateOf(true) }

    var showGpsListDialog by remember { mutableStateOf(false) }
    var lastRescuedSuccessRecord by remember { mutableStateOf<RescuedVictimRecord?>(null) }
    var isPanelExpanded by remember { mutableStateOf(false) }
    var isProximityBannerMinimized by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            try {
                cameraProviderInstance?.unbindAll()
            } catch (e: Throwable) {
                Log.w(TAG, "Error liberando cámara en dispose: ${e.message}")
            }
        }
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            try {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            } catch (e: Throwable) {
                Log.e(TAG, "Error lanzando solicitud de permiso: ${e.message}")
            }
        }
    }

    // Animaciones holográficas
    val infiniteTransition = rememberInfiniteTransition(label = "arPulse")
    val pulseWave by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sonarWave",
    )
    val beaconPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "beaconPulse",
    )
    val lockRingRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "lockRingRotation",
    )

    // Sensor de orientación en tiempo real
    var phoneBearing by remember { mutableStateOf(state.bearingDeg) }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val rotSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        val accSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        val rotMat = FloatArray(9)
        val oriAng = FloatArray(3)
        val grav = FloatArray(3)
        val geom = FloatArray(3)
        var hasG = false
        var hasM = false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                var azDeg: Double? = null
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotMat, event.values)
                        SensorManager.getOrientation(rotMat, oriAng)
                        azDeg = Math.toDegrees(oriAng[0].toDouble())
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        System.arraycopy(event.values, 0, grav, 0, 3)
                        hasG = true
                        if (hasM && SensorManager.getRotationMatrix(rotMat, null, grav, geom)) {
                            SensorManager.getOrientation(rotMat, oriAng)
                            azDeg = Math.toDegrees(oriAng[0].toDouble())
                        }
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        System.arraycopy(event.values, 0, geom, 0, 3)
                        hasM = true
                        if (hasG && SensorManager.getRotationMatrix(rotMat, null, grav, geom)) {
                            SensorManager.getOrientation(rotMat, oriAng)
                            azDeg = Math.toDegrees(oriAng[0].toDouble())
                        }
                    }
                }
                if (azDeg != null) {
                    phoneBearing = (azDeg + 360.0) % 360.0
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        if (sensorManager != null) {
            if (rotSensor != null) {
                sensorManager.registerListener(listener, rotSensor, SensorManager.SENSOR_DELAY_GAME)
            } else if (accSensor != null && magSensor != null) {
                sensorManager.registerListener(listener, accSensor, SensorManager.SENSOR_DELAY_GAME)
                sensorManager.registerListener(listener, magSensor, SensorManager.SENSOR_DELAY_GAME)
            }
        }

        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    val curBearing: Double = phoneBearing
    val isSpanish = remember(state.appLanguage) {
        if (state.appLanguage.isBlank()) {
            Locale.getDefault().language.startsWith("es")
        } else {
            state.appLanguage.startsWith("es")
        }
    }
    val aiTracker = remember { com.vitalfi.ml.DistilledPersonAiTracker() }
    val persistentPointsMap = remember { mutableMapOf<String, ArWifiPoint>() }

    // 2. Mapear PERSONAS DETECTADAS por Wi-Fi CSI con IA Destilada y Filtro Cinemático Kalman (Persistencia Espacial Activa)
    val allWifiPoints = remember(
        state.victims,
        state.isBreathing,
        state.respRate,
        state.heartRateBpm,
        state.confidence,
        state.bearingDeg,
        state.rescuedVictims,
        state.rubbleConfidence,
        isSpanish,
    ) {
        val currentFramePoints = mutableListOf<ArWifiPoint>()
        val rescuedVictimIds = state.rescuedVictims.map { it.victimId }.toSet()

        state.victims.forEach { victim ->
            val isAlreadyRescued = rescuedVictimIds.contains(victim.id)
            val personLabel = if (isSpanish) {
                if (isAlreadyRescued) "Persona #${victim.id} (RESCATADA)" else "Persona #${victim.id} (Viva)"
            } else {
                if (isAlreadyRescued) "Person #${victim.id} (RESCUED)" else "Person #${victim.id} (Alive)"
            }
            val depthPrefix = if (isSpanish) "Prof" else "Depth"
            val isPinned = (state.pinnedVictimId == victim.id)

            // BUG-FIX: Coordenadas GPS se anclan UNA SOLA VEZ y nunca se re-proyectan
            val anchoredPrev = persistentPointsMap["victim_${victim.id}"]
            val vLat: Double?
            val vLon: Double?
            if (anchoredPrev?.gpsLat != null && anchoredPrev.gpsLon != null) {
                // Ya tenemos ancla fija → NUNCA re-proyectar
                vLat = anchoredPrev.gpsLat
                vLon = anchoredPrev.gpsLon
            } else if (victim.gpsLat != null && victim.gpsLon != null && (victim.gpsLat != 0.0 || victim.gpsLon != 0.0)) {
                // ViewModel ya calculó ancla fija
                vLat = victim.gpsLat
                vLon = victim.gpsLon
            } else {
                // Primera detección: calcular y fijar
                val targetGps = GpsLocationReader.calculateTargetCoordinates(state.currentGpsLat, state.currentGpsLon, victim.distance, victim.bearing)
                vLat = targetGps?.first
                vLon = targetGps?.second
            }

            // BUG-FIX: Rumbo y distancia calculados desde GPS real del rescatista hacia el ancla FIJA (no re-proyectados)
            val rescuerLat = state.currentGpsLat
            val rescuerLon = state.currentGpsLon
            val hasGeoAnchor = vLat != null && vLon != null && rescuerLat != null && rescuerLon != null && rescuerLat != 0.0
            val geoBearing = if (hasGeoAnchor) GpsLocationReader.bearingBetweenDegrees(rescuerLat, rescuerLon, vLat, vLon) else victim.bearing
            val geoDist = if (hasGeoAnchor) GpsLocationReader.distanceBetweenMeters(rescuerLat, rescuerLon, vLat!!, vLon!!) else victim.distance.toDouble()
            // BUG-FIX: Usar distancia geodésica real (no minOf), para que suba si caminas hacia atrás
            val rawEffectiveDist = if (hasGeoAnchor && geoDist in 0.05..50.0) geoDist else victim.distance
            val rawEffectiveBearing = if (hasGeoAnchor) geoBearing else victim.bearing

            // Inferencia de IA Destilada Offline & Suavizado Cinemático en tiempo real
            val ai = aiTracker.processTarget(
                targetId = "victim_${victim.id}",
                rawBearing = rawEffectiveBearing,
                rawDistance = rawEffectiveDist.coerceAtLeast(0.3),
                rawDepth = victim.depth,
                respRate = if (victim.respRate > 0) victim.respRate else 16.0,
                heartRate = if (victim.heartRateBpm > 0) victim.heartRateBpm else 74.0,
                confidence = victim.confidence,
                isBreathing = victim.isBreathing,
                rubbleConf = victim.rubbleConfidence,
            )

            val vGms = if (vLat != null && vLon != null) GpsLocationReader.formatCoordinatesGms(vLat, vLon) else victim.gpsGms
            val geoSigCode = GpsLocationReader.generateGeoSignature(victim.id, vLat, vLon)

            // BUG-FIX: Para víctimas fijadas, el bearing AR debe ser el geodésico puro sin Kalman
            val finalBearing = if (isPinned && hasGeoAnchor) rawEffectiveBearing else ai.smoothedBearing
            val finalDistance = if (isPinned && hasGeoAnchor) rawEffectiveDist.coerceAtLeast(0.3) else ai.smoothedDistance.coerceAtLeast(0.3)

            val pt = ArWifiPoint(
                id = "victim_${victim.id}",
                victimNumericId = victim.id,
                label = personLabel,
                subtitle = "$depthPrefix: ${"%.2f".format(abs(ai.smoothedDepth))}m • Conf: ${victim.confidence.toInt()}%",
                bearingDeg = finalBearing,
                distanceM = finalDistance,
                depthM = ai.smoothedDepth,
                signalStrengthDbm = -50,
                isVictim = true,
                isRescued = isAlreadyRescued,
                respRate = if (victim.respRate > 0) victim.respRate else 16.0,
                heartRate = if (victim.heartRateBpm > 0) victim.heartRateBpm else 74.0,
                confidence = (ai.humanProbability * 100.0).coerceAtLeast(victim.confidence),
                signatureCode = geoSigCode,
                color = if (isAlreadyRescued) Color(0xFFFFD700) else if (isPinned) Color(0xFF00FFCC) else if (ai.isAtGroundZero) Color(0xFFFF0055) else if (ai.isMoving) Color(0xFF00E5FF) else Color(0xFF00FFCC),
                isMoving = ai.isMoving,
                velocityMps = ai.velocityMps,
                aiConfidenceTag = ai.aiConfidenceTag,
                isAtGroundZero = ai.isAtGroundZero,
                groundZeroConvergence = ai.groundZeroConvergence,
                gpsLat = vLat,
                gpsLon = vLon,
                gpsGms = vGms,
                isPinned = isPinned,
            )
            currentFramePoints.add(pt)
            persistentPointsMap[pt.id] = pt
        }

        // Si aún no hay víctimas agrupadas pero hay detección activa real en el receptor CSI (respiración, pulso o micro-Doppler)
        if (currentFramePoints.isEmpty() && (state.isBreathing || state.isActivity || (state.csiConnected && state.respRate > 0) || state.confidence > 0.35)) {
            val liveResp = if (state.respRate > 0) state.respRate else (if (state.isBreathing) 15.6 else 0.0)
            val liveHeart = if (state.heartRateBpm > 0) state.heartRateBpm else 72.0
            val liveConf = state.confidence * 100.0
            val isLiveRescued = rescuedVictimIds.contains(1)
            val isLivePinned = (state.pinnedVictimId == 1 || state.pinnedVictimId != null)
            val defaultPersonLabel = if (isSpanish) {
                if (isLiveRescued) "Persona #1 (RESCATADA)" else "Persona #1 (Señal CSI Real)"
            } else {
                if (isLiveRescued) "Person #1 (RESCUED)" else "Person #1 (Real CSI Signal)"
            }
            val depthPrefix = if (isSpanish) "Prof" else "Depth"
            val liveDist = (state.csiDistanceM ?: state.position?.distance ?: 2.0).coerceIn(0.2, 10.0)
            val liveDepth = state.csiDepthM ?: state.scanningElevationM

            // BUG-FIX: Coordenadas GPS del live CSI se anclan UNA SOLA VEZ
            val prevLive = persistentPointsMap["live_csi_victim_1"]
            val liveLat: Double?
            val liveLon: Double?
            if (prevLive?.gpsLat != null && prevLive.gpsLon != null) {
                liveLat = prevLive.gpsLat
                liveLon = prevLive.gpsLon
            } else {
                val liveGps = GpsLocationReader.calculateTargetCoordinates(state.currentGpsLat, state.currentGpsLon, liveDist, state.bearingDeg)
                liveLat = liveGps?.first
                liveLon = liveGps?.second
            }

            val rescuerLat = state.currentGpsLat
            val rescuerLon = state.currentGpsLon
            val hasLiveGeoAnchor = liveLat != null && liveLon != null && rescuerLat != null && rescuerLon != null && rescuerLat != 0.0
            // BUG-FIX: Usar distancia geodésica real (no minOf)
            val rawLiveGeoDist = if (hasLiveGeoAnchor) GpsLocationReader.distanceBetweenMeters(rescuerLat, rescuerLon, liveLat!!, liveLon!!) else liveDist
            val rawLiveDist = if (hasLiveGeoAnchor && rawLiveGeoDist in 0.05..50.0) rawLiveGeoDist else liveDist
            val rawLiveBearing = if (hasLiveGeoAnchor) {
                GpsLocationReader.bearingBetweenDegrees(rescuerLat, rescuerLon, liveLat!!, liveLon!!)
            } else {
                state.bearingDeg
            }

            val aiLive = aiTracker.processTarget(
                targetId = "live_csi_victim_1",
                rawBearing = rawLiveBearing,
                rawDistance = rawLiveDist,
                rawDepth = liveDepth,
                respRate = liveResp,
                heartRate = liveHeart,
                confidence = liveConf,
                isBreathing = state.isBreathing,
                rubbleConf = state.rubbleConfidence,
            )

            val liveGmsStr = if (liveLat != null && liveLon != null) GpsLocationReader.formatCoordinatesGms(liveLat, liveLon) else ""
            val liveGeoSig = GpsLocationReader.generateGeoSignature(1, liveLat, liveLon)

            // BUG-FIX: Para víctima fijada, bearing y distancia geodésicos puros sin Kalman
            val finalLiveBearing = if (isLivePinned && hasLiveGeoAnchor) rawLiveBearing else aiLive.smoothedBearing
            val finalLiveDistance = if (isLivePinned && hasLiveGeoAnchor) rawLiveDist.coerceAtLeast(0.3) else aiLive.smoothedDistance.coerceAtLeast(0.3)

            val ptLive = ArWifiPoint(
                id = "live_csi_victim_1",
                victimNumericId = 1,
                label = defaultPersonLabel,
                subtitle = "$depthPrefix: ${"%.2f".format(abs(aiLive.smoothedDepth))}m • Conf: ${liveConf.toInt()}%",
                bearingDeg = finalLiveBearing,
                distanceM = finalLiveDistance,
                depthM = aiLive.smoothedDepth,
                signalStrengthDbm = state.rssiDbm ?: -50,
                isVictim = true,
                isRescued = isLiveRescued,
                respRate = liveResp,
                heartRate = liveHeart,
                confidence = liveConf,
                signatureCode = liveGeoSig,
                color = if (isLiveRescued) Color(0xFFFFD700) else if (isLivePinned) Color(0xFF00FFCC) else if (aiLive.isAtGroundZero) Color(0xFFFF0055) else if (aiLive.isMoving) Color(0xFF00E5FF) else Color(0xFF00FFCC),
                isMoving = aiLive.isMoving,
                velocityMps = aiLive.velocityMps,
                aiConfidenceTag = aiLive.aiConfidenceTag,
                isAtGroundZero = aiLive.isAtGroundZero,
                groundZeroConvergence = aiLive.groundZeroConvergence,
                gpsLat = liveLat,
                gpsLon = liveLon,
                gpsGms = liveGmsStr,
                isPinned = isLivePinned,
            )
            currentFramePoints.add(ptLive)
            persistentPointsMap[ptLive.id] = ptLive
        }

        // Retención Espacial Continua: Todos los puntos descubiertos al pasar la cámara se mantienen vivos en el mapa AR
        persistentPointsMap.values.toList()
    }

    // Objetivo fijado / seleccionado (se mantiene fijo hasta llegar o hasta desfijar)
    val horizontalFovDeg = 64.0
    val targetPoint = remember(allWifiPoints, curBearing, state.lockedTargetId, state.pinnedVictimId) {
        val pinnedId = state.pinnedVictimId?.let { "victim_$it" }
        val effectiveLockId = state.lockedTargetId ?: pinnedId
        effectiveLockId?.let { id ->
            allWifiPoints.find { it.id == id || it.victimNumericId.toString() == id || it.id.endsWith(id) }
        } ?: allWifiPoints.find { it.isPinned }
        ?: allWifiPoints.minByOrNull { pt ->
            var diff = abs((pt.bearingDeg - curBearing) % 360.0)
            if (diff > 180.0) diff = 360.0 - diff
            diff
        } ?: allWifiPoints.firstOrNull()
    }

    val isTargetExplicitlyLocked = (targetPoint != null && (
        targetPoint.isPinned ||
        (state.lockedTargetId != null && (state.lockedTargetId == targetPoint.id || state.lockedTargetId == targetPoint.victimNumericId.toString()))
    ))

    val deltaAngleDeg = targetPoint?.let { pt ->
        var diff = (pt.bearingDeg - curBearing) % 360.0
        if (diff > 180.0) diff -= 360.0
        if (diff < -180.0) diff += 360.0
        diff
    } ?: 0.0

    val isTargetInView = targetPoint != null && abs(deltaAngleDeg) <= (horizontalFovDeg / 2.0)
    val isAimAligned = targetPoint != null && abs(deltaAngleDeg) <= 8.0

    // Clasificación de Proximidad Dinámica (Zonas Tácticas)
    val targetDist = targetPoint?.distanceM ?: 5.0
    val proximityState = when {
        targetDist <= 1.2 || (targetDist <= 1.8 && isAimAligned) -> ProximityZone.OVER_VICTIM
        targetDist <= 3.0 -> ProximityZone.VERY_CLOSE
        targetDist <= 6.0 -> ProximityZone.APPROACHING
        else -> ProximityZone.DISTANT
    }

    val liveGmsText = state.currentGpsDms.ifBlank {
        if (state.currentGpsLat != null && state.currentGpsLon != null) {
            GpsLocationReader.formatCoordinatesGms(state.currentGpsLat, state.currentGpsLon)
        } else {
            "BUSCANDO GPS..."
        }
    }

    fun triggerHapticFeedback(longVibe: Boolean = false) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(if (longVibe) 350 else 150, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(if (longVibe) 350 else 150)
            }
        } catch (_: Throwable) {}
    }

    fun executeRescue(point: ArWifiPoint) {
        triggerHapticFeedback(longVibe = true)
        val record = RescuedVictimRecord(
            id = "rescue_${System.currentTimeMillis()}_${point.victimNumericId}",
            victimId = point.victimNumericId,
            label = point.label.replace(" (RESCATADA)", "").replace(" (Detectada)", "").replace(" (Viva)", ""),
            timestampMs = System.currentTimeMillis(),
            latitudeGms = if (state.currentGpsLat != null) GpsLocationReader.formatLatitudeGms(state.currentGpsLat) else "00°00'00.0\"N",
            longitudeGms = if (state.currentGpsLon != null) GpsLocationReader.formatLongitudeGms(state.currentGpsLon) else "00°00'00.0\"W",
            fullGms = liveGmsText,
            latitude = state.currentGpsLat ?: 0.0,
            longitude = state.currentGpsLon ?: 0.0,
            altitudeM = state.currentGpsAlt ?: 0.0,
            depthM = abs(point.depthM),
            respRate = point.respRate,
            heartRate = point.heartRate,
            confidence = point.confidence,
            signatureCode = point.signatureCode,
            rescueNotes = "Rescate asegurado con éxito mediante navegación táctica VitalFi AR",
        )
        lastRescuedSuccessRecord = record
        onRescueVictim(
            point.victimNumericId,
            point.label,
            point.depthM,
            point.respRate,
            point.heartRate,
            point.confidence,
            point.signatureCode,
        )
        Toast.makeText(context, "✅ ¡${point.label} RESCATADA! Coordenada GMS registrada", Toast.LENGTH_LONG).show()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF040A07))
            .pointerInput(allWifiPoints) {
                detectTapGestures { offset ->
                    val w = size.width
                    val h = size.height
                    val cx = w / 2f
                    val cy = h / 2f

                    var closestPoint: ArWifiPoint? = null
                    var minDistance = Float.MAX_VALUE

                    allWifiPoints.forEach { pt ->
                        var diff = (pt.bearingDeg - curBearing) % 360.0
                        if (diff > 180.0) diff -= 360.0
                        if (diff < -180.0) diff += 360.0

                        if (abs(diff) <= (horizontalFovDeg / 2.0)) {
                            val sx = cx + (diff / (horizontalFovDeg / 2.0)).toFloat() * (w * 0.46f)
                            val sy = (cy + (pt.depthM * 40f).toFloat()).coerceIn(h * 0.28f, h * 0.76f)
                            val d = kotlin.math.hypot(offset.x - sx, offset.y - sy)
                            if (d < 140f && d < minDistance) {
                                minDistance = d
                                closestPoint = pt
                            }
                        }
                    }

                    if (closestPoint != null) {
                        val newLockId = if (state.lockedTargetId == closestPoint!!.id) null else closestPoint!!.id
                        onLockTarget(newLockId)
                        triggerHapticFeedback()
                        if (newLockId != null) {
                            Toast.makeText(context, if (isSpanish) "🔒 Objetivo fijado: ${closestPoint!!.label}" else "🔒 Target locked: ${closestPoint!!.label}", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, if (isSpanish) "🔓 Objetivo liberado (guía automática)" else "🔓 Target unlocked", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
    ) {
        // 1. Vista de Cámara Real en Vivo (CameraX) o Grid Táctico
        if (hasCameraPermission && cameraAvailable) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                    try {
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            try {
                                val cameraProvider = cameraProviderFuture.get()
                                cameraProviderInstance = cameraProvider

                                val selector = when {
                                    cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                                    cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                                    else -> null
                                }

                                if (selector != null) {
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    }
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview)
                                } else {
                                    cameraAvailable = false
                                }
                            } catch (_: Throwable) {
                                cameraAvailable = false
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                    } catch (_: Throwable) {
                        cameraAvailable = false
                    }
                    previewView
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else if (!hasCameraPermission) {
            Box(
                Modifier.fillMaxSize().background(Color(0xFF07140E)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(24.dp),
                ) {
                    Text(
                        "📷 PERMISO DE CÁMARA REQUERIDO",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        "Para ver a las víctimas y la línea de navegación en Realidad Aumentada, activa la cámara.",
                        color = Color(0xFFA0C4A0),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                    )
                    Button(
                        onClick = {
                            try {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            } catch (_: Throwable) {}
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC), contentColor = Color.Black),
                    ) {
                        Text("ACTIVAR CÁMARA AR", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            // Fondo de cuadrícula táctica
            Canvas(modifier = Modifier.fillMaxSize()) {
                val step = 40.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    drawLine(Color(0x1500FFCC), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                    x += step
                }
                var y = 0f
                while (y < size.height) {
                    drawLine(Color(0x1500FFCC), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    y += step
                }
            }
        }

        // 2. Capa de Realidad Aumentada (Canvas con Puntos Wi-Fi CSI, Zona Caliente y Guía en Suelo)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f

            // a) Retícula central táctica
            drawArLockReticle(cx, cy, isAimAligned, isTargetExplicitlyLocked, lockRingRotation)

            // b) Línea Láser de Guía en el suelo hacia el objetivo (se achica dinámicamente al acercarse)
            targetPoint?.let { target ->
                val tDiff = run {
                    var diff = (target.bearingDeg - curBearing) % 360.0
                    if (diff > 180.0) diff -= 360.0
                    if (diff < -180.0) diff += 360.0
                    diff
                }
                val inView = abs(tDiff) <= (horizontalFovDeg / 2.0)
                val targetScreenX = if (inView) {
                    cx + (tDiff / (horizontalFovDeg / 2.0)).toFloat() * (w * 0.46f)
                } else {
                    if (tDiff < 0) 30f else w - 30f
                }
                val distNorm = (target.distanceM.toFloat() / 8.0f).coerceIn(0f, 1f)
                val targetScreenY = (cy + (1.0f - distNorm) * (h * 0.36f) + (target.depthM * 18f).toFloat()).coerceIn(h * 0.38f, h * 0.84f)

                drawArGroundNavigationTrail(
                    cx = cx,
                    h = h,
                    targetX = targetScreenX,
                    targetY = targetScreenY,
                    progress = pulseWave,
                    distanceM = target.distanceM.toFloat(),
                    isInView = inView,
                    color = target.color,
                    isExplicitlyLocked = isTargetExplicitlyLocked,
                    isSpanish = isSpanish,
                    isMoving = target.isMoving,
                    velocityMps = target.velocityMps,
                )
            }

            // c) Renderizar Puntos Wi-Fi CSI detectados en el campo de visión (perspectiva 3D dinámica)
            allWifiPoints.forEach { pt ->
                var diff = (pt.bearingDeg - curBearing) % 360.0
                if (diff > 180.0) diff -= 360.0
                if (diff < -180.0) diff += 360.0

                if (abs(diff) <= (horizontalFovDeg / 2.0)) {
                    val sx = cx + (diff / (horizontalFovDeg / 2.0)).toFloat() * (w * 0.46f)
                    val distNormPt = (pt.distanceM.toFloat() / 8.0f).coerceIn(0f, 1f)
                    val sy = (cy + (1.0f - distNormPt) * (h * 0.33f) + (pt.depthM * 18f).toFloat()).coerceIn(h * 0.28f, h * 0.84f)
                    val isCurrentTarget = (pt.id == targetPoint?.id)

                    // Si está en zona cercana (< 2.2m), proyectar el PUNTO CERO / EPICENTRO en el suelo
                    if (pt.distanceM <= 2.2) {
                        drawArGroundZeroEpicenter(
                            sx = sx,
                            sy = sy + 30f,
                            depthM = pt.depthM,
                            pulse = beaconPulse,
                            isAtGroundZero = pt.isAtGroundZero,
                            convergence = pt.groundZeroConvergence,
                            isSpanish = isSpanish,
                            gpsGms = pt.gpsGms,
                        )
                    } else {
                        // Ondas de Sonar normales a mayor distancia
                        drawArSonarWaves(
                            sx = sx,
                            sy = sy + 30f,
                            progress = pulseWave,
                            distanceM = pt.distanceM.toFloat(),
                            color = pt.color,
                        )
                    }

                    // Orbe Holográfico 3D con indicador táctico de Fijado
                    drawArWifiNodePoint(
                        sx = sx,
                        sy = sy,
                        point = pt,
                        pulse = beaconPulse,
                        isExplicitlyLocked = isCurrentTarget && isTargetExplicitlyLocked,
                        isAimAligned = isCurrentTarget && isAimAligned,
                        lockRotationDeg = lockRingRotation,
                        isSpanish = isSpanish,
                    )
                }
            }
        }

        // 3. Flechas de Guía Fuera de Pantalla (Off-Screen Direction Indicators)
        if (targetPoint != null && !isTargetInView) {
            val isLeft = deltaAngleDeg < 0
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xEE00241A)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .align(if (isLeft) Alignment.CenterStart else Alignment.CenterEnd)
                        .border(1.5.dp, targetPoint.color, RoundedCornerShape(12.dp))
                        .clickable { onLockTarget(targetPoint.id) },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = null,
                            tint = targetPoint.color,
                            modifier = Modifier
                                .size(24.dp)
                                .rotate(if (isLeft) -90f else 90f),
                        )
                        Column {
                            Text(
                                if (isLeft) "← GIRA IZQ ${"%.0f".format(abs(deltaAngleDeg))}°" else "GIRA DER ${"%.0f".format(abs(deltaAngleDeg))}° →",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            )
                            Text(
                                "${targetPoint.label} (~${"%.1f".format(targetPoint.distanceM)}m)",
                                color = targetPoint.color,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }

        // 4. Encabezado Superior HUD: GPS en Vivo, Botón Listado GPS y Selector de Víctimas
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 6.dp, start = 10.dp, end = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Fila 1: Estado GPS y Botones de Acción
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xEE071A12)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.border(1.dp, Color(0x6600FFCC), RoundedCornerShape(8.dp)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF00FFCC), modifier = Modifier.size(16.dp))
                        Column {
                            Text(
                                "📍 GPS GMS EN VIVO",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            )
                            Text(
                                liveGmsText,
                                color = Color(0xFF00FFCC),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // BOTÓN DE LISTADO GPS DE TODO LO DETECTADO
                    Button(
                        onClick = { showGpsListDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF004D36),
                            contentColor = Color(0xFF00FFCC),
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(34.dp).border(1.dp, Color(0xFF00FFCC), RoundedCornerShape(8.dp)),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ListAlt, contentDescription = "Listado", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("LISTA GPS (${allWifiPoints.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .background(Color(0xAA000000), CircleShape)
                            .size(34.dp),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar AR", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Fila 2: Selector Rápido de Víctimas Detectadas o Estado de Escaneo CSI en Vivo
            if (allWifiPoints.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (isSpanish) "🎯 FIJAR:" else "🎯 LOCK:",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(end = 2.dp),
                    )
                    allWifiPoints.forEach { pt ->
                        val isSelected = (pt.id == targetPoint?.id)
                        Button(
                            onClick = {
                                if (isSelected && isTargetExplicitlyLocked) {
                                    onLockTarget(null)
                                    triggerHapticFeedback()
                                    Toast.makeText(context, if (isSpanish) "🔓 Objetivo liberado (guía automática)" else "🔓 Target unlocked", Toast.LENGTH_SHORT).show()
                                } else {
                                    onLockTarget(pt.id)
                                    triggerHapticFeedback()
                                    Toast.makeText(context, if (isSpanish) "🔒 Objetivo fijado: ${pt.label}" else "🔒 Target locked: ${pt.label}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) Color(0xFF00CC88) else Color(0xDD08241A),
                                contentColor = if (isSelected) Color.Black else Color.White,
                            ),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .height(28.dp)
                                .border(1.dp, if (isSelected) Color(0xFF00FFCC) else Color(0x4400FFCC), RoundedCornerShape(14.dp)),
                        ) {
                            Text(
                                (if (isSelected && isTargetExplicitlyLocked) "🔒 " else "🎯 ") + "${pt.label} (${"%.1f".format(pt.distanceM)}m)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xD0081C14)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, Color(0x5500FFCC), RoundedCornerShape(8.dp)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .scale(beaconPulse)
                                .background(Color(0xFF00FFCC), CircleShape)
                        )
                        Text(
                            if (isSpanish) "📡 ESCANEANDO SEÑAL CSI REAL... (Apunte a la zona)" else "📡 SCANNING REAL CSI SIGNAL... (Aim at search area)",
                            color = Color(0xFFA0D4B0),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                        )
                    }
                }
            }

            // Fila 3: Banner de ZONA DINÁMICA (Al ir llegando) — con opción de Minimizar/Expandir
            targetPoint?.let { pt ->
                if (isProximityBannerMinimized) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = proximityState.backgroundColor.copy(alpha = 0.85f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .border(1.dp, proximityState.borderColor, RoundedCornerShape(12.dp))
                                .clickable { isProximityBannerMinimized = false },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .scale(if (proximityState == ProximityZone.OVER_VICTIM) beaconPulse else 1f)
                                        .background(proximityState.textColor, CircleShape),
                                )
                                Text(
                                    "ZONA ${"%.1f".format(pt.distanceM)}m",
                                    color = proximityState.textColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                )
                                Text(
                                    "⬇ ${"%.1f".format(abs(pt.depthM))}m",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                )
                                Text(
                                    "➕ VER",
                                    color = Color(0xFF00FFCC),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                } else {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = proximityState.backgroundColor
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.5.dp, proximityState.borderColor, RoundedCornerShape(8.dp)),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                modifier = Modifier.weight(1f).padding(end = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .scale(if (proximityState == ProximityZone.OVER_VICTIM) beaconPulse else 1f)
                                        .background(proximityState.textColor, CircleShape),
                                )
                                Text(
                                    proximityState.titleText(pt.distanceM, isSpanish),
                                    color = proximityState.textColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    "⬇ ${"%.2f".format(abs(pt.depthM))}m",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                )
                                Button(
                                    onClick = { isProximityBannerMinimized = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x66000000), contentColor = Color(0xFFA0C4A0)),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.height(24.dp),
                                    shape = RoundedCornerShape(6.dp),
                                ) {
                                    Text("✕ MIN", fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }
            }
            // Fila 4: Panel Superior de RESCATAR y Telemetría (Despeja totalmente el suelo y láser)
            targetPoint?.let { pt ->
                // BOTÓN PRINCIPAL DE RESCATAR
                Button(
                    onClick = { executeRescue(pt) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (pt.isRescued) Color(0xFF998800) else if (proximityState == ProximityZone.OVER_VICTIM) Color(0xFF00EE99) else Color(0xFF00A86B),
                        contentColor = Color.Black,
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .border(
                            1.5.dp,
                            if (pt.isRescued) Color(0xFFFFD700) else if (proximityState == ProximityZone.OVER_VICTIM) Color(0xFF00FFCC) else Color(0xFF00CC88),
                            RoundedCornerShape(10.dp)
                        ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = if (pt.isRescued) Icons.Default.CheckCircle else Icons.Default.GpsFixed,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            if (pt.isRescued) "VÍCTIMA RESCATADA ✅ (GUARDAR NUEVO GMS)" else "🚨 RESCATAR ${pt.label.uppercase()} • REGISTRAR GMS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }

                // TARJETA DE TELEMETRÍA SUPERIOR COMPACTA O EXPANDIDA
                if (!isPanelExpanded) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xF00B1E16),
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0x8800FFCC), RoundedCornerShape(12.dp))
                            .clickable { isPanelExpanded = true },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    "🎯 ${"%.2f".format(pt.distanceM)}m",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                )
                                Text(
                                    "🫁 ${"%.1f".format(pt.respRate)}",
                                    color = Color(0xFF00FFCC),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                                Text(
                                    "❤️ ${pt.heartRate.toInt()}",
                                    color = Color(0xFFFF6688),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                                Text(
                                    "⬇ ${"%.2f".format(abs(pt.depthM))}m",
                                    color = Color(0xFFFFCC00),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }

                            Button(
                                onClick = { isPanelExpanded = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0x4400FFCC), contentColor = Color(0xFF00FFCC)),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 1.dp),
                                modifier = Modifier.height(24.dp),
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Text("▲ MÁS", fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xF50D241A)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF00FFCC), RoundedCornerShape(12.dp)),
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "📍 ${pt.label} (${if (isTargetExplicitlyLocked) "FIJADO 🔒" else "SELECCIONADO"})",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Button(
                                    onClick = { isPanelExpanded = false },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x66000000), contentColor = Color(0xFFA0C4A0)),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 1.dp),
                                    modifier = Modifier.height(22.dp),
                                    shape = RoundedCornerShape(6.dp),
                                ) {
                                    Text("✕ MIN", fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("DISTANCIA EXACTA", color = Color.Gray, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
                                    Text("🎯 ${"%.2f".format(pt.distanceM)} m", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("PROFUNDIDAD ESCOMBROS", color = Color.Gray, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
                                    Text("⬇ ${"%.2f".format(abs(pt.depthM))} m", color = Color(0xFFFFCC00), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("RESPIRACIÓN CSI", color = Color.Gray, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
                                    Text("🫁 ${"%.1f".format(pt.respRate)} rpm", color = Color(0xFF00FFCC), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("RITMO CARDÍACO", color = Color.Gray, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
                                    Text("❤️ ${pt.heartRate.toInt()} lpm", color = Color(0xFFFF6688), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                            }

                            Column {
                                Text("COORDENADA GPS GMS", color = Color.Gray, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
                                Text("📍 $liveGmsText", color = Color(0xFF00FFCC), fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        }

        // 5. Mini-Brújula Radar Táctica (Esquina inferior izquierda - Totalmente despejada)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 14.dp, bottom = 24.dp)
                .size(54.dp)
                .background(Color(0xEE08140E), CircleShape)
                .border(1.5.dp, Color(0xFF00FFCC), CircleShape),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                val c = Offset(r, r)
                drawCircle(Color(0x3300FFCC), radius = r * 0.7f, center = c, style = Stroke(1f))
                drawCircle(Color(0x6600FFCC), radius = r * 0.35f, center = c, style = Stroke(1f))
                drawLine(Color(0xFF00FFCC), c, Offset(c.x, c.y - r * 0.85f), strokeWidth = 1.5f)

                allWifiPoints.forEach { pt ->
                    val relAngleRad = Math.toRadians((pt.bearingDeg - curBearing) - 90.0)
                    val distFraction = (pt.distanceM / 10.0).coerceIn(0.18, 0.85).toFloat()
                    val vx = c.x + cos(relAngleRad).toFloat() * (r * distFraction)
                    val vy = c.y + sin(relAngleRad).toFloat() * (r * distFraction)
                    val isT = (pt.id == targetPoint?.id)
                    drawCircle(pt.color, radius = if (isT) 5f else 2.5f, center = Offset(vx, vy))
                }
            }
            Text(
                "${curBearing.toInt()}°",
                color = Color.White,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }

    // Modal de Diálogo: Confirmación de Rescate Exitoso con Coordenadas GMS
    lastRescuedSuccessRecord?.let { record ->
        Dialog(onDismissRequest = { lastRescuedSuccessRecord = null }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF061E14)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, Color(0xFF00FFCC), RoundedCornerShape(16.dp)),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(Color(0xFF004D36), CircleShape)
                            .border(2.dp, Color(0xFF00FFCC), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF00FFCC), modifier = Modifier.size(32.dp))
                    }

                    Text(
                        "🎉 ¡VÍCTIMA RESCATADA CON ÉXITO!",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0C2B1D)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                "📍 COORDENADAS GMS REGISTRADAS:",
                                color = Color.Gray,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                            Text(
                                record.fullGms,
                                color = Color(0xFF00FFCC),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("⬇ Prof: ${"%.2f".format(record.depthM)}m", color = Color(0xFFFFCC00), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                Text("🫁 ${"%.1f".format(record.respRate)} rpm", color = Color(0xFF00FFCC), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                Text("❤️ ${record.heartRate.toInt()} lpm", color = Color(0xFFFF6688), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = ClipData.newPlainText("Coordenadas GMS", record.fullGms)
                                clipboard?.setPrimaryClip(clip)
                                Toast.makeText(context, "Coordenadas GMS copiadas al portapapeles", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC), contentColor = Color.Black),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("COPIAR GMS", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Reporte de Rescate VitalFi")
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "🚨 REPORTE DE RESCATE VITALFI AR\n" +
                                        "Víctima: ${record.label}\n" +
                                        "Coordenadas GMS: ${record.fullGms}\n" +
                                        "Profundidad: ${"%.2f".format(record.depthM)} m\n" +
                                        "Signos Vitales: ${"%.1f".format(record.respRate)} rpm | ${record.heartRate.toInt()} lpm\n" +
                                        "Fecha/Hora: ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(record.timestampMs))}"
                                    )
                                }
                                try {
                                    val chooser = Intent.createChooser(shareIntent, "Compartir rescate").apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(chooser)
                                } catch (_: Throwable) {
                                    Toast.makeText(context, "No se pudo compartir", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF144D34), contentColor = Color.White),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("COMPARTIR", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = { lastRescuedSuccessRecord = null },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FFFFFF), contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("CONTINUAR EN MODO AR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Modal de Diálogo: LISTADO GPS DE TODO LO DETECTADO Y RESCATADO
    if (showGpsListDialog) {
        var selectedTab by remember { mutableIntStateOf(0) }

        Dialog(onDismissRequest = { showGpsListDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF061A12)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, Color(0xFF00FFCC), RoundedCornerShape(16.dp)),
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ListAlt, contentDescription = null, tint = Color(0xFF00FFCC))
                            Text(
                                "LISTADO GPS TÁCTICO",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        IconButton(
                            onClick = { showGpsListDialog = false },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }

                    // Pestañas: Detectados vs Rescatados
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color(0xFF0C241B),
                        contentColor = Color(0xFF00FFCC),
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = Color(0xFF00FFCC)
                            )
                        }
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("DETECTADOS (${allWifiPoints.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("RESCATADOS (${state.rescuedVictims.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                        )
                    }

                    if (selectedTab == 0) {
                        // LISTA DE VÍCTIMAS DETECTADAS EN EL SITIO
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(allWifiPoints, key = { it.id }) { pt ->
                                val isCurLocked = (pt.id == targetPoint?.id && isTargetExplicitlyLocked)
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0B241A)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, if (isCurLocked) Color(0xFF00FFCC) else Color(0x6600FFCC), RoundedCornerShape(10.dp)),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                "👤 ${pt.label}",
                                                color = pt.color,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                            Text(
                                                "Dist: ${"%.1f".format(pt.distanceM)}m • Rumbo: ${pt.bearingDeg.toInt()}°",
                                                color = Color.Gray,
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace,
                                            )
                                        }

                                        Text(
                                            "📍 GPS: $liveGmsText",
                                            color = Color(0xFF00FFCC),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                "⬇ Prof: ${"%.2f".format(abs(pt.depthM))}m • 🫁 ${"%.1f".format(pt.respRate)} rpm",
                                                color = Color(0xFFA0C4A0),
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace,
                                            )

                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Button(
                                                    onClick = {
                                                        onLockTarget(pt.id)
                                                        showGpsListDialog = false
                                                        triggerHapticFeedback()
                                                        Toast.makeText(context, "🎯 Fijado como objetivo: ${pt.label}", Toast.LENGTH_SHORT).show()
                                                    },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (isCurLocked) Color(0xFF00FFCC) else Color(0xFF144D34),
                                                        contentColor = if (isCurLocked) Color.Black else Color.White,
                                                    ),
                                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(26.dp),
                                                    shape = RoundedCornerShape(6.dp),
                                                ) {
                                                    Text(if (isCurLocked) "FIJADO 🔒" else "FIJAR 🎯", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // LISTA DE VÍCTIMAS YA RESCATADAS
                        if (state.rescuedVictims.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "No hay víctimas registradas como rescatadas todavía.\nAcerque la cámara a una persona y presione RESCATAR VÍCTIMA.",
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(state.rescuedVictims, key = { it.id }) { victim ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B241A)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, Color(0xFFFFD700), RoundedCornerShape(10.dp)),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    "✅ ${victim.label}",
                                                    color = Color(0xFFFFD700),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                                Text(
                                                    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(victim.timestampMs)),
                                                    color = Color.Gray,
                                                    fontSize = 9.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                )
                                            }

                                            Text(
                                                "📍 GMS: ${victim.fullGms}",
                                                color = Color(0xFF00FFCC),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                            )

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    "⬇ ${"%.2f".format(victim.depthM)}m • 🫁 ${"%.1f".format(victim.respRate)} rpm • ❤️ ${victim.heartRate.toInt()} lpm",
                                                    color = Color(0xFFA0C4A0),
                                                    fontSize = 9.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                )

                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    IconButton(
                                                        onClick = {
                                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                            val clip = ClipData.newPlainText("GMS", victim.fullGms)
                                                            clipboard?.setPrimaryClip(clip)
                                                            Toast.makeText(context, "Coordenadas GMS copiadas", Toast.LENGTH_SHORT).show()
                                                        },
                                                        modifier = Modifier.size(24.dp),
                                                    ) {
                                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = Color(0xFF00FFCC), modifier = Modifier.size(14.dp))
                                                    }
                                                    IconButton(
                                                        onClick = { onRemoveRescuedVictim(victim.id) },
                                                        modifier = Modifier.size(24.dp),
                                                    ) {
                                                        Icon(Icons.Default.Delete, contentDescription = "Borrar", tint = Color(0xFFFF6688), modifier = Modifier.size(14.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Botones inferiores de Compartir y Limpiar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onClearRescuedVictims,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FF4444), contentColor = Color(0xFFFF8888)),
                            modifier = Modifier.weight(0.35f),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text("LIMPIAR", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                val fullReport = StringBuilder()
                                fullReport.append("🚨 REPORTE DE RESCATE VITALFI (COORDENADAS GMS)\n")
                                fullReport.append("Total Detectados: ${allWifiPoints.size}\n")
                                fullReport.append("Total Rescatados: ${state.rescuedVictims.size}\n\n")
                                fullReport.append("--- VÍCTIMAS RESCATADAS ---\n")
                                state.rescuedVictims.forEachIndexed { i, v ->
                                    fullReport.append("${i + 1}. ${v.label}\n")
                                    fullReport.append("   📍 GMS: ${v.fullGms}\n")
                                    fullReport.append("   ⬇ Profundidad: ${"%.2f".format(v.depthM)} m\n")
                                    fullReport.append("   🫁 Signos: ${"%.1f".format(v.respRate)} rpm | ❤️ ${v.heartRate.toInt()} lpm\n")
                                    fullReport.append("   ⏱ Hora: ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(v.timestampMs))}\n\n")
                                }
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Reporte de Rescates y Coordenadas GPS")
                                    putExtra(Intent.EXTRA_TEXT, fullReport.toString())
                                }
                                try {
                                    val chooser = Intent.createChooser(shareIntent, "Compartir Reporte").apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(chooser)
                                } catch (_: Throwable) {
                                    Toast.makeText(context, "No se pudo compartir", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC), contentColor = Color.Black),
                            modifier = Modifier.weight(0.65f),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("COMPARTIR LISTA GMS", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/** Enum para el cálculo dinámico de la zona táctica según distancia */
private enum class ProximityZone(
    val backgroundColor: Color,
    val borderColor: Color,
    val textColor: Color,
) {
    OVER_VICTIM(Color(0xF0770020), Color(0xFFFF0055), Color(0xFFFFEE44)),
    VERY_CLOSE(Color(0xEE4D2800), Color(0xFFFF9900), Color(0xFFFFCC00)),
    APPROACHING(Color(0xDD3A2800), Color(0xFFFFCC00), Color(0xFFFFDD44)),
    DISTANT(Color(0xDD00241A), Color(0x6600FFCC), Color(0xFFA0C4A0));

    fun titleText(distM: Double, isSpanish: Boolean = true): String {
        val d = "%.2f".format(distM)
        return if (isSpanish) {
            when (this) {
                OVER_VICTIM -> "🛑 ¡DETÉNGASE! PUNTO CERO ALCANZADO (${d}m)"
                VERY_CLOSE -> "🎯 APROXIMÁNDOSE A PUNTO CERO (${d}m)"
                APPROACHING -> "🟠 EN CAMINO AL EPICENTRO (~${d}m)"
                DISTANT -> "🔵 RUMBO A LA ZONA (~${d}m) — SIGA EL LÁSER"
            }
        } else {
            when (this) {
                OVER_VICTIM -> "🛑 STOP! GROUND ZERO REACHED (${d}m)"
                VERY_CLOSE -> "🎯 APPROACHING GROUND ZERO (${d}m)"
                APPROACHING -> "🟠 ON ROUTE TO EPICENTER (~${d}m)"
                DISTANT -> "🔵 HEADING TO TARGET (~${d}m) — FOLLOW LASER"
            }
        }
    }
}

/** Modelo interno para cada punto/nodo Wi-Fi CSI proyectado en el espacio 3D */
private data class ArWifiPoint(
    val id: String,
    val victimNumericId: Int = 1,
    val label: String,
    val subtitle: String,
    val bearingDeg: Double,
    val distanceM: Double,
    val depthM: Double,
    val signalStrengthDbm: Int,
    val isVictim: Boolean,
    val isRescued: Boolean = false,
    val respRate: Double = 0.0,
    val heartRate: Double = 0.0,
    val confidence: Double = 0.0,
    val signatureCode: String = "",
    val color: Color = Color(0xFF00FFCC),
    val isMoving: Boolean = false,
    val velocityMps: Float = 0.0f,
    val aiConfidenceTag: String = "",
    val isAtGroundZero: Boolean = false,
    val groundZeroConvergence: Float = 0.0f,
    val gpsLat: Double? = null,
    val gpsLon: Double? = null,
    val gpsGms: String = "",
    val isPinned: Boolean = false,
)

/** Dibuja el Marcador Táctico de PUNTO CERO directamente en el suelo sobre la víctima */
private fun DrawScope.drawArGroundZeroEpicenter(
    sx: Float,
    sy: Float,
    depthM: Double,
    pulse: Float,
    isAtGroundZero: Boolean,
    convergence: Float,
    isSpanish: Boolean = true,
    gpsGms: String = "",
) {
    val baseRadius = (55f + convergence * 35f).coerceIn(40f, 100f) * (if (isAtGroundZero) (0.90f + pulse * 0.20f) else 1.0f)
    val ringColor = if (isAtGroundZero) Color(0xFFFF0055) else Color(0xFF00FFCC)
    val beaconColor = if (isAtGroundZero) Color(0xFFFF2266) else Color(0xFF00E5FF)

    // 1. Círculos concéntricos de impacto / Diana de Punto Cero en el suelo
    for (i in 1..3) {
        val r = baseRadius * (i / 3f)
        drawOval(
            color = ringColor.copy(alpha = if (isAtGroundZero) (0.95f - i * 0.15f) else (0.65f - i * 0.15f)),
            topLeft = Offset(sx - r, sy - r * 0.32f),
            size = Size(r * 2f, r * 0.64f),
            style = Stroke(width = if (i == 3) 3.6f else 2.0f),
        )
    }

    // 2. Cruz táctica sobre la diana
    val crossLen = baseRadius * 1.35f
    drawLine(
        color = ringColor,
        start = Offset(sx - crossLen, sy),
        end = Offset(sx + crossLen, sy),
        strokeWidth = 2.4f,
    )
    drawLine(
        color = ringColor,
        start = Offset(sx, sy - crossLen * 0.32f),
        end = Offset(sx, sy + crossLen * 0.32f),
        strokeWidth = 2.4f,
    )

    // 3. Columna / Baliza vertical que penetra hacia el suelo
    val pillarHeight = 105f
    drawLine(
        brush = Brush.verticalGradient(
            colors = listOf(beaconColor.copy(alpha = 0.9f), Color.White, beaconColor.copy(alpha = 0.2f), Color.Transparent),
            startY = sy - pillarHeight,
            endY = sy + 25f,
        ),
        start = Offset(sx, sy - pillarHeight),
        end = Offset(sx, sy + 25f),
        strokeWidth = if (isAtGroundZero) 6.5f else 4.0f,
        cap = StrokeCap.Round,
    )

    // 4. Cartel flotante de PUNTO CERO / EXCAVAR con GPS
    val hasGps = gpsGms.isNotBlank()
    val badgeW = (if (hasGps) 260f else 230f)
    val badgeH = (if (hasGps) 66f else 50f)
    val badgeY = sy - pillarHeight - 10f

    val paintBg = android.graphics.Paint().apply {
        color = if (isAtGroundZero) android.graphics.Color.argb(245, 90, 0, 24) else android.graphics.Color.argb(235, 0, 48, 36)
        style = android.graphics.Paint.Style.FILL
        isAntiAlias = true
    }
    val paintBorder = android.graphics.Paint().apply {
        color = if (isAtGroundZero) android.graphics.Color.parseColor("#FF0055") else android.graphics.Color.parseColor("#00FFCC")
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }
    val paintText = android.graphics.Paint().apply {
        color = if (isAtGroundZero) android.graphics.Color.parseColor("#FFFF55") else android.graphics.Color.parseColor("#00FFFF")
        textSize = 19f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }
    val paintSub = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 13f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.NORMAL)
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }
    val paintGps = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#00FFCC")
        textSize = 11f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }

    drawContext.canvas.nativeCanvas.drawRoundRect(
        sx - badgeW / 2f, badgeY - badgeH, sx + badgeW / 2f, badgeY, 10f, 10f, paintBg
    )
    drawContext.canvas.nativeCanvas.drawRoundRect(
        sx - badgeW / 2f, badgeY - badgeH, sx + badgeW / 2f, badgeY, 10f, 10f, paintBorder
    )

    val title = if (isAtGroundZero) {
        if (isSpanish) "🛑 ¡PUNTO CERO!" else "🛑 GROUND ZERO!"
    } else {
        if (isSpanish) "🎯 EPICENTRO VÍCTIMA" else "🎯 VICTIM EPICENTER"
    }
    val sub = if (isSpanish) "⬇ EXCAVAR A ~${"%.2f".format(abs(depthM))}m" else "⬇ DIG AT ~${"%.2f".format(abs(depthM))}m"

    if (hasGps) {
        drawContext.canvas.nativeCanvas.drawText(title, sx, badgeY - badgeH * 0.68f, paintText)
        drawContext.canvas.nativeCanvas.drawText(sub, sx, badgeY - badgeH * 0.40f, paintSub)
        drawContext.canvas.nativeCanvas.drawText("📍 $gpsGms", sx, badgeY - badgeH * 0.12f, paintGps)
    } else {
        drawContext.canvas.nativeCanvas.drawText(title, sx, badgeY - badgeH * 0.55f, paintText)
        drawContext.canvas.nativeCanvas.drawText(sub, sx, badgeY - badgeH * 0.16f, paintSub)
    }
}

/** Dibuja la retícula táctica central */
private fun DrawScope.drawArLockReticle(
    cx: Float,
    cy: Float,
    isAimAligned: Boolean,
    isExplicitlyLocked: Boolean,
    rotationDeg: Float,
) {
    val reticleColor = if (isAimAligned || isExplicitlyLocked) Color(0xFF00FFCC) else Color(0x88FFFFFF)
    val len = 24f
    val gap = 14f

    drawLine(reticleColor, Offset(cx - len - gap, cy), Offset(cx - gap, cy), strokeWidth = 2f)
    drawLine(reticleColor, Offset(cx + gap, cy), Offset(cx + len + gap, cy), strokeWidth = 2f)
    drawLine(reticleColor, Offset(cx, cy - len - gap), Offset(cx, cy - gap), strokeWidth = 2f)
    drawLine(reticleColor, Offset(cx, cy + gap), Offset(cx, cy + len + gap), strokeWidth = 2f)

    drawCircle(
        color = reticleColor.copy(alpha = if (isAimAligned || isExplicitlyLocked) 0.55f else 0.20f),
        radius = 44f,
        center = Offset(cx, cy),
        style = Stroke(width = 1.4f),
    )

    if (isAimAligned || isExplicitlyLocked) {
        val numBrackets = 4
        val bracketRadius = 54f
        val sweepAngle = 38f
        for (i in 0 until numBrackets) {
            val startAngle = rotationDeg + i * (360f / numBrackets)
            drawArc(
                color = reticleColor,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(cx - bracketRadius, cy - bracketRadius),
                size = Size(bracketRadius * 2f, bracketRadius * 2f),
                style = Stroke(width = 2.4f, cap = StrokeCap.Round),
            )
        }
    }
}

/** Dibuja la línea láser de navegación sobre el suelo hacia el objetivo.
 *  El haz se adapta dinámicamente a la distancia:
 *    - LEJOS (>6m)  → fino, azul, chevrons lentos
 *    - MEDIO (3-6m) → medio, cian/verde, chevrons moderados
 *    - CERCA (1-3m) → grueso, naranja/verde intenso, chevrons rápidos
 *    - ENCIMA (<1m) → máximo grosor, rojo/blanco, pulsación de alerta
 */
private fun DrawScope.drawArGroundNavigationTrail(
    cx: Float,
    h: Float,
    targetX: Float,
    targetY: Float,
    progress: Float,
    distanceM: Float,
    isInView: Boolean,
    color: Color = Color(0xFF00FFCC),
    isExplicitlyLocked: Boolean = false,
    isSpanish: Boolean = true,
    isMoving: Boolean = false,
    velocityMps: Float = 0.0f,
) {
    val startPt = Offset(cx, h - 15f)
    val endPt = Offset(targetX, targetY)

    // ── Factor de proximidad: 1.0 = encima, 0.0 = lejos (>8m) ──
    val proximityFactor = (1.0f - (distanceM / 8.0f)).coerceIn(0f, 1f)

    // ── Colores adaptativos basados en distancia ──
    val laserCoreColor: Color
    val laserGlowColor: Color
    val chevronColor: Color
    when {
        distanceM <= 1.0f -> {
            // ENCIMA DEL PUNTO CERO — rojo intenso pulsante
            val pulse = 0.7f + 0.3f * kotlin.math.sin((progress * Math.PI * 4).toFloat())
            laserCoreColor = Color(0xFFFF3300).copy(alpha = pulse)
            laserGlowColor = Color(0xBBFF4400).copy(alpha = pulse * 0.8f)
            chevronColor   = Color(0xFFFFCC00).copy(alpha = pulse)
        }
        distanceM <= 3.0f -> {
            // CERCA — naranja a verde brillante
            val t = ((distanceM - 1f) / 2f).coerceIn(0f, 1f)
            laserCoreColor = lerpColor(Color(0xFFFF8800), Color(0xFF00FFCC), t)
            laserGlowColor = lerpColor(Color(0x99FF6600), Color(0x7700FFCC), t)
            chevronColor   = lerpColor(Color(0xFFFFDD00), Color(0xFFFFFFFF), t)
        }
        distanceM <= 6.0f -> {
            // APROXIMÁNDOSE — verde cian
            val t = ((distanceM - 3f) / 3f).coerceIn(0f, 1f)
            laserCoreColor = lerpColor(Color(0xFF00FFCC), Color(0xFF3388FF), t)
            laserGlowColor = lerpColor(Color(0x7700FFCC), Color(0x443388FF), t)
            chevronColor   = lerpColor(Color(0xFFFFFFFF), Color(0xCC88BBFF), t)
        }
        else -> {
            // LEJOS — azul tenue
            laserCoreColor = Color(0xFF3388FF).copy(alpha = 0.55f)
            laserGlowColor = Color(0x332266CC)
            chevronColor   = Color(0x9988AAFF)
        }
    }

    // ── Overrides si está fijado explícitamente ──
    val finalCoreColor = if (isExplicitlyLocked) {
        if (isMoving) Color(0xFF00E5FF) else laserCoreColor
    } else laserCoreColor
    val finalGlowColor = if (isExplicitlyLocked) {
        if (isMoving) Color(0x7700E5FF) else laserGlowColor
    } else laserGlowColor

    // ── Grosor adaptativo: más cerca = más grueso ──
    val baseGlowWidth  = if (isExplicitlyLocked) 24f else (8f + proximityFactor * 28f)
    val baseCoreWidth  = if (isExplicitlyLocked) 6.5f else (2f + proximityFactor * 7f)
    // Pulso de grosor extra cuando está muy cerca
    val widthPulse = if (distanceM <= 2.0f) {
        1f + 0.25f * kotlin.math.sin((progress * Math.PI * 6).toFloat())
    } else 1f

    // 1. Halo / Resplandor difuso exterior
    drawLine(
        brush = Brush.verticalGradient(
            colors = listOf(
                finalGlowColor.copy(alpha = finalGlowColor.alpha * 0.3f),
                finalGlowColor,
            ),
            startY = targetY,
            endY = h,
        ),
        start = startPt,
        end = endPt,
        strokeWidth = baseGlowWidth * widthPulse,
        cap = StrokeCap.Round,
    )

    // 2. Núcleo del Haz Láser sólido
    drawLine(
        brush = Brush.verticalGradient(
            colors = listOf(
                finalCoreColor.copy(alpha = 0.50f),
                finalCoreColor,
            ),
            startY = targetY,
            endY = h,
        ),
        start = startPt,
        end = endPt,
        strokeWidth = baseCoreWidth * widthPulse,
        cap = StrokeCap.Round,
    )

    // 3. Chevrons direccionados — velocidad y cantidad adaptativas a la distancia
    val numChevrons = when {
        isExplicitlyLocked -> 10
        distanceM <= 1.5f -> 14    // Muy rápido = muchos chevrons
        distanceM <= 3.0f -> 10
        distanceM <= 6.0f -> 7
        else              -> 5     // Lejos = pocos chevrons
    }
    // Factor de velocidad: más cerca = chevrons se mueven más rápido
    val speedMultiplier = when {
        distanceM <= 1.0f -> 3.0f
        distanceM <= 3.0f -> 2.0f
        distanceM <= 6.0f -> 1.3f
        else              -> 1.0f
    }
    val chevronProgress = (progress * speedMultiplier) % 1.0f

    for (i in 0 until numChevrons) {
        val t = ((chevronProgress + i.toFloat() / numChevrons) % 1.0f)
        val curX = startPt.x + (endPt.x - startPt.x) * t
        val curY = startPt.y + (endPt.y - startPt.y) * t
        val chevronWidth = ((12f + proximityFactor * 30f) * (1.0f - t * 0.40f)).coerceAtLeast(8f)

        val path = Path().apply {
            moveTo(curX - chevronWidth, curY + 16f)
            lineTo(curX, curY)
            lineTo(curX + chevronWidth, curY + 16f)
        }
        val alpha = ((1.0f - t * 0.25f) * (0.4f + proximityFactor * 0.55f)).coerceIn(0.08f, 1f)
        drawPath(
            path = path,
            color = if (isExplicitlyLocked) {
                Color(0xFFFFFFFF).copy(alpha = alpha)
            } else {
                chevronColor.copy(alpha = alpha)
            },
            style = Stroke(
                width = (1.5f + proximityFactor * 3.5f) * widthPulse,
                cap = StrokeCap.Round,
            ),
        )
    }

    // 4. Anillos de impacto progresivos en el punto target
    //    Se vuelven más grandes y frecuentes conforme te acercas
    if (isExplicitlyLocked || distanceM <= 4.0f) {
        val ringCount = if (distanceM <= 1.5f) 3 else if (distanceM <= 3.0f) 2 else 1
        for (r in 0 until ringCount) {
            val rPhase = ((progress * speedMultiplier + r.toFloat() / ringCount) % 1.0f)
            val maxRadius = 14f + proximityFactor * 30f
            val ringRadius = 6f + rPhase * maxRadius
            val ringAlpha = (1f - rPhase).coerceIn(0.05f, 0.85f) * proximityFactor.coerceAtLeast(0.3f)
            drawCircle(
                color = finalCoreColor.copy(alpha = ringAlpha),
                radius = ringRadius,
                center = endPt,
                style = Stroke(width = 1.5f + proximityFactor * 2f),
            )
        }
        // Punto central sólido (más grande cerca)
        drawCircle(
            color = finalCoreColor,
            radius = 4f + proximityFactor * 8f,
            center = endPt,
        )
    }

    // 5. Etiqueta de distancia
    if (isInView) {
        val midX = (startPt.x + endPt.x) / 2f
        val midY = (startPt.y + endPt.y) / 2f
        val paintDist = android.graphics.Paint().apply {
            this.color = when {
                distanceM <= 1.0f -> android.graphics.Color.parseColor("#FF3300")
                isExplicitlyLocked && isMoving -> android.graphics.Color.parseColor("#00E5FF")
                isExplicitlyLocked -> android.graphics.Color.parseColor("#00FFCC")
                distanceM <= 3.0f -> android.graphics.Color.parseColor("#FFCC00")
                else -> android.graphics.Color.WHITE
            }
            this.textSize = (18f + proximityFactor * 8f).coerceIn(18f, 28f)
            this.typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
            this.textAlign = android.graphics.Paint.Align.CENTER
            this.isAntiAlias = true
        }
        val paintDistBg = android.graphics.Paint().apply {
            this.color = when {
                distanceM <= 1.0f -> android.graphics.Color.argb(240, 80, 0, 0)
                distanceM <= 3.0f -> android.graphics.Color.argb(230, 60, 40, 0)
                isExplicitlyLocked -> android.graphics.Color.argb(235, 0, 48, 34)
                else -> android.graphics.Color.argb(180, 0, 28, 48)
            }
            this.style = android.graphics.Paint.Style.FILL
            this.isAntiAlias = true
        }
        val paintDistBorder = android.graphics.Paint().apply {
            this.color = when {
                distanceM <= 1.0f -> android.graphics.Color.parseColor("#FF3300")
                distanceM <= 3.0f -> android.graphics.Color.parseColor("#FFCC00")
                isExplicitlyLocked -> android.graphics.Color.parseColor("#00FFCC")
                else -> android.graphics.Color.parseColor("#3388FF")
            }
            this.style = android.graphics.Paint.Style.STROKE
            this.strokeWidth = 2.5f
            this.isAntiAlias = true
        }
        val labelText = if (distanceM <= 1.0f) {
            if (isSpanish) "🛑 ¡PUNTO CERO! · ${"%.2f".format(distanceM)}m" else "🛑 GROUND ZERO! · ${"%.2f".format(distanceM)}m"
        } else if (isExplicitlyLocked) {
            if (isMoving) {
                if (isSpanish) "🔒 RASTREANDO · ${"%.2f".format(distanceM)}m (~${"%.1f".format(velocityMps)}m/s)" else "🔒 TRACKING · ${"%.2f".format(distanceM)}m (~${"%.1f".format(velocityMps)}m/s)"
            } else {
                if (isSpanish) "🔒 LÁSER FIJADO · ${"%.2f".format(distanceM)}m" else "🔒 LOCKED LASER · ${"%.2f".format(distanceM)}m"
            }
        } else if (distanceM <= 3.0f) {
            if (isSpanish) "⚠️ CERCA · ${"%.2f".format(distanceM)}m" else "⚠️ CLOSE · ${"%.2f".format(distanceM)}m"
        } else {
            "▲ ${"%.2f".format(distanceM)}m"
        }
        val boxWidth = when {
            distanceM <= 1.0f -> 145f
            isExplicitlyLocked && isMoving -> 135f
            isExplicitlyLocked -> 105f
            distanceM <= 3.0f -> 95f
            else -> 68f
        }
        drawContext.canvas.nativeCanvas.drawRoundRect(
            midX - boxWidth, midY - 22f, midX + boxWidth, midY + 9f, 8f, 8f, paintDistBg
        )
        drawContext.canvas.nativeCanvas.drawRoundRect(
            midX - boxWidth, midY - 22f, midX + boxWidth, midY + 9f, 8f, 8f, paintDistBorder
        )
        drawContext.canvas.nativeCanvas.drawText(
            labelText,
            midX,
            midY,
            paintDist
        )
    }
}

/** Interpola linealmente entre dos colores Compose */
private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val tt = t.coerceIn(0f, 1f)
    return Color(
        red   = a.red   + (b.red   - a.red)   * tt,
        green = a.green + (b.green - a.green) * tt,
        blue  = a.blue  + (b.blue  - a.blue)  * tt,
        alpha = a.alpha + (b.alpha - a.alpha) * tt,
    )
}

private fun DrawScope.drawArSonarWaves(
    sx: Float,
    sy: Float,
    progress: Float,
    distanceM: Float,
    color: Color = Color(0xFF00FFCC),
) {
    val perspectiveScale = (2.4f / distanceM.coerceIn(0.6f, 12.0f)).coerceIn(0.25f, 2.5f)
    val maxRadius = 130f * perspectiveScale

    for (i in 0..2) {
        val p = (progress + i * 0.33f) % 1.0f
        val r = p * maxRadius
        val alpha = (1.0f - p).coerceIn(0f, 1f) * 0.70f

        drawOval(
            color = color.copy(alpha = alpha),
            topLeft = Offset(sx - r, sy - r * 0.28f),
            size = Size(r * 2f, r * 0.56f),
            style = Stroke(width = (2.5f * perspectiveScale).coerceIn(1.2f, 4.5f)),
        )
    }
}

/** Renderiza el Punto / Orbe Holográfico 3D estilo Pokémon GO con IA Destilada, Coordenadas GPS e indicador de movimiento */
private fun DrawScope.drawArWifiNodePoint(
    sx: Float,
    sy: Float,
    point: ArWifiPoint,
    pulse: Float,
    isExplicitlyLocked: Boolean,
    isAimAligned: Boolean,
    lockRotationDeg: Float = 0f,
    isSpanish: Boolean = true,
) {
    val isPinnedOrLocked = point.isPinned || isExplicitlyLocked
    val perspectiveScale = (2.4f / point.distanceM.toFloat().coerceIn(0.6f, 12.0f)).coerceIn(0.30f, 2.4f)
    val baseColor = if (point.isRescued) {
        Color(0xFFFFD700)
    } else if (isPinnedOrLocked) {
        if (point.isMoving) Color(0xFF00E5FF) else Color(0xFF00FFCC)
    } else {
        point.color
    }

    // 1. Animación de flotación suave 3D estilo Pokémon GO
    val bobbingOffset = kotlin.math.sin((lockRotationDeg * 2.2f + point.victimNumericId * 50f) * (Math.PI.toFloat() / 180f)) * (9f * perspectiveScale)
    val coreY = sy + bobbingOffset
    val columnHeight = (85f * perspectiveScale).coerceIn(28f, 160f)
    val groundY = sy + columnHeight

    // 2. Plataforma Holográfica de Suelo 3D (Poké-Spawn Ground Rings)
    val groundRadius = (32f * perspectiveScale).coerceIn(12f, 65f)
    val ringAlpha = if (isPinnedOrLocked) 0.85f else 0.45f
    // Anillo exterior en el suelo con perspectiva elíptica
    drawOval(
        color = baseColor.copy(alpha = ringAlpha),
        topLeft = Offset(sx - groundRadius, groundY - groundRadius * 0.30f),
        size = Size(groundRadius * 2f, groundRadius * 0.60f),
        style = Stroke(width = (2.8f * perspectiveScale).coerceIn(1.5f, 5.0f)),
    )
    // Anillo interior concéntrico
    drawOval(
        color = baseColor.copy(alpha = ringAlpha * 0.55f),
        topLeft = Offset(sx - groundRadius * 0.6f, groundY - groundRadius * 0.18f),
        size = Size(groundRadius * 1.2f, groundRadius * 0.36f),
        style = Stroke(width = (1.8f * perspectiveScale).coerceIn(1.0f, 3.5f)),
    )
    // Muescas cardinales giratorias en la base del suelo
    if (isPinnedOrLocked) {
        for (i in 0 until 4) {
            val angleRad = (lockRotationDeg + i * 90f) * (Math.PI.toFloat() / 180f)
            val nx = sx + groundRadius * kotlin.math.cos(angleRad)
            val ny = groundY + groundRadius * 0.30f * kotlin.math.sin(angleRad)
            drawCircle(Color.White, (3.2f * perspectiveScale).coerceIn(1.8f, 5.5f), Offset(nx, ny))
        }
    }

    // 3. Columna de Luz Vertical / Haz de Baliza de Rescate (Beacon Beam)
    if (isPinnedOrLocked) {
        val topSkyBeam = (80f * perspectiveScale).coerceIn(30f, 160f)
        val beamBrush = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                baseColor.copy(alpha = 0.50f),
                Color.White.copy(alpha = 0.90f),
                baseColor.copy(alpha = 0.80f),
                baseColor.copy(alpha = 0.20f),
                Color.Transparent
            ),
            startY = coreY - topSkyBeam,
            endY = groundY + 30f,
        )
        drawLine(
            brush = beamBrush,
            start = Offset(sx, coreY - topSkyBeam),
            end = Offset(sx, groundY + 30f),
            strokeWidth = (4.5f * perspectiveScale).coerceIn(2.5f, 8.5f),
            cap = StrokeCap.Round,
        )
    } else {
        drawLine(
            brush = Brush.verticalGradient(
                colors = listOf(baseColor.copy(alpha = 0.85f), Color.Transparent),
                startY = coreY,
                endY = groundY,
            ),
            start = Offset(sx, coreY),
            end = Offset(sx, groundY),
            strokeWidth = (2.2f * perspectiveScale).coerceIn(1.2f, 4.5f),
        )
    }

    // 4. Aura de energía radial y orbe central
    val orbRadius = (16f * perspectiveScale * (if (isPinnedOrLocked) pulse * 1.15f else pulse)).coerceIn(6f, 42f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                baseColor.copy(alpha = if (isPinnedOrLocked) 0.95f else 0.70f),
                baseColor.copy(alpha = if (isPinnedOrLocked) 0.40f else 0.15f),
                Color.Transparent
            ),
            center = Offset(sx, coreY),
            radius = orbRadius * (if (isPinnedOrLocked) 2.8f else 2.0f),
        ),
        radius = orbRadius * (if (isPinnedOrLocked) 2.8f else 2.0f),
        center = Offset(sx, coreY),
    )

    // 5. Silueta Holográfica Humana Estilo Pokémon GO (Avatar 3D)
    if (isPinnedOrLocked) {
        val headR = (6.5f * perspectiveScale).coerceIn(3.5f, 15f)
        val bodyW = (13f * perspectiveScale).coerceIn(6f, 28f)
        val bodyH = (16f * perspectiveScale).coerceIn(8f, 32f)

        // Cabeza del avatar
        drawCircle(
            color = Color.White,
            radius = headR,
            center = Offset(sx, coreY - bodyH * 0.65f),
        )
        drawCircle(
            color = baseColor,
            radius = headR * 1.35f,
            center = Offset(sx, coreY - bodyH * 0.65f),
            style = Stroke(width = 1.8f * perspectiveScale),
        )

        // Cuerpo / Torso holográfico
        drawRoundRect(
            color = baseColor.copy(alpha = 0.85f),
            topLeft = Offset(sx - bodyW / 2f, coreY - bodyH * 0.15f),
            size = Size(bodyW, bodyH * 0.85f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f * perspectiveScale, 4f * perspectiveScale),
        )

        // Corazón vital con pulso cardíaco en tiempo real
        val heartPulse = 0.85f + 0.35f * kotlin.math.sin(lockRotationDeg * 0.12f).coerceAtLeast(0f)
        drawCircle(
            color = Color(0xFFFF2255),
            radius = (3.8f * perspectiveScale * heartPulse).coerceIn(2f, 9f),
            center = Offset(sx, coreY + bodyH * 0.15f),
        )
    } else {
        // Orbe básico si no está fijada
        drawCircle(color = baseColor, radius = orbRadius, center = Offset(sx, coreY))
        drawCircle(color = Color.White, radius = (orbRadius * 0.50f).coerceAtLeast(2.5f), center = Offset(sx, coreY))
    }

    // 6. Anillo de Captura / Foco Dinámico estilo Pokémon GO (Catch Ring Animado que se contrae y expande)
    if (isPinnedOrLocked) {
        val outerRingRadius = orbRadius * 2.8f
        // Color del anillo de captura estilo Pokémon GO: Verde = Muy Cerca/Alineado, Amarillo/Ámbar = Distancia Media, Rojo = Lejos
        val catchRingColor = when {
            point.distanceM <= 1.8 || isAimAligned -> Color(0xFF00FF66)
            point.distanceM <= 4.0 -> Color(0xFFFFCC00)
            else -> Color(0xFFFF3344)
        }

        // Anillo exterior blanco translúcido de referencia
        drawCircle(
            color = Color.White.copy(alpha = 0.65f),
            radius = outerRingRadius,
            center = Offset(sx, coreY),
            style = Stroke(width = (2.0f * perspectiveScale).coerceIn(1.2f, 4f)),
        )

        // Anillo interior de captura que se CONTRAE hacia el centro estilo Pokémon GO
        val ringPhase = ((lockRotationDeg * 2.5f) % 360f) / 360f
        val contractingRadius = outerRingRadius * (1.0f - ringPhase * 0.75f)
        val contractingAlpha = (0.35f + 0.65f * (1.0f - ringPhase)).coerceIn(0.2f, 1f)

        drawCircle(
            color = catchRingColor.copy(alpha = contractingAlpha),
            radius = contractingRadius,
            center = Offset(sx, coreY),
            style = Stroke(width = (3.2f * perspectiveScale).coerceIn(2.0f, 6.5f)),
        )

        // Partículas de aura brillante que flotan hacia arriba estilo Pokémon GO spawn
        for (p in 0 until 5) {
            val pPhase = ((lockRotationDeg * 1.8f + p * 72f) % 360f) / 360f
            val pAngleRad = (p * 72f + lockRotationDeg * 0.8f) * (Math.PI.toFloat() / 180f)
            val pDist = groundRadius * 0.85f * (1.0f - pPhase * 0.3f)
            val px = sx + pDist * kotlin.math.cos(pAngleRad)
            val py = groundY - (pPhase * columnHeight * 1.2f)
            val pAlpha = (1.0f - pPhase).coerceIn(0f, 1f) * 0.85f

            drawCircle(
                color = catchRingColor.copy(alpha = pAlpha),
                radius = (3.0f * perspectiveScale * (1.0f - pPhase * 0.5f)).coerceIn(1.5f, 6f),
                center = Offset(px, py),
            )
        }

        // Corchetes tácticos giratorios
        val bracketRadius = orbRadius * 2.4f
        val numBrackets = 4
        val bracketSweep = 40f
        val bracketColor = if (point.isMoving) Color(0xFF00E5FF) else (if (point.isPinned) Color(0xFFFFCC00) else Color(0xFF00FFCC))
        for (i in 0 until numBrackets) {
            val startAngle = lockRotationDeg + i * 90f - (bracketSweep / 2f)
            drawArc(
                color = bracketColor,
                startAngle = startAngle,
                sweepAngle = bracketSweep,
                useCenter = false,
                topLeft = Offset(sx - bracketRadius, coreY - bracketRadius),
                size = Size(bracketRadius * 2f, bracketRadius * 2f),
                style = Stroke(width = (3.2f * perspectiveScale).coerceIn(2f, 6f), cap = StrokeCap.Round),
            )
        }
    }

    // 6. Gran PIN 3D Flotante Estilo Pokémon GO / Google Maps Live View AR (Clavado en el lugar exacto)
    val pinHeadR = (17f * perspectiveScale).coerceIn(9f, 40f)
    val pinHeight = pinHeadR * 2.6f
    // La punta del PIN toca EXACTAMENTE la posición física de la persona (coreY)
    val pinTipY = coreY
    val pinCenterY = pinTipY - pinHeight + pinHeadR

    // Forma geométrica 3D del PIN (Cabeza circular + Punta triangular inferior)
    val pinPath = Path().apply {
        moveTo(sx, pinTipY)
        // Lado izquierdo hacia la curvatura de la cabeza
        lineTo(sx - pinHeadR * 0.88f, pinCenterY + pinHeadR * 0.35f)
        // Arco superior
        arcTo(
            rect = androidx.compose.ui.geometry.Rect(
                left = sx - pinHeadR,
                top = pinCenterY - pinHeadR,
                right = sx + pinHeadR,
                bottom = pinCenterY + pinHeadR
            ),
            startAngleDegrees = 150f,
            sweepAngleDegrees = 240f,
            forceMoveTo = false,
        )
        // Lado derecho de regreso a la punta inferior
        lineTo(sx, pinTipY)
        close()
    }

    val pinColor = if (point.isPinned) Color(0xFFFFCC00) else if (isPinnedOrLocked) Color(0xFF00FFCC) else Color(0xFFFF3366)
    // Sombra y halo brillante del Pin
    drawPath(
        path = pinPath,
        color = pinColor.copy(alpha = 0.35f),
        style = Stroke(width = 6f * perspectiveScale),
    )
    // Relleno principal con degradado 3D del Pin
    drawPath(
        path = pinPath,
        brush = Brush.verticalGradient(
            colors = listOf(Color.White, pinColor, pinColor.copy(alpha = 0.85f)),
            startY = pinCenterY - pinHeadR,
            endY = pinTipY,
        ),
    )
    // Borde brillante exterior
    drawPath(
        path = pinPath,
        color = Color.White,
        style = Stroke(width = (2.2f * perspectiveScale).coerceIn(1.2f, 4f)),
    )

    // Núcleo central del PIN con icono de persona o respiración
    drawCircle(
        color = Color(0xFF06140E),
        radius = pinHeadR * 0.52f,
        center = Offset(sx, pinCenterY),
    )
    drawCircle(
        color = if (point.respRate > 0 || point.isVictim) Color(0xFF00FFCC) else Color.White,
        radius = pinHeadR * 0.30f,
        center = Offset(sx, pinCenterY),
    )

    // Medidor Flotante de Metros directamente sobre el Pin (Cuenta regresiva interactiva al caminar)
    val meterBadgeWidth = (92f * perspectiveScale).coerceIn(58f, 155f)
    val meterBadgeHeight = (25f * perspectiveScale).coerceIn(16f, 38f)
    val meterBadgeY = pinCenterY - pinHeadR - (6f * perspectiveScale)

    drawRoundRect(
        color = Color(0xEE001E14),
        topLeft = Offset(sx - meterBadgeWidth / 2f, meterBadgeY - meterBadgeHeight),
        size = Size(meterBadgeWidth, meterBadgeHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
    )
    drawRoundRect(
        color = pinColor,
        topLeft = Offset(sx - meterBadgeWidth / 2f, meterBadgeY - meterBadgeHeight),
        size = Size(meterBadgeWidth, meterBadgeHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
        style = Stroke(width = 1.5f),
    )

    val paintMeter = android.graphics.Paint().apply {
        this.color = android.graphics.Color.WHITE
        this.textSize = (13f * perspectiveScale).coerceIn(9.5f, 19f)
        this.typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        this.textAlign = android.graphics.Paint.Align.CENTER
        this.isAntiAlias = true
    }
    val meterText = if (point.distanceM < 0.8) "🛑 ¡0.0m!" else "📍 ${"%.1f".format(point.distanceM)}m"
    drawContext.canvas.nativeCanvas.drawText(
        meterText,
        sx,
        meterBadgeY - meterBadgeHeight * 0.28f,
        paintMeter
    )

    // 7. Tarjeta Táctica Flotante (Encounter HUD Card estilo Pokémon GO sobre el PIN)
    val hasAiTag = point.aiConfidenceTag.isNotBlank()
    val hasGps = point.gpsGms.isNotBlank() || (point.gpsLat != null && point.gpsLon != null)
    val extraLines = (if (hasAiTag) 1 else 0) + (if (hasGps) 1 else 0)
    val badgeHeight = (when (extraLines) {
        2 -> (92f * perspectiveScale)
        1 -> (74f * perspectiveScale)
        else -> if (isPinnedOrLocked) (62f * perspectiveScale) else (50f * perspectiveScale)
    }).coerceIn(38f, 128f)
    val labelY = meterBadgeY - meterBadgeHeight - (10f * perspectiveScale)
    val badgeWidth = (if (hasGps) 275f * perspectiveScale else if (hasAiTag || isPinnedOrLocked) 250f * perspectiveScale else 215f * perspectiveScale).coerceIn(170f, 395f)

    val paintBg = android.graphics.Paint().apply {
        this.color = if (point.isPinned) {
            android.graphics.Color.argb(245, 0, 42, 32)
        } else if (isPinnedOrLocked) {
            android.graphics.Color.argb(240, 0, 48, 34)
        } else if (isAimAligned) {
            android.graphics.Color.argb(220, 0, 42, 28)
        } else {
            android.graphics.Color.argb(205, 6, 18, 14)
        }
        this.style = android.graphics.Paint.Style.FILL
        this.isAntiAlias = true
    }
    val paintBorder = android.graphics.Paint().apply {
        this.color = if (point.isPinned) {
            android.graphics.Color.parseColor("#FFCC00")
        } else if (point.isMoving) {
            android.graphics.Color.parseColor("#00E5FF")
        } else if (isPinnedOrLocked) {
            android.graphics.Color.parseColor("#00FFCC")
        } else {
            android.graphics.Color.parseColor("#225533")
        }
        this.style = android.graphics.Paint.Style.STROKE
        this.strokeWidth = (2.6f * perspectiveScale).coerceIn(1.5f, 4.5f)
        this.isAntiAlias = true
    }
    val paintText = android.graphics.Paint().apply {
        this.color = if (point.isRescued) {
            android.graphics.Color.parseColor("#FFD700")
        } else if (point.isPinned) {
            android.graphics.Color.parseColor("#00FFCC")
        } else if (isPinnedOrLocked) {
            if (point.isMoving) android.graphics.Color.parseColor("#00E5FF") else android.graphics.Color.parseColor("#00FFFF")
        } else if (isAimAligned) {
            android.graphics.Color.parseColor("#00FFCC")
        } else {
            android.graphics.Color.WHITE
        }
        this.textSize = (20f * perspectiveScale).coerceIn(12f, 28f)
        this.typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        this.textAlign = android.graphics.Paint.Align.CENTER
        this.isAntiAlias = true
    }
    val paintSub = android.graphics.Paint().apply {
        this.color = if (point.isPinned) {
            android.graphics.Color.WHITE
        } else if (isPinnedOrLocked) {
            android.graphics.Color.parseColor("#00FFCC")
        } else {
            android.graphics.Color.parseColor("#FFCC00")
        }
        this.textSize = (15f * perspectiveScale).coerceIn(10f, 21f)
        this.typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.NORMAL)
        this.textAlign = android.graphics.Paint.Align.CENTER
        this.isAntiAlias = true
    }

    drawContext.canvas.nativeCanvas.drawRoundRect(
        sx - badgeWidth / 2f,
        labelY - badgeHeight,
        sx + badgeWidth / 2f,
        labelY,
        12f, 12f, paintBg
    )
    drawContext.canvas.nativeCanvas.drawRoundRect(
        sx - badgeWidth / 2f,
        labelY - badgeHeight,
        sx + badgeWidth / 2f,
        labelY,
        12f, 12f, paintBorder
    )

    val pinPrefix = if (point.isPinned) {
        if (isSpanish) "📌 [ZONA #${point.victimNumericId} FIJADA] " else "📌 [ZONE #${point.victimNumericId} LOCKED] "
    } else if (isExplicitlyLocked) {
        if (isSpanish) "🎯 [ZONA #${point.victimNumericId}] " else "🎯 [ZONE #${point.victimNumericId}] "
    } else {
        ""
    }
    val displayLabel = "$pinPrefix${point.label}"
    val line1Y = when (extraLines) {
        2 -> labelY - badgeHeight * 0.76f
        1 -> labelY - badgeHeight * 0.68f
        else -> labelY - badgeHeight * 0.54f
    }
    val line2Y = when (extraLines) {
        2 -> labelY - badgeHeight * 0.52f
        1 -> labelY - badgeHeight * 0.38f
        else -> labelY - badgeHeight * 0.16f
    }

    drawContext.canvas.nativeCanvas.drawText(
        displayLabel,
        sx,
        line1Y,
        paintText
    )
    drawContext.canvas.nativeCanvas.drawText(
        "🎯 ${"%.2f".format(point.distanceM)}m • ⬇ ${"%.2f".format(kotlin.math.abs(point.depthM))}m • 🧭 ${point.bearingDeg.toInt()}°",
        sx,
        line2Y,
        paintSub
    )

    if (hasGps) {
        val paintGps = android.graphics.Paint().apply {
            this.color = android.graphics.Color.parseColor("#FFCC00")
            this.textSize = (12f * perspectiveScale).coerceIn(8.5f, 16f)
            this.typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
            this.textAlign = android.graphics.Paint.Align.CENTER
            this.isAntiAlias = true
        }
        val gpsLineY = if (hasAiTag) labelY - badgeHeight * 0.28f else labelY - badgeHeight * 0.10f
        val gpsText = if (point.gpsGms.isNotBlank()) "📍 ${point.gpsGms}" else "📍 ${"%.5f".format(point.gpsLat ?: 0.0)}°, ${"%.5f".format(point.gpsLon ?: 0.0)}°"
        drawContext.canvas.nativeCanvas.drawText(
            gpsText,
            sx,
            gpsLineY,
            paintGps
        )
    }

    if (hasAiTag) {
        val paintAi = android.graphics.Paint().apply {
            this.color = if (point.isMoving) android.graphics.Color.parseColor("#00E5FF") else android.graphics.Color.parseColor("#00FFCC")
            this.textSize = (12f * perspectiveScale).coerceIn(8.5f, 15f)
            this.typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
            this.textAlign = android.graphics.Paint.Align.CENTER
            this.isAntiAlias = true
        }
        drawContext.canvas.nativeCanvas.drawText(
            point.aiConfidenceTag,
            sx,
            labelY - badgeHeight * 0.08f,
            paintAi
        )
    }
}
