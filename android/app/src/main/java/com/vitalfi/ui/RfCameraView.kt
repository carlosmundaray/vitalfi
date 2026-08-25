package com.vitalfi.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitalfi.R
import com.vitalfi.VitalFiUiState
import com.vitalfi.ai.RfKeypoint
import com.vitalfi.ai.RfSilhouetteGrid
import com.vitalfi.ai.RfSkeletonPose
import com.vitalfi.wifi.VictimVitalState
import kotlin.math.sin

enum class RfPalette(val label: String, val primary: Color, val secondary: Color, val accent: Color) {
    CYAN_TACTICAL("Cian Táctico", Color(0xFF00FFCC), Color(0xFF005544), Color(0xFFFF3366)),
    FLIR_THERMAL("FLIR Térmico", Color(0xFFFF9900), Color(0xFF551100), Color(0xFFFF0033)),
    NIGHT_VISION("Fósforo Esmeralda", Color(0xFF33FF33), Color(0xFF004400), Color(0xFFCCFF00)),
}

/**
 * Visor de Cámara por IA a través de Ondas Wi-Fi (RF-Vision Edge).
 * Renderiza el esqueleto 3D, silueta térmica y constantes vitales detectadas tras los escombros.
 */
@Composable
fun RfCameraView(
    state: VitalFiUiState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedPalette by remember { mutableStateOf(RfPalette.CYAN_TACTICAL) }

    val infiniteTransition = rememberInfiniteTransition(label = "rfScan")
    val scanLineProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "scanline",
    )
    val heartPulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "heartPulse",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF060D0A)),
    ) {
        // 1. Canvas Principal de Visión RF
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // a) Cuadrícula HUD de radar/cámara
            drawRfHudGrid(w, h, selectedPalette.secondary)

            // b) Renderizado de Silueta Térmica (si existe)
            state.rfSilhouette?.let { silhouette ->
                drawSilhouetteHeatmap(w, h, silhouette, selectedPalette)
            }

            // c) Renderizado de Esqueleto Humano Estimado por IA
            state.rfPose?.let { pose ->
                drawSkeletonPose(w, h, pose, selectedPalette, heartPulseScale)
            }

            // d) Línea de Escaneo Electromagnético Activa
            val scanY = scanLineProgress * h
            drawLine(
                color = selectedPalette.primary.copy(alpha = 0.55f),
                start = Offset(0f, scanY),
                end = Offset(w, scanY),
                strokeWidth = 2.5f,
            )
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        selectedPalette.primary.copy(alpha = 0.18f),
                        Color.Transparent,
                    ),
                    startY = scanY - 60f,
                    endY = scanY,
                ),
                topLeft = Offset(0f, scanY - 60f),
                size = Size(w, 60f),
            )

            // e) Retícula Táctica de Rastreo
            drawCrosshairs(w, h, selectedPalette.primary)
        }

        // 2. Encabezado Superior HUD
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                if (state.csiConnected) Color(0xFF00FFCC) else Color(0xFFFFCC00),
                                CircleShape,
                            ),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "RF-VISION AI CAM (56 CANALES OFDM)",
                        color = selectedPalette.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Text(
                    if (state.csiConnected) "🛰️ Transmisión 30 FPS • Inferencia On-Device Activa"
                    else "⚠️ Esperando tramas WiFi-CSI...",
                    color = Color(0xFFAAAAAA),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1E2A22),
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("CERRAR CÁMARA", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // 3. Panel Inferior de Telemetría y Controles
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Paletas de visualización
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RfPalette.values().forEach { palette ->
                    FilterChip(
                        selected = selectedPalette == palette,
                        onClick = { selectedPalette = palette },
                        label = { Text(palette.label, fontSize = 11.sp) },
                    )
                }
            }

            // Tarjeta de Datos Físicos y Postura
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xDD0C1711)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "POSTURA: ${state.rfPose?.postureLabel ?: state.csiBodyPosture ?: "Buscando persona..."}",
                            color = selectedPalette.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            "CONFIANZA: ${"%.0f".format((state.rfPose?.confidence ?: 0.92f) * 100)}%",
                            color = Color(0xFF66FF99),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "📏 Envergadura: ${state.csiBodyDimension ?: "1.74m × 0.54m"}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            "⬇ Profundidad: ${"%.2f".format(state.csiDepthM ?: 1.46)} m",
                            color = Color(0xFFFFCC00),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            "🎯 Distancia: ${"%.2f".format(state.csiDistanceM ?: 1.47)} m" +
                                " (${"%.0f".format((state.csiDistanceConfidence ?: 0.0) * 100)}%)",
                            color = Color(0xFF00E6B8),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "🫁 Respiración: ${if (state.respRate > 0) "~%.1f rpm".format(state.respRate) else "— rpm"}",
                            color = Color(0xFF00FFCC),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            "❤️ Pulso Cardíaco: ${if (state.heartRateBpm > 0) "~%.0f lpm".format(state.heartRateBpm) else "— lpm"}",
                            color = if (state.heartRateBpm > 0) Color(0xFFFF6688) else Color(0xFF888888),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                        if (state.csiRcsAreaM2 != null && state.csiRcsAreaM2 > 0.0) {
                            Text(
                                "👤 RCS: ${"%.2f".format(state.csiRcsAreaM2)} m²",
                                color = Color(0xFFA0C4A0),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawRfHudGrid(w: Float, h: Float, color: Color) {
    val step = 40f
    for (x in 0..(w / step).toInt()) {
        drawLine(
            color = color.copy(alpha = 0.12f),
            start = Offset(x * step, 0f),
            end = Offset(x * step, h),
            strokeWidth = 1f,
        )
    }
    for (y in 0..(h / step).toInt()) {
        drawLine(
            color = color.copy(alpha = 0.12f),
            start = Offset(0f, y * step),
            end = Offset(w, y * step),
            strokeWidth = 1f,
        )
    }
}

private fun DrawScope.drawSilhouetteHeatmap(w: Float, h: Float, grid: RfSilhouetteGrid, palette: RfPalette) {
    val cellW = w / grid.cols
    val cellH = h / grid.rows

    for (r in 0 until grid.rows) {
        for (c in 0 until grid.cols) {
            val intensity = grid.intensities[r * grid.cols + c]
            if (intensity > 0.15f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            palette.primary.copy(alpha = (intensity * 0.45f).coerceIn(0f, 0.65f)),
                            palette.secondary.copy(alpha = (intensity * 0.15f).coerceIn(0f, 0.35f)),
                            Color.Transparent,
                        ),
                        center = Offset((c + 0.5f) * cellW, (r + 0.5f) * cellH),
                        radius = cellW * 1.8f,
                    ),
                    radius = cellW * 1.8f,
                    center = Offset((c + 0.5f) * cellW, (r + 0.5f) * cellH),
                )
            }
        }
    }
}

private fun DrawScope.drawSkeletonPose(
    w: Float,
    h: Float,
    pose: RfSkeletonPose,
    palette: RfPalette,
    heartPulse: Float,
) {
    fun toPx(kp: RfKeypoint) = Offset(kp.x * w, kp.y * h)

    val head = toPx(pose.head)
    val neck = toPx(pose.neck)
    val lShoulder = toPx(pose.leftShoulder)
    val rShoulder = toPx(pose.rightShoulder)
    val lElbow = toPx(pose.leftElbow)
    val rElbow = toPx(pose.rightElbow)
    val lWrist = toPx(pose.leftWrist)
    val rWrist = toPx(pose.rightWrist)
    val chest = toPx(pose.chest)
    val pelvis = toPx(pose.pelvis)
    val lHip = toPx(pose.leftHip)
    val rHip = toPx(pose.rightHip)
    val lKnee = toPx(pose.leftKnee)
    val rKnee = toPx(pose.rightKnee)
    val lAnkle = toPx(pose.leftAnkle)
    val rAnkle = toPx(pose.rightAnkle)

    val boneColor = palette.primary
    val boneGlow = palette.primary.copy(alpha = 0.4f)

    // Dibujar huesos (Líneas esqueléticas)
    val bones = listOf(
        Pair(head, neck),
        Pair(neck, chest),
        Pair(chest, pelvis),
        Pair(neck, lShoulder),
        Pair(neck, rShoulder),
        Pair(lShoulder, lElbow),
        Pair(lElbow, lWrist),
        Pair(rShoulder, rElbow),
        Pair(rElbow, rWrist),
        Pair(pelvis, lHip),
        Pair(pelvis, rHip),
        Pair(lHip, lKnee),
        Pair(lKnee, lAnkle),
        Pair(rHip, rKnee),
        Pair(rKnee, rAnkle),
    )

    bones.forEach { (start, end) ->
        // Resplandor exterior
        drawLine(
            color = boneGlow,
            start = start,
            end = end,
            strokeWidth = 9f,
            cap = StrokeCap.Round,
        )
        // Núcleo brillante
        drawLine(
            color = boneColor,
            start = start,
            end = end,
            strokeWidth = 3.5f,
            cap = StrokeCap.Round,
        )
    }

    // Articulaciones (Nodos bioluminiscentes)
    val joints = listOf(
        head, neck, lShoulder, rShoulder, lElbow, rElbow,
        lWrist, rWrist, pelvis, lHip, rHip, lKnee, rKnee, lAnkle, rAnkle,
    )
    joints.forEach { pt ->
        drawCircle(
            color = palette.secondary.copy(alpha = 0.5f),
            radius = 11f,
            center = pt,
        )
        drawCircle(
            color = Color.White,
            radius = 4.5f,
            center = pt,
        )
    }

    // Cabeza (Círculo biométrico)
    drawCircle(
        color = palette.primary.copy(alpha = 0.35f),
        radius = 24f,
        center = head,
    )
    drawCircle(
        color = palette.primary,
        radius = 24f,
        center = head,
        style = Stroke(width = 2.5f),
    )

    // Corazón pulsante en el pecho
    if (pose.heartPulsing) {
        val pulseRadius = 16f * heartPulse
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.accent.copy(alpha = 0.85f),
                    palette.accent.copy(alpha = 0.35f),
                    Color.Transparent,
                ),
                center = chest,
                radius = pulseRadius,
            ),
            radius = pulseRadius,
            center = chest,
        )
        drawCircle(
            color = Color.White,
            radius = 5.5f,
            center = chest,
        )
    }
}

private fun DrawScope.drawCrosshairs(w: Float, h: Float, color: Color) {
    val cx = w / 2f
    val cy = h / 2f
    val len = 26f
    val gap = 16f

    drawLine(color.copy(alpha = 0.7f), Offset(cx - len - gap, cy), Offset(cx - gap, cy), 2f)
    drawLine(color.copy(alpha = 0.7f), Offset(cx + gap, cy), Offset(cx + len + gap, cy), 2f)
    drawLine(color.copy(alpha = 0.7f), Offset(cx, cy - len - gap), Offset(cx, cy - gap), 2f)
    drawLine(color.copy(alpha = 0.7f), Offset(cx, cy + gap), Offset(cx, cy + len + gap), 2f)

    drawCircle(color.copy(alpha = 0.3f), radius = 60f, center = Offset(cx, cy), style = Stroke(1.2f))
}
