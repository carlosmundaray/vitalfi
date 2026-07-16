package com.whofi.vitalfi.ui

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
import com.whofi.vitalfi.VitalFiUiState
import com.whofi.vitalfi.dsp.TrappedVictim
import com.whofi.vitalfi.dsp.describePersonPosition
import kotlin.math.roundToInt

private val BgDark = Color(0xFF0A0F0A)
private val Cyan = Color(0xFF00FFCC)
private val NavYellow = Color(0xFFFFCC00)
private val NavOrange = Color(0xFFFF9900)
private val PanelBg = Color(0xFF121A12)

@Composable
fun PositionOverlayBanner(state: VitalFiUiState, modifier: Modifier = Modifier) {
    val target = navigationTarget(state.victims, state.selectedVictim?.id) ?: state.position?.let { pos ->
        TrappedVictim(
            id = 0,
            label = "Persona",
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
            status = if (state.isBreathing) "Respiración detectada" else "Señal detectada",
            score = state.confidence,
            lastSeenMs = 0L,
        )
    } ?: return

    val rel = describePersonPosition(target.x, target.y, target.depth, state.bearingDeg)

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
                "${rel.vertical} · ${"%.2f".format(rel.distanceM)} m · ${target.proximity}",
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
                "Tu rumbo: $bearing° $cardinal",
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
                            "↑ Recto → $targetCardinal · ${"%.2f".format(target.distance)} m"
                        turn > 0 ->
                            "↻ $turn° derecha → $targetCardinal · ${"%.2f".format(target.distance)} m"
                        else ->
                            "↺ ${-turn}° izquierda → $targetCardinal · ${"%.2f".format(target.distance)} m"
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
                        "Gira 360° · línea cyan = tu frente",
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
                "Altura $sign${"%.1f".format(state.scanningElevationM)} m",
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
                            if (state.autoElevationActive) "Auto" else "Manual",
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
    val target = navigationTarget(state.victims, state.selectedVictim?.id)
    val bearing = state.bearingDeg.roundToInt()
    val cardinal = bearingToCardinal(state.bearingDeg)
    val steer = when {
        target == null -> "Gira 360° · busca señal real Wi‑Fi"
        target.distance < 0.6 -> "⛏ YA ENCIMA · EXCAVA AQUÍ · no camines más"
        else -> {
            val turn = normalizedTurnDelta(target.bearing, state.bearingDeg).roundToInt()
            val dist = "%.2f".format(target.distance)
            when {
                kotlin.math.abs(turn) < 12 -> "↑ Camina recto $dist m → PUNTO CERO"
                turn > 0 -> "→ Gira $turn° der, luego camina $dist m"
                else -> "← Gira ${-turn}° izq, luego camina $dist m"
            }
        }
    }
    val relLine = target?.let {
        val rel = describePersonPosition(it.x, it.y, it.depth, state.bearingDeg)
        "${rel.horizontal} · ${rel.vertical}"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = PanelBg),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
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
                    "DATOS REALES Wi‑Fi",
                    color = Color(0xFF66FF99),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
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
            Text("Capas del radar", color = Color(0xFF778877), fontSize = 11.sp)
            Row {
                TextButton(
                    onClick = onModoRescate,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                ) {
                    Text("Rescate", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(
                    onClick = onSoloPersona,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                ) {
                    Text("Solo persona", color = NavOrange, fontSize = 11.sp)
                }
                TextButton(
                    onClick = onTodo,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                ) {
                    Text("Todo", color = Color.Gray, fontSize = 11.sp)
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
                label = { Text("Regla", fontSize = 11.sp) },
                colors = chipColors,
            )
            FilterChip(
                selected = layers.compass,
                onClick = { onToggle("compass") },
                label = { Text("Brújula", fontSize = 11.sp) },
                colors = chipColors,
            )
            FilterChip(
                selected = layers.wifi,
                onClick = { onToggle("wifi") },
                label = { Text("Wi‑Fi", fontSize = 11.sp) },
                colors = chipColors,
            )
            FilterChip(
                selected = layers.heatmap,
                onClick = { onToggle("heatmap") },
                label = { Text("Señal", fontSize = 11.sp) },
                colors = chipColors,
            )
            FilterChip(
                selected = layers.guide,
                onClick = { onToggle("guide") },
                label = { Text("Guía", fontSize = 11.sp) },
                colors = chipColors,
            )
            FilterChip(
                selected = layers.labels,
                onClick = { onToggle("labels") },
                label = { Text("Textos", fontSize = 11.sp) },
                colors = chipColors,
            )
            FilterChip(
                selected = layers.depthRays,
                onClick = { onToggle("depthRays") },
                label = { Text("Rayas", fontSize = 11.sp) },
                colors = chipColors,
            )
            if (show3D) {
                FilterChip(
                    selected = layers.rubble,
                    onClick = { onToggle("rubble") },
                    label = { Text("Escombros", fontSize = 11.sp) },
                    colors = chipColors,
                )
                FilterChip(
                    selected = layers.trail,
                    onClick = { onToggle("trail") },
                    label = { Text("Rastro", fontSize = 11.sp) },
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
                Text("Guía", color = NavYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onZoomOut, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ZoomOut, "Alejar", tint = Cyan, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onZoomIn, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ZoomIn, "Acercar", tint = Cyan, modifier = Modifier.size(20.dp))
            }
            TextButton(
                onClick = onResetView,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            ) {
                Text("Reset", color = Color.Gray, fontSize = 11.sp)
            }
            IconButton(onClick = onToggleFullscreen, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    if (isFullscreen) "Salir pantalla completa" else "Pantalla completa",
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
                "Guía de uso VitalFi",
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
                Text(
                    "Cómo usar la app y qué significa cada indicativo del radar.",
                    color = Color(0xFFAAAAAA),
                    fontSize = 12.sp,
                )

                GuideSection("Cómo usar la aplicación")
                GuideRow("1. Wi‑Fi", "Activa el Wi‑Fi del teléfono cerca del router (no hace falta internet).", Cyan)
                GuideRow("2. Gira 360°", "Camina despacio alrededor del escombro apuntando el teléfono.", NavYellow)
                GuideRow("3. Mira el radar", "Aparecerán puntos de señal y luego personas P1, P2…", Color(0xFFFF6699))
                GuideRow("4. Toca una persona", "Abre el detalle: distancia, profundidad, respiración y rumbo.", Color(0xFFFF3366))
                GuideRow("5. Capas", "Usa «Rescate» (limpio + guía F‑16), «Solo persona» o apaga capas sueltas.", NavOrange)
                GuideRow("6. Botón Guía", "Este menú: siempre disponible en la barra del radar.", NavYellow)

                GuideSection("Puntos en el radar (sin figura humana)")
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
                            "La figura y la esfera se quitaron para no ocupar espacio. " +
                                "Cada persona es un punto pequeño con forma distinta:",
                            color = Color(0xFFCCCCCC),
                            fontSize = 12.sp,
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            GuideDotSample(shape = "cross", color = Color(0xFFFFEE00), label = "Cavar")
                            GuideDotSample(shape = "diamond", color = Color(0xFF4DA6FF), label = "Respira")
                            GuideDotSample(shape = "triangle", color = Color(0xFFFFAA00), label = "Mov.")
                            GuideDotSample(shape = "square", color = Color(0xFFEEEEEE), label = "Señal")
                        }
                    }
                }
                GuideRow("P1, P2…", "Etiqueta del punto. Tócalo para ver mediciones.", Color(0xFFFF3366))
                GuideRow("⊕ cruz en círculo", "PUNTO CERO — persona debajo: excava aquí.", Color(0xFFFFEE00))
                GuideRow("◆ rombo", "Respiración / viva (pulso).", Color(0xFF4DA6FF))
                GuideRow("▲ triángulo", "Movimiento / actividad.", Color(0xFFFFAA00))
                GuideRow("■ cuadrado", "Señal débil en análisis.", Color(0xFFEEEEEE))

                GuideSection("Puntos de señal (mapa Wi‑Fi)")
                GuideRow(
                    "Puntos naranja",
                    "NO es temperatura. Son zonas donde la señal Wi‑Fi vital es más fuerte por dirección. " +
                        "Más brillante = más señal en ese rumbo.",
                    Color(0xFFFF6600),
                )

                GuideSection("Profundidad")
                GuideRow("Texto ↓ Xm", "Metros bajo el suelo junto al punto.", Color(0xFFCCCCCC))
                GuideRow("Capa Rayas", "Opcional: línea vertical de profundidad (OFF en Rescate).", Color(0xFFFFEE00))

                GuideSection("Orientación y distancia")
                GuideRow("N / E / S / O", "Norte real de la brújula (N en rojo).", Color(0xFFFF4444))
                GuideRow("Línea cyan ↑", "Tu frente (hacia dónde apuntas). Siempre hacia arriba.", Cyan)
                GuideRow("▲ naranja (HUD)", "Director: gira hasta centrarlo = vas hacia la persona (búsqueda del tesoro).", NavOrange)
                GuideRow("Flecha → persona", "Camino grueso desde ti hasta el punto. Sin aguja giratoria de radar.", NavOrange)
                GuideRow("Anillos / Regla", "Metros desde ti (centro = 0 m).", Color(0xFF66FFCC))
                GuideRow("Barra de escala", "Referencia visual de metros en la esquina.", Cyan)

                GuideSection("Capas (chips para ocultar)")
                GuideRow("Regla", "Anillos y marcas de metros.", Color(0xFF66FFCC))
                GuideRow("Brújula", "Rosa de rumbos N/E/S/O y frente.", Color(0xFFFF4444))
                GuideRow("Wi‑Fi", "Círculo de cobertura estimada del router.", Color(0xFF00FFCC))
                GuideRow("Señal", "Puntos de fuerza de señal por dirección.", Color(0xFFFF6600))
                GuideRow("Rayas", "Líneas verticales de profundidad (por defecto OFF).", Color(0xFFFFEE00))
                GuideRow("Guía", "Flecha → PUNTO CERO. En el sitio: ⛏ EXCAVA AQUÍ.", NavOrange)
                GuideRow("Textos", "Etiquetas de metros y estado.", Color(0xFFCCCCCC))
                GuideRow("Escombros", "Corte 3D del terreno (solo vista 3D).", Color(0xFF445544))
                GuideRow("Rastro", "Camino recorrido de la detección (3D).", Color(0xFFFF6699))
                GuideRow("Solo persona", "Apaga el ruido y deja solo los puntos P1, P2…", NavOrange)

                GuideSection("Zonas de proximidad")
                GuideRow("⛏ PUNTO CERO", "Menos de 0.6 m: persona debajo. Cruz ⊕ — excava aquí.", Color(0xFFFFEE00))
                GuideRow("◆ RESPIRA / ♥ VIVA", "Rombo = respiración o pulso.", Color(0xFF4DA6FF))
                GuideRow("▲ MOVIMIENTO", "Triángulo = actividad detectada.", Color(0xFFFFAA00))
                GuideRow("? SEÑAL", "Cuadrado = señal débil en análisis.", Color(0xFFEEEEEE))
                GuideRow("MUY CERCA", "Aprox. menos de 1 m.", Color(0xFFFF3366))
                GuideRow("Modo daltónico", "En ⚙️ Configuración: formas + azul/ámbar/blanco.", Color(0xFF80C0FF))
                GuideRow("CERCA", "Aprox. 1–2 m.", Color(0xFFFF6699))
                GuideRow("MEDIA", "Aprox. 2–3.5 m.", NavYellow)
                GuideRow("LEJOS", "Más de ~3.5 m o señal débil.", Color(0xFF888888))

                GuideSection("Controles")
                GuideRow("Reiniciar", "Arriba a la derecha: borra el mapa y empieza de nuevo.", NavOrange)
                GuideRow("＋ / − zoom", "Acercar o alejar el mapa.", Cyan)
                GuideRow("Reset", "Vuelve zoom y panorámica al inicio.", Color.Gray)
                GuideRow("Pantalla completa", "Radar a toda la pantalla.", Cyan)
                GuideRow("Altura ±", "Tu altura de escaneo sobre el suelo.", Color(0xFF66FF99))
                GuideRow("Vista 2D / 3D", "Cambia entre mapa plano y vista 3D.", Cyan)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Entendido", color = Cyan)
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
