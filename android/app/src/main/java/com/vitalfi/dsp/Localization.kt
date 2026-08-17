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
/**
 * Convierte coordenadas absolutas a descripción localizada comprensible.
 */
fun describePersonPosition(
    context: android.content.Context? = null,
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
        relY >= 0.12 -> horParts += context?.getString(com.vitalfi.R.string.pos_front, "%.2f".format(relY)) ?: "Frente ${"%.2f".format(relY)} m"
        relY <= -0.12 -> horParts += context?.getString(com.vitalfi.R.string.pos_back, "%.2f".format(-relY)) ?: "Atrás ${"%.2f".format(-relY)} m"
    }
    when {
        relX >= 0.12 -> horParts += context?.getString(com.vitalfi.R.string.pos_right, "%.2f".format(relX)) ?: "Derecha ${"%.2f".format(relX)} m"
        relX <= -0.12 -> horParts += context?.getString(com.vitalfi.R.string.pos_left, "%.2f".format(-relX)) ?: "Izquierda ${"%.2f".format(-relX)} m"
    }
    val horizontalDistM = hypot(x, y)
    val horizontal = when {
        horizontalDistM < 0.6 -> context?.getString(com.vitalfi.R.string.pos_signal_very_close) ?: "Señal muy cerca (horiz. <0.6 m) — verifica el sitio"
        horParts.isEmpty() -> context?.getString(com.vitalfi.R.string.pos_aligned) ?: "Alineado contigo (±0.1 m)"
        else -> horParts.joinToString(" · ")
    }

    val depthM = (-z).coerceAtLeast(0.0)
    val vertical = when {
        z > 0.20 -> context?.getString(com.vitalfi.R.string.pos_elevation_est, "%.2f".format(z)) ?: "Altura estimada +${"%.2f".format(z)} m (no confirmada)"
        z < -0.12 -> context?.getString(com.vitalfi.R.string.pos_depth_est, "%.2f".format(depthM)) ?: "Profundidad estimada ~${"%.2f".format(depthM)} m — no confirmada"
        else -> context?.getString(com.vitalfi.R.string.pos_depth_unknown) ?: "Profundidad desconocida"
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

fun localizeDynamicStatus(
    statusRaw: String,
    isBreathing: Boolean,
    isActivity: Boolean,
    wifiEnabled: Boolean,
    context: android.content.Context,
): String {
    if (!wifiEnabled) return context.getString(com.vitalfi.R.string.status_wifi_disabled)
    if (isBreathing) return context.getString(com.vitalfi.R.string.status_person_detected)
    if (isActivity) return context.getString(com.vitalfi.R.string.status_activity_detected)
    val lower = statusRaw.lowercase()
    return when {
        lower.contains("iniciando") || lower.contains("starting") -> context.getString(com.vitalfi.R.string.status_starting)
        lower.contains("recolectando") || lower.contains("collecting") -> context.getString(com.vitalfi.R.string.status_collecting_samples)
        lower.contains("plana") || lower.contains("flat") -> context.getString(com.vitalfi.R.string.status_signal_flat)
        lower.contains("esperando") || lower.contains("waiting") -> context.getString(com.vitalfi.R.string.status_waiting_wifi)
        lower.contains("leyendo") || lower.contains("reading") -> context.getString(com.vitalfi.R.string.status_reading_local)
        lower.contains("rescatista") || lower.contains("rescuer") -> context.getString(com.vitalfi.R.string.status_possible_rescuer)
        lower.contains("analizando") || lower.contains("analyzing") -> context.getString(com.vitalfi.R.string.status_analyzing_rubble)
        lower.contains("reiniciado") || lower.contains("reset") -> context.getString(com.vitalfi.R.string.status_restarted)
        lower.contains("sin patrón") || lower.contains("no clear") -> context.getString(com.vitalfi.R.string.status_no_clear_pattern)
        else -> statusRaw
    }
}

fun localizeDynamicProximity(proxRaw: String, context: android.content.Context): String {
    val upper = proxRaw.uppercase()
    return when {
        upper.contains("MUY CERCA") || upper.contains("VERY CLOSE") -> context.getString(com.vitalfi.R.string.guide_prox_very_close)
        upper.contains("CERCA") || upper.contains("CLOSE") -> context.getString(com.vitalfi.R.string.guide_prox_close)
        upper.contains("MEDIA") || upper.contains("MEDIUM") -> context.getString(com.vitalfi.R.string.guide_prox_medium)
        upper.contains("LEJOS") || upper.contains("FAR") -> context.getString(com.vitalfi.R.string.guide_prox_far)
        else -> proxRaw
    }
}

fun localizeDynamicHint(hintRaw: String, context: android.content.Context): String {
    val lower = hintRaw.lowercase()
    return when {
        lower.contains("espera") || lower.contains("recolectando") || lower.contains("collecting") -> context.getString(com.vitalfi.R.string.hint_waiting_samples)
        lower.contains("plana") || lower.contains("router a 0.5") || lower.contains("flat") -> context.getString(com.vitalfi.R.string.hint_flat_signal)
        lower.contains("conectado al router") || lower.contains("connected to router") -> context.getString(com.vitalfi.R.string.hint_connected)
        lower.contains("escaneo pasivo") || lower.contains("passive scan") -> context.getString(com.vitalfi.R.string.hint_passive)
        lower.contains("gira 360") || lower.contains("rotate 360") -> context.getString(com.vitalfi.R.string.hint_turn_360)
        else -> hintRaw
    }
}

