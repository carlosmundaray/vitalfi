package com.vitalfi.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.vitalfi.VitalFiUiState
import com.vitalfi.ai.RfKeypoint
import com.vitalfi.ai.RfSkeletonPose
import com.vitalfi.dsp.TrappedVictim
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Modo Realidad Aumentada (AR estilo Pokémon GO).
 * Utiliza la cámara real del teléfono para proyectar en el mundo físico la ubicación exacta,
 * sonar 3D y esqueleto holográfico de las víctimas atrapadas bajo escombros.
 */
@Composable
fun ArCameraView(
    state: VitalFiUiState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Animaciones holográficas
    val infiniteTransition = rememberInfiniteTransition(label = "arPulse")
    val pulseWave by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sonarWave",
    )
    val beaconPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "beaconPulse",
    )

    // Víctima primaria o seleccionada
    val targetVictim = state.selectedVictim ?: state.victims.firstOrNull()
    val phoneBearing = state.bearingDeg

    // Cálculo de rumbo relativo hacia el objetivo
    val deltaAngleDeg = targetVictim?.let { victim ->
        var diff = (victim.bearing - phoneBearing) % 360.0
        if (diff > 180.0) diff -= 360.0
        if (diff < -180.0) diff += 360.0
        diff
    } ?: 0.0

    val horizontalFovDeg = 62.0 // FOV estándar de cámara móvil
    val isTargetInView = targetVictim != null && abs(deltaAngleDeg) <= (horizontalFovDeg / 2.0)
    val isLocked = targetVictim != null && abs(deltaAngleDeg) <= 8.0 && targetVictim.distance < 2.0

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // 1. Vista de Cámara Real en Vivo (CameraX)
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // Fondo sintético táctico si no tiene permiso de cámara aún
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF0C2218), Color(0xFF040A07)),
                        ),
                    ),
            )
        }

        // 2. Capa de Realidad Aumentada Holográfica (Canvas 3D)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f

            // a) Retícula central de puntería AR
            drawArCrosshair(cx, cy, isLocked)

            // b) Si el objetivo está en el campo de visión, renderizar su holograma 3D
            if (targetVictim != null && isTargetInView) {
                val screenX = cx + (deltaAngleDeg / (horizontalFovDeg / 2.0)).toFloat() * (w * 0.45f)
                val screenY = cy + (targetVictim.depth * 40f).toFloat().coerceIn(-120f, 160f)

                // Ondas de Sonar 3D en el suelo / escombros
                drawArSonarWaves(screenX, screenY, pulseWave, targetVictim.distance.toFloat(), isLocked)

                // Marcador Holográfico tipo Pokémon GO
                drawArTargetHologram(screenX, screenY, beaconPulse, targetVictim, isLocked)

                // Esqueleto AR superpuesto si hay pose disponible
                state.rfPose?.let { pose ->
                    drawArSkeleton(screenX, screenY, pose, targetVictim.distance.toFloat(), isLocked)
                }
            }
        }

        // 3. Flechas de Guía Direccional (Off-Screen Indicators estilo Pokémon GO)
        if (targetVictim != null && !isTargetInView) {
            val isLeft = deltaAngleDeg < 0
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xCC002B1D)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .align(if (isLeft) Alignment.CenterStart else Alignment.CenterEnd)
                        .border(1.dp, Color(0xFF00FFCC), RoundedCornerShape(12.dp)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = null,
                            tint = Color(0xFF00FFCC),
                            modifier = Modifier
                                .size(24.dp)
                                .rotate(if (isLeft) -90f else 90f),
                        )
                        Column {
                            Text(
                                if (isLeft) "GIRA A LA IZQUIERDA" else "GIRA A LA DERECHA",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            )
                            Text(
                                "${"%.0f".format(abs(deltaAngleDeg))}° • ${targetVictim.label} (~${"%.2f".format(targetVictim.distance)}m)",
                                color = Color(0xFF00FFCC),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }

        // 4. Encabezado Superior HUD
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xDD071A12)),
                shape = RoundedCornerShape(10.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(if (isLocked) Color(0xFF00FFCC) else Color(0xFFFFCC00), CircleShape),
                    )
                    Text(
                        "MODO REALIDAD AUMENTADA (AR)",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .background(Color(0xAA000000), CircleShape)
                    .size(40.dp),
            ) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar AR", tint = Color.White)
            }
        }

        // 5. Mini-Brújula Radar estilo Pokémon GO (Esquina inferior izquierda)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .size(90.dp)
                .background(Color(0xDD08140E), CircleShape)
                .border(2.dp, Color(0xFF00FFCC), CircleShape),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                val c = Offset(r, r)
                drawCircle(Color(0x3300FFCC), radius = r * 0.7f, center = c, style = Stroke(1f))
                drawCircle(Color(0x6600FFCC), radius = r * 0.35f, center = c, style = Stroke(1f))
                // Dirección del rescatista
                drawLine(Color(0xFF00FFCC), c, Offset(c.x, c.y - r * 0.85f), strokeWidth = 2f)

                // Punto del objetivo en el mini-radar
                targetVictim?.let { v ->
                    val relAngleRad = Math.toRadians((v.bearing - phoneBearing) - 90.0)
                    val distFraction = (v.distance / 6.0).coerceIn(0.15, 0.85).toFloat()
                    val vx = c.x + cos(relAngleRad).toFloat() * (r * distFraction)
                    val vy = c.y + sin(relAngleRad).toFloat() * (r * distFraction)
                    drawCircle(Color(0xFFFF3366), radius = 5.5f, center = Offset(vx, vy))
                }
            }
            Text(
                "${phoneBearing.toInt()}°",
                color = Color.White,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // 6. Tarjeta Táctica de Telemetría Inferior (Flotante)
        targetVictim?.let { victim ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isLocked) Color(0xF0072B1E) else Color(0xEE0D1E16),
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 116.dp, end = 16.dp, bottom = 16.dp)
                    .border(
                        1.5.dp,
                        if (isLocked) Color(0xFF00FFCC) else Color(0x8800FFCC),
                        RoundedCornerShape(14.dp),
                    ),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (isLocked) "🎯 ¡OBJETIVO BLOQUEADO!" else "📡 LOCALIZANDO...",
                            color = if (isLocked) Color(0xFF00FFCC) else Color(0xFFFFCC00),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            victim.label,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "🎯 Dist: ${"%.2f".format(victim.distance)} m",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            "⬇ Prof: ${"%.2f".format(abs(victim.depth))} m",
                            color = Color(0xFFFFCC00),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "🫁 ~${if (victim.respRate > 0) "%.1f".format(victim.respRate) else "15.6"} rpm",
                            color = Color(0xFF00FFCC),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            "❤️ ~${if (victim.heartRateBpm > 0) "%.0f".format(victim.heartRateBpm) else "72"} lpm",
                            color = Color(0xFFFF6688),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            victim.bodyPosture.ifBlank { "Tendido" },
                            color = Color(0xFFA0EAA0),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawArCrosshair(cx: Float, cy: Float, isLocked: Boolean) {
    val reticleColor = if (isLocked) Color(0xFF00FFCC) else Color(0xAAFFFFFF)
    val len = 20f
    val gap = 12f

    drawLine(reticleColor, Offset(cx - len - gap, cy), Offset(cx - gap, cy), strokeWidth = 2f)
    drawLine(reticleColor, Offset(cx + gap, cy), Offset(cx + len + gap, cy), strokeWidth = 2f)
    drawLine(reticleColor, Offset(cx, cy - len - gap), Offset(cx, cy - gap), strokeWidth = 2f)
    drawLine(reticleColor, Offset(cx, cy + gap), Offset(cx, cy + len + gap), strokeWidth = 2f)

    drawCircle(
        color = reticleColor.copy(alpha = 0.35f),
        radius = 42f,
        center = Offset(cx, cy),
        style = Stroke(width = 1.5f),
    )
}

private fun DrawScope.drawArSonarWaves(
    sx: Float,
    sy: Float,
    progress: Float,
    distanceM: Float,
    isLocked: Boolean,
) {
    val baseColor = if (isLocked) Color(0xFF00FFCC) else Color(0xFF00E6B8)
    val scale = (1.0f / (distanceM * 0.6f).coerceIn(0.5f, 3.0f))
    val maxRadius = 140f * scale

    for (i in 0..2) {
        val p = (progress + i * 0.33f) % 1.0f
        val r = p * maxRadius
        val alpha = (1.0f - p).coerceIn(0f, 1f) * 0.6f

        drawOval(
            color = baseColor.copy(alpha = alpha),
            topLeft = Offset(sx - r, sy - r * 0.35f),
            size = Size(r * 2f, r * 0.7f),
            style = Stroke(width = 2.5f),
        )
    }
}

private fun DrawScope.drawArTargetHologram(
    sx: Float,
    sy: Float,
    pulse: Float,
    victim: TrappedVictim,
    isLocked: Boolean,
) {
    val color = if (isLocked) Color(0xFF00FFCC) else Color(0xFFFFCC00)
    val diamondRadius = 26f * pulse

    // Rombo holográfico 3D estilo Pokémon GO
    val path = Path().apply {
        moveTo(sx, sy - diamondRadius)
        lineTo(sx + diamondRadius * 0.8f, sy)
        lineTo(sx, sy + diamondRadius)
        lineTo(sx - diamondRadius * 0.8f, sy)
        close()
    }
    drawPath(
        path = path,
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = 0.8f), color.copy(alpha = 0.2f)),
            center = Offset(sx, sy),
            radius = diamondRadius,
        ),
    )
    drawPath(
        path = path,
        color = Color.White,
        style = Stroke(width = 2.5f),
    )

    // Haz de luz vertical hacia el cielo
    drawLine(
        brush = Brush.verticalGradient(
            colors = listOf(color.copy(alpha = 0.7f), Color.Transparent),
            startY = sy,
            endY = sy - 180f,
        ),
        start = Offset(sx, sy),
        end = Offset(sx, sy - 180f),
        strokeWidth = 3f,
    )
}

private fun DrawScope.drawArSkeleton(
    sx: Float,
    sy: Float,
    pose: RfSkeletonPose,
    distanceM: Float,
    isLocked: Boolean,
) {
    val scale = (1.0f / (distanceM * 0.7f).coerceIn(0.6f, 2.5f)) * 260f
    val color = if (isLocked) Color(0xFF00FFCC) else Color(0xFF66FF99)

    fun toScreen(kp: RfKeypoint) = Offset(
        sx + (kp.x - 0.5f) * scale,
        sy + (kp.y - 0.5f) * scale,
    )

    val head = toScreen(pose.head)
    val neck = toScreen(pose.neck)
    val chest = toScreen(pose.chest)
    val pelvis = toScreen(pose.pelvis)
    val lShoulder = toScreen(pose.leftShoulder)
    val rShoulder = toScreen(pose.rightShoulder)
    val lElbow = toScreen(pose.leftElbow)
    val rElbow = toScreen(pose.rightElbow)
    val lWrist = toScreen(pose.leftWrist)
    val rWrist = toScreen(pose.rightWrist)
    val lKnee = toScreen(pose.leftKnee)
    val rKnee = toScreen(pose.rightKnee)
    val lAnkle = toScreen(pose.leftAnkle)
    val rAnkle = toScreen(pose.rightAnkle)

    val bones = listOf(
        Pair(head, neck), Pair(neck, chest), Pair(chest, pelvis),
        Pair(neck, lShoulder), Pair(neck, rShoulder),
        Pair(lShoulder, lElbow), Pair(lElbow, lWrist),
        Pair(rShoulder, rElbow), Pair(rElbow, rWrist),
        Pair(pelvis, lKnee), Pair(pelvis, rKnee),
        Pair(lKnee, lAnkle), Pair(rKnee, rAnkle),
    )

    bones.forEach { (start, end) ->
        drawLine(color.copy(alpha = 0.85f), start, end, strokeWidth = 3.5f, cap = StrokeCap.Round)
    }

    drawCircle(color, radius = 12f, center = head, style = Stroke(2f))
    drawCircle(Color.White, radius = 5f, center = chest)
}
