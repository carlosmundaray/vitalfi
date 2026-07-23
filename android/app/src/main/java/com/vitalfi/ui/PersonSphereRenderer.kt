package com.vitalfi.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.vitalfi.dsp.personSphereCenter
import com.vitalfi.dsp.TrappedVictim
import kotlin.math.cos
import kotlin.math.sin

private enum class SpherePlane { XY, XZ, YZ }

/**
 * Esfera 3D wireframe en la posición estimada de la persona (detección Wi-Fi).
 * Tres anillos (ecuador, meridiano X, meridiano Y) dan noción de profundidad y altura.
 */
fun DrawScope.drawPersonSphere3D(
    centerX: Double,
    centerY: Double,
    centerZ: Double,
    radiusM: Double,
    color: Color,
    selected: Boolean,
    project: (Double, Double, Double) -> Offset,
    showAxis: Boolean = false,
) {
    val strokeW = if (selected) 2.5f else 1.8f
    val glowAlpha = if (selected) 0.22f else 0.14f

    drawSphereRing(centerX, centerY, centerZ, radiusM, SpherePlane.XY, color, strokeW, project)
    drawSphereRing(centerX, centerY, centerZ, radiusM, SpherePlane.XZ, color.copy(alpha = 0.75f), strokeW * 0.85f, project)
    drawSphereRing(centerX, centerY, centerZ, radiusM, SpherePlane.YZ, color.copy(alpha = 0.75f), strokeW * 0.85f, project)

    val center = project(centerX, centerY, centerZ)
    drawCircle(color.copy(alpha = glowAlpha), radius = if (selected) 28f else 20f, center = center)
    drawCircle(color, radius = if (selected) 7f else 5f, center = center)

    // Eje vertical solo si el usuario activa capa «Rayas» (evita la raya que tapa el centro)
    if (showAxis) {
        val top = project(centerX, centerY, centerZ + radiusM)
        val bottom = project(centerX, centerY, centerZ - radiusM)
        drawLine(color.copy(alpha = 0.35f), center, top, strokeWidth = 1.2f)
        drawLine(color.copy(alpha = 0.35f), center, bottom, strokeWidth = 1.2f)
    }
}

fun DrawScope.drawPersonSphere3D(
    victim: TrappedVictim,
    selected: Boolean,
    project: (Double, Double, Double) -> Offset,
    color: Color = victimColor(victim),
    radiusM: Double = 0.38,
    showAxis: Boolean = false,
) {
    val (cx, cy, cz) = personSphereCenter(victim)
    drawPersonSphere3D(cx, cy, cz, radiusM, color, selected, project, showAxis = showAxis)
}

private fun DrawScope.drawSphereRing(
    cx: Double,
    cy: Double,
    cz: Double,
    radius: Double,
    plane: SpherePlane,
    color: Color,
    strokeWidth: Float,
    project: (Double, Double, Double) -> Offset,
) {
    val segments = 36
    val path = Path()
    for (i in 0..segments) {
        val t = i / segments.toDouble() * 2 * Math.PI
        val (x, y, z) = when (plane) {
            SpherePlane.XY -> Triple(cx + radius * cos(t), cy + radius * sin(t), cz)
            SpherePlane.XZ -> Triple(cx + radius * cos(t), cy, cz + radius * sin(t))
            SpherePlane.YZ -> Triple(cx, cy + radius * cos(t), cz + radius * sin(t))
        }
        val p = project(x, y, z)
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    path.close()
    drawPath(path, color, style = Stroke(strokeWidth))
}
