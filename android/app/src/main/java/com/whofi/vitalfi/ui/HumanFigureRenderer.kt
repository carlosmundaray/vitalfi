package com.whofi.vitalfi.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.whofi.vitalfi.dsp.TrappedVictim
import kotlin.math.cos
import kotlin.math.sin

/** Esqueleto y silueta de carne estilizada inspirada en Person-in-WiFi-3D para marcar posición estimada. */
internal object HumanFigureRenderer {

    private data class Joint(val x: Double, val y: Double, val z: Double)

    private val bones = listOf(
        "head" to "neck",
        "neck" to "chest",
        "chest" to "pelvis",
        "chest" to "l_shoulder",
        "l_shoulder" to "l_elbow",
        "l_elbow" to "l_hand",
        "chest" to "r_shoulder",
        "r_shoulder" to "r_elbow",
        "r_elbow" to "r_hand",
        "pelvis" to "l_hip",
        "l_hip" to "l_knee",
        "l_knee" to "l_foot",
        "pelvis" to "r_hip",
        "r_hip" to "r_knee",
        "r_knee" to "r_foot",
    )

    private fun skeletonJoints(breatheScale: Double): Map<String, Joint> {
        val chest = 1.0 + 0.12 * breatheScale
        return mapOf(
            "pelvis" to Joint(0.0, 0.0, 0.0),
            "chest" to Joint(0.0, 0.0, 0.42 * chest),
            "neck" to Joint(0.0, 0.0, 0.58 * chest),
            "head" to Joint(0.0, 0.0, 0.78),
            "l_shoulder" to Joint(-0.20 * chest, 0.0, 0.52 * chest),
            "r_shoulder" to Joint(0.20 * chest, 0.0, 0.52 * chest),
            "l_elbow" to Joint(-0.32, 0.0, 0.32),
            "r_elbow" to Joint(0.32, 0.0, 0.32),
            "l_hand" to Joint(-0.38, 0.0, 0.12),
            "r_hand" to Joint(0.38, 0.0, 0.12),
            "l_hip" to Joint(-0.12, 0.0, -0.02),
            "r_hip" to Joint(0.12, 0.0, -0.02),
            "l_knee" to Joint(-0.12, 0.0, -0.38),
            "r_knee" to Joint(0.12, 0.0, -0.38),
            "l_foot" to Joint(-0.12, 0.06, -0.72),
            "r_foot" to Joint(0.12, 0.06, -0.72),
        )
    }

    private fun breatheScale(victim: TrappedVictim, animPhase: Double): Double {
        if (!victim.isBreathing) return 0.0
        val freq = (victim.respRate / 60.0).coerceIn(0.12, 0.45)
        return sin(animPhase * freq * 2 * Math.PI)
    }

    private fun victimJoints(victim: TrappedVictim, animPhase: Double): Map<String, Triple<Double, Double, Double>> {
        val bs = breatheScale(victim, animPhase)
        val sk = skeletonJoints(bs)
        val baseZ = victim.depth + 0.72
        return sk.mapValues { (_, j) ->
            Triple(victim.x + j.x, victim.y + j.y, baseZ + j.z)
        }
    }

    fun DrawScope.drawHumanFigure3D(
        victim: TrappedVictim,
        bearingDeg: Double,
        selected: Boolean,
        animPhase: Double,
        project: (Double, Double, Double) -> Offset,
        color: Color,
    ) {
        val joints = victimJoints(victim, animPhase)
        val strokeW = if (selected) 3.5f else 2.5f
        val alpha = if (selected) 1f else 0.85f
        val figureColor = color.copy(alpha = alpha)
        
        // Silueta/Flesh con menor opacidad y grosor amplio
        val fleshColor = color.copy(alpha = if (selected) 0.22f else 0.15f)

        val pelvis = joints["pelvis"]!!
        val (prx, pry) = rotateMapCoords(pelvis.first, pelvis.second, bearingDeg)
        val footCenter = project(prx, pry, pelvis.third)
        drawCircle(color.copy(alpha = 0.15f), if (selected) 22f else 16f, footCenter)
        drawCircle(color.copy(alpha = 0.35f), if (selected) 10f else 7f, footCenter)

        val neckJ = joints["neck"]!!
        val pelvisJ = joints["pelvis"]!!
        val (nx, ny) = rotateMapCoords(neckJ.first, neckJ.second, bearingDeg)
        val (px, py) = rotateMapCoords(pelvisJ.first, pelvisJ.second, bearingDeg)
        val neckPt = project(nx, ny, neckJ.third)
        val pelvisPt = project(px, py, pelvisJ.third)

        // 1. Dibujar silueta de carne translúcida (Torso + Extremidades)
        drawLine(
            color = fleshColor,
            start = neckPt,
            end = pelvisPt,
            strokeWidth = 26f,
            cap = StrokeCap.Round
        )

        for ((a, b) in bones) {
            if (a == "head" || a == "neck" || a == "chest") continue
            val ja = joints[a] ?: continue
            val jb = joints[b] ?: continue
            val (ax, ay) = rotateMapCoords(ja.first, ja.second, bearingDeg)
            val (bx, by) = rotateMapCoords(jb.first, jb.second, bearingDeg)
            val pa = project(ax, ay, ja.third)
            val pb = project(bx, by, jb.third)
            drawLine(
                color = fleshColor,
                start = pa,
                end = pb,
                strokeWidth = 16f,
                cap = StrokeCap.Round
            )
        }

        // Cabeza rellena silueta
        joints["head"]?.let { h ->
            val (hx, hy) = rotateMapCoords(h.first, h.second, bearingDeg)
            val hp = project(hx, hy, h.third)
            drawCircle(fleshColor, 15f, hp)
        }

        // 2. Dibujar líneas de esqueleto óseo encima
        for ((a, b) in bones) {
            val ja = joints[a] ?: continue
            val jb = joints[b] ?: continue
            val (ax, ay) = rotateMapCoords(ja.first, ja.second, bearingDeg)
            val (bx, by) = rotateMapCoords(jb.first, jb.second, bearingDeg)
            val pa = project(ax, ay, ja.third)
            val pb = project(bx, by, jb.third)
            drawLine(figureColor.copy(alpha = 0.25f), pa, pb, strokeWidth = strokeW + 2f)
            drawLine(figureColor, pa, pb, strokeWidth = strokeW)
        }

        joints["head"]?.let { h ->
            val (hx, hy) = rotateMapCoords(h.first, h.second, bearingDeg)
            val hp = project(hx, hy, h.third)
            drawCircle(figureColor, if (selected) 9f else 7f, hp)
            if (victim.isBreathing) {
                drawCircle(
                    RadarGreen.copy(alpha = 0.25f + 0.15f * (0.5 + 0.5 * breatheScale(victim, animPhase)).toFloat()),
                    if (selected) 20f else 15f,
                    hp,
                )
            }
        }
    }

    fun DrawScope.drawHumanFigure2D(
        victim: TrappedVictim,
        bearingDeg: Double,
        divisor: Float,
        viewport: RadarViewport,
        selected: Boolean,
        animPhase: Double,
        color: Color,
    ) {
        val (cx, cy) = radarCenter(viewport)
        val scale = effectiveRadarScale(divisor, viewport)
        val joints = victimJoints(victim, animPhase)
        val strokeW = if (selected) 3f else 2f
        val figureColor = color.copy(alpha = if (selected) 1f else 0.88f)
        
        // Silueta/Flesh con menor opacidad y grosor amplio
        val fleshColor = color.copy(alpha = if (selected) 0.25f else 0.18f)

        fun project2D(world: Triple<Double, Double, Double>): Offset {
            val (rx, ry) = rotateMapCoords(world.first, world.second, bearingDeg)
            return Offset(cx + rx.toFloat() * scale, cy - ry.toFloat() * scale)
        }

        val pelvis = project2D(joints["pelvis"]!!)
        val neck = project2D(joints["neck"]!!)
        val head = project2D(joints["head"]!!)

        // 1. Dibujar silueta de carne translúcida (Torso + Extremidades)
        drawLine(
            color = fleshColor,
            start = neck,
            end = pelvis,
            strokeWidth = 24f,
            cap = StrokeCap.Round
        )

        for ((a, b) in bones) {
            if (a == "head" || a == "neck" || a == "chest") continue
            val ja = joints[a] ?: continue
            val jb = joints[b] ?: continue
            val pa = project2D(ja)
            val pb = project2D(jb)
            drawLine(
                color = fleshColor,
                start = pa,
                end = pb,
                strokeWidth = 14f,
                cap = StrokeCap.Round
            )
        }

        // Cabeza rellena silueta
        drawCircle(fleshColor, 14f, head)

        // 2. Dibujar líneas de esqueleto óseo encima
        for ((a, b) in bones) {
            val ja = joints[a] ?: continue
            val jb = joints[b] ?: continue
            val pa = project2D(ja)
            val pb = project2D(jb)
            drawLine(figureColor.copy(alpha = 0.3f), pa, pb, strokeWidth = strokeW + 1.5f)
            drawLine(figureColor, pa, pb, strokeWidth = strokeW)
        }

        // Centro cabeza
        drawCircle(figureColor, if (selected) 8f else 6f, head)

        if (victim.isBreathing) {
            val pulse = (0.5 + 0.5 * breatheScale(victim, animPhase)).toFloat()
            drawCircle(RadarGreen.copy(alpha = 0.2f + 0.2f * pulse), 18f, head)
        }

        if (selected) {
            drawCircle(
                RadarCyan,
                32f,
                pelvis,
                style = Stroke(2f),
            )
        }
    }
}
