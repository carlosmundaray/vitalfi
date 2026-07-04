package com.whofi.vitalfi.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import com.whofi.vitalfi.dsp.HeatPoint
import com.whofi.vitalfi.dsp.TrappedVictim
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

data class RadarViewport(
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
) {
    fun zoomIn(): RadarViewport = copy(zoom = (zoom * 1.3f).coerceAtMost(5f))
    fun zoomOut(): RadarViewport = copy(zoom = (zoom / 1.3f).coerceAtLeast(0.5f))
    fun reset(): RadarViewport = RadarViewport()
}

internal val RadarCyan = Color(0xFF00FFCC)
internal val RadarRed = Color(0xFFFF3366)
internal val RadarOrange = Color(0xFFFF9900)
internal val RadarGreen = Color(0xFF66FF99)

fun DrawScope.radarCenter(viewport: RadarViewport): Pair<Float, Float> {
    val cx = size.width / 2f + viewport.panX
    val cy = size.height / 2f + viewport.panY
    return cx to cy
}

fun DrawScope.effectiveRadarScale(divisor: Float, viewport: RadarViewport): Float {
    val half = minOf(size.width, size.height) / 2f
    return half / divisor * viewport.zoom
}

fun Modifier.radarGestures(
    viewport: RadarViewport,
    victims: List<TrappedVictim>,
    projectVictim: (TrappedVictim) -> Offset,
    onVictimClick: (Int) -> Unit,
    onViewportChange: (RadarViewport) -> Unit,
): Modifier = this
    .pointerInput(viewport) {
        detectTransformGestures { _, pan, zoom, _ ->
            onViewportChange(
                viewport.copy(
                    zoom = (viewport.zoom * zoom).coerceIn(0.5f, 5f),
                    panX = viewport.panX + pan.x,
                    panY = viewport.panY + pan.y,
                ),
            )
        }
    }
    .pointerInput(victims, viewport) {
        detectTapGestures { tap ->
            val hit = victims.minByOrNull { victim ->
                val p = projectVictim(victim)
                hypot(tap.x - p.x, tap.y - p.y)
            }
            hit?.let { victim ->
                val p = projectVictim(victim)
                val hitRadius = (36f * viewport.zoom).coerceIn(28f, 56f)
                if (hypot(tap.x - p.x, tap.y - p.y) <= hitRadius) {
                    onVictimClick(victim.id)
                }
            }
        }
    }

fun DrawScope.drawWifiCoverage2D(
    coverageRadiusM: Double,
    divisor: Float,
    viewport: RadarViewport,
    labelPaint: android.graphics.Paint,
) {
    if (coverageRadiusM <= 0.0) return

    val (cx, cy) = radarCenter(viewport)
    val scale = effectiveRadarScale(divisor, viewport)
    val radiusPx = coverageRadiusM.toFloat() * scale

    drawCircle(Color(0xFF0088FF).copy(alpha = 0.10f), radiusPx, Offset(cx, cy))
    drawCircle(Color(0xFF00FFCC).copy(alpha = 0.22f), radiusPx, Offset(cx, cy))
    drawCircle(
        RadarCyan.copy(alpha = 0.55f),
        radiusPx,
        Offset(cx, cy),
        style = androidx.compose.ui.graphics.drawscope.Stroke(2f),
    )
    drawContext.canvas.nativeCanvas.drawText(
        "Wi-Fi ~${"%.1f".format(coverageRadiusM)} m",
        cx - 52f,
        cy - radiusPx - 8f,
        labelPaint,
    )
}

fun DrawScope.drawWifiCoverage3D(
    coverageRadiusM: Double,
    project: (Double, Double, Double) -> Offset,
    labelPaint: android.graphics.Paint,
) {
    if (coverageRadiusM <= 0.0) return

    val segments = 56
    val points = (0..segments).map { i ->
        val t = i / segments.toDouble() * 2 * Math.PI
        project(coverageRadiusM * cos(t), coverageRadiusM * sin(t), 0.0)
    }
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
    drawPath(path, Color(0xFF0088FF).copy(alpha = 0.10f))
    drawPath(path, RadarCyan.copy(alpha = 0.45f), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))

    val labelPt = project(coverageRadiusM, 0.0, 0.0)
    drawContext.canvas.nativeCanvas.drawText(
        "Wi-Fi ~${"%.1f".format(coverageRadiusM)} m",
        labelPt.x + 4f,
        labelPt.y - 6f,
        labelPaint,
    )
}

fun bearingToCardinal(bearingDeg: Double): String {
    val dirs = listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
    val idx = (((bearingDeg + 22.5) % 360.0) / 45.0).toInt() % 8
    return dirs[idx]
}

fun normalizedTurnDelta(targetBearing: Double, currentBearing: Double): Double {
    var turn = targetBearing - currentBearing
    if (turn > 180.0) turn -= 360.0
    if (turn < -180.0) turn += 360.0
    return turn
}

fun navigationTarget(victims: List<TrappedVictim>, selectedVictimId: Int?): TrappedVictim? =
    selectedVictimId?.let { id -> victims.find { it.id == id } } ?: victims.firstOrNull()

fun buildNavigationHint(
    targetBearing: Double,
    currentBearing: Double,
    distanceM: Double,
): String {
    val turn = normalizedTurnDelta(targetBearing, currentBearing)
    val cardinal = bearingToCardinal(targetBearing)
    val steer = when {
        abs(turn) < 12 -> "↑ Ve recto hacia $cardinal (${targetBearing.roundToInt()}°)"
        turn > 0 -> "↻ Gira ${turn.roundToInt()}° a la derecha → $cardinal (${targetBearing.roundToInt()}°)"
        else -> "↺ Gira ${(-turn).roundToInt()}° a la izquierda → $cardinal (${targetBearing.roundToInt()}°)"
    }
    return "$steer\nDistancia: ${"%.1f".format(distanceM)} m"
}

fun DrawScope.drawCompassRose2D(
    bearingDeg: Double,
    divisor: Float,
    viewport: RadarViewport,
    labelPaint: android.graphics.Paint,
) {
    val (cx, cy) = radarCenter(viewport)
    val scale = effectiveRadarScale(divisor, viewport)
    val radius = 5.4f * scale
    val northPaint = android.graphics.Paint(labelPaint).apply {
        color = android.graphics.Color.parseColor("#FF3366")
        isFakeBoldText = true
        textSize = labelPaint.textSize * 1.15f
    }
    val interPaint = android.graphics.Paint(labelPaint).apply {
        textSize = labelPaint.textSize * 0.85f
    }

    // Anillo exterior de brújula (gira con el rumbo real del teléfono)
    drawCircle(
        Color(0xFF2A5A4A).copy(alpha = 0.55f),
        radius + 18f,
        Offset(cx, cy),
        style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f),
    )

    for (deg in 0 until 360 step 30) {
        val relativeDeg = deg - bearingDeg
        val rad = Math.toRadians(relativeDeg)
        val inner = radius * if (deg % 90 == 0) 0.88f else 0.93f
        val outer = radius + if (deg % 90 == 0) 14f else 8f
        val x1 = cx + sin(rad).toFloat() * inner
        val y1 = cy - cos(rad).toFloat() * inner
        val x2 = cx + sin(rad).toFloat() * outer
        val y2 = cy - cos(rad).toFloat() * outer
        val tickColor = if (deg % 90 == 0) Color(0xFF44AA88) else Color(0xFF2A4A3A)
        drawLine(tickColor, Offset(x1, y1), Offset(x2, y2), strokeWidth = if (deg % 90 == 0) 2.5f else 1.2f)
    }

    val labels = listOf(
        "N" to 0.0, "NE" to 45.0, "E" to 90.0, "SE" to 135.0,
        "S" to 180.0, "SO" to 225.0, "O" to 270.0, "NO" to 315.0,
    )
    for ((label, geoDeg) in labels) {
        val relativeDeg = geoDeg - bearingDeg
        val rad = Math.toRadians(relativeDeg)
        val px = cx + sin(rad).toFloat() * (radius + 28f)
        val py = cy - cos(rad).toFloat() * (radius + 28f)
        val paint = when (label) {
            "N" -> northPaint
            "E", "S", "O" -> labelPaint
            else -> interPaint
        }
        if (label == "N") {
            drawCircle(Color(0xFFFF3366).copy(alpha = 0.85f), 6f, Offset(px, py))
        }
        val textWidth = paint.measureText(label)
        drawContext.canvas.nativeCanvas.drawText(label, px - textWidth / 2f, py + 8f, paint)
    }

    // Frente del teléfono (arriba del radar) — adónde apuntas ahora
    val frontTip = Offset(cx, cy - radius * 0.55f)
    drawLine(RadarCyan.copy(alpha = 0.4f), Offset(cx, cy), frontTip, strokeWidth = 3f)
    drawArrowHead(frontTip, 0.0, RadarCyan, size = 12f)
    drawContext.canvas.nativeCanvas.drawText(
        "▲ Frente ${bearingDeg.roundToInt()}°",
        cx - 42f,
        cy - radius * 0.55f - 10f,
        android.graphics.Paint(tagPaintDefaults(labelPaint)).apply { color = android.graphics.Color.parseColor("#00FFCC") },
    )
}

private fun tagPaintDefaults(labelPaint: android.graphics.Paint): android.graphics.Paint =
    android.graphics.Paint(labelPaint).apply {
        textSize = labelPaint.textSize * 0.9f
        isFakeBoldText = true
    }

private fun DrawScope.drawArrowHead(tip: Offset, angleRad: Double, color: Color, size: Float = 14f) {
    val left = Offset(
        tip.x - size * cos(angleRad - Math.PI / 7).toFloat(),
        tip.y - size * sin(angleRad - Math.PI / 7).toFloat(),
    )
    val right = Offset(
        tip.x - size * cos(angleRad + Math.PI / 7).toFloat(),
        tip.y - size * sin(angleRad + Math.PI / 7).toFloat(),
    )
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(left.x, left.y)
        lineTo(right.x, right.y)
        close()
    }
    drawPath(path, color)
}

fun DrawScope.drawNavigationGuide2D(
    bearingDeg: Double,
    target: TrappedVictim?,
    divisor: Float,
    viewport: RadarViewport,
    labelPaint: android.graphics.Paint,
    tagPaint: android.graphics.Paint,
) {
    val (cx, cy) = radarCenter(viewport)
    val scale = effectiveRadarScale(divisor, viewport)

    if (target == null) return

    val relativeBearing = target.bearing - bearingDeg
    val targetRad = Math.toRadians(relativeBearing)
    val targetLen = target.distance.coerceIn(0.8, 5.5).toFloat() * scale
    val targetPoint = Offset(
        cx + sin(targetRad).toFloat() * targetLen,
        cy - cos(targetRad).toFloat() * targetLen,
    )
    drawLine(RadarOrange.copy(alpha = 0.35f), Offset(cx, cy), targetPoint, strokeWidth = 10f)
    drawLine(RadarOrange.copy(alpha = 0.55f), Offset(cx, cy), targetPoint, strokeWidth = 6f)
    drawLine(RadarOrange, Offset(cx, cy), targetPoint, strokeWidth = 2.5f)
    drawArrowHead(targetPoint, targetRad + Math.PI / 2, RadarOrange, size = 18f)
    drawCircle(RadarOrange, 9f, targetPoint)

    val turn = normalizedTurnDelta(target.bearing, bearingDeg)
    val turnText = when {
        abs(turn) < 12 -> "↑ Ve recto hacia ${bearingToCardinal(target.bearing)}"
        turn > 0 -> "↻ Gira ${turn.roundToInt()}° a la DERECHA → ${bearingToCardinal(target.bearing)}"
        else -> "↺ Gira ${(-turn).roundToInt()}° a la IZQUIERDA → ${bearingToCardinal(target.bearing)}"
    }

    val bannerTop = 6f
    drawRect(
        color = Color(0xFF101810).copy(alpha = 0.88f),
        topLeft = Offset(8f, bannerTop),
        size = androidx.compose.ui.geometry.Size(size.width - 16f, 72f),
    )
    drawContext.canvas.nativeCanvas.drawText(
        "→ ${target.label}: ${target.bearing.roundToInt()}° ${bearingToCardinal(target.bearing)}",
        16f,
        bannerTop + 26f,
        tagPaint,
    )
    drawContext.canvas.nativeCanvas.drawText(
        turnText,
        16f,
        bannerTop + 56f,
        android.graphics.Paint(tagPaint).apply {
            color = android.graphics.Color.parseColor("#FFCC00")
            textSize = tagPaint.textSize * 1.05f
        },
    )

    val infoY = cy + scale * 0.72f
    drawContext.canvas.nativeCanvas.drawText(
        "Distancia: ${"%.1f".format(target.distance)} m",
        cx - 70f,
        infoY,
        labelPaint,
    )
}

fun rotateMapCoords(x: Double, y: Double, bearingDeg: Double): Pair<Double, Double> {
    val rad = Math.toRadians(-bearingDeg)
    val rx = x * cos(rad) - y * sin(rad)
    val ry = x * sin(rad) + y * cos(rad)
    return rx to ry
}

fun DrawScope.drawCompassRose3D(
    bearingDeg: Double,
    project: (Double, Double, Double) -> Offset,
    labelPaint: android.graphics.Paint,
) {
    val northPaint = android.graphics.Paint(labelPaint).apply {
        color = android.graphics.Color.parseColor("#FF3366")
        isFakeBoldText = true
    }
    val interPaint = android.graphics.Paint(labelPaint).apply {
        textSize = labelPaint.textSize * 0.85f
    }
    val labels = listOf(
        "N" to 0.0, "NE" to 45.0, "E" to 90.0, "SE" to 135.0,
        "S" to 180.0, "SO" to 225.0, "O" to 270.0, "NO" to 315.0,
    )
    for ((label, geoDeg) in labels) {
        val relativeDeg = geoDeg - bearingDeg
        val rad = Math.toRadians(relativeDeg)
        val point = project(5.5 * sin(rad), 5.5 * cos(rad), 0.0)
        val paint = when (label) {
            "N" -> northPaint
            "E", "S", "O" -> labelPaint
            else -> interPaint
        }
        val textWidth = paint.measureText(label)
        drawContext.canvas.nativeCanvas.drawText(label, point.x - textWidth / 2f, point.y + 8f, paint)
    }
}

fun DrawScope.drawRadarGrid2D(
    divisor: Float,
    viewport: RadarViewport,
    labelPaint: android.graphics.Paint,
) {
    val (cx, cy) = radarCenter(viewport)
    val scale = effectiveRadarScale(divisor, viewport)

    for (r in listOf(1.5f, 3f, 5f)) {
        drawCircle(
            Color(0xFF1A3A1A),
            r * scale,
            Offset(cx, cy),
            style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f),
        )
        drawContext.canvas.nativeCanvas.drawText(
            "${r}m",
            cx + 4f,
            cy - r * scale - 4f,
            labelPaint,
        )
    }

    drawLine(Color(0xFF2A4A2A), Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 1f)
    drawLine(Color(0xFF2A4A2A), Offset(0f, cy), Offset(size.width, cy), strokeWidth = 1f)
    drawContext.canvas.nativeCanvas.drawText("Y+", cx + 6f, 18f, labelPaint)
    drawContext.canvas.nativeCanvas.drawText("X+", size.width - 28f, cy - 6f, labelPaint)
    drawContext.canvas.nativeCanvas.drawText("(0,0)", cx + 8f, cy + 22f, labelPaint)
}

fun DrawScope.drawVictims2D(
    victims: List<TrappedVictim>,
    bearingDeg: Double,
    divisor: Float,
    viewport: RadarViewport,
    selectedVictimId: Int?,
    labelPaint: android.graphics.Paint,
    tagPaint: android.graphics.Paint,
) {
    val (cx, cy) = radarCenter(viewport)
    val scale = effectiveRadarScale(divisor, viewport)

    for (victim in victims) {
        val (rx, ry) = rotateMapCoords(victim.x, victim.y, bearingDeg)
        val tx = cx + rx.toFloat() * scale
        val ty = cy - ry.toFloat() * scale
        val selected = victim.id == selectedVictimId
        val color = victimColor(victim)
        val radius = if (selected) 14f else 11f
        drawCircle(color.copy(alpha = 0.25f), radius + 10f, Offset(tx, ty))
        drawCircle(color, radius, Offset(tx, ty))
        if (selected) {
            drawCircle(RadarCyan, radius + 6f, Offset(tx, ty), style = androidx.compose.ui.graphics.drawscope.Stroke(2.5f))
        }
        drawLine(color.copy(alpha = 0.45f), Offset(cx, cy), Offset(tx, ty), strokeWidth = if (selected) 2.5f else 1.5f)
        drawContext.canvas.nativeCanvas.drawText(victim.label, tx - 12f, ty - radius - 8f, tagPaint)
        drawContext.canvas.nativeCanvas.drawText(
            "(${formatCoord(victim.x)},${formatCoord(victim.y)})",
            tx - 34f,
            ty + radius + 16f,
            labelPaint,
        )
    }
}

fun DrawScope.drawHeatmap2D(
    heatmap: List<HeatPoint>,
    bearingDeg: Double,
    divisor: Float,
    viewport: RadarViewport,
) {
    val (cx, cy) = radarCenter(viewport)
    val scale = effectiveRadarScale(divisor, viewport)
    for (pt in heatmap) {
        val (rx, ry) = rotateMapCoords(pt.x, pt.y, bearingDeg)
        val px = cx + rx.toFloat() * scale
        val py = cy - ry.toFloat() * scale
        val alpha = (0.15f + 0.55f * pt.intensity.coerceIn(0.0, 1.0)).toFloat()
        drawCircle(Color(0xFFFF6600).copy(alpha = alpha), 6f, Offset(px, py))
    }
}

fun DrawScope.drawVictims3D(
    victims: List<TrappedVictim>,
    bearingDeg: Double,
    selectedVictimId: Int?,
    project: (Double, Double, Double) -> Offset,
    origin: Offset,
    tagPaint: android.graphics.Paint,
    labelPaint: android.graphics.Paint,
) {
    for (victim in victims) {
        val (rx, ry) = rotateMapCoords(victim.x, victim.y, bearingDeg)
        val target = project(rx, ry, victim.depth)
        val selected = victim.id == selectedVictimId
        val color = victimColor(victim)
        val radius = if (selected) 11f else 8f
        drawCircle(color.copy(alpha = 0.2f), radius + 8f, target)
        drawCircle(color, radius, target)
        if (selected) {
            drawCircle(RadarCyan, radius + 5f, target, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
        }
        drawLine(color.copy(alpha = 0.4f), origin, target, strokeWidth = if (selected) 2f else 1.5f)
        drawContext.canvas.nativeCanvas.drawText(victim.label, target.x - 10f, target.y - radius - 6f, tagPaint)
        drawContext.canvas.nativeCanvas.drawText(
            "Z=${formatCoord(-victim.depth)}m",
            target.x - 28f,
            target.y + radius + 14f,
            labelPaint,
        )
    }
}

fun projectRadar3D(
    canvasWidth: Float,
    canvasHeight: Float,
    viewAzim: Double,
    viewElev: Double,
    x: Double,
    y: Double,
    z: Double,
    viewport: RadarViewport,
    divisor: Float = 7f,
): Offset {
    val cx = canvasWidth / 2f + viewport.panX
    val cy = canvasHeight / 2f + viewport.panY
    val half = minOf(canvasWidth, canvasHeight) / 2f
    val scale = half / divisor * viewport.zoom
    val az = Math.toRadians(viewAzim)
    val el = Math.toRadians(viewElev)
    val x1 = x * cos(az) - y * sin(az)
    val y1 = x * sin(az) + y * cos(az)
    val z1 = z
    val y2 = y1 * cos(el) - z1 * sin(el)
    val z2 = y1 * sin(el) + z1 * cos(el)
    return Offset(cx + x1.toFloat() * scale, cy - y2.toFloat() * scale - z2.toFloat() * scale * 0.35f)
}

private fun formatCoord(value: Double): String = "%.1f".format(value)

fun victimColor(victim: TrappedVictim): Color = when {
    victim.isBreathing && victim.heartBeating -> RadarRed
    victim.isBreathing -> RadarRed.copy(alpha = 0.85f)
    victim.heartBeating -> Color(0xFFFF6699)
    victim.isActivity -> RadarOrange
    else -> RadarOrange.copy(alpha = 0.7f)
}
