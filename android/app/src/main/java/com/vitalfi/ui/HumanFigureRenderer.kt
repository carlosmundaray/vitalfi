package com.vitalfi.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.vitalfi.dsp.TrappedVictim
import kotlin.math.cos
import kotlin.math.sin

/**
 * Motor de Renderizado 3D de Figura Humana y Esqueleto Articulado
 * Inspirado en el proyecto Person-in-WiFi 3D (CVPR 2024 / IEEE TPAMI 2026).
 *
 * Proyecta en tiempo real:
 *  - 17 articulaciones anatómicas (COCO / SMPL Keypoints)
 *  - Huesos y enlaces esqueléticos con efecto volumétrico (Glow Mesh)
 *  - Expansión/contracción torácica sincronizada con la respiración (RPM)
 *  - Cinemática de extremidades para personas de pie, sentadas, tumbadas o en movimiento
 *  - Núcleo vital cardíaco pulsante
 */
internal object HumanFigureRenderer {

    data class Joint3D(var x: Double, var y: Double, var z: Double)

    enum class Posture3D {
        STANDING,
        SITTING,
        LYING_DOWN,
        COLLAPSED_FALLEN
    }

    // ═══════════════════════════════════════════════════════════════
    // Mapeo COCO-17 → VitalFi bone names (para Pose3D On-Device)
    // ═══════════════════════════════════════════════════════════════
    private val COCO_TO_BONE = mapOf(
        0 to "head",        // nose → head
        5 to "l_shoulder",
        6 to "r_shoulder",
        7 to "l_elbow",
        8 to "r_elbow",
        9 to "l_hand",
        10 to "r_hand",
        11 to "l_hip",
        12 to "r_hip",
        13 to "l_knee",
        14 to "r_knee",
        15 to "l_foot",
        16 to "r_foot",
    )

    /**
     * Convierte los 17 keypoints 3D reales del modelo neural Pose3D
     * al formato de joints que usa el renderizador de esqueleto.
     *
     * Los joints de cuello, pecho y pelvis se infieren como puntos medios
     * de los hombros y caderas respectivamente.
     *
     * @param kp FloatArray de 51 valores (17 × 3: x, y, z por joint)
     * @return Map de nombre de hueso → Joint3D con coordenadas reales
     */
    fun computeJointsFromPose3D(kp: FloatArray): Map<String, Joint3D> {
        if (kp.size < 51) return emptyMap()

        val joints = mutableMapOf<String, Joint3D>()

        // Mapear directamente los 13 joints COCO que tienen equivalente
        COCO_TO_BONE.forEach { (idx, name) ->
            val base = idx * 3
            joints[name] = Joint3D(
                kp[base].toDouble(),
                kp[base + 1].toDouble(),
                kp[base + 2].toDouble(),
            )
        }

        // Inferir joints intermedios desde los existentes
        val lShoulder = joints["l_shoulder"]!!
        val rShoulder = joints["r_shoulder"]!!
        val lHip = joints["l_hip"]!!
        val rHip = joints["r_hip"]!!

        // Cuello = punto medio de hombros, ligeramente arriba
        joints["neck"] = Joint3D(
            (lShoulder.x + rShoulder.x) / 2.0,
            (lShoulder.y + rShoulder.y) / 2.0,
            (lShoulder.z + rShoulder.z) / 2.0 + 0.06,
        )

        // Pecho = punto medio de hombros, ligeramente abajo
        joints["chest"] = Joint3D(
            (lShoulder.x + rShoulder.x) / 2.0,
            (lShoulder.y + rShoulder.y) / 2.0,
            (lShoulder.z + rShoulder.z) / 2.0 - 0.10,
        )

        // Pelvis = punto medio de caderas
        joints["pelvis"] = Joint3D(
            (lHip.x + rHip.x) / 2.0,
            (lHip.y + rHip.y) / 2.0,
            (lHip.z + rHip.z) / 2.0,
        )

        return joints
    }

    /**
     * Valida que un conjunto de articulaciones 3D tenga proporciones anatómicas humanas reales
     * antes de ser renderizado. Evita deformaciones visuales si los pesos del modelo neural
     * aún no han sido calibrados en el entorno.
     */
    fun isAnatomicallyValid(joints: Map<String, Joint3D>): Boolean {
        if (joints.size < 13) return false
        val head = joints["head"] ?: return false
        val pelvis = joints["pelvis"] ?: return false
        val lShoulder = joints["l_shoulder"] ?: return false
        val rShoulder = joints["r_shoulder"] ?: return false

        // Distancia razonable entre hombros (15cm a 70cm)
        val shoulderWidth = kotlin.math.hypot(lShoulder.x - rShoulder.x, lShoulder.y - rShoulder.y)
        if (shoulderWidth !in 0.15..0.70) return false

        // Longitud del torso (40cm a 130cm)
        val torsoLen = kotlin.math.abs(head.z - pelvis.z)
        if (torsoLen !in 0.35..1.35) return false

        // Ningún nodo fuera del espacio físico del cuerpo (<= 1.8m del centro)
        for ((_, j) in joints) {
            val distFromCenter = kotlin.math.sqrt(j.x * j.x + j.y * j.y + j.z * j.z)
            if (distFromCenter > 1.85 || j.x.isNaN() || j.y.isNaN() || j.z.isNaN()) return false
        }
        return true
    }

    val bones = listOf(
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

    /**
     * Genera las articulaciones 3D en función de la postura, la respiración, el movimiento y la elevación de brazos.
     */
    fun computeJoints(
        posture: Posture3D,
        breatheScale: Double,
        walkPhase: Double = 0.0,
        isMoving: Boolean = false,
        rArmLift: Float = 0.0f,
        lArmLift: Float = 0.0f,
        isWaving: Boolean = false,
    ): Map<String, Joint3D> {
        val chestExpand = 1.0 + 0.15 * breatheScale

        // Cinemática de Marcha Cruzada Realista (Natural Cross-Gait Biomechanics)
        val lArmSwing = if (isMoving) sin(walkPhase) * 0.35 else 0.0
        val rArmSwing = if (isMoving) -sin(walkPhase) * 0.35 else 0.0

        val lLegSwing = if (isMoving) -sin(walkPhase) * 0.38 else 0.0
        val rLegSwing = if (isMoving) sin(walkPhase) * 0.38 else 0.0

        // Flexión biomecánica de rodilla (se dobla hacia atrás al levantar el pie)
        val lKneeBend = if (isMoving) (kotlin.math.max(0.0, -lLegSwing) * 0.22) else 0.0
        val rKneeBend = if (isMoving) (kotlin.math.max(0.0, -rLegSwing) * 0.22) else 0.0

        // Flexión de codo al oscilar el brazo
        val lElbowBend = if (isMoving) (0.06 + kotlin.math.max(0.0, lArmSwing) * 0.18) else 0.0
        val rElbowBend = if (isMoving) (0.06 + kotlin.math.max(0.0, rArmSwing) * 0.18) else 0.0

        // Inclinación y balanceo sutil de pelvis al caminar
        val pelvicSway = if (isMoving) sin(walkPhase) * 0.03 else 0.0

        // Elevación de Brazos 3D (Levantar la mano por Wi-Fi)
        val waveWobbleR = if (isWaving || rArmLift > 0.6f) sin(walkPhase * 2.5) * 0.08 else 0.0
        val waveWobbleL = if (isWaving || lArmLift > 0.6f) cos(walkPhase * 2.5) * 0.08 else 0.0

        val rElbowZ = if (rArmLift > 0.2f) (0.30 + rArmLift * 0.38) else (0.30 + rElbowBend)
        val rElbowX = if (rArmLift > 0.2f) (0.28 + rArmLift * 0.08) else 0.28
        val rHandZ = if (rArmLift > 0.2f) (0.06 + rArmLift * 0.90) else (0.06 + rElbowBend * 1.6)
        val rHandX = if (rArmLift > 0.2f) (0.32 + waveWobbleR) else 0.32

        val lElbowZ = if (lArmLift > 0.2f) (0.30 + lArmLift * 0.38) else (0.30 + lElbowBend)
        val lElbowX = if (lArmLift > 0.2f) (-0.28 - lArmLift * 0.08) else -0.28
        val lHandZ = if (lArmLift > 0.2f) (0.06 + lArmLift * 0.90) else (0.06 + lElbowBend * 1.6)
        val lHandX = if (lArmLift > 0.2f) (-0.32 + waveWobbleL) else -0.32

        return when (posture) {
            Posture3D.STANDING -> mapOf(
                "head" to Joint3D(pelvicSway * 0.5, 0.0, 0.78),
                "neck" to Joint3D(pelvicSway * 0.4, 0.0, 0.58 * chestExpand),
                "chest" to Joint3D(pelvicSway * 0.3, 0.0, 0.42 * chestExpand),
                "pelvis" to Joint3D(pelvicSway, 0.0, 0.0),
                "l_shoulder" to Joint3D(-0.20 * chestExpand, 0.0, 0.52 * chestExpand),
                "r_shoulder" to Joint3D(0.20 * chestExpand, 0.0, 0.52 * chestExpand),
                // Brazos con soporte para elevar la mano y saludar
                "l_elbow" to Joint3D(lElbowX, lArmSwing * 0.8, lElbowZ),
                "r_elbow" to Joint3D(rElbowX, rArmSwing * 0.8, rElbowZ),
                "l_hand" to Joint3D(lHandX, lArmSwing * 1.5, lHandZ),
                "r_hand" to Joint3D(rHandX, rArmSwing * 1.5, rHandZ),
                "l_hip" to Joint3D(-0.12, 0.0, -0.04),
                "r_hip" to Joint3D(0.12, 0.0, -0.04),
                // Piernas con zancada y flexión de rodilla al dar el paso
                "l_knee" to Joint3D(-0.12, lLegSwing * 0.7, -0.38 + lKneeBend),
                "r_knee" to Joint3D(0.12, rLegSwing * 0.7, -0.38 + rKneeBend),
                "l_foot" to Joint3D(-0.12, lLegSwing * 1.3, -0.72 + lKneeBend * 0.6),
                "r_foot" to Joint3D(0.12, rLegSwing * 1.3, -0.72 + rKneeBend * 0.6),
            )
            Posture3D.SITTING -> mapOf(
                "head" to Joint3D(0.0, 0.0, 0.42),
                "neck" to Joint3D(0.0, 0.0, 0.26 * chestExpand),
                "chest" to Joint3D(0.0, 0.0, 0.12 * chestExpand),
                "pelvis" to Joint3D(0.0, 0.0, -0.22),
                "l_shoulder" to Joint3D(-0.19 * chestExpand, 0.0, 0.20 * chestExpand),
                "r_shoulder" to Joint3D(0.19 * chestExpand, 0.0, 0.20 * chestExpand),
                "l_elbow" to Joint3D(-0.25, 0.12, 0.02),
                "r_elbow" to Joint3D(0.25, 0.12, 0.02),
                "l_hand" to Joint3D(-0.22, 0.24, -0.15),
                "r_hand" to Joint3D(0.22, 0.24, -0.15),
                "l_hip" to Joint3D(-0.12, 0.0, -0.22),
                "r_hip" to Joint3D(0.12, 0.0, -0.22),
                "l_knee" to Joint3D(-0.12, 0.32, -0.22),
                "r_knee" to Joint3D(0.12, 0.32, -0.22),
                "l_foot" to Joint3D(-0.12, 0.34, -0.62),
                "r_foot" to Joint3D(0.12, 0.34, -0.62),
            )
            Posture3D.LYING_DOWN -> {
                // Tumbado boca arriba o en escombros (Eje Z a lo largo del suelo)
                val respZ = 0.08 * breatheScale
                mapOf(
                    "head" to Joint3D(0.0, -0.72, 0.10),
                    "neck" to Joint3D(0.0, -0.52, 0.08),
                    "chest" to Joint3D(0.0, -0.34, 0.10 + respZ),
                    "pelvis" to Joint3D(0.0, 0.05, 0.08),
                    "l_shoulder" to Joint3D(-0.20, -0.46, 0.08 + respZ),
                    "r_shoulder" to Joint3D(0.20, -0.46, 0.08 + respZ),
                    "l_elbow" to Joint3D(-0.28, -0.25, 0.06),
                    "r_elbow" to Joint3D(0.28, -0.25, 0.06),
                    "l_hand" to Joint3D(-0.24, -0.05, 0.08),
                    "r_hand" to Joint3D(0.24, -0.05, 0.08),
                    "l_hip" to Joint3D(-0.12, 0.05, 0.06),
                    "r_hip" to Joint3D(0.12, 0.05, 0.06),
                    "l_knee" to Joint3D(-0.13, 0.40, 0.14),
                    "r_knee" to Joint3D(0.13, 0.42, 0.12),
                    "l_foot" to Joint3D(-0.12, 0.72, 0.06),
                    "r_foot" to Joint3D(0.12, 0.75, 0.05),
                )
            }
            Posture3D.COLLAPSED_FALLEN -> {
                // Colapsado asimétrico
                mapOf(
                    "head" to Joint3D(0.08, -0.65, 0.08),
                    "neck" to Joint3D(0.04, -0.48, 0.07),
                    "chest" to Joint3D(0.0, -0.30, 0.09),
                    "pelvis" to Joint3D(-0.04, 0.05, 0.06),
                    "l_shoulder" to Joint3D(-0.22, -0.42, 0.07),
                    "r_shoulder" to Joint3D(0.18, -0.40, 0.12),
                    "l_elbow" to Joint3D(-0.35, -0.22, 0.05),
                    "r_elbow" to Joint3D(0.26, -0.18, 0.16),
                    "l_hand" to Joint3D(-0.28, -0.02, 0.06),
                    "r_hand" to Joint3D(0.32, 0.05, 0.10),
                    "l_hip" to Joint3D(-0.14, 0.05, 0.06),
                    "r_hip" to Joint3D(0.10, 0.05, 0.08),
                    "l_knee" to Joint3D(-0.22, 0.35, 0.06),
                    "r_knee" to Joint3D(0.08, 0.38, 0.18),
                    "l_foot" to Joint3D(-0.28, 0.68, 0.05),
                    "r_foot" to Joint3D(0.02, 0.70, 0.08),
                )
            }
        }
    }

    /**
     * Dibuja la silueta humana 3D (Person-in-WiFi 3D) en Realidad Aumentada (CameraX / AR Overlay).
     */
    fun DrawScope.drawArPersonSkeleton3D(
        cx: Float,
        cy: Float,
        perspectiveScale: Float,
        color: Color,
        respRate: Double,
        heartRate: Double,
        isBreathing: Boolean,
        isMoving: Boolean,
        velocityMps: Float,
        depthM: Double,
        isPinned: Boolean,
        animTimeSec: Double,
        phoneRollDeg: Double = 0.0,
        rArmLift: Float = 0.0f,
        lArmLift: Float = 0.0f,
        isWaving: Boolean = false,
        neuralJoints: Map<String, Joint3D>? = null,
        forcedPosture: Posture3D? = null,
    ) {
        val freq = if (isBreathing && respRate > 0) (respRate / 60.0).coerceIn(0.12, 0.50) else 0.25
        val breatheScale = if (isBreathing) sin(animTimeSec * freq * 2.0 * Math.PI) else 0.0
        val walkPhase = if (isMoving) animTimeSec * (velocityMps * 3.5 + 2.0) * Math.PI else (animTimeSec * 3.0 * Math.PI)

        // Determinar postura 3D: primero forcedPosture (de neural posture head), luego heurística
        val posture = forcedPosture ?: when {
            isMoving || rArmLift > 0.3f || lArmLift > 0.3f -> Posture3D.STANDING
            kotlin.math.abs(depthM) >= 0.9 -> Posture3D.LYING_DOWN
            kotlin.math.abs(depthM) >= 0.4 -> Posture3D.COLLAPSED_FALLEN
            else -> Posture3D.STANDING
        }

        // Si los joints neurales pasan la validación anatómica humana, usarlos; si no, cinemática garantizada
        val joints = if (neuralJoints != null && isAnatomicallyValid(neuralJoints)) {
            neuralJoints
        } else {
            computeJoints(
                posture = posture,
                breatheScale = breatheScale,
                walkPhase = walkPhase,
                isMoving = isMoving,
                rArmLift = rArmLift,
                lArmLift = lArmLift,
                isWaving = isWaving,
            )
        }

        // Escala métrica a píxeles en pantalla con perspectiva
        val pxPerMeter = (140f * perspectiveScale).coerceIn(40f, 320f)
        val strokeBone = (3.2f * perspectiveScale).coerceIn(1.8f, 6.5f)
        val jointNodeRadius = (4.5f * perspectiveScale).coerceIn(2.5f, 9.0f)
        val fleshRadius = (16f * perspectiveScale).coerceIn(8f, 34f)

        // Proyección 3D isométrica/perspectiva sobre la posición AR
        val yFloorFactor = if (posture == Posture3D.LYING_DOWN || posture == Posture3D.COLLAPSED_FALLEN) 0.72f else 0.35f
        fun projectToScreen(j: Joint3D): Offset {
            val rollRad = Math.toRadians(phoneRollDeg)
            val cosR = cos(rollRad).toFloat()
            val sinR = sin(rollRad).toFloat()

            // Coordenadas locales en metros
            val lx = (j.x.toFloat() * pxPerMeter)
            val ly = (-j.z.toFloat() * pxPerMeter + j.y.toFloat() * (pxPerMeter * yFloorFactor))

            // Rotación por inclinación de cámara
            val rx = lx * cosR - ly * sinR
            val ry = lx * sinR + ly * cosR

            return Offset(cx + rx, cy + ry)
        }

        val screenJoints = joints.mapValues { projectToScreen(it.value) }

        val meshGlowColor = color.copy(alpha = if (isPinned) 0.22f else 0.14f)
        val boneColor = if (isPinned) Color.White else color.copy(alpha = 0.95f)
        val boneGlowColor = color.copy(alpha = 0.45f)

        // 1. CAPA VOLUMÉTRICA DE CARNE / MALLA SMPL TRANSLÚCIDA (Glow Body Mesh)
        for ((a, b) in bones) {
            val pa = screenJoints[a] ?: continue
            val pb = screenJoints[b] ?: continue
            drawLine(
                color = meshGlowColor,
                start = pa,
                end = pb,
                strokeWidth = fleshRadius,
                cap = StrokeCap.Round
            )
        }

        // Cabeza volumen
        screenJoints["head"]?.let { hp ->
            val headVolRadius = (15f * perspectiveScale).coerceIn(7f, 30f)
            drawCircle(meshGlowColor, headVolRadius, hp)
        }

        // 2. CAPA ESQUELÉTICA ÓSEA 3D (Huesos luminosos con resplandor)
        for ((a, b) in bones) {
            val pa = screenJoints[a] ?: continue
            val pb = screenJoints[b] ?: continue

            // Resplandor exterior
            drawLine(
                color = boneGlowColor,
                start = pa,
                end = pb,
                strokeWidth = strokeBone + (3.5f * perspectiveScale),
                cap = StrokeCap.Round
            )
            // Núcleo óseo
            drawLine(
                color = boneColor,
                start = pa,
                end = pb,
                strokeWidth = strokeBone,
                cap = StrokeCap.Round
            )
        }

        // 3. NODOS ARTICULARES ILUMINADOS (Joint Spheres)
        for ((name, pt) in screenJoints) {
            val isHead = name == "head"
            val nodeR = if (isHead) jointNodeRadius * 1.5f else jointNodeRadius
            val nodeColor = when (name) {
                "head" -> Color(0xFF00FFFF)
                "chest" -> Color(0xFFFF3366) // Nodo torácico
                "l_hand", "r_hand" -> Color(0xFF00FFCC)
                "l_foot", "r_foot" -> Color(0xFFFFCC00)
                else -> Color.White
            }

            drawCircle(
                color = nodeColor,
                radius = nodeR,
                center = pt
            )
            drawCircle(
                color = nodeColor.copy(alpha = 0.4f),
                radius = nodeR * 1.6f,
                center = pt,
                style = Stroke(width = 1.5f)
            )
        }

        // 4. CORAZÓN VITAL PULSANTE (Cardiac Pulse Core en el Pecho)
        screenJoints["chest"]?.let { chestPt ->
            val hrFreq = if (heartRate > 0) (heartRate / 60.0).coerceIn(0.7, 2.2) else 1.2
            val cardiacPulse = 0.80f + 0.35f * (0.5f + 0.5f * sin(animTimeSec * hrFreq * 2.0 * Math.PI).toFloat())
            val heartRadius = (8f * perspectiveScale * cardiacPulse).coerceIn(4f, 22f)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFF1155), Color(0xFFFF1155).copy(alpha = 0.3f), Color.Transparent),
                    center = chestPt,
                    radius = heartRadius * 2.2f
                ),
                radius = heartRadius * 2.2f,
                center = chestPt
            )
            drawCircle(
                color = Color(0xFFFF3366),
                radius = heartRadius,
                center = chestPt
            )
        }
    }

    private fun rotateMapCoords(x: Double, y: Double, bearingDeg: Double): Pair<Double, Double> {
        val rad = Math.toRadians(bearingDeg)
        val cosB = cos(rad)
        val sinB = sin(rad)
        val rx = x * cosB - y * sinB
        val ry = x * sinB + y * cosB
        return Pair(rx, ry)
    }

    fun DrawScope.drawHumanFigure3D(
        victim: TrappedVictim,
        bearingDeg: Double,
        selected: Boolean,
        animPhase: Double,
        project: (Double, Double, Double) -> Offset,
        color: Color,
    ) {
        val freq = if (victim.isBreathing) (victim.respRate / 60.0).coerceIn(0.12, 0.45) else 0.2
        val breatheScale = if (victim.isBreathing) sin(animPhase * freq * 2.0 * Math.PI) else 0.0
        val joints = computeJoints(
            posture = if (kotlin.math.abs(victim.depth) >= 0.8) Posture3D.LYING_DOWN else Posture3D.STANDING,
            breatheScale = breatheScale
        )
        val baseZ = victim.depth + 0.72
        val worldJoints = joints.mapValues { (_, j) ->
            Triple(victim.x + j.x, victim.y + j.y, baseZ + j.z)
        }

        val strokeW = if (selected) 3.5f else 2.5f
        val alpha = if (selected) 1f else 0.85f
        val figureColor = color.copy(alpha = alpha)
        val fleshColor = color.copy(alpha = if (selected) 0.22f else 0.15f)

        val pelvis = worldJoints["pelvis"]!!
        val (prx, pry) = rotateMapCoords(pelvis.first, pelvis.second, bearingDeg)
        val footCenter = project(prx, pry, pelvis.third)
        drawCircle(color.copy(alpha = 0.15f), if (selected) 22f else 16f, footCenter)
        drawCircle(color.copy(alpha = 0.35f), if (selected) 10f else 7f, footCenter)

        val neckJ = worldJoints["neck"]!!
        val pelvisJ = worldJoints["pelvis"]!!
        val (nx, ny) = rotateMapCoords(neckJ.first, neckJ.second, bearingDeg)
        val (px, py) = rotateMapCoords(pelvisJ.first, pelvisJ.second, bearingDeg)
        val neckPt = project(nx, ny, neckJ.third)
        val pelvisPt = project(px, py, pelvisJ.third)

        // 1. Silueta de carne translúcida
        drawLine(
            color = fleshColor,
            start = neckPt,
            end = pelvisPt,
            strokeWidth = 26f,
            cap = StrokeCap.Round
        )

        for ((a, b) in bones) {
            if (a == "head" || a == "neck" || a == "chest") continue
            val ja = worldJoints[a] ?: continue
            val jb = worldJoints[b] ?: continue
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

        // 2. Líneas óseas 3D
        for ((a, b) in bones) {
            val ja = worldJoints[a] ?: continue
            val jb = worldJoints[b] ?: continue
            val (ax, ay) = rotateMapCoords(ja.first, ja.second, bearingDeg)
            val (bx, by) = rotateMapCoords(jb.first, jb.second, bearingDeg)
            val pa = project(ax, ay, ja.third)
            val pb = project(bx, by, jb.third)
            drawLine(figureColor.copy(alpha = 0.25f), pa, pb, strokeWidth = strokeW + 2f)
            drawLine(figureColor, pa, pb, strokeWidth = strokeW)
        }

        worldJoints["head"]?.let { h ->
            val (hx, hy) = rotateMapCoords(h.first, h.second, bearingDeg)
            val hp = project(hx, hy, h.third)
            drawCircle(figureColor, if (selected) 9f else 7f, hp)
            if (victim.isBreathing) {
                drawCircle(
                    Color(0xFF00FF88).copy(alpha = 0.25f + 0.15f * (0.5 + 0.5 * breatheScale).toFloat()),
                    if (selected) 20f else 15f,
                    hp,
                )
            }
        }
    }
}
