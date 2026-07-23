package com.vitalfi.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import com.vitalfi.dsp.TrappedVictim
import kotlin.math.cos
import kotlin.math.sin

/**
 * Accesibilidad profesional: iconos + formas (no solo color).
 * Modo daltónico usa azul / ámbar / blanco / negro (seguro para deuteranopía/protanopía).
 */
enum class VictimStatusKind {
    /** Señal horizontal muy cerca — NO implica profundidad ni orden de cavar. */
    NEARBY,
    BREATHING,
    HEART,
    ACTIVITY,
    WEAK,
}

fun victimStatusKind(victim: TrappedVictim): VictimStatusKind = when {
    victim.distance < 0.6 -> VictimStatusKind.NEARBY
    victim.isBreathing && victim.heartBeating -> VictimStatusKind.HEART
    victim.isBreathing -> VictimStatusKind.BREATHING
    victim.isActivity -> VictimStatusKind.ACTIVITY
    else -> VictimStatusKind.WEAK
}

/** Paleta CVD-safe o normal. */
fun accessibleAccent(
    kind: VictimStatusKind,
    colorblind: Boolean,
): Color = if (colorblind) {
    when (kind) {
        VictimStatusKind.NEARBY -> Color(0xFFFFEE00) // amarillo intenso
        VictimStatusKind.HEART -> Color(0xFF4DA6FF)    // azul
        VictimStatusKind.BREATHING -> Color(0xFF80C0FF) // azul claro
        VictimStatusKind.ACTIVITY -> Color(0xFFFFAA00)  // ámbar
        VictimStatusKind.WEAK -> Color(0xFFEEEEEE)      // blanco
    }
} else {
    when (kind) {
        VictimStatusKind.NEARBY -> Color(0xFFFF3366)
        VictimStatusKind.HEART -> RadarRed
        VictimStatusKind.BREATHING -> RadarRed.copy(alpha = 0.85f)
        VictimStatusKind.ACTIVITY -> RadarOrange
        VictimStatusKind.WEAK -> RadarOrange.copy(alpha = 0.7f)
    }
}

fun victimColor(victim: TrappedVictim, colorblind: Boolean = false): Color =
    accessibleAccent(victimStatusKind(victim), colorblind)

fun guideAlignedColor(colorblind: Boolean): Color =
    if (colorblind) Color(0xFF4DA6FF) else RadarGreen

fun guideTurnColor(colorblind: Boolean): Color =
    if (colorblind) Color(0xFFFFAA00) else RadarOrange

fun guideForwardColor(colorblind: Boolean): Color =
    if (colorblind) Color(0xFFEEEEEE) else RadarCyan

fun depthAccent(depthM: Double, atPointZero: Boolean, colorblind: Boolean): Color = when {
    atPointZero -> accessibleAccent(VictimStatusKind.NEARBY, colorblind)
    depthM < 0.6 -> if (colorblind) Color(0xFF80C0FF) else Color(0xFF00FFCC)
    depthM <= 1.2 -> if (colorblind) Color(0xFFFFAA00) else Color(0xFFFF9900)
    else -> if (colorblind) Color(0xFFFFEE00) else Color(0xFFFF3366)
}

/** Texto corto + icono para etiquetas (siempre legible sin color). */
fun victimStatusIconLabel(kind: VictimStatusKind): String = when (kind) {
    VictimStatusKind.NEARBY -> "◎ CERCA"
    VictimStatusKind.HEART -> "♥ VIVA"
    VictimStatusKind.BREATHING -> "~ RESPIRA"
    VictimStatusKind.ACTIVITY -> "⚡ MOV"
    VictimStatusKind.WEAK -> "? SEÑAL"
}

/**
 * Dibuja un icono de estado encima del objetivo.
 * Formas: mira (cerca), rombo (viva), onda (respira), rayo (mov), interrogación.
 * No ordena cavar: la profundidad Wi‑Fi no está confirmada.
 */
fun DrawScope.drawVictimStatusIcon(
    center: Offset,
    kind: VictimStatusKind,
    color: Color,
    scale: Float = 1f,
) {
    val s = 14f * scale
    when (kind) {
        VictimStatusKind.NEARBY -> {
            // Mira / punto de interés (no pico de excavación)
            drawCircle(color.copy(alpha = 0.2f), s * 1.4f, center)
            drawCircle(color, s * 1.15f, center, style = Stroke(width = 2.5f * scale))
            drawCircle(color, s * 0.35f, center)
            drawLine(color, Offset(center.x - s, center.y), Offset(center.x - s * 0.45f, center.y), strokeWidth = 2.5f * scale)
            drawLine(color, Offset(center.x + s * 0.45f, center.y), Offset(center.x + s, center.y), strokeWidth = 2.5f * scale)
            drawLine(color, Offset(center.x, center.y - s), Offset(center.x, center.y - s * 0.45f), strokeWidth = 2.5f * scale)
            drawLine(color, Offset(center.x, center.y + s * 0.45f), Offset(center.x, center.y + s), strokeWidth = 2.5f * scale)
        }
        VictimStatusKind.HEART, VictimStatusKind.BREATHING -> {
            // Rombo = persona / vida (forma clara, no depende del color)
            val path = Path().apply {
                moveTo(center.x, center.y - s)
                lineTo(center.x + s * 0.75f, center.y)
                lineTo(center.x, center.y + s)
                lineTo(center.x - s * 0.75f, center.y)
                close()
            }
            drawPath(path, color.copy(alpha = 0.25f))
            drawPath(path, color, style = Stroke(width = 2.8f * scale))
            if (kind == VictimStatusKind.HEART) {
                drawCircle(color, 3.5f * scale, center)
            } else {
                // Ondas = respiración
                drawLine(
                    color,
                    Offset(center.x - s * 0.9f, center.y),
                    Offset(center.x - s * 0.4f, center.y - s * 0.35f),
                    strokeWidth = 2f * scale,
                )
                drawLine(
                    color,
                    Offset(center.x - s * 0.4f, center.y - s * 0.35f),
                    Offset(center.x + s * 0.4f, center.y + s * 0.35f),
                    strokeWidth = 2f * scale,
                )
                drawLine(
                    color,
                    Offset(center.x + s * 0.4f, center.y + s * 0.35f),
                    Offset(center.x + s * 0.9f, center.y),
                    strokeWidth = 2f * scale,
                )
            }
        }
        VictimStatusKind.ACTIVITY -> {
            // Triángulo = movimiento
            val path = Path().apply {
                moveTo(center.x, center.y - s)
                lineTo(center.x + s * 0.85f, center.y + s * 0.7f)
                lineTo(center.x - s * 0.85f, center.y + s * 0.7f)
                close()
            }
            drawPath(path, color.copy(alpha = 0.3f))
            drawPath(path, color, style = Stroke(width = 2.5f * scale))
        }
        VictimStatusKind.WEAK -> {
            // Cuadrado con ? = señal débil
            val half = s * 0.75f
            drawRect(
                color.copy(alpha = 0.2f),
                topLeft = Offset(center.x - half, center.y - half),
                size = androidx.compose.ui.geometry.Size(half * 2f, half * 2f),
            )
            drawRect(
                color,
                topLeft = Offset(center.x - half, center.y - half),
                size = androidx.compose.ui.geometry.Size(half * 2f, half * 2f),
                style = Stroke(width = 2.2f * scale),
            )
            val paint = android.graphics.Paint().apply {
                this.color = android.graphics.Color.WHITE
                textSize = 16f * scale
                isFakeBoldText = true
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }
            drawContext.canvas.nativeCanvas.drawText("?", center.x, center.y + 5f * scale, paint)
        }
    }
}

/** Chevrón IZQ / DER con etiqueta de texto (accesible sin color). */
fun DrawScope.drawAccessibleTurnIcon(
    toRight: Boolean,
    color: Color,
    strength: Float,
) {
    val x = if (toRight) size.width - 36f else 36f
    val y = size.height * 0.48f
    val dir = if (toRight) 1f else -1f
    for (i in 0 until 2) {
        val ox = x - dir * i * 18f
        val path = Path().apply {
            moveTo(ox, y - 24f)
            lineTo(ox + dir * 18f, y)
            lineTo(ox, y + 24f)
            lineTo(ox - dir * 7f, y + 24f)
            lineTo(ox + dir * 11f, y)
            lineTo(ox - dir * 7f, y - 24f)
            close()
        }
        drawPath(path, color.copy(alpha = (0.55f + 0.4f * strength) * (1f - i * 0.35f)))
    }
    val paint = android.graphics.Paint().apply {
        this.color = android.graphics.Color.WHITE
        textSize = 14f
        isFakeBoldText = true
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }
    val label = if (toRight) "DER →" else "← IZQ"
    drawContext.canvas.nativeCanvas.drawText(label, x, y + 48f, paint)
}

/** Patrón de profundidad: sólido / rayado / punteado (no solo color). */
fun DrawScope.drawDepthPatternLine(
    start: Offset,
    end: Offset,
    depthM: Double,
    color: Color,
    strokeWidth: Float,
) {
    when {
        depthM < 0.6 -> drawLine(color, start, end, strokeWidth = strokeWidth)
        depthM <= 1.2 -> {
            // Rayado: segmentos cortos
            val dx = end.x - start.x
            val dy = end.y - start.y
            val steps = 6
            for (i in 0 until steps step 2) {
                val t0 = i / steps.toFloat()
                val t1 = ((i + 1) / steps.toFloat()).coerceAtMost(1f)
                drawLine(
                    color,
                    Offset(start.x + dx * t0, start.y + dy * t0),
                    Offset(start.x + dx * t1, start.y + dy * t1),
                    strokeWidth = strokeWidth,
                )
            }
        }
        else -> {
            // Puntos: profundidad mayor
            val dx = end.x - start.x
            val dy = end.y - start.y
            for (i in 0..8) {
                val t = i / 8f
                drawCircle(color, strokeWidth * 0.7f, Offset(start.x + dx * t, start.y + dy * t))
            }
        }
    }
}

/** Flecha de dirección accesible. */
fun DrawScope.drawDirectionArrowIcon(
    tip: Offset,
    angleRad: Double,
    color: Color,
    size: Float = 14f,
) {
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
