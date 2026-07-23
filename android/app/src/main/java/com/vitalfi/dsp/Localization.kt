package com.vitalfi.dsp

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

data class HeatPoint(val x: Double, val y: Double, val intensity: Double)

data class PositionEstimate(
    val x: Double,
    val y: Double,
    val bearing: Double,
    val distance: Double,
    val depth: Double,
)

/** Posición relativa de la persona respecto al teléfono / AP (origen en suelo, Z↑). */
data class PersonRelativePosition(
    val horizontal: String,
    val vertical: String,
    val depthM: Double,
    val distanceM: Double,
    /** Distancia en línea recta 3D (horizontal + profundidad). */
    val slantRangeM: Double,
    val forwardM: Double,
    val rightM: Double,
    val summary: String,
    val coordsLine: String,
)

fun polarToXY(distanceM: Double, bearingDeg: Double): Pair<Double, Double> {
    val rad = Math.toRadians(bearingDeg)
    val x = distanceM * sin(rad)
    val y = distanceM * cos(rad)
    return x to y
}

/**
 * Convierte coordenadas absolutas (X este, Y norte, Z altura) a descripción comprensible
 * respecto al rescate: frente/atrás, izquierda/derecha, arriba/abajo bajo escombros,
 * con desplazamientos en metros.
 *
 * [phoneBearingDeg] = rumbo actual del teléfono (brújula).
 */
fun describePersonPosition(
    x: Double,
    y: Double,
    z: Double,
    phoneBearingDeg: Double,
): PersonRelativePosition {
    val rad = Math.toRadians(-phoneBearingDeg)
    val relX = x * cos(rad) - y * sin(rad)
    val relY = x * sin(rad) + y * cos(rad)

    val horParts = mutableListOf<String>()
    when {
        relY >= 0.12 -> horParts += "Frente ${"%.2f".format(relY)} m"
        relY <= -0.12 -> horParts += "Atrás ${"%.2f".format(-relY)} m"
    }
    when {
        relX >= 0.12 -> horParts += "Derecha ${"%.2f".format(relX)} m"
        relX <= -0.12 -> horParts += "Izquierda ${"%.2f".format(-relX)} m"
    }
    val horizontalDistM = hypot(x, y)
    val horizontal = when {
        horizontalDistM < 0.6 -> "Señal muy cerca (horiz. <0.6 m) — verifica el sitio"
        horParts.isEmpty() -> "Alineado contigo (±0.1 m)"
        else -> horParts.joinToString(" · ")
    }

    val depthM = (-z).coerceAtLeast(0.0)
    // La “profundidad” Wi‑Fi es heurística: no afirmar entierro ni ordenar cavar.
    val vertical = when {
        z > 0.20 -> "Altura estimada +${"%.2f".format(z)} m (no confirmada)"
        z < -0.12 -> "Profundidad estimada ~${"%.2f".format(depthM)} m — no confirmada"
        else -> "Profundidad desconocida (Wi‑Fi no mide entierro)"
    }

    val distanceM = horizontalDistM
    val slantRangeM = hypot(distanceM, depthM)
    val summary = "$horizontal · $vertical"
    val coordsLine =
        "X=${"%.2f".format(x)} Y=${"%.2f".format(y)} Z=${"%.2f".format(z)} m | " +
            "horiz ${"%.2f".format(distanceM)} m · 3D ${"%.2f".format(slantRangeM)} m"

    return PersonRelativePosition(
        horizontal = horizontal,
        vertical = vertical,
        depthM = depthM,
        distanceM = distanceM,
        slantRangeM = slantRangeM,
        forwardM = relY,
        rightM = relX,
        summary = summary,
        coordsLine = coordsLine,
    )
}

/** Centro del torso para la esfera de localización Wi-Fi (Person-in-WiFi). */
fun personSphereCenter(victim: TrappedVictim): Triple<Double, Double, Double> {
    val torsoZ = victim.depth + 0.72 + 0.42
    return Triple(victim.x, victim.y, torsoZ)
}
