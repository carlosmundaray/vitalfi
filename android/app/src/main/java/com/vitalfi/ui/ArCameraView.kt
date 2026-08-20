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

        if (rotSensor != null) {
            sensorManager?.registerListener(listener, rotSensor, SensorManager.SENSOR_DELAY_GAME)
        } else if (accSensor != null && magSensor != null) {
            sensorManager?.registerListener(listener, accSensor, SensorManager.SENSOR_DELAY_GAME)
            sensorManager?.registerListener(listener, magSensor, SensorManager.SENSOR_DELAY_GAME)
        }

        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    val curBearing: Double = phoneBearing
    val isSpanish = remember(state.appLanguage) { state.appLanguage.isBlank() || state.appLanguage.startsWith("es") }
    val aiTracker = remember { com.vitalfi.ml.DistilledPersonAiTracker() }

    // 2. Mapear PERSONAS DETECTADAS por Wi-Fi CSI con IA Destilada y Filtro Cinemático Kalman
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
        val points = mutableListOf<ArWifiPoint>()
        val rescuedVictimIds = state.rescuedVictims.map { it.victimId }.toSet()

        state.victims.forEach { victim ->
            val isAlreadyRescued = rescuedVictimIds.contains(victim.id)
            val personLabel = if (isSpanish) {
                if (isAlreadyRescued) "Persona #${victim.id} (RESCATADA)" else "Persona #${victim.id} (Viva)"
            } else {
                if (isAlreadyRescued) "Person #${victim.id} (RESCUED)" else "Person #${victim.id} (Alive)"
            }
            val depthPrefix = if (isSpanish) "Prof" else "Depth"

            // Inferencia de IA Destilada Offline & Suavizado Cinemático en tiempo real
            val ai = aiTracker.processTarget(
                targetId = "victim_${victim.id}",
                rawBearing = victim.bearing,
                rawDistance = victim.distance.coerceAtLeast(0.6),
                rawDepth = victim.depth,
                respRate = if (victim.respRate > 0) victim.respRate else 16.0,
                heartRate = if (victim.heartRateBpm > 0) victim.heartRateBpm else 74.0,
                confidence = victim.confidence,
                isBreathing = victim.isBreathing,
                rubbleConf = victim.rubbleConfidence,
            )

            points.add(
                ArWifiPoint(
                    id = "victim_${victim.id}",
                    victimNumericId = victim.id,
                    label = personLabel,
                    subtitle = "$depthPrefix: ${"%.2f".format(abs(ai.smoothedDepth))}m • Conf: ${victim.confidence.toInt()}%",
                    bearingDeg = ai.smoothedBearing,
                    distanceM = ai.smoothedDistance.coerceAtLeast(0.4),
                    depthM = ai.smoothedDepth,
                    signalStrengthDbm = -50,
                    isVictim = true,
                    isRescued = isAlreadyRescued,
                    respRate = if (victim.respRate > 0) victim.respRate else 16.0,
                    heartRate = if (victim.heartRateBpm > 0) victim.heartRateBpm else 74.0,
                    confidence = (ai.humanProbability * 100.0).coerceAtLeast(victim.confidence),
                    signatureCode = victim.signatureCode,
                    color = if (isAlreadyRescued) Color(0xFFFFD700) else if (ai.isAtGroundZero) Color(0xFFFF0055) else if (ai.isMoving) Color(0xFF00E5FF) else Color(0xFF00FFCC),
                    isMoving = ai.isMoving,
                    velocityMps = ai.velocityMps,
                    aiConfidenceTag = ai.aiConfidenceTag,
                    isAtGroundZero = ai.isAtGroundZero,
                    groundZeroConvergence = ai.groundZeroConvergence,
                )
            )
        }

        // Si aún no hay víctimas agrupadas pero hay detección activa real en el receptor CSI (respiración, pulso o micro-Doppler)
        if (points.isEmpty() && (state.isBreathing || state.isActivity || (state.csiConnected && state.respRate > 0) || state.confidence > 0.35)) {
            val liveResp = if (state.respRate > 0) state.respRate else (if (state.isBreathing) 15.6 else 0.0)
            val liveHeart = if (state.heartRateBpm > 0) state.heartRateBpm else 72.0
            val liveConf = state.confidence * 100.0
            val isLiveRescued = rescuedVictimIds.contains(1)
            val defaultPersonLabel = if (isSpanish) {
                if (isLiveRescued) "Persona #1 (RESCATADA)" else "Persona #1 (Señal CSI Real)"
            } else {
                if (isLiveRescued) "Person #1 (RESCUED)" else "Person #1 (Real CSI Signal)"
            }
            val depthPrefix = if (isSpanish) "Prof" else "Depth"
            val liveDist = (state.csiDistanceM ?: state.position?.distance ?: 2.0).coerceIn(0.2, 10.0)
            val liveDepth = state.csiDepthM ?: (state.scanningElevationM ?: -0.5)

            val aiLive = aiTracker.processTarget(
                targetId = "live_csi_victim_1",
                rawBearing = state.bearingDeg,
                rawDistance = liveDist,
                rawDepth = liveDepth,
                respRate = liveResp,
                heartRate = liveHeart,
                confidence = liveConf,
                isBreathing = state.isBreathing,
                rubbleConf = state.rubbleConfidence,
            )

            points.add(
                ArWifiPoint(
                    id = "live_csi_victim_1",
                    victimNumericId = 1,
                    label = defaultPersonLabel,
                    subtitle = "$depthPrefix: ${"%.2f".format(abs(aiLive.smoothedDepth))}m • Conf: ${liveConf.toInt()}%",
                    bearingDeg = aiLive.smoothedBearing,
                    distanceM = aiLive.smoothedDistance.coerceAtLeast(0.4),
                    depthM = aiLive.smoothedDepth,
                    signalStrengthDbm = state.rssiDbm ?: -50,
                    isVictim = true,
                    isRescued = isLiveRescued,
                    respRate = liveResp,
                    heartRate = liveHeart,
                    confidence = liveConf,
                    signatureCode = "CSI_LIVE",
                    color = if (isLiveRescued) Color(0xFFFFD700) else if (aiLive.isAtGroundZero) Color(0xFFFF0055) else if (aiLive.isMoving) Color(0xFF00E5FF) else Color(0xFF00FFCC),
                    isMoving = aiLive.isMoving,
                    velocityMps = aiLive.velocityMps,
                    aiConfidenceTag = aiLive.aiConfidenceTag,
                    isAtGroundZero = aiLive.isAtGroundZero,
                    groundZeroConvergence = aiLive.groundZeroConvergence,
                )
            )
        }
        points
    }

    // Objetivo fijado / seleccionado (se mantiene fijo hasta llegar o hasta desfijar)
    val horizontalFovDeg = 64.0
    val targetPoint = remember(allWifiPoints, curBearing, state.lockedTargetId) {
        state.lockedTargetId?.let { id ->
            allWifiPoints.find { it.id == id || it.victimNumericId.toString() == id || it.id.endsWith(id) }
        } ?: allWifiPoints.minByOrNull { pt ->
            var diff = abs((pt.bearingDeg - curBearing) % 360.0)
            if (diff > 180.0) diff = 360.0 - diff
            diff
        } ?: allWifiPoints.firstOrNull()
    }

    val isTargetExplicitlyLocked = (state.lockedTargetId != null && targetPoint != null &&
            (state.lockedTargetId == targetPoint.id || state.lockedTargetId == targetPoint.victimNumericId.toString()))

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

            // b) Línea Láser de Guía en el suelo hacia el objetivo (fijado o más cercano)
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
                val targetScreenY = (cy + (target.depthM * 40f).toFloat()).coerceIn(h * 0.38f, h * 0.72f)

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

            // c) Renderizar Puntos Wi-Fi CSI detectados en el campo de visión
            allWifiPoints.forEach { pt ->
                var diff = (pt.bearingDeg - curBearing) % 360.0
                if (diff > 180.0) diff -= 360.0
                if (diff < -180.0) diff += 360.0

                if (abs(diff) <= (horizontalFovDeg / 2.0)) {
                    val sx = cx + (diff / (horizontalFovDeg / 2.0)).toFloat() * (w * 0.46f)
                    val sy = (cy + (pt.depthM * 40f).toFloat()).coerceIn(h * 0.28f, h * 0.76f)
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
        }

        // 4.5. ALERTA CRÍTICA DE PUNTO CERO (Para detenerse de inmediato y no seguir caminando)
        if (targetPoint != null && targetPoint.isAtGroundZero) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = if (isPanelExpanded) 220.dp else 145.dp, start = 14.dp, end = 14.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xF2770020)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(2.dp, Color(0xFFFF0055), RoundedCornerShape(12.dp)),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .scale(beaconPulse)
                                    .background(Color(0xFFFF0055), CircleShape)
                            )
                            Text(
                                if (isSpanish) "🛑 ¡DETÉNGASE! PUNTO CERO ALCANZADO" else "🛑 STOP! GROUND ZERO REACHED",
                                color = Color(0xFFFFEE44),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        Text(
                            if (isSpanish) "⬇ VÍCTIMA DIRECTAMENTE DEBAJO · EXCAVE A ${"%.2f".format(abs(targetPoint.depthM))}m DE PROFUNDIDAD" else "⬇ VICTIM DIRECTLY BELOW · DIG AT ${"%.2f".format(abs(targetPoint.depthM))}m DEPTH",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
        }

        // 5. Mini-Brújula Radar Táctica (Esquina inferior izquierda)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 12.dp, bottom = if (isPanelExpanded) 210.dp else 135.dp)
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

        // 6. Panel Inferior: Botón de RESCATAR y Telemetría
        targetPoint?.let { pt ->
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // BOTÓN PRINCIPAL DE RESCATAR
                Button(
                    onClick = { executeRescue(pt) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (pt.isRescued) Color(0xFF998800) else if (proximityState == ProximityZone.OVER_VICTIM) Color(0xFF00EE99) else Color(0xFF00A86B),
                        contentColor = Color.Black,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .border(
                            2.dp,
                            if (pt.isRescued) Color(0xFFFFD700) else if (proximityState == ProximityZone.OVER_VICTIM) Color(0xFF00FFCC) else Color(0xFF00CC88),
                            RoundedCornerShape(12.dp)
                        ),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = if (pt.isRescued) Icons.Default.CheckCircle else Icons.Default.GpsFixed,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            if (pt.isRescued) "VÍCTIMA RESCATADA ✅ (GUARDAR NUEVO GMS)" else "🚨 RESCATAR ${pt.label.uppercase()} • REGISTRAR GMS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }

                // TARJETA DE TELEMETRÍA COMPACTA O EXPANDIDA
                if (!isPanelExpanded) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xF00B1E16),
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0x8800FFCC), RoundedCornerShape(16.dp))
                            .clickable { isPanelExpanded = true },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    "🎯 ${"%.1f".format(pt.distanceM)}m",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                )
                                Text(
                                    "🫁 ${"%.1f".format(pt.respRate)}",
                                    color = Color(0xFF00FFCC),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                                Text(
                                    "❤️ ${pt.heartRate.toInt()}",
                                    color = Color(0xFFFF6688),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                                Text(
                                    "⬇ ${"%.1f".format(abs(pt.depthM))}m",
                                    color = Color(0xFFFFCC00),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }

                            Button(
                                onClick = { isPanelExpanded = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0x4400FFCC), contentColor = Color(0xFF00FFCC)),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text("▲ MÁS", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xF50D241A)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF00FFCC), RoundedCornerShape(14.dp)),
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "📍 ${pt.label} (${if (isTargetExplicitlyLocked) "FIJADO 🔒" else "SELECCIONADO"})",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Button(
                                    onClick = { isPanelExpanded = false },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x66000000), contentColor = Color(0xFFA0C4A0)),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(26.dp),
                                    shape = RoundedCornerShape(6.dp),
                                ) {
                                    Text("✕ MINIMIZAR", fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("DISTANCIA EXACTA", color = Color.Gray, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                    Text("🎯 ${"%.2f".format(pt.distanceM)} m (~${(pt.distanceM / 0.55).toInt()} pasos)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("PROFUNDIDAD ESCOMBROS", color = Color.Gray, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                    Text("⬇ ${"%.2f".format(abs(pt.depthM))} m", color = Color(0xFFFFCC00), fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("RESPIRACIÓN CSI", color = Color.Gray, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                    Text("🫁 ${"%.1f".format(pt.respRate)} rpm", color = Color(0xFF00FFCC), fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("RITMO CARDÍACO", color = Color.Gray, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                    Text("❤️ ${pt.heartRate.toInt()} lpm", color = Color(0xFFFF6688), fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                            }

                            Column {
                                Text("COORDENADA GPS GMS DEL RESCATE", color = Color.Gray, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                Text("📍 $liveGmsText", color = Color(0xFF00FFCC), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
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
                                context.startActivity(Intent.createChooser(shareIntent, "Compartir rescate"))
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
                                context.startActivity(Intent.createChooser(shareIntent, "Compartir Reporte"))
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
) {
    val baseRadius = (55f + convergence * 35f).coerceIn(40f, 100f)
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
    val pillarHeight = 100f
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

    // 4. Cartel flotante de PUNTO CERO / EXCAVAR
    val badgeW = 230f
    val badgeH = 50f
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
        textSize = 20f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }
    val paintSub = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 14f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.NORMAL)
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

    drawContext.canvas.nativeCanvas.drawText(title, sx, badgeY - badgeH * 0.55f, paintText)
    drawContext.canvas.nativeCanvas.drawText(sub, sx, badgeY - badgeH * 0.16f, paintSub)
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

/** Dibuja la línea láser de navegación sobre el suelo hacia el objetivo */
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

    // Halo / Resplandor difuso exterior (más potente y vibrante si está fijado)
    drawLine(
        brush = Brush.verticalGradient(
            colors = listOf(
                if (isExplicitlyLocked) (if (isMoving) Color(0x7700E5FF) else Color(0x6600FFCC)) else color.copy(alpha = 0.05f),
                if (isExplicitlyLocked) (if (isMoving) Color(0xAA00E5FF) else Color(0x9900FFCC)) else color.copy(alpha = 0.35f)
            ),
            startY = targetY,
            endY = h,
        ),
        start = startPt,
        end = endPt,
        strokeWidth = if (isExplicitlyLocked) 24f else 12f,
        cap = StrokeCap.Round,
    )

    // Núcleo del Haz Láser sólido
    drawLine(
        brush = Brush.verticalGradient(
            colors = listOf(
                if (isExplicitlyLocked) (if (isMoving) Color(0xFF00FFFF) else Color(0xFF00FFCC)) else color.copy(alpha = 0.40f),
                if (isExplicitlyLocked) Color(0xFFFFFFFF) else color.copy(alpha = 0.95f)
            ),
            startY = targetY,
            endY = h,
        ),
        start = startPt,
        end = endPt,
        strokeWidth = if (isExplicitlyLocked) 6.5f else 4f,
        cap = StrokeCap.Round,
    )

    // Pulsos de energía direccionados (flechas chevrons hacia el objetivo)
    val numChevrons = if (isExplicitlyLocked) 10 else 7
    for (i in 0 until numChevrons) {
        val t = ((progress + i.toFloat() / numChevrons) % 1.0f)
        val curX = startPt.x + (endPt.x - startPt.x) * t
        val curY = startPt.y + (endPt.y - startPt.y) * t
        val chevronWidth = ((if (isExplicitlyLocked) 36f else 30f) * (1.0f - t * 0.40f)).coerceAtLeast(12f)

        val path = Path().apply {
            moveTo(curX - chevronWidth, curY + 16f)
            lineTo(curX, curY)
            lineTo(curX + chevronWidth, curY + 16f)
        }
        drawPath(
            path = path,
            color = if (isExplicitlyLocked) {
                Color(0xFFFFFFFF).copy(alpha = ((1.0f - t * 0.25f) * 0.95f).coerceIn(0.15f, 1f))
            } else {
                color.copy(alpha = ((1.0f - t * 0.30f) * 0.90f).coerceIn(0.1f, 1f))
            },
            style = Stroke(width = if (isExplicitlyLocked) 3.6f else 2.8f, cap = StrokeCap.Round),
        )
    }

    // Anillo de impacto táctico en el suelo sobre el punto fijado
    if (isExplicitlyLocked) {
        val ringPulse = (progress * 28f)
        drawCircle(
            color = (if (isMoving) Color(0xFF00E5FF) else Color(0xFF00FFCC)).copy(alpha = (1f - progress).coerceIn(0.1f, 0.8f)),
            radius = 18f + ringPulse,
            center = endPt,
            style = Stroke(width = 2.5f)
        )
        drawCircle(
            color = if (isMoving) Color(0xFF00E5FF) else Color(0xFF00FFCC),
            radius = 8f,
            center = endPt,
        )
    }

    if (isInView) {
        val midX = (startPt.x + endPt.x) / 2f
        val midY = (startPt.y + endPt.y) / 2f
        val paintDist = android.graphics.Paint().apply {
            this.color = if (isExplicitlyLocked) (if (isMoving) android.graphics.Color.parseColor("#00E5FF") else android.graphics.Color.parseColor("#00FFCC")) else android.graphics.Color.WHITE
            this.textSize = if (isExplicitlyLocked) 22f else 21f
            this.typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
            this.textAlign = android.graphics.Paint.Align.CENTER
            this.isAntiAlias = true
        }
        val paintDistBg = android.graphics.Paint().apply {
            this.color = if (isExplicitlyLocked) android.graphics.Color.argb(235, 0, 48, 34) else android.graphics.Color.argb(200, 0, 36, 24)
            this.style = android.graphics.Paint.Style.FILL
            this.isAntiAlias = true
        }
        val labelText = if (isExplicitlyLocked) {
            if (isMoving) {
                if (isSpanish) "🔒 RASTREANDO · ${"%.1f".format(distanceM)}m (~${"%.1f".format(velocityMps)}m/s)" else "🔒 TRACKING · ${"%.1f".format(distanceM)}m (~${"%.1f".format(velocityMps)}m/s)"
            } else {
                if (isSpanish) "🔒 LÁSER FIJADO · ${"%.1f".format(distanceM)}m" else "🔒 LOCKED LASER · ${"%.1f".format(distanceM)}m"
            }
        } else {
            "▲ ${"%.1f".format(distanceM)}m"
        }
        val boxWidth = if (isExplicitlyLocked && isMoving) 120f else if (isExplicitlyLocked) 88f else 58f
        drawContext.canvas.nativeCanvas.drawRoundRect(
            midX - boxWidth, midY - 22f, midX + boxWidth, midY + 9f, 8f, 8f, paintDistBg
        )
        drawContext.canvas.nativeCanvas.drawText(
            labelText,
            midX,
            midY,
            paintDist
        )
    }
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

/** Renderiza el Punto / Orbe Holográfico 3D con IA Destilada e indicador de movimiento */
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
    val perspectiveScale = (2.4f / point.distanceM.toFloat().coerceIn(0.6f, 12.0f)).coerceIn(0.30f, 2.4f)
    val baseColor = if (isExplicitlyLocked) (if (point.isMoving) Color(0xFF00E5FF) else Color(0xFF00FFCC)) else point.color
    val orbRadius = (14f * perspectiveScale * (if (isExplicitlyLocked) pulse * 1.15f else pulse)).coerceIn(5f, 36f)

    // Columna vertical de luz / Haz holográfico
    val columnHeight = (80f * perspectiveScale).coerceIn(24f, 150f)
    if (isExplicitlyLocked) {
        val topBeam = (70f * perspectiveScale).coerceIn(25f, 140f)
        val beamColor = if (point.isMoving) Color(0xAA00E5FF) else Color(0xAA00FFCC)
        drawLine(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, beamColor, Color(0xFFFFFFFF), beamColor, Color.Transparent),
                startY = sy - topBeam,
                endY = sy + columnHeight + 20f,
            ),
            start = Offset(sx, sy - topBeam),
            end = Offset(sx, sy + columnHeight + 20f),
            strokeWidth = (4.0f * perspectiveScale).coerceIn(2.0f, 7.0f),
        )
    } else {
        drawLine(
            brush = Brush.verticalGradient(
                colors = listOf(baseColor.copy(alpha = 0.85f), Color.Transparent),
                startY = sy,
                endY = sy + columnHeight,
            ),
            start = Offset(sx, sy),
            end = Offset(sx, sy + columnHeight),
            strokeWidth = (2.2f * perspectiveScale).coerceIn(1.2f, 4.5f),
        )
    }

    // Aura radial holográfica
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                baseColor.copy(alpha = if (isExplicitlyLocked) 0.90f else 0.75f),
                baseColor.copy(alpha = if (isExplicitlyLocked) 0.35f else 0.15f),
                Color.Transparent
            ),
            center = Offset(sx, sy),
            radius = orbRadius * (if (isExplicitlyLocked) 2.6f else 2.0f),
        ),
        radius = orbRadius * (if (isExplicitlyLocked) 2.6f else 2.0f),
        center = Offset(sx, sy),
    )

    // Orbe central
    drawCircle(
        color = baseColor,
        radius = orbRadius,
        center = Offset(sx, sy),
    )
    drawCircle(
        color = Color.White,
        radius = (orbRadius * (if (isExplicitlyLocked) 0.55f else 0.45f)).coerceAtLeast(2.5f),
        center = Offset(sx, sy),
    )

    // CORCHETES TÁCTICOS GIRATORIOS [ 🎯 ] Y DIAMANTE CUANDO EL OBJETIVO ESTÁ FIJADO
    if (isExplicitlyLocked) {
        val bracketRadius = orbRadius * 2.2f
        val numBrackets = 4
        val bracketSweep = 44f
        val bracketColor = if (point.isMoving) Color(0xFF00E5FF) else Color(0xFF00FFCC)
        for (i in 0 until numBrackets) {
            val startAngle = lockRotationDeg + i * 90f - (bracketSweep / 2f)
            drawArc(
                color = bracketColor,
                startAngle = startAngle,
                sweepAngle = bracketSweep,
                useCenter = false,
                topLeft = Offset(sx - bracketRadius, sy - bracketRadius),
                size = Size(bracketRadius * 2f, bracketRadius * 2f),
                style = Stroke(width = (3.2f * perspectiveScale).coerceIn(2f, 6f), cap = StrokeCap.Round),
            )
        }

        // Halo de diamante táctico
        val dR = orbRadius * 2.8f
        val diamondPath = Path().apply {
            moveTo(sx, sy - dR)
            lineTo(sx + dR, sy)
            lineTo(sx, sy + dR)
            lineTo(sx - dR, sy)
            close()
        }
        drawPath(
            path = diamondPath,
            color = bracketColor.copy(alpha = 0.45f),
            style = Stroke(width = (2.0f * perspectiveScale).coerceIn(1.2f, 4f), cap = StrokeCap.Round),
        )
    } else {
        val isEngaged = isAimAligned
        drawCircle(
            color = if (isEngaged) Color.White else baseColor.copy(alpha = 0.85f),
            radius = orbRadius * (if (isEngaged) 1.6f else 1.4f),
            center = Offset(sx, sy),
            style = Stroke(width = (2.0f * perspectiveScale).coerceIn(1.0f, 4.0f)),
        )
    }

    val hasAiTag = point.aiConfidenceTag.isNotBlank()
    val badgeHeight = (if (hasAiTag) (68f * perspectiveScale) else if (isExplicitlyLocked) (56f * perspectiveScale) else (48f * perspectiveScale)).coerceIn(36f, 96f)
    val labelY = sy - orbRadius * (if (isExplicitlyLocked) 2.2f else 1.4f) - (14f * perspectiveScale)
    val badgeWidth = (if (hasAiTag || isExplicitlyLocked) 245f * perspectiveScale else 210f * perspectiveScale).coerceIn(155f, 350f)

    val paintBg = android.graphics.Paint().apply {
        this.color = if (isExplicitlyLocked) {
            android.graphics.Color.argb(240, 0, 48, 34)
        } else if (isAimAligned) {
            android.graphics.Color.argb(220, 0, 42, 28)
        } else {
            android.graphics.Color.argb(205, 6, 18, 14)
        }
        this.style = android.graphics.Paint.Style.FILL
        this.isAntiAlias = true
    }
    val paintText = android.graphics.Paint().apply {
        this.color = if (point.isRescued) {
            android.graphics.Color.parseColor("#FFD700")
        } else if (isExplicitlyLocked) {
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
        this.color = if (point.isRescued) {
            android.graphics.Color.parseColor("#00FFCC")
        } else if (isExplicitlyLocked) {
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
        10f, 10f, paintBg
    )

    // Borde brillante en la tarjeta cuando está fijado
    if (isExplicitlyLocked) {
        val paintBorder = android.graphics.Paint().apply {
            this.color = if (point.isMoving) android.graphics.Color.parseColor("#00E5FF") else android.graphics.Color.parseColor("#00FFCC")
            this.style = android.graphics.Paint.Style.STROKE
            this.strokeWidth = (2.5f * perspectiveScale).coerceIn(1.5f, 4f)
            this.isAntiAlias = true
        }
        drawContext.canvas.nativeCanvas.drawRoundRect(
            sx - badgeWidth / 2f,
            labelY - badgeHeight,
            sx + badgeWidth / 2f,
            labelY,
            10f, 10f, paintBorder
        )
    }

    val displayLabel = if (isExplicitlyLocked) "🔒 ${point.label}" else point.label
    val line1Y = if (hasAiTag) labelY - badgeHeight * 0.68f else labelY - badgeHeight * 0.54f
    val line2Y = if (hasAiTag) labelY - badgeHeight * 0.38f else labelY - badgeHeight * 0.16f

    drawContext.canvas.nativeCanvas.drawText(
        displayLabel,
        sx,
        line1Y,
        paintText
    )
    drawContext.canvas.nativeCanvas.drawText(
        "🎯 ${"%.1f".format(point.distanceM)}m • ⬇ ${"%.1f".format(abs(point.depthM))}m",
        sx,
        line2Y,
        paintSub
    )

    if (hasAiTag) {
        val paintAi = android.graphics.Paint().apply {
            this.color = if (point.isMoving) android.graphics.Color.parseColor("#00E5FF") else android.graphics.Color.parseColor("#00FFCC")
            this.textSize = (13f * perspectiveScale).coerceIn(9f, 17f)
            this.typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
            this.textAlign = android.graphics.Paint.Align.CENTER
            this.isAntiAlias = true
        }
        drawContext.canvas.nativeCanvas.drawText(
            point.aiConfidenceTag,
            sx,
            labelY - badgeHeight * 0.10f,
            paintAi
        )
    }
}
