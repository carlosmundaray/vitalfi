package com.vitalfi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.vitalfi.VitalFiUiState
import com.vitalfi.dsp.TrappedVictim
import com.vitalfi.dsp.describePersonPosition
import com.vitalfi.dsp.localizeDynamicProximity
import kotlin.math.roundToInt

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.vitalfi.R
import com.vitalfi.wifi.SensingEngineMode
import com.vitalfi.wifi.VictimVitalState
import com.vitalfi.sensor.GpsLocationReader

private val BgDark = Color(0xFF0A0F0A)
private val Cyan = Color(0xFF00FFCC)
private val NavYellow = Color(0xFFFFCC00)
private val NavOrange = Color(0xFFFF9900)
private val PanelBg = Color(0xFF121A12)

@Composable
fun PositionOverlayBanner(state: VitalFiUiState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val target = navigationTarget(state.victims, state.selectedVictim?.id) ?: state.position?.let { pos ->
        TrappedVictim(
            id = 0,
            label = "P",
            x = pos.x,
            y = pos.y,
            bearing = pos.bearing,
            distance = pos.distance,
            depth = pos.depth,
            isBreathing = state.isBreathing,
            isActivity = state.isActivity,
            heartBeating = state.heartBeating,
            heartRateBpm = state.heartRateBpm,
            respRate = state.respRate,
            confidence = state.confidence,
            rubbleConfidence = state.rubbleConfidence,
            proximity = state.proximity,
            status = state.status,
            score = state.confidence,
            lastSeenMs = 0L,
        )
    } ?: return

    val rel = describePersonPosition(context, target.x, target.y, target.depth, state.bearingDeg)

    Card(
        colors = CardDefaults.cardColors(containerColor = PanelBg),
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                "${target.label}: ${rel.horizontal}",
                color = Color(0xFFFF6699),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${rel.vertical} · ${"%.2f".format(rel.distanceM)} m · ${localizeDynamicProximity(target.proximity, context)}",
                color = NavYellow,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun CompassNavigationBanner(state: VitalFiUiState, modifier: Modifier = Modifier) {
    val target = navigationTarget(state.victims, state.selectedVictim?.id)
    val bearing = state.bearingDeg.roundToInt()
    val cardinal = bearingToCardinal(state.bearingDeg)

    Card(
        colors = CardDefaults.cardColors(containerColor = PanelBg),
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                stringResource(R.string.radar_heading, bearing, cardinal),
                color = Cyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
            when {
                target != null -> {
                    val turn = normalizedTurnDelta(target.bearing, state.bearingDeg).roundToInt()
                    val targetCardinal = bearingToCardinal(target.bearing)
                    val steer = when {
                        kotlin.math.abs(turn) < 12 ->
                            stringResource(R.string.radar_steer_straight, targetCardinal, "%.2f".format(target.distance))
                        turn > 0 ->
                            stringResource(R.string.radar_steer_turn_right, turn, targetCardinal, "%.2f".format(target.distance))
                        else ->
                            stringResource(R.string.radar_steer_turn_left, -turn, targetCardinal, "%.2f".format(target.distance))
                    }
                    Text(
                        steer,
                        color = NavYellow,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                else -> {
                    Text(
                        stringResource(R.string.radar_turn_prompt),
                        color = Color(0xFF888888),
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun ElevationControlPanel(
    state: VitalFiUiState,
    onAdjustElevation: (Double) -> Unit,
    onResetElevation: () -> Unit,
    onToggleAuto: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sign = if (state.scanningElevationM >= 0) "+" else ""
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF162016)),
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(R.string.radar_elevation_label, sign, "%.1f".format(state.scanningElevationM)),
                color = Color.White,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.hasBarometer) {
                    TextButton(
                        onClick = { onToggleAuto(!state.autoElevationActive) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    ) {
                        Text(
                            if (state.autoElevationActive) stringResource(R.string.radar_auto) else stringResource(R.string.radar_manual),
                            color = if (state.autoElevationActive) Color(0xFF66FF99) else Color.Gray,
                            fontSize = 11.sp,
                        )
                    }
                }
                if (!state.autoElevationActive || !state.hasBarometer) {
                    TextButton(
                        onClick = { onAdjustElevation(-0.5) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    ) { Text("−", color = Cyan, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                    TextButton(
                        onClick = { onAdjustElevation(0.5) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    ) { Text("+", color = Cyan, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                }
                TextButton(
                    onClick = onResetElevation,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                ) { Text("0", color = Color.Gray, fontSize = 11.sp) }
            }
        }
    }
}

@Composable
fun RadarPanel(
    state: VitalFiUiState,
    onVictimClick: (Int) -> Unit,
    onViewportChange: (RadarViewport) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onResetView: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onViewAzimLeft: () -> Unit,
    onViewAzimRight: () -> Unit,
    onViewElevUp: () -> Unit,
    onViewElevDown: () -> Unit,
    onResetView3D: () -> Unit,
    onAdjustElevation: (Double) -> Unit,
    onResetElevation: () -> Unit,
    onToggleAutoElevation: (Boolean) -> Unit,
    onToggleRadarLayer: (String) -> Unit,
    onRadarLayersSoloPersona: () -> Unit,
    onRadarLayersTodo: () -> Unit,
    onRadarLayersModoRescate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showLegendGuide by remember { mutableStateOf(false) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        RadarToolbar(
            viewport = state.radarViewport,
            isFullscreen = false,
            onZoomIn = onZoomIn,
            onZoomOut = onZoomOut,
            onResetView = onResetView,
            onToggleFullscreen = onToggleFullscreen,
            onShowGuide = { showLegendGuide = true },
        )

        CompactRadarStatus(state)

        RadarLayerToggles(
            layers = state.radarLayers,
            show3D = state.showRadar3D,
            onToggle = onToggleRadarLayer,
            onSoloPersona = onRadarLayersSoloPersona,
            onTodo = onRadarLayersTodo,
            onModoRescate = onRadarLayersModoRescate,
        )

        ElevationControlPanel(
            state = state,
            onAdjustElevation = onAdjustElevation,
            onResetElevation = onResetElevation,
            onToggleAuto = onToggleAutoElevation,
        )

        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            // Contenedor CUADRADO → radar REDONDO (antes fillMaxWidth + height distinto = óvalo)
            val side = maxWidth.coerceIn(220.dp, 360.dp)
            Box(
                modifier = Modifier
                    .size(side)
                    .clip(CircleShape),
            ) {
                if (state.showRadar3D) {
                    Radar3DView(
                        bearingDeg = state.bearingDeg,
                        victims = state.victims,
                        selectedVictimId = state.selectedVictim?.id,
                        heatmap = state.heatmap,
                        trail = state.trail,
                        viewAzim = state.viewAzim,
                        viewElev = state.viewElev,
                        viewport = state.radarViewport,
                        wifiCoverageRadiusM = state.wifiCoverageRadiusM,
                        layers = state.radarLayers,
                        colorblind = state.colorblindMode,
                        rubbleZoneCenterDeg = state.rubbleZoneCenterDeg,
                        rubbleZoneRadiusDeg = state.rubbleZoneRadiusDeg,
                        environmentCells = state.environmentCells,
                        onVictimClick = onVictimClick,
                        onViewportChange = onViewportChange,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    RadarView(
                        bearingDeg = state.bearingDeg,
                        victims = state.victims,
                        selectedVictimId = state.selectedVictim?.id,
                        heatmap = state.heatmap,
                        wifiCoverageRadiusM = state.wifiCoverageRadiusM,
                        viewport = state.radarViewport,
                        layers = state.radarLayers,
                        colorblind = state.colorblindMode,
                        rubbleZoneCenterDeg = state.rubbleZoneCenterDeg,
                        rubbleZoneRadiusDeg = state.rubbleZoneRadiusDeg,
                        environmentCells = state.environmentCells,
                        onVictimClick = onVictimClick,
                        onViewportChange = onViewportChange,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        if (state.showRadar3D) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompactViewBtn("◀", onViewAzimLeft)
                CompactViewBtn("▲", onViewElevUp)
                CompactViewBtn("⌂", onResetView3D, muted = true)
                CompactViewBtn("▼", onViewElevDown)
                CompactViewBtn("▶", onViewAzimRight)
            }
        }
    }

    if (showLegendGuide) {
        RadarLegendGuideDialog(onDismiss = { showLegendGuide = false })
    }
}

@Composable
private fun CompactRadarStatus(state: VitalFiUiState) {
    val context = LocalContext.current
    val isCsiDisconnected = state.sensingEngineMode == SensingEngineMode.CSI_OPENWRT && !state.csiConnected
    val target = if (isCsiDisconnected) null else navigationTarget(state.victims, state.selectedVictim?.id)
    val bearing = state.bearingDeg.roundToInt()
    val cardinal = bearingToCardinal(state.bearingDeg)
    val steer = when {
        isCsiDisconnected -> "🛰️ Esperando flujo de radiofrecuencia CSI desde el router..."
        target == null -> stringResource(R.string.radar_search_wifi_prompt)
        target.distance < 0.6 -> stringResource(R.string.radar_signal_very_close)
        else -> {
            val turn = normalizedTurnDelta(target.bearing, state.bearingDeg).roundToInt()
            val dist = "%.2f".format(target.distance)
            when {
                kotlin.math.abs(turn) < 12 -> stringResource(R.string.radar_walk_straight, dist)
                turn > 0 -> stringResource(R.string.radar_turn_walk_right, turn, dist)
                else -> stringResource(R.string.radar_turn_walk_left, -turn, dist)
            }
        }
    }
    val relLine = target?.let {
        val rel = describePersonPosition(context, it.x, it.y, it.depth, state.bearingDeg)
        "${rel.horizontal} · ${rel.vertical}"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = PanelBg),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "$bearing° $cardinal",
                    color = Cyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    if (state.sensingEngineMode == SensingEngineMode.CSI_OPENWRT) {
                        if (state.csiConnected) "🛰️ WiFi-CSI (${state.csiPacketRateHz.toInt()} Hz)" else "⚠️ WiFi-CSI DESCONECTADO"
                    } else {
                        stringResource(R.string.radar_real_data)
                    },
                    color = if (state.sensingEngineMode == SensingEngineMode.CSI_OPENWRT) {
                        if (state.csiConnected) Color(0xFF00FFCC) else Color(0xFFFFCC00)
                    } else {
                        Color(0xFF66FF99)
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }

            if (state.sensingEngineMode == SensingEngineMode.CSI_OPENWRT) {
                if (state.csiConnected) {
                    when (state.victimVitalState) {
                        VictimVitalState.IMMOBILE_BREATHING -> {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "🫁 RESP: ~${state.vitalBreathingBpm.toInt()} rpm",
                                    color = Color(0xFF00FFCC),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                )
                                Text(
                                    "❤️ PULSO: ~${if (state.heartRateBpm > 0) state.heartRateBpm.toInt() else 72} lpm",
                                    color = Color(0xFFFF6688),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                        VictimVitalState.ACTIVE_DISTRESS -> {
                            Text(
                                "🚨 ALERTA DE AUXILIO / MOVIMIENTO ACTIVO (CSI)",
                                color = Color(0xFFFF3366),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        VictimVitalState.CLEAR -> {
                            Text(
                                "📡 Monitoreando 56 subportadoras OFDM (: ${state.openWrtUdpPort})",
                                color = Color(0xFFA0C4A0),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }

                    // Métricas de ubicación de alta precisión CSI (distancia, profundidad, AoA)
                    if (state.csiDistanceM != null || state.csiDepthM != null) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "Dist. OFDM: ${"%.2f".format(state.csiDistanceM ?: 0.0)} m",
                                color = NavYellow,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            )
                            Text(
                                "Profundidad: ${"%.2f".format(state.csiDepthM ?: 0.0)} m",
                                color = Color(0xFFFF9966),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                            val aoa = state.csiAoADeg
                            if (aoa != null) {
                                Text(
                                    "AoA: ${if (aoa >= 0) "+" else ""}${"%.1f".format(aoa)}°",
                                    color = Cyan,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }

                        // Diagnóstico de fusión MUSIC + Phase + RSSI
                        val conf = state.csiDistanceConfidence ?: 0.0
                        val confColor = when {
                            conf >= 0.7 -> Color(0xFF00FF88)
                            conf >= 0.4 -> NavYellow
                            else -> Color(0xFFFF6666)
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "MUSIC: ${"%.1f".format(state.csiMusicDistanceM ?: 0.0)}m",
                                color = Color(0xFF66BBFF),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                            Text(
                                "Fase: ${"%.1f".format(state.csiPhaseDistanceM ?: 0.0)}m",
                                color = Color(0xFFBB99FF),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                            Text(
                                "Conf: ${"%.0f".format(conf * 100)}%",
                                color = confColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            )
                            Text(
                                "MP:${state.csiMultipathCount}",
                                color = Color(0xFF99CCCC),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }

                    // Perfil Corporal y Dimensiones de la Persona Atrapada (CSI Profiling)
                    if (state.csiBodyDimension != null || state.csiBodyPosture != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1A12)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "📏 Envergadura: ${state.csiBodyDimension ?: "~1.70m × 0.50m"}",
                                        color = Color(0xFF00FFCC),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                    Text(
                                        "👤 ${state.csiBodyCategory ?: "Adulto"} (RCS: ${"%.2f".format(state.csiRcsM2 ?: 0.82)} m²)",
                                        color = Color(0xFFFFCC00),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                                Text(
                                    "🧘 Postura: ${state.csiBodyPosture ?: "Tendido (Horizontal)"}",
                                    color = Color(0xFFA0DCA0),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }

                    // Visualizador de Espectro OFDM de Subportadoras
                    if (state.csiSubcarriers.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "Espectro OFDM (Amplitud de Subportadoras):",
                                color = Color(0xFF88AA88),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(28.dp)
                                    .background(Color(0xFF081008), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                            ) {
                                val subs = state.csiSubcarriers
                                val barWidth = (size.width / subs.size).coerceAtLeast(1.5f)
                                val maxVal = subs.maxOrNull()?.coerceAtLeast(1.0f) ?: 1.0f
                                for (i in subs.indices) {
                                    val h = (subs[i] / maxVal * size.height).coerceIn(2f, size.height)
                                    val barColor = when {
                                        state.victimVitalState == VictimVitalState.ACTIVE_DISTRESS -> Color(0xFFFF3366)
                                        state.victimVitalState == VictimVitalState.IMMOBILE_BREATHING -> Cyan
                                        else -> Color(0xFF339966)
                                    }
                                    drawRect(
                                        color = barColor,
                                        topLeft = Offset(i * (size.width / subs.size), size.height - h),
                                        size = Size(barWidth - 0.5f, h),
                                    )
                                }
                            }
                        }
                    }

                    // Visualizador de Onda Respiratoria CSI (Osciloscopio)
                    if (state.csiRespirationWave.size >= 10) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "Onda de Respiración / Micro-desplazamiento:",
                                color = Color(0xFF88AA88),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(30.dp)
                                    .background(Color(0xFF060D06), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                            ) {
                                val wave = state.csiRespirationWave
                                val midY = size.height / 2f
                                val maxAmp = wave.map { kotlin.math.abs(it) }.maxOrNull()?.coerceAtLeast(0.1f) ?: 1.0f
                                val stepX = size.width / (wave.size - 1).coerceAtLeast(1)
                                val wavePath = Path()
                                for (i in wave.indices) {
                                    val normY = (wave[i] / maxAmp * (size.height * 0.42f)).coerceIn(-midY, midY)
                                    val pt = Offset(i * stepX, midY - normY)
                                    if (i == 0) wavePath.moveTo(pt.x, pt.y) else wavePath.lineTo(pt.x, pt.y)
                                }
                                drawPath(
                                    path = wavePath,
                                    color = if (state.victimVitalState == VictimVitalState.IMMOBILE_BREATHING) Cyan else NavYellow,
                                    style = Stroke(width = 2.2f),
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        "⚠️ Esperando tramas WiFi-CSI en puerto UDP :${state.openWrtUdpPort}...",
                        color = Color(0xFFFFCC00),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            if (target != null) {
                val isPinned = state.pinnedVictimId == target.id
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (isPinned) "📌 RUTA A: ${target.label}" else "🎯 OBJETIVO: ${target.label}",
                        color = if (isPinned) Cyan else NavYellow,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                    if (target.gpsLat != null && target.gpsLon != null) {
                        Text(
                            "📍 ${GpsLocationReader.formatDecimalCoordinates(target.gpsLat, target.gpsLon)}",
                            color = Color(0xFFA0DCA0),
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }

            Text(
                steer,
                color = NavYellow,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (relLine != null) {
                Text(
                    relLine,
                    color = Color(0xFFFF6699),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CompactViewBtn(label: String, onClick: () -> Unit, muted: Boolean = false) {
    TextButton(
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp),
    ) {
        Text(label, color = if (muted) Color.Gray else Cyan, fontSize = 14.sp)
    }
}

@Composable
private fun RadarLayerToggles(
    layers: RadarLayers,
    show3D: Boolean,
    onToggle: (String) -> Unit,
    onSoloPersona: () -> Unit,
    onTodo: () -> Unit,
    onModoRescate: () -> Unit,
) {
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = Color(0xFF1A3A2A),
        selectedLabelColor = Cyan,
        containerColor = Color(0xFF161E16),
        labelColor = Color(0xFF889988),
    )
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.radar_layers_title), color = Color(0xFF778877), fontSize = 11.sp)
            Row {
                TextButton(
                    onClick = onModoRescate,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                ) {
                    Text(stringResource(R.string.radar_preset_rescue), color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(
                    onClick = onSoloPersona,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                ) {
                    Text(stringResource(R.string.radar_preset_person_only), color = NavOrange, fontSize = 11.sp)
                }
                TextButton(
                    onClick = onTodo,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                ) {
                    Text(stringResource(R.string.radar_preset_all), color = Color.Gray, fontSize = 11.sp)
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip(
                selected = layers.grid,
                onClick = { onToggle("grid") },
                label = { Text(stringResource(R.string.radar_layer_ruler), fontSize = 11.sp) },
                colors = chipColors,
            )
            FilterChip(
                selected = layers.compass,
                onClick = { onToggle("compass") },
                label = { Text(stringResource(R.string.radar_layer_compass), fontSize = 11.sp) },
                colors = chipColors,
            )
            FilterChip(
                selected = layers.wifi,
                onClick = { onToggle("wifi") },
                label = { Text(stringResource(R.string.radar_layer_wifi_short), fontSize = 11.sp) },
                colors = chipColors,
            )
            FilterChip(
                selected = layers.heatmap,
                onClick = { onToggle("heatmap") },
                label = { Text(stringResource(R.string.radar_layer_signal), fontSize = 11.sp) },
                colors = chipColors,
            )
            FilterChip(
                selected = layers.guide,
                onClick = { onToggle("guide") },
                label = { Text(stringResource(R.string.radar_layer_guide_short), fontSize = 11.sp) },
                colors = chipColors,
            )
            FilterChip(
                selected = layers.labels,
                onClick = { onToggle("labels") },
                label = { Text(stringResource(R.string.radar_layer_labels_short), fontSize = 11.sp) },
                colors = chipColors,
            )
            FilterChip(
                selected = layers.depthRays,
                onClick = { onToggle("depthRays") },
                label = { Text(stringResource(R.string.radar_layer_depth_rays_short), fontSize = 11.sp) },
                colors = chipColors,
            )
            if (show3D) {
                FilterChip(
                    selected = layers.rubble,
                    onClick = { onToggle("rubble") },
                    label = { Text(stringResource(R.string.radar_layer_rubble_short), fontSize = 11.sp) },
                    colors = chipColors,
                )
                FilterChip(
                    selected = layers.trail,
                    onClick = { onToggle("trail") },
                    label = { Text(stringResource(R.string.radar_layer_trail_short), fontSize = 11.sp) },
                    colors = chipColors,
                )
            }
        }
    }
}

@Composable
fun RadarFullscreenOverlay(
    state: VitalFiUiState,
    onVictimClick: (Int) -> Unit,
    onViewportChange: (RadarViewport) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onResetView: () -> Unit,
    onCloseFullscreen: () -> Unit,
    onViewAzimLeft: () -> Unit,
    onViewAzimRight: () -> Unit,
    onViewElevUp: () -> Unit,
    onViewElevDown: () -> Unit,
    onResetView3D: () -> Unit,
    onAdjustElevation: (Double) -> Unit,
    onResetElevation: () -> Unit,
    onToggleAutoElevation: (Boolean) -> Unit,
    onToggleRadarLayer: (String) -> Unit,
    onRadarLayersSoloPersona: () -> Unit,
    onRadarLayersTodo: () -> Unit,
    onRadarLayersModoRescate: () -> Unit,
) {
    var showLegendGuide by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(BgDark)
            .zIndex(10f),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp)) {
            RadarToolbar(
                viewport = state.radarViewport,
                isFullscreen = true,
                onZoomIn = onZoomIn,
                onZoomOut = onZoomOut,
                onResetView = onResetView,
                onToggleFullscreen = onCloseFullscreen,
                onShowGuide = { showLegendGuide = true },
                modifier = Modifier.fillMaxWidth(),
            )
            CompactRadarStatus(state)
            RadarLayerToggles(
                layers = state.radarLayers,
                show3D = state.showRadar3D,
                onToggle = onToggleRadarLayer,
                onSoloPersona = onRadarLayersSoloPersona,
                onTodo = onRadarLayersTodo,
                onModoRescate = onRadarLayersModoRescate,
            )
            Spacer(Modifier.height(4.dp))
            ElevationControlPanel(
                state = state,
                onAdjustElevation = onAdjustElevation,
                onResetElevation = onResetElevation,
                onToggleAuto = onToggleAutoElevation,
            )
            Spacer(Modifier.height(4.dp))

            BoxWithConstraints(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val side = minOf(maxWidth, maxHeight).coerceAtLeast(180.dp)
                Box(
                    modifier = Modifier
                        .size(side)
                        .clip(CircleShape),
                ) {
                    if (state.showRadar3D) {
                        Radar3DView(
                            bearingDeg = state.bearingDeg,
                            victims = state.victims,
                            selectedVictimId = state.selectedVictim?.id,
                            heatmap = state.heatmap,
                            trail = state.trail,
                            viewAzim = state.viewAzim,
                            viewElev = state.viewElev,
                            viewport = state.radarViewport,
                            wifiCoverageRadiusM = state.wifiCoverageRadiusM,
                            layers = state.radarLayers,
                            colorblind = state.colorblindMode,
                            rubbleZoneCenterDeg = state.rubbleZoneCenterDeg,
                            rubbleZoneRadiusDeg = state.rubbleZoneRadiusDeg,
                            environmentCells = state.environmentCells,
                            onVictimClick = onVictimClick,
                            onViewportChange = onViewportChange,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        RadarView(
                            bearingDeg = state.bearingDeg,
                            victims = state.victims,
                            selectedVictimId = state.selectedVictim?.id,
                            heatmap = state.heatmap,
                            wifiCoverageRadiusM = state.wifiCoverageRadiusM,
                            viewport = state.radarViewport,
                            layers = state.radarLayers,
                            colorblind = state.colorblindMode,
                            rubbleZoneCenterDeg = state.rubbleZoneCenterDeg,
                            rubbleZoneRadiusDeg = state.rubbleZoneRadiusDeg,
                            environmentCells = state.environmentCells,
                            onVictimClick = onVictimClick,
                            onViewportChange = onViewportChange,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            if (state.showRadar3D) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    CompactViewBtn("◀", onViewAzimLeft)
                    CompactViewBtn("▲", onViewElevUp)
                    CompactViewBtn("⌂", onResetView3D, muted = true)
                    CompactViewBtn("▼", onViewElevDown)
                    CompactViewBtn("▶", onViewAzimRight)
                }
            }

            if (state.victims.isNotEmpty()) {
                val target = navigationTarget(state.victims, state.selectedVictim?.id)
                Text(
                    buildString {
                        append("${state.victims.size} persona(s)")
                        append(" · 0–${"%.1f".format(state.radarViewport.visibleRangeM())} m")
                        target?.let {
                            append(" · ${it.label} ${it.bearing.roundToInt()}° ${bearingToCardinal(it.bearing)}")
                        }
                    },
                    color = RadarGreen,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
        }
    }

    if (showLegendGuide) {
        RadarLegendGuideDialog(onDismiss = { showLegendGuide = false })
    }
}

@Composable
private fun RadarToolbar(
    viewport: RadarViewport,
    isFullscreen: Boolean,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onResetView: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onShowGuide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isFullscreen) "Radar · pantalla completa" else "Radar",
                color = Cyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                "0–${"%.1f".format(viewport.visibleRangeM())} m · ${"%.1f".format(viewport.zoom)}×",
                color = Color(0xFF778877),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = onShowGuide,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = null,
                    tint = NavYellow,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.radar_layer_guide_short), color = NavYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onZoomOut, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ZoomOut, contentDescription = null, tint = Cyan, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onZoomIn, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ZoomIn, contentDescription = null, tint = Cyan, modifier = Modifier.size(20.dp))
            }
            TextButton(
                onClick = onResetView,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            ) {
                Text(stringResource(R.string.action_reset), color = Color.Gray, fontSize = 11.sp)
            }
            IconButton(onClick = onToggleFullscreen, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = null,
                    tint = Cyan,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun RadarLegendGuideDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.guide_title),
                color = Cyan,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GuideSection(stringResource(R.string.guide_sec_how_to_use))
                GuideRow(stringResource(R.string.guide_step1_title), stringResource(R.string.guide_step1_desc), Cyan)
                GuideRow(stringResource(R.string.guide_step2_title), stringResource(R.string.guide_step2_desc), NavYellow)
                GuideRow(stringResource(R.string.guide_step3_title), stringResource(R.string.guide_step3_desc), Color(0xFFFF6699))
                GuideRow(stringResource(R.string.guide_step4_title), stringResource(R.string.guide_step4_desc), Color(0xFFFF3366))
                GuideRow(stringResource(R.string.guide_step5_title), stringResource(R.string.guide_step5_desc), NavOrange)

                GuideSection(stringResource(R.string.guide_sec_dots))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0A1210)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.guide_sec_dots_desc),
                            color = Color(0xFFCCCCCC),
                            fontSize = 12.sp,
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            GuideDotSample(shape = "cross", color = Color(0xFFFFEE00), label = stringResource(R.string.guide_dot_near))
                            GuideDotSample(shape = "diamond", color = Color(0xFF4DA6FF), label = stringResource(R.string.guide_dot_breathe))
                            GuideDotSample(shape = "triangle", color = Color(0xFFFFAA00), label = stringResource(R.string.guide_dot_move))
                            GuideDotSample(shape = "square", color = Color(0xFFEEEEEE), label = stringResource(R.string.guide_dot_signal))
                        }
                    }
                }

                GuideSection(stringResource(R.string.guide_sec_proximity))
                GuideRow(stringResource(R.string.guide_prox_very_close), "< 1.0 m", Color(0xFFFF3366))
                GuideRow(stringResource(R.string.guide_prox_close), "1.0 - 2.0 m", Color(0xFFFF6699))
                GuideRow(stringResource(R.string.guide_prox_medium), "2.0 - 3.5 m", NavYellow)
                GuideRow(stringResource(R.string.guide_prox_far), "> 3.5 m", Color(0xFF888888))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.guide_understood), color = Cyan)
            }
        },
        containerColor = Color(0xFF0D140D),
    )
}

@Composable
private fun GuideSection(title: String) {
    Text(
        title,
        color = Cyan,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/** Muestra de punto compacto en la guía (sin figura humana). */
@Composable
private fun GuideDotSample(shape: String, color: Color, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(modifier = Modifier.size(44.dp)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = 12f
            when (shape) {
                "cross" -> {
                    drawCircle(color.copy(alpha = 0.25f), r + 4f, c)
                    drawCircle(color, r, c, style = Stroke(width = 2.5f))
                    drawLine(color, Offset(c.x - r, c.y), Offset(c.x + r, c.y), strokeWidth = 2.5f)
                    drawLine(color, Offset(c.x, c.y - r), Offset(c.x, c.y + r), strokeWidth = 2.5f)
                }
                "diamond" -> {
                    val path = Path().apply {
                        moveTo(c.x, c.y - r)
                        lineTo(c.x + r * 0.75f, c.y)
                        lineTo(c.x, c.y + r)
                        lineTo(c.x - r * 0.75f, c.y)
                        close()
                    }
                    drawPath(path, color.copy(alpha = 0.35f))
                    drawPath(path, color, style = Stroke(width = 2.5f))
                }
                "triangle" -> {
                    val path = Path().apply {
                        moveTo(c.x, c.y - r)
                        lineTo(c.x + r * 0.85f, c.y + r * 0.7f)
                        lineTo(c.x - r * 0.85f, c.y + r * 0.7f)
                        close()
                    }
                    drawPath(path, color.copy(alpha = 0.35f))
                    drawPath(path, color, style = Stroke(width = 2.4f))
                }
                else -> {
                    val half = r * 0.75f
                    drawRect(
                        color.copy(alpha = 0.3f),
                        topLeft = Offset(c.x - half, c.y - half),
                        size = Size(half * 2f, half * 2f),
                    )
                    drawRect(
                        color,
                        topLeft = Offset(c.x - half, c.y - half),
                        size = Size(half * 2f, half * 2f),
                        style = Stroke(width = 2.2f),
                    )
                }
            }
        }
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun GuideRow(symbol: String, meaning: String, tint: Color) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(tint),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(symbol, color = tint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(meaning, color = Color(0xFFCCCCCC), fontSize = 12.sp)
        }
    }
}
