package com.vitalfi.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.vitalfi.dsp.HeatPoint
import com.vitalfi.dsp.TrappedVictim
import kotlin.math.abs
import kotlin.math.hypot

data class TrailPoint3D(val x: Double, val y: Double, val z: Double)

@Composable
fun Radar3DView(
    bearingDeg: Double,
    victims: List<TrappedVictim>,
    selectedVictimId: Int?,
    heatmap: List<HeatPoint>,
    trail: List<TrailPoint3D>,
    viewAzim: Double,
    viewElev: Double,
    viewport: RadarViewport,
    wifiCoverageRadiusM: Double,
    layers: RadarLayers = RadarLayers(),
    colorblind: Boolean = false,
    rubbleZoneCenterDeg: Double? = null,
    rubbleZoneRadiusDeg: Double = 55.0,
    environmentCells: List<com.vitalfi.dsp.EnvironmentCell> = emptyList(),
    onVictimClick: (Int) -> Unit,
    onViewportChange: (RadarViewport) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sonarTransition = rememberInfiniteTransition(label = "sonar3d")
    val sonarPulse by sonarTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
        ),
        label = "sonarPulse",
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF050A05)),
        modifier = modifier,
        shape = CircleShape,
    ) {
        val labelPaint = remember {
            android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#44AA66")
                textSize = 22f
                isAntiAlias = true
            }
        }
        val tagPaint = remember {
            android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#AAFFCC")
                textSize = 24f
                isFakeBoldText = true
                isAntiAlias = true
            }
        }
        val context = androidx.compose.ui.platform.LocalContext.current
        var canvasSize by remember { mutableStateOf(IntSize.Zero) }

        fun project(x: Double, y: Double, z: Double): Offset =
            projectRadar3D(
                canvasSize.width.toFloat(),
                canvasSize.height.toFloat(),
                viewAzim,
                viewElev,
                x,
                y,
                z,
                viewport,
            )

        Canvas(
            Modifier
                .fillMaxSize()
                .padding(6.dp)
                .onSizeChanged { canvasSize = it }
                .radarGestures(
                    viewport = viewport,
                    victims = victims,
                    projectVictim = { victim ->
                        val (rx, ry) = rotateMapCoords(victim.x, victim.y, bearingDeg)
                        project(rx, ry, 0.0)
                    },
                    onVictimClick = onVictimClick,
                    onViewportChange = onViewportChange,
                ),
        ) {
            canvasSize = IntSize(size.width.toInt(), size.height.toInt())

            // Fondo tipo HUD solo si la regla/grid está activa
            if (layers.grid) drawMilitaryHudBackground()

            if (layers.rubble) {
                drawRubbleVolume3D(
                    project = ::project,
                    labelPaint = labelPaint,
                    rubbleCenterDeg = rubbleZoneCenterDeg,
                    rubbleRadiusDeg = rubbleZoneRadiusDeg,
                    phoneBearingDeg = bearingDeg,
                )
            }
            if (layers.grid) {
                drawDistanceRings3D(::project, viewport, labelPaint, RADAR_DEFAULT_RANGE_M)
                drawRadarSonarPulse3D(sonarPulse, ::project)
            }

            if (layers.wifi) {
                drawWifiCoverage3D(wifiCoverageRadiusM, ::project, labelPaint, sonarPulse)
                drawRfEnvironment3D(
                    cells = environmentCells,
                    phoneBearingDeg = bearingDeg,
                    project = ::project,
                    labelPaint = labelPaint,
                )
            }
            if (layers.compass) drawCompassRose3D(bearingDeg, ::project, labelPaint)

            if (layers.heatmap) {
                for (pt in heatmap) {
                    val (rx, ry) = rotateMapCoords(pt.x, pt.y, bearingDeg)
                    val p = project(rx, ry, -0.4)
                    
                    val intensity = pt.intensity.coerceIn(0.0, 1.0).toFloat()
                    val maxAlpha = 0.2f + 0.6f * intensity
                    val radius = (25f + 20f * intensity) * viewport.zoom
                    
                    if (radius > 0f) {
                        val brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFF3300).copy(alpha = maxAlpha),
                                Color(0xFFFF9900).copy(alpha = maxAlpha * 0.4f),
                                Color.Transparent
                            ),
                            center = p,
                            radius = radius
                        )
                        drawCircle(
                            brush = brush,
                            radius = radius,
                            center = p,
                            blendMode = BlendMode.Screen
                        )
                    }
                }
            }

            if (layers.trail && trail.size >= 2) {
                val path = Path()
                trail.forEachIndexed { i, t ->
                    val (rx, ry) = rotateMapCoords(t.x, t.y, bearingDeg)
                    val p = project(rx, ry, t.z)
                    if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                drawPath(path, Color(0xFFFF6699).copy(alpha = 0.5f), style = Stroke(2f))
            }

            val you = project(0.0, 0.0, 0.05)
            drawCircle(Color(0xFF00FF88), 7f, you)

            val target = navigationTarget(victims, selectedVictimId)
            val atZero = target != null && target.distance < 0.6

            // Flecha de frente: se oculta en punto cero (no tapa el sitio de excavación)
            if (layers.compass && !atZero) {
                val frontEnd = project(0.0, 1.4, 0.05)
                drawLine(Color(0xFF00FF88).copy(alpha = 0.35f), you, frontEnd, strokeWidth = 5f)
                drawLine(Color(0xFF00FF88), you, frontEnd, strokeWidth = 2.2f)
            }

            if (layers.guide) {
                drawHeadingDirector3D(
                    bearingDeg = bearingDeg,
                    target = target,
                    labelPaint = labelPaint,
                    tagPaint = tagPaint,
                    colorblind = colorblind,
                )
            }

            drawVictims3D(
                victims,
                bearingDeg,
                selectedVictimId,
                ::project,
                tagPaint,
                labelPaint,
                showLabels = layers.labels,
                colorblind = colorblind,
                showDepthRays = layers.depthRays,
            )

            // Telemetría solo con la capa Guía (omitible)
            if (layers.guide) {
                drawMilitaryTelemetryHud(
                    bearingDeg = bearingDeg,
                    target = target,
                    victimCount = victims.size,
                    tagPaint = tagPaint,
                    labelPaint = labelPaint,
                    context = context,
                )
            }
        }
    }
}



/** Cuadrícula fina de fondo estilo pantalla táctica. */
private fun DrawScope.drawMilitaryHudBackground() {
    val step = 28f
    val grid = Color(0xFF0A2A12).copy(alpha = 0.55f)
    var x = 0f
    while (x < size.width) {
        drawLine(grid, Offset(x, 0f), Offset(x, size.height), strokeWidth = 0.8f)
        x += step
    }
    var y = 0f
    while (y < size.height) {
        drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 0.8f)
        y += step
    }
}

/**
 * Datos en esquinas: HDG, RNG, DEPTH estimada.
 * La profundidad Wi‑Fi es heurística; no ordena cavar.
 */
private fun DrawScope.drawMilitaryTelemetryHud(
    bearingDeg: Double,
    target: TrappedVictim?,
    victimCount: Int,
    tagPaint: android.graphics.Paint,
    labelPaint: android.graphics.Paint,
    context: android.content.Context? = null,
) {
    val green = android.graphics.Color.parseColor("#66FF99")
    val dim = android.graphics.Color.parseColor("#448866")
    val alert = android.graphics.Color.parseColor("#FF3366")
    val leftPaint = android.graphics.Paint(labelPaint).apply {
        color = dim
        textSize = 13f
        isAntiAlias = true
    }
    val valuePaint = android.graphics.Paint(tagPaint).apply {
        color = green
        textSize = 15f
        isFakeBoldText = true
        isAntiAlias = true
    }
    val alertPaint = android.graphics.Paint(valuePaint).apply { color = alert }

    // Superior izquierda — estado
    val rescueModeText = context?.getString(com.vitalfi.R.string.radar_mode_rescue) ?: "MODO RESCATE"
    drawContext.canvas.nativeCanvas.drawText("VITALFI PPI", 10f, 18f, valuePaint)
    drawContext.canvas.nativeCanvas.drawText(rescueModeText, 10f, 34f, leftPaint)
    drawContext.canvas.nativeCanvas.drawText(
        "OBJETIVOS $victimCount",
        10f,
        50f,
        leftPaint,
    )

    // Superior derecha — navegación
    val rx = size.width - 10f
    val rightAlign = android.graphics.Paint(valuePaint).apply {
        textAlign = android.graphics.Paint.Align.RIGHT
    }
    val rightDim = android.graphics.Paint(leftPaint).apply {
        textAlign = android.graphics.Paint.Align.RIGHT
    }
    drawContext.canvas.nativeCanvas.drawText(
        "HDG ${"%.0f".format(((bearingDeg % 360) + 360) % 360)}°",
        rx,
        18f,
        rightAlign,
    )
    if (target != null) {
        val turn = normalizedTurnDelta(target.bearing, bearingDeg)
        val rng = target.distance
        val depth = abs(target.depth)
        drawContext.canvas.nativeCanvas.drawText(
            "RNG ${"%.2f".format(rng)} m",
            rx,
            36f,
            rightAlign,
        )
        drawContext.canvas.nativeCanvas.drawText(
            "DEPTH~${"%.2f".format(depth)} m ?",
            rx,
            54f,
            rightAlign,
        )
        val steer = when {
            rng < 0.6 -> "◎ CERCA — verifica"
            abs(turn) < 12 -> "↑ RECTO"
            turn > 0 -> "↻ DER ${turn.toInt()}°"
            else -> "↺ IZQ ${(-turn).toInt()}°"
        }
        drawContext.canvas.nativeCanvas.drawText(
            steer,
            rx,
            72f,
            if (rng < 0.6) {
                android.graphics.Paint(alertPaint).apply {
                    textAlign = android.graphics.Paint.Align.RIGHT
                }
            } else {
                rightAlign
            },
        )
    } else {
        val searchingText = context?.getString(com.vitalfi.R.string.radar_status_searching) ?: "BUSCANDO…"
        drawContext.canvas.nativeCanvas.drawText("RNG —", rx, 36f, rightDim)
        drawContext.canvas.nativeCanvas.drawText(searchingText, rx, 54f, rightDim)
    }

    // Inferior derecha — modo
    drawContext.canvas.nativeCanvas.drawText(
        "ESCOMBROS 3D",
        rx,
        size.height - 12f,
        rightDim,
    )
    // Inferior izquierda — leyenda corta
    drawContext.canvas.nativeCanvas.drawText(
        "□ objetivo · ↓ prof. estimada · ◎ cerca",
        10f,
        size.height - 12f,
        leftPaint,
    )
}
