package com.whofi.vitalfi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.whofi.vitalfi.dsp.HeatPoint
import com.whofi.vitalfi.dsp.TrappedVictim
import kotlin.math.cos
import kotlin.math.sin

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
    onVictimClick: (Int) -> Unit,
    onViewportChange: (RadarViewport) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D140D)),
        modifier = modifier,
    ) {
        val labelPaint = remember {
            android.graphics.Paint().apply {
                color = android.graphics.Color.GRAY
                textSize = 24f
            }
        }
        val tagPaint = remember {
            android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 24f
                isFakeBoldText = true
            }
        }
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
                .padding(8.dp)
                .radarGestures(
                    viewport = viewport,
                    victims = victims,
                    projectVictim = { victim ->
                        val (rx, ry) = rotateMapCoords(victim.x, victim.y, bearingDeg)
                        project(rx, ry, victim.depth)
                    },
                    onVictimClick = onVictimClick,
                    onViewportChange = onViewportChange,
                ),
        ) {
            canvasSize = IntSize(size.width.toInt(), size.height.toInt())

            for (r in listOf(1.5, 3.0, 5.0)) {
                val pts = (0..48).map { i ->
                    val t = i / 48.0 * 2 * Math.PI
                    project(r * cos(t), r * sin(t), 0.0)
                }
                val path = Path().apply {
                    moveTo(pts.first().x, pts.first().y)
                    pts.drop(1).forEach { lineTo(it.x, it.y) }
                    close()
                }
                drawPath(path, Color(0xFF1A3A1A), style = Stroke(1.5f))
                val labelPt = project(r, 0.0, 0.0)
                drawContext.canvas.nativeCanvas.drawText("${r}m", labelPt.x + 4f, labelPt.y - 4f, labelPaint)
            }

            val gridN = 5
            for (i in -gridN..gridN) {
                val a = project(i.toDouble(), -gridN.toDouble(), 0.0)
                val b = project(i.toDouble(), gridN.toDouble(), 0.0)
                drawLine(Color(0xFF1A2A1A), a, b, strokeWidth = 0.5f)
                val c = project(-gridN.toDouble(), i.toDouble(), 0.0)
                val d = project(gridN.toDouble(), i.toDouble(), 0.0)
                drawLine(Color(0xFF1A2A1A), c, d, strokeWidth = 0.5f)
            }

            drawWifiCoverage3D(wifiCoverageRadiusM, ::project, labelPaint)
            drawCompassRose3D(bearingDeg, ::project, labelPaint)

            for (pt in heatmap) {
                val (rx, ry) = rotateMapCoords(pt.x, pt.y, bearingDeg)
                val p = project(rx, ry, -0.4)
                val alpha = (0.2f + 0.5f * pt.intensity.coerceIn(0.0, 1.0)).toFloat()
                drawCircle(Color(0xFFFF6600).copy(alpha = alpha), 5f, p)
            }

            if (trail.size >= 2) {
                val path = Path()
                trail.forEachIndexed { i, t ->
                    val (rx, ry) = rotateMapCoords(t.x, t.y, bearingDeg)
                    val p = project(rx, ry, t.z)
                    if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                drawPath(path, Color(0xFFFF6699).copy(alpha = 0.5f), style = Stroke(2f))
            }

            val laptop = project(0.0, 0.0, 1.0)
            drawCircle(RadarCyan, 12f, laptop)

            // Frente del teléfono (hacia arriba en pantalla)
            val frontEnd = project(0.0, 1.2, 1.0)
            drawLine(RadarCyan.copy(alpha = 0.35f), laptop, frontEnd, strokeWidth = 5f)
            drawLine(RadarCyan, laptop, frontEnd, strokeWidth = 3f)
            drawContext.canvas.nativeCanvas.drawText(
                "▲ Frente ${bearingDeg.toInt()}° ${bearingToCardinal(bearingDeg)}",
                frontEnd.x + 8f,
                frontEnd.y,
                tagPaint,
            )

            navigationTarget(victims, selectedVictimId)?.let { target ->
                val (rx, ry) = rotateMapCoords(target.x, target.y, bearingDeg)
                val targetPoint = project(rx, ry, target.depth)
                drawLine(RadarOrange.copy(alpha = 0.35f), laptop, targetPoint, strokeWidth = 8f)
                drawLine(RadarOrange.copy(alpha = 0.55f), laptop, targetPoint, strokeWidth = 5f)
                drawLine(RadarOrange, laptop, targetPoint, strokeWidth = 2.5f)
                val turn = normalizedTurnDelta(target.bearing, bearingDeg)
                val turnText = when {
                    kotlin.math.abs(turn) < 12 -> "↑ Ve recto → ${bearingToCardinal(target.bearing)}"
                    turn > 0 -> "↻ Gira ${turn.toInt()}° DERECHA → ${bearingToCardinal(target.bearing)}"
                    else -> "↺ Gira ${(-turn).toInt()}° IZQUIERDA → ${bearingToCardinal(target.bearing)}"
                }
                drawContext.canvas.nativeCanvas.drawText(
                    "→ ${target.label}: ${target.bearing.toInt()}° ${bearingToCardinal(target.bearing)} | ${"%.1f".format(target.distance)} m",
                    8f,
                    24f,
                    tagPaint,
                )
                drawContext.canvas.nativeCanvas.drawText(
                    turnText,
                    8f,
                    52f,
                    android.graphics.Paint(tagPaint).apply {
                        color = android.graphics.Color.parseColor("#FFCC00")
                        textSize = tagPaint.textSize * 1.05f
                    },
                )
            }

            drawVictims3D(
                victims,
                bearingDeg,
                selectedVictimId,
                ::project,
                laptop,
                tagPaint,
                labelPaint,
            )

            drawContext.canvas.nativeCanvas.apply {
                drawText("Radar 3D — brújula N/E/S/O | pellizca zoom", 8f, size.height - 8f, labelPaint)
            }
        }
    }
}
