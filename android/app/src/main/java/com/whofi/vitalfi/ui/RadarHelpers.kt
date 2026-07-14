package com.whofi.vitalfi.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import com.whofi.vitalfi.dsp.HeatPoint
import com.whofi.vitalfi.dsp.TrappedVictim
import com.whofi.vitalfi.dsp.describePersonPosition
import com.whofi.vitalfi.dsp.personSphereCenter
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** Default visible radius in meters at zoom 1.0 (center → edge). */
const val RADAR_DEFAULT_RANGE_M = 6f
private const val RADAR_ZOOM_MIN = 0.35f
private const val RADAR_ZOOM_MAX = 10f
private const val RADAR_ZOOM_STEP = 1.18f

data class RadarViewport(
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
) {
    fun zoomIn(): RadarViewport = copy(zoom = (zoom * RADAR_ZOOM_STEP).coerceAtMost(RADAR_ZOOM_MAX))
    fun zoomOut(): RadarViewport = copy(zoom = (zoom / RADAR_ZOOM_STEP).coerceAtLeast(RADAR_ZOOM_MIN))
    fun reset(): RadarViewport = RadarViewport()

    /** Meters from center to the near edge of the canvas at current zoom. */
    fun visibleRangeM(divisor: Float = RADAR_DEFAULT_RANGE_M): Float =
        (divisor / zoom).coerceAtLeast(0.5f)
}

/**
 * Capas del radar. Por defecto = modo rescate (limpio para navegar hacia la persona).
 */
data class RadarLayers(
    val grid: Boolean = true,
    val compass: Boolean = true,
    val wifi: Boolean = false,
    val heatmap: Boolean = false,
    val guide: Boolean = true,
    val rubble: Boolean = false,
    val trail: Boolean = false,
    val labels: Boolean = true,
    /** Rayas verticales de profundidad / esfera (la “raya” amarilla que tapa el centro). */
    val depthRays: Boolean = false,
) {
    /** Solo la figura + guía de rumbo (para ver y seguir a la persona). */
    fun soloPersona(): RadarLayers = RadarLayers(
        grid = false,
        compass = false,
        wifi = false,
        heatmap = false,
        guide = true,
        rubble = false,
        trail = false,
        labels = true,
        depthRays = false,
    )

    /** Vista limpia de rescate: navegar al punto cero sin rayas que tapen. */
    fun modoRescate(): RadarLayers = RadarLayers(
        grid = true,
        compass = true,
        wifi = false,
        heatmap = false,
        guide = true,
        rubble = false,
        trail = false,
        labels = true,
        depthRays = false,
    )

    fun todoVisible(): RadarLayers = RadarLayers(
        grid = true,
        compass = true,
        wifi = true,
        heatmap = true,
        guide = true,
        rubble = true,
        trail = true,
        labels = true,
        depthRays = true,
    )
}

internal val RadarCyan = Color(0xFF00FFCC)
internal val RadarRed = Color(0xFFFF3366)
internal val RadarOrange = Color(0xFFFF9900)
internal val RadarGreen = Color(0xFF66FF99)

/** Tick spacing adapts so zoom always shows a readable meter ruler. */
fun radarTickStepM(visibleRangeM: Float): Float = when {
    visibleRangeM <= 1.5f -> 0.25f
    visibleRangeM <= 3f -> 0.5f
    visibleRangeM <= 6f -> 1f
    visibleRangeM <= 12f -> 2f
    else -> 5f
}

fun radarMajorEvery(tickStepM: Float): Int = when {
    tickStepM <= 0.25f -> 4  // label every 1 m
    tickStepM <= 0.5f -> 2   // label every 1 m
    else -> 1                // label every tick
}

fun formatRadarMeters(meters: Float): String =
    if (meters == meters.toInt().toFloat()) "${meters.toInt()} m" else "%.1f m".format(meters)

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
                    zoom = (viewport.zoom * zoom).coerceIn(RADAR_ZOOM_MIN, RADAR_ZOOM_MAX),
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
    // Estilo búsqueda del tesoro: gira hasta alinear, luego camina al punto
    val proximity = when {
        distanceM < 0.6 -> "⛏ ¡AQUÍ! Excava — la persona está debajo."
        distanceM < 1.5 -> "🔥 Muy cerca — sigue unos pasos."
        distanceM < 3.5 -> "🟠 Cerca — estás acercándote."
        else -> "🔵 Lejos — sigue la flecha al punto."
    }
    val steer = when {
        distanceM < 0.6 -> proximity
        abs(turn) < 12 -> "↑ Camina recto hacia $cardinal · ${"%.1f".format(distanceM)} m\n$proximity"
        turn > 0 -> "→ Gira ${turn.roundToInt()}° a la derecha hasta alinear · luego camina ${"%.1f".format(distanceM)} m\n$proximity"
        else -> "← Gira ${(-turn).roundToInt()}° a la izquierda hasta alinear · luego camina ${"%.1f".format(distanceM)} m\n$proximity"
    }
    return "🗺️ Búsqueda → persona ($cardinal ${targetBearing.roundToInt()}°)\n$steer"
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

    // Frente del teléfono (arriba del radar) — solo si no hay guía HUD (evita duplicar)
    // La guía F-16 dibuja el boresight; aquí solo etiqueta ligera si hace falta.
    drawContext.canvas.nativeCanvas.drawText(
        "▲ Frente",
        cx - 22f,
        cy - radius * 0.55f - 10f,
        android.graphics.Paint(tagPaintDefaults(labelPaint)).apply {
            color = android.graphics.Color.parseColor("#00FFCC")
            textSize = labelPaint.textSize * 0.85f
        },
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
    colorblind: Boolean = false,
) {
    val (cx, cy) = radarCenter(viewport)
    val scale = effectiveRadarScale(divisor, viewport)
    val frontLen = minOf(size.width, size.height) * 0.40f
    val fwd = guideForwardColor(colorblind)
    val ok = guideAlignedColor(colorblind)
    val turnCol = guideTurnColor(colorblind)

    // 1) Boresight: tu frente SIEMPRE hacia arriba, con regla de metros
    drawForwardBoresight(cx, cy, frontLen, scale, viewport.visibleRangeM(divisor), labelPaint, fwd)

    if (target == null) {
        drawHeadingDirectorHud(
            turnDeg = 0.0,
            targetLabel = "—",
            distanceM = 0.0,
            labelPaint = labelPaint,
            tagPaint = tagPaint,
            idle = true,
            colorblind = colorblind,
        )
        return
    }

    // PUNTO CERO: la persona está debajo de ti → marca de excavación en el centro
    if (target.distance < 0.6) {
        drawPointZeroMarker(cx, cy, tagPaint, colorblind)
        drawHeadingDirectorHud(
            turnDeg = 0.0,
            targetLabel = target.label,
            distanceM = target.distance,
            labelPaint = labelPaint,
            tagPaint = tagPaint,
            idle = false,
            colorblind = colorblind,
        )
        return
    }

    val turn = normalizedTurnDelta(target.bearing, bearingDeg)
    val relativeBearing = target.bearing - bearingDeg
    val targetRad = Math.toRadians(relativeBearing)
    val targetLen = target.distance.coerceIn(0.5, 12.0).toFloat() * scale
    val targetPoint = Offset(
        cx + sin(targetRad).toFloat() * targetLen,
        cy - cos(targetRad).toFloat() * targetLen,
    )
    val aligned = abs(turn) < 12.0
    val lineColor = if (aligned) ok else turnCol
    val you = Offset(cx, cy)

    // 2) Cono / pasillo hacia el objetivo (se cierra cuando estás alineado)
    drawSteerCone(cx, cy, frontLen, turn, aligned, ok, turnCol)

    // 3) Camino tipo “tesoro”: pasillo grueso desde ti hasta la persona
    drawTreasurePathToPerson(you, targetPoint, target.distance, lineColor, aligned)

    // 4) Chevrones laterales grandes (giro claro) + texto IZQ/DER
    if (abs(turn) >= 12.0) {
        drawAccessibleTurnIcon(toRight = turn > 0, color = turnCol, strength = (abs(turn) / 60.0).coerceIn(0.35, 1.0).toFloat())
    }

    // 5) HUD: gira hasta alinear, luego camina al punto
    drawHeadingDirectorHud(
        turnDeg = turn,
        targetLabel = target.label,
        distanceM = target.distance,
        labelPaint = labelPaint,
        tagPaint = tagPaint,
        idle = false,
        colorblind = colorblind,
    )
}

/** Camino grueso tú → persona (búsqueda del tesoro, sin barrido giratorio). */
private fun DrawScope.drawTreasurePathToPerson(
    from: Offset,
    to: Offset,
    distanceM: Double,
    color: Color,
    aligned: Boolean,
) {
    drawLine(color.copy(alpha = 0.16f), from, to, strokeWidth = 22f)
    drawLine(color.copy(alpha = if (aligned) 0.95f else 0.75f), from, to, strokeWidth = if (aligned) 4.5f else 3.2f)
    val dx = to.x - from.x
    val dy = to.y - from.y
    val len = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f)
    val ux = dx / len
    val uy = dy / len
    val tip = to
    val back = Offset(tip.x - ux * 16f, tip.y - uy * 16f)
    val left = Offset(back.x - uy * 9f, back.y + ux * 9f)
    val right = Offset(back.x + uy * 9f, back.y - ux * 9f)
    drawPath(
        Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(left.x, left.y)
            lineTo(right.x, right.y)
            close()
        },
        color,
    )
    drawCircle(color.copy(alpha = 0.95f), 7f, to)
    val mid = Offset((from.x + to.x) / 2f, (from.y + to.y) / 2f)
    val paint = android.graphics.Paint().apply {
        this.color = android.graphics.Color.WHITE
        textSize = 13f
        isFakeBoldText = true
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }
    drawContext.canvas.nativeCanvas.drawText(
        "→ ${"%.1f".format(distanceM)} m",
        mid.x,
        mid.y - 8f,
        paint,
    )
}

/** Marca de PUNTO CERO: la víctima está debajo del rescatista — excavar aquí. */
private fun DrawScope.drawPointZeroMarker(
    cx: Float,
    cy: Float,
    tagPaint: android.graphics.Paint,
    colorblind: Boolean = false,
) {
    val accent = accessibleAccent(VictimStatusKind.DIG_HERE, colorblind)
    drawCircle(accent.copy(alpha = 0.12f), 60f, Offset(cx, cy))
    drawCircle(accent.copy(alpha = 0.85f), 44f, Offset(cx, cy), style = Stroke(width = 3.5f))
    drawCircle(accent.copy(alpha = 0.85f), 26f, Offset(cx, cy), style = Stroke(width = 2.5f))
    drawVictimStatusIcon(Offset(cx, cy), VictimStatusKind.DIG_HERE, accent, scale = 1.6f)
    val paint = android.graphics.Paint(tagPaint).apply {
        color = if (colorblind) {
            android.graphics.Color.parseColor("#FFEE00")
        } else {
            android.graphics.Color.parseColor("#FF3366")
        }
        textSize = 16f
        isFakeBoldText = true
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
    }
    drawContext.canvas.nativeCanvas.drawText("⛏ PUNTO CERO — EXCAVA AQUÍ", cx, cy + 84f, paint)
}

/** Cono de dirección: indica el rumbo relativo sin línea diagonal confusa. */
private fun DrawScope.drawSteerCone(
    cx: Float,
    cy: Float,
    length: Float,
    turnDeg: Double,
    aligned: Boolean,
    okColor: Color = RadarGreen,
    turnColor: Color = RadarOrange,
) {
    val halfSpread = if (aligned) 8.0 else (18.0 + abs(turnDeg).coerceAtMost(45.0) * 0.15)
    // En pantalla: ángulo 0 = derecha matemática; -90° = arriba
    val screenAngle = Math.toRadians(-90.0) + Math.toRadians(turnDeg.coerceIn(-75.0, 75.0))
    val leftA = screenAngle - Math.toRadians(halfSpread)
    val rightA = screenAngle + Math.toRadians(halfSpread)
    val color = if (aligned) okColor.copy(alpha = 0.22f) else turnColor.copy(alpha = 0.18f)
    val edge = if (aligned) okColor.copy(alpha = 0.65f) else turnColor.copy(alpha = 0.55f)

    val tip = Offset(cx, cy)
    val p1 = Offset(cx + cos(leftA).toFloat() * length, cy + sin(leftA).toFloat() * length)
    val p2 = Offset(cx + cos(rightA).toFloat() * length, cy + sin(rightA).toFloat() * length)
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(p1.x, p1.y)
        lineTo(p2.x, p2.y)
        close()
    }
    drawPath(path, color)
    drawLine(edge, tip, p1, strokeWidth = 2f)
    drawLine(edge, tip, p2, strokeWidth = 2f)

    // Flecha en el eje del cono
    val midA = screenAngle
    val arrowTip = Offset(cx + cos(midA).toFloat() * length * 0.92f, cy + sin(midA).toFloat() * length * 0.92f)
    drawLine(edge, tip, arrowTip, strokeWidth = 2.5f)
    drawArrowHead(arrowTip, midA, edge, size = 14f)
}

/** Chevrones IZQ/DER tipo avión de combate. */
private fun DrawScope.drawTurnChevrons(turnDeg: Double) {
    if (abs(turnDeg) < 12.0) return
    val toRight = turnDeg > 0
    val strength = (abs(turnDeg) / 60.0).coerceIn(0.35, 1.0).toFloat()
    val color = RadarOrange.copy(alpha = 0.55f + 0.4f * strength)
    val x = if (toRight) size.width - 28f else 28f
    val y = size.height * 0.48f
    val dir = if (toRight) 1f else -1f
    for (i in 0 until 2) {
        val ox = x - dir * i * 16f
        val path = Path().apply {
            moveTo(ox, y - 22f)
            lineTo(ox + dir * 16f, y)
            lineTo(ox, y + 22f)
            lineTo(ox - dir * 6f, y + 22f)
            lineTo(ox + dir * 10f, y)
            lineTo(ox - dir * 6f, y - 22f)
            close()
        }
        drawPath(path, color.copy(alpha = color.alpha * (1f - i * 0.35f)))
    }
}

/**
 * Línea de frente fija hacia arriba + reticle (hacia dónde apuntas),
 * con regla de metros: marcas horizontales cada tick con su etiqueta.
 */
private fun DrawScope.drawForwardBoresight(
    cx: Float,
    cy: Float,
    length: Float,
    scale: Float,
    visibleRangeM: Float,
    labelPaint: android.graphics.Paint,
    accent: Color = RadarCyan,
) {
    val tip = Offset(cx, cy - length)
    drawLine(accent.copy(alpha = 0.14f), Offset(cx, cy), tip, strokeWidth = 16f)
    drawLine(accent.copy(alpha = 0.65f), Offset(cx, cy), tip, strokeWidth = 3.2f)

    // Regla de metros hacia el frente: ticks horizontales + etiqueta
    if (scale > 0f) {
        val tickStep = radarTickStepM(visibleRangeM)
        val majorEvery = radarMajorEvery(tickStep)
        val tickPaint = android.graphics.Paint(labelPaint).apply {
            color = android.graphics.Color.parseColor(
                if (accent == RadarCyan) "#00E5FF" else "#EEEEEE",
            )
            textSize = labelPaint.textSize * 0.85f
            isFakeBoldText = true
            textAlign = android.graphics.Paint.Align.LEFT
        }
        var i = 1
        while (true) {
            val meters = tickStep * i
            val y = cy - meters * scale
            if (y < cy - length) break
            val major = i % majorEvery == 0
            val half = if (major) 14f else 7f
            val alpha = if (major) 0.95f else 0.55f
            drawLine(
                accent.copy(alpha = alpha),
                Offset(cx - half, y),
                Offset(cx + half, y),
                strokeWidth = if (major) 3f else 2f,
            )
            if (major) {
                drawContext.canvas.nativeCanvas.drawText(
                    formatRadarMeters(meters),
                    cx + half + 6f,
                    y + tickPaint.textSize * 0.35f,
                    tickPaint,
                )
            }
            i++
            if (i > 200) break
        }
    }

    drawArrowHead(tip, -Math.PI / 2, accent, size = 13f)
    drawCircle(accent.copy(alpha = 0.5f), 4f, Offset(cx, cy))
}

/**
 * Cinta de rumbo tipo F-16: el centro = tu frente.
 * El marcador naranja se mueve IZQ/DER según cuánto debes girar para alinear.
 */
private fun DrawScope.drawHeadingDirectorHud(
    turnDeg: Double,
    targetLabel: String,
    distanceM: Double,
    labelPaint: android.graphics.Paint,
    tagPaint: android.graphics.Paint,
    idle: Boolean = false,
    colorblind: Boolean = false,
) {
    val hudCx = size.width / 2f
    val hudY = 34f
    val halfWidth = (size.width * 0.40f).coerceIn(95f, 170f)
    val clampedTurn = turnDeg.coerceIn(-90.0, 90.0)
    val bugX = hudCx + (clampedTurn / 90.0).toFloat() * halfWidth
    val aligned = !idle && abs(turnDeg) < 12.0
    val atPointZero = !idle && distanceM < 0.6
    val fwd = guideForwardColor(colorblind)
    val ok = guideAlignedColor(colorblind)
    val turnCol = guideTurnColor(colorblind)
    val dig = accessibleAccent(VictimStatusKind.DIG_HERE, colorblind)

    drawRect(
        color = Color(0xFF050E08).copy(alpha = 0.88f),
        topLeft = Offset(hudCx - halfWidth - 14f, 6f),
        size = androidx.compose.ui.geometry.Size(halfWidth * 2f + 28f, 62f),
    )

    drawLine(
        Color(0xFF2A4A3A),
        Offset(hudCx - halfWidth, hudY),
        Offset(hudCx + halfWidth, hudY),
        strokeWidth = 2.2f,
    )
    for (tick in listOf(-60f, -30f, 0f, 30f, 60f)) {
        val x = hudCx + (tick / 90f) * halfWidth
        val h = if (tick == 0f) 14f else 8f
        drawLine(
            if (tick == 0f) fwd else Color(0xFF4A6A5A),
            Offset(x, hudY - h),
            Offset(x, hudY + h),
            strokeWidth = if (tick == 0f) 3f else 1.6f,
        )
    }

    // Centro = frente
    drawLine(fwd, Offset(hudCx, hudY - 16f), Offset(hudCx, hudY + 16f), strokeWidth = 3.2f)
    drawCircle(fwd, 4.5f, Offset(hudCx, hudY))

    if (!idle) {
        val bugColor = when {
            atPointZero -> dig
            aligned -> ok
            else -> turnCol
        }
        val bugPath = Path().apply {
            moveTo(bugX, hudY - 18f)
            lineTo(bugX - 10f, hudY - 3f)
            lineTo(bugX + 10f, hudY - 3f)
            close()
        }
        drawPath(bugPath, bugColor)
        drawCircle(bugColor, 4f, Offset(bugX, hudY))
    }

    val steerText = when {
        idle -> "Gira el teléfono — busca el punto de la persona"
        atPointZero -> "⛏ PUNTO CERO · $targetLabel DEBAJO — EXCAVA"
        aligned -> "▲ CAMINA RECTO · $targetLabel · ${"%.2f".format(distanceM)} m"
        turnDeg > 0 -> "→ GIRA DERECHA ${turnDeg.roundToInt()}° hasta alinear · $targetLabel"
        else -> "← GIRA IZQUIERDA ${(-turnDeg).roundToInt()}° hasta alinear · $targetLabel"
    }
    val textPaint = android.graphics.Paint(tagPaint).apply {
        color = when {
            idle -> android.graphics.Color.parseColor("#88AAAA")
            atPointZero -> if (colorblind) {
                android.graphics.Color.parseColor("#FFEE00")
            } else {
                android.graphics.Color.parseColor("#FF3366")
            }
            aligned -> if (colorblind) {
                android.graphics.Color.parseColor("#4DA6FF")
            } else {
                android.graphics.Color.parseColor("#66FF99")
            }
            else -> android.graphics.Color.parseColor("#FFCC00")
        }
        textSize = 16f
        isFakeBoldText = true
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
    }
    drawContext.canvas.nativeCanvas.drawText(steerText, hudCx, hudY + 30f, textPaint)

    if (!idle) {
        val hintPaint = android.graphics.Paint(labelPaint).apply {
            color = android.graphics.Color.parseColor("#889988")
            textSize = 12f
            textAlign = android.graphics.Paint.Align.CENTER
        }
        drawContext.canvas.nativeCanvas.drawText(
            if (colorblind) {
                "▲ = frente · →← = girar · ⛏ = cavar (iconos, no solo color)"
            } else {
                "Centra ▲ con | del frente = camino recto"
            },
            hudCx,
            hudY + 48f,
            hintPaint,
        )
    }
}

private fun DrawScope.drawDashedLine(
    start: Offset,
    end: Offset,
    color: Color,
    strokeWidth: Float,
    dash: Float,
    gap: Float,
) {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val len = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f)
    val ux = dx / len
    val uy = dy / len
    var drawn = 0f
    var on = true
    while (drawn < len) {
        val seg = if (on) dash else gap
        val next = (drawn + seg).coerceAtMost(len)
        if (on) {
            drawLine(
                color,
                Offset(start.x + ux * drawn, start.y + uy * drawn),
                Offset(start.x + ux * next, start.y + uy * next),
                strokeWidth = strokeWidth,
            )
        }
        drawn = next
        on = !on
    }
}

/** HUD de dirección 3D (mismo concepto F-16, coordenadas de pantalla). */
fun DrawScope.drawHeadingDirector3D(
    bearingDeg: Double,
    target: TrappedVictim?,
    labelPaint: android.graphics.Paint,
    tagPaint: android.graphics.Paint,
    colorblind: Boolean = false,
) {
    if (target == null) {
        drawHeadingDirectorHud(
            turnDeg = 0.0,
            targetLabel = "—",
            distanceM = 0.0,
            labelPaint = labelPaint,
            tagPaint = tagPaint,
            idle = true,
            colorblind = colorblind,
        )
        return
    }
    if (target.distance < 0.6) {
        drawHeadingDirectorHud(
            turnDeg = 0.0,
            targetLabel = target.label,
            distanceM = target.distance,
            labelPaint = labelPaint,
            tagPaint = tagPaint,
            idle = false,
            colorblind = colorblind,
        )
        return
    }
    val turn = normalizedTurnDelta(target.bearing, bearingDeg)
    drawHeadingDirectorHud(
        turnDeg = turn,
        targetLabel = target.label,
        distanceM = target.distance,
        labelPaint = labelPaint,
        tagPaint = tagPaint,
        idle = false,
        colorblind = colorblind,
    )
    if (abs(turn) >= 12.0) {
        drawAccessibleTurnIcon(
            toRight = turn > 0,
            color = guideTurnColor(colorblind),
            strength = (abs(turn) / 60.0).coerceIn(0.35, 1.0).toFloat(),
        )
    }
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
    val visibleRange = viewport.visibleRangeM(divisor)
    val tickStep = radarTickStepM(visibleRange)
    val majorEvery = radarMajorEvery(tickStep)
    val maxTicks = ((visibleRange / tickStep).toInt() + 1).coerceIn(2, 24)

    val axisColor = Color(0xFF2A4A2A)
    val minorRing = Color(0xFF163016)
    val majorRing = Color(0xFF2A5A2A)
    val tickColor = Color(0xFF3A7A3A)

    drawLine(axisColor, Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 1.2f)
    drawLine(axisColor, Offset(0f, cy), Offset(size.width, cy), strokeWidth = 1.2f)

    val meterPaint = android.graphics.Paint(labelPaint).apply {
        color = android.graphics.Color.parseColor("#66FFCC")
        textSize = 18f
        isFakeBoldText = true
        isAntiAlias = true
    }

    for (i in 1..maxTicks) {
        val r = i * tickStep
        if (r > visibleRange * 1.02f) break
        val isMajor = i % majorEvery == 0
        val radiusPx = r * scale
        // Evitar anillos que salen del canvas y cortan mal
        if (radiusPx > minOf(size.width, size.height) * 0.52f) continue

        drawCircle(
            if (isMajor) majorRing else minorRing,
            radiusPx,
            Offset(cx, cy),
            style = androidx.compose.ui.graphics.drawscope.Stroke(if (isMajor) 1.6f else 0.9f),
        )

        // Marcas tipo regla solo en ejes (más limpias)
        val tickLen = if (isMajor) 8f else 5f
        drawLine(tickColor.copy(alpha = if (isMajor) 0.9f else 0.45f), Offset(cx, cy - radiusPx), Offset(cx, cy - radiusPx + tickLen), strokeWidth = if (isMajor) 1.8f else 1f)
        drawLine(tickColor.copy(alpha = if (isMajor) 0.9f else 0.45f), Offset(cx + radiusPx - tickLen, cy), Offset(cx + radiusPx, cy), strokeWidth = if (isMajor) 1.8f else 1f)

        // Una sola etiqueta por anillo mayor (este) — evita saturación
        if (isMajor) {
            val label = formatRadarMeters(r)
            val tx = (cx + radiusPx + 4f).coerceAtMost(size.width - 36f)
            val ty = (cy - 3f).coerceIn(14f, size.height - 8f)
            drawContext.canvas.nativeCanvas.drawText(label, tx, ty, meterPaint)
        }
    }

    // Origin
    drawCircle(RadarCyan.copy(alpha = 0.35f), 4f, Offset(cx, cy))
    drawCircle(RadarCyan, 2f, Offset(cx, cy))

    drawRadarScaleBar2D(scale, visibleRange, tickStep)
}

/** Corner scale bar: visual 1-unit ruler matching real meters. */
private fun DrawScope.drawRadarScaleBar2D(
    scale: Float,
    visibleRangeM: Float,
    tickStepM: Float,
) {
    val barMeters = when {
        visibleRangeM <= 2f -> tickStepM.coerceAtLeast(0.25f)
        visibleRangeM <= 5f -> 1f
        visibleRangeM <= 10f -> 2f
        else -> 5f
    }
    val barWidth = (barMeters * scale).coerceIn(28f, size.width * 0.28f)
    val left = 10f
    val bottom = size.height - 10f
    val top = bottom - 7f
    val barColor = Color(0xFF00FFCC).copy(alpha = 0.8f)

    drawLine(barColor, Offset(left, bottom), Offset(left + barWidth, bottom), strokeWidth = 2f)
    drawLine(barColor, Offset(left, top), Offset(left, bottom), strokeWidth = 2f)
    drawLine(barColor, Offset(left + barWidth, top), Offset(left + barWidth, bottom), strokeWidth = 2f)

    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#00FFCC")
        textSize = 16f
        isFakeBoldText = true
        isAntiAlias = true
    }
    drawContext.canvas.nativeCanvas.drawText(formatRadarMeters(barMeters), left + 2f, top - 3f, paint)
}

/**
 * Radar PPI militar REDONDO: anillos verdaderos en pantalla (no elipses por perspectiva),
 * regla horizontal de metros y grados en el borde.
 */
fun DrawScope.drawDistanceRings3D(
    project: (Double, Double, Double) -> Offset,
    viewport: RadarViewport,
    labelPaint: android.graphics.Paint,
    rangeM: Float = RADAR_DEFAULT_RANGE_M,
) {
    val visibleRange = viewport.visibleRangeM(rangeM)
    val tickStep = radarTickStepM(visibleRange)
    val majorEvery = radarMajorEvery(tickStep)
    val maxTicks = ((visibleRange / tickStep).toInt() + 1).coerceIn(2, 20)

    // Escala en px/m a partir de proyección (círculos verdaderos → no óvalo)
    val origin = project(0.0, 0.0, 0.0)
    val east1 = project(1.0, 0.0, 0.0)
    val north1 = project(0.0, 1.0, 0.0)
    val pxPerM = (
        hypot(east1.x - origin.x, east1.y - origin.y) +
            hypot(north1.x - origin.x, north1.y - origin.y)
        ) / 2f
    if (pxPerM < 1f) return

    val outerR = (tickStep * maxTicks).coerceAtMost(visibleRange * 1.02f)
    val maxPx = minOf(size.width, size.height) * 0.46f

    val meterPaint = android.graphics.Paint(labelPaint).apply {
        color = android.graphics.Color.parseColor("#66FF99")
        textSize = 16f
        isFakeBoldText = true
        isAntiAlias = true
    }
    val degPaint = android.graphics.Paint(labelPaint).apply {
        color = android.graphics.Color.parseColor("#44AA66")
        textSize = 13f
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
    }
    val ringMajor = Color(0xFF2AFF6A).copy(alpha = 0.55f)
    val ringMinor = Color(0xFF1A7A3A).copy(alpha = 0.35f)
    val spokeColor = Color(0xFF1A5A2A).copy(alpha = 0.45f)
    val axisColor = Color(0xFF44FF88).copy(alpha = 0.75f)

    // Radios cada 30°
    for (deg in 0 until 360 step 30) {
        val rad = Math.toRadians(deg.toDouble())
        val len = (outerR * pxPerM).coerceAtMost(maxPx)
        val tip = Offset(
            origin.x + sin(rad).toFloat() * len,
            origin.y - cos(rad).toFloat() * len,
        )
        drawLine(spokeColor, origin, tip, strokeWidth = if (deg % 90 == 0) 1.8f else 1.0f)
    }

    for (i in 1..maxTicks) {
        val r = i * tickStep
        if (r > outerR * 1.01f) break
        val radiusPx = (r * pxPerM).coerceAtMost(maxPx)
        val isMajor = i % majorEvery == 0
        drawCircle(
            if (isMajor) ringMajor else ringMinor,
            radiusPx,
            origin,
            style = Stroke(if (isMajor) 1.8f else 1.0f),
        )
        if (isMajor) {
            val label = formatRadarMeters(r)
            val east = Offset(origin.x + radiusPx, origin.y)
            val west = Offset(origin.x - radiusPx, origin.y)
            val north = Offset(origin.x, origin.y - radiusPx)
            drawContext.canvas.nativeCanvas.drawText(label, east.x + 4f, east.y + 5f, meterPaint)
            val w = meterPaint.measureText(label)
            drawContext.canvas.nativeCanvas.drawText(label, west.x - w - 4f, west.y + 5f, meterPaint)
            drawLine(axisColor, Offset(east.x - 5f, east.y), Offset(east.x + 5f, east.y), strokeWidth = 2f)
            drawLine(axisColor, Offset(west.x - 5f, west.y), Offset(west.x + 5f, west.y), strokeWidth = 2f)
            drawLine(axisColor, Offset(north.x, north.y - 5f), Offset(north.x, north.y + 5f), strokeWidth = 2f)
        }
    }

    val labelR = (outerR * pxPerM * 1.06f).coerceAtMost(maxPx * 1.05f)
    for (deg in 0 until 360 step 30) {
        val rad = Math.toRadians(deg.toDouble())
        val p = Offset(
            origin.x + sin(rad).toFloat() * labelR,
            origin.y - cos(rad).toFloat() * labelR,
        )
        drawContext.canvas.nativeCanvas.drawText("$deg°", p.x, p.y + 4f, degPaint)
    }

    val axisLen = (outerR * pxPerM).coerceAtMost(maxPx)
    drawLine(axisColor.copy(alpha = 0.5f), Offset(origin.x - axisLen, origin.y), Offset(origin.x + axisLen, origin.y), strokeWidth = 1.6f)
    drawLine(axisColor.copy(alpha = 0.5f), Offset(origin.x, origin.y + axisLen), Offset(origin.x, origin.y - axisLen), strokeWidth = 1.6f)

    drawCircle(Color(0xFF00FF88).copy(alpha = 0.25f), 10f, origin)
    drawCircle(Color(0xFF00FF88), 4f, origin)
    drawLine(Color(0xFF00FF88), Offset(origin.x - 14f, origin.y), Offset(origin.x + 14f, origin.y), strokeWidth = 1.5f)
    drawLine(Color(0xFF00FF88), Offset(origin.x, origin.y - 14f), Offset(origin.x, origin.y + 14f), strokeWidth = 1.5f)
}

/**
 * Punto compacto de víctima en el radar (sin figura humana ni esfera grande).
 * Forma distinta por estado — no depende solo del color.
 */
fun DrawScope.drawVictimRadarDot(
    center: Offset,
    kind: VictimStatusKind,
    color: Color,
    selected: Boolean,
    label: String?,
    tagPaint: android.graphics.Paint?,
) {
    val r = if (selected) 11f else 8f
    // Halo de selección
    if (selected) {
        drawCircle(color.copy(alpha = 0.2f), r + 8f, center)
        drawCircle(color.copy(alpha = 0.7f), r + 5f, center, style = Stroke(width = 2f))
    }
    when (kind) {
        VictimStatusKind.DIG_HERE -> {
            drawCircle(color.copy(alpha = 0.25f), r + 4f, center)
            drawCircle(color, r, center, style = Stroke(width = 2.8f))
            drawLine(color, Offset(center.x - r, center.y), Offset(center.x + r, center.y), strokeWidth = 2.5f)
            drawLine(color, Offset(center.x, center.y - r), Offset(center.x, center.y + r), strokeWidth = 2.5f)
            drawCircle(color, 3f, center)
        }
        VictimStatusKind.HEART, VictimStatusKind.BREATHING -> {
            // Rombo compacto
            val path = Path().apply {
                moveTo(center.x, center.y - r)
                lineTo(center.x + r * 0.75f, center.y)
                lineTo(center.x, center.y + r)
                lineTo(center.x - r * 0.75f, center.y)
                close()
            }
            drawPath(path, color.copy(alpha = 0.35f))
            drawPath(path, color, style = Stroke(width = 2.5f))
            drawCircle(color, if (kind == VictimStatusKind.HEART) 3.5f else 2.5f, center)
        }
        VictimStatusKind.ACTIVITY -> {
            val path = Path().apply {
                moveTo(center.x, center.y - r)
                lineTo(center.x + r * 0.85f, center.y + r * 0.7f)
                lineTo(center.x - r * 0.85f, center.y + r * 0.7f)
                close()
            }
            drawPath(path, color.copy(alpha = 0.35f))
            drawPath(path, color, style = Stroke(width = 2.4f))
        }
        VictimStatusKind.WEAK -> {
            val half = r * 0.75f
            drawRect(
                color.copy(alpha = 0.3f),
                topLeft = Offset(center.x - half, center.y - half),
                size = androidx.compose.ui.geometry.Size(half * 2f, half * 2f),
            )
            drawRect(
                color,
                topLeft = Offset(center.x - half, center.y - half),
                size = androidx.compose.ui.geometry.Size(half * 2f, half * 2f),
                style = Stroke(width = 2.2f),
            )
            drawCircle(color, 2.5f, center)
        }
    }
    if (label != null && tagPaint != null) {
        drawContext.canvas.nativeCanvas.drawText(
            label,
            center.x - 10f,
            center.y - r - 6f,
            android.graphics.Paint(tagPaint).apply {
                textSize = tagPaint.textSize * 0.8f
                isFakeBoldText = true
            },
        )
    }
}

fun DrawScope.drawVictims2D(
    victims: List<TrappedVictim>,
    bearingDeg: Double,
    divisor: Float,
    viewport: RadarViewport,
    selectedVictimId: Int?,
    animPhase: Double,
    labelPaint: android.graphics.Paint,
    tagPaint: android.graphics.Paint,
    showLabels: Boolean = true,
    showGuideLines: Boolean = true,
    colorblind: Boolean = false,
) {
    val (cx, cy) = radarCenter(viewport)

    for (victim in victims) {
        val (rx, ry) = rotateMapCoords(victim.x, victim.y, bearingDeg)
        val scale = effectiveRadarScale(divisor, viewport)
        val tx = cx + rx.toFloat() * scale
        val ty = cy - ry.toFloat() * scale
        val selected = victim.id == selectedVictimId
        val kind = victimStatusKind(victim)
        val tint = victimColor(victim, colorblind)
        val center = Offset(tx, ty)

        // Solo punto compacto (sin figura humana)
        drawVictimRadarDot(
            center = center,
            kind = kind,
            color = tint,
            selected = selected,
            label = if (showLabels) victim.label else null,
            tagPaint = if (showLabels) tagPaint else null,
        )

        if (showGuideLines) {
            drawLine(
                tint.copy(alpha = 0.35f),
                Offset(cx, cy),
                center,
                strokeWidth = if (selected) 2.5f else 1.5f,
            )
        }
        if (showLabels) {
            val statusLabel = victimStatusIconLabel(kind)
            drawContext.canvas.nativeCanvas.drawText(
                statusLabel,
                tx - 22f,
                ty + 22f,
                android.graphics.Paint(labelPaint).apply {
                    textSize = 13f
                    isFakeBoldText = true
                    color = android.graphics.Color.WHITE
                },
            )
            if (selected) {
                drawContext.canvas.nativeCanvas.drawText(
                    "${"%.1f".format(victim.distance)} m · ↓${"%.1f".format(kotlin.math.abs(victim.depth))} m",
                    tx - 40f,
                    ty + 38f,
                    labelPaint,
                )
            }
        }
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

fun DrawScope.drawRubbleVolume3D(
    project: (Double, Double, Double) -> Offset,
    labelPaint: android.graphics.Paint,
) {
    val gridN = 5.0
    val maxDepth = -2.0

    // 1. Plano superior (Z = 0.0) - sutil, no tapa el PPI militar
    for (i in -5..5) {
        val a0 = project(i.toDouble(), -gridN, 0.0)
        val b0 = project(i.toDouble(), gridN, 0.0)
        drawLine(Color(0xFF1A3A1A).copy(alpha = 0.22f), a0, b0, strokeWidth = 1.0f)

        val c0 = project(-gridN, i.toDouble(), 0.0)
        val d0 = project(gridN, i.toDouble(), 0.0)
        drawLine(Color(0xFF1A3A1A).copy(alpha = 0.22f), c0, d0, strokeWidth = 1.0f)
    }

    // 2. Plano inferior (Z = -2.0) - azul/gris muy oscuro
    for (i in -5..5) {
        val a1 = project(i.toDouble(), -gridN, maxDepth)
        val b1 = project(i.toDouble(), gridN, maxDepth)
        drawLine(Color(0xFF112233).copy(alpha = 0.35f), a1, b1, strokeWidth = 0.8f)

        val c1 = project(-gridN, i.toDouble(), maxDepth)
        val d1 = project(gridN, i.toDouble(), maxDepth)
        drawLine(Color(0xFF112233).copy(alpha = 0.35f), c1, d1, strokeWidth = 0.8f)
    }

    // 3. Columnas de las esquinas
    val corners = listOf(
        Pair(-gridN, -gridN),
        Pair(gridN, -gridN),
        Pair(gridN, gridN),
        Pair(-gridN, gridN)
    )
    for (c in corners) {
        val top = project(c.first, c.second, 0.0)
        val bottom = project(c.first, c.second, maxDepth)
        drawLine(Color(0xFF334433).copy(alpha = 0.5f), top, bottom, strokeWidth = 2.0f)
    }

    // 4. Estratos / Capas horizontales de tierra
    val levels = listOf(-0.5, -1.0, -1.5)
    val c0 = corners[0]
    val c1 = corners[1]
    for (lvl in levels) {
        val ptA = project(c0.first, c0.second, lvl)
        val ptB = project(c1.first, c1.second, lvl)
        drawLine(Color(0xFF2A2A2A).copy(alpha = 0.25f), ptA, ptB, strokeWidth = 1.0f)

        drawContext.canvas.nativeCanvas.drawText(
            "Nivel ${"%.1f".format(-lvl)}m",
            ptA.x + 6f,
            ptA.y - 4f,
            android.graphics.Paint(labelPaint).apply {
                color = android.graphics.Color.parseColor("#445544")
                textSize = 20f
                isFakeBoldText = true
            }
        )
    }
}

fun DrawScope.drawVictims3D(
    victims: List<TrappedVictim>,
    bearingDeg: Double,
    selectedVictimId: Int?,
    animPhase: Double,
    project: (Double, Double, Double) -> Offset,
    origin: Offset,
    tagPaint: android.graphics.Paint,
    labelPaint: android.graphics.Paint,
    showLabels: Boolean = true,
    showGuideLines: Boolean = true,
    colorblind: Boolean = false,
    showDepthRays: Boolean = false,
) {
    for (victim in victims) {
        val selected = victim.id == selectedVictimId
        val kind = victimStatusKind(victim)
        val victimTint = victimColor(victim, colorblind)
        val atPointZero = victim.distance < 0.6

        val rel = describePersonPosition(victim.x, victim.y, victim.depth, bearingDeg)
        val (rx, ry) = rotateMapCoords(victim.x, victim.y, bearingDeg)
        val groundProj = project(rx, ry, 0.0)
        val absDepth = kotlin.math.abs(victim.depth)
        val depthColor = depthAccent(absDepth, atPointZero, colorblind)

        // Rayas de profundidad: solo si el usuario activa «Rayas»
        if (showDepthRays && !atPointZero) {
            val steps = 8
            for (i in 0 until steps step 2) {
                val zStart = 0.0 + (victim.depth * i) / steps
                val zEnd = 0.0 + (victim.depth * (i + 1)) / steps
                val ptStart = project(rx, ry, zStart)
                val ptEnd = project(rx, ry, zEnd)
                drawDepthPatternLine(
                    ptStart,
                    ptEnd,
                    absDepth,
                    depthColor,
                    strokeWidth = if (selected) 3.5f else 2.2f,
                )
            }
        }

        // Punto compacto (sin figura ni esfera)
        drawVictimRadarDot(
            center = groundProj,
            kind = kind,
            color = victimTint,
            selected = selected || atPointZero,
            label = if (showLabels) victim.label else null,
            tagPaint = if (showLabels) tagPaint else null,
        )

        if (atPointZero) {
            drawDigHereMarker3D(groundProj, absDepth, tagPaint, colorblind, compact = true)
        }

        if (showLabels) {
            val statusLabel = if (atPointZero) "⛏ EXCAVA AQUÍ" else victimStatusIconLabel(kind)
            drawContext.canvas.nativeCanvas.drawText(
                statusLabel,
                groundProj.x - 36f,
                groundProj.y + 28f,
                android.graphics.Paint(tagPaint).apply {
                    color = android.graphics.Color.WHITE
                    isFakeBoldText = true
                    textSize = 13f
                },
            )
            val info = if (atPointZero) {
                "↓ ${"%.1f".format(absDepth)} m — no camines más"
            } else {
                "${"%.1f".format(rel.distanceM)} m · ↓ ${"%.1f".format(absDepth)} m"
            }
            drawContext.canvas.nativeCanvas.drawText(
                info,
                groundProj.x - 48f,
                groundProj.y + 44f,
                android.graphics.Paint(labelPaint).apply {
                    color = android.graphics.Color.WHITE
                    isFakeBoldText = true
                    textSize = 13f
                },
            )
            if (selected && !atPointZero) {
                drawContext.canvas.nativeCanvas.drawText(
                    rel.horizontal,
                    groundProj.x - 40f,
                    groundProj.y - 22f,
                    android.graphics.Paint(labelPaint).apply {
                        color = android.graphics.Color.parseColor(if (colorblind) "#80C0FF" else "#66FF99")
                        isFakeBoldText = true
                        textSize = labelPaint.textSize * 0.9f
                    },
                )
            }
        }
    }
}

/** Corchetes de mira militar alrededor del objetivo en el suelo. */
private fun DrawScope.drawTargetBracket(center: Offset, size: Float, color: Color) {
    val half = size / 2f
    val arm = size * 0.35f
    val stroke = 2.4f
    // Esquina superior izq
    drawLine(color, Offset(center.x - half, center.y - half), Offset(center.x - half + arm, center.y - half), strokeWidth = stroke)
    drawLine(color, Offset(center.x - half, center.y - half), Offset(center.x - half, center.y - half + arm), strokeWidth = stroke)
    // Superior der
    drawLine(color, Offset(center.x + half, center.y - half), Offset(center.x + half - arm, center.y - half), strokeWidth = stroke)
    drawLine(color, Offset(center.x + half, center.y - half), Offset(center.x + half, center.y - half + arm), strokeWidth = stroke)
    // Inferior izq
    drawLine(color, Offset(center.x - half, center.y + half), Offset(center.x - half + arm, center.y + half), strokeWidth = stroke)
    drawLine(color, Offset(center.x - half, center.y + half), Offset(center.x - half, center.y + half - arm), strokeWidth = stroke)
    // Inferior der
    drawLine(color, Offset(center.x + half, center.y + half), Offset(center.x + half - arm, center.y + half), strokeWidth = stroke)
    drawLine(color, Offset(center.x + half, center.y + half), Offset(center.x + half, center.y + half - arm), strokeWidth = stroke)
}

/** Mira grande: la persona está debajo — excava aquí. */
private fun DrawScope.drawDigHereMarker3D(
    ground: Offset,
    depthM: Double,
    tagPaint: android.graphics.Paint,
    colorblind: Boolean = false,
    compact: Boolean = true,
) {
    val accent = accessibleAccent(VictimStatusKind.DIG_HERE, colorblind)
    val r = if (compact) 22f else 36f
    drawCircle(accent.copy(alpha = 0.15f), r + 8f, ground)
    drawCircle(accent.copy(alpha = 0.95f), r, ground, style = Stroke(width = 3f))
    drawVictimStatusIcon(ground, VictimStatusKind.DIG_HERE, accent, scale = if (compact) 1.0f else 1.35f)
    val paint = android.graphics.Paint(tagPaint).apply {
        color = if (colorblind) {
            android.graphics.Color.parseColor("#FFEE00")
        } else {
            android.graphics.Color.parseColor("#FF3366")
        }
        textSize = 14f
        isFakeBoldText = true
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
    }
    drawContext.canvas.nativeCanvas.drawText(
        "⛏ EXCAVA AQUÍ · ${"%.1f".format(depthM)} m ↓",
        ground.x,
        ground.y + r + 22f,
        paint,
    )
}

private fun victimJointsForLabel(victim: TrappedVictim, animPhase: Double): Triple<Double, Double, Double> {
    val bs = if (victim.isBreathing) {
        kotlin.math.sin(animPhase * (victim.respRate / 60.0).coerceIn(0.12, 0.45) * 2 * Math.PI)
    } else {
        0.0
    }
    val chest = 1.0 + 0.12 * bs
    val baseZ = victim.depth + 0.72
    return Triple(victim.x, victim.y, baseZ + 0.78 * chest)
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
    divisor: Float = RADAR_DEFAULT_RANGE_M,
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
