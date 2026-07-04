package com.whofi.vitalfi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.whofi.vitalfi.VitalFiUiState
import kotlin.math.roundToInt

private val BgDark = Color(0xFF0A0F0A)
private val Cyan = Color(0xFF00FFCC)
private val NavYellow = Color(0xFFFFCC00)
private val NavOrange = Color(0xFFFF9900)

@Composable
fun CompassNavigationBanner(state: VitalFiUiState, modifier: Modifier = Modifier) {
    val target = navigationTarget(state.victims, state.selectedVictim?.id)
    val bearing = state.bearingDeg.roundToInt()
    val cardinal = bearingToCardinal(state.bearingDeg)

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121A12)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "Brújula en vivo — ▲ frente del teléfono | N (rojo) = Norte real",
                color = Color(0xFF888888),
                fontSize = 11.sp,
            )
            Text(
                "Tu rumbo: $bearing° $cardinal (${state.compassLabel})",
                color = Cyan,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
            )
            when {
                target != null -> {
                    val turn = normalizedTurnDelta(target.bearing, state.bearingDeg).roundToInt()
                    val targetCardinal = bearingToCardinal(target.bearing)
                    val steer = when {
                        kotlin.math.abs(turn) < 12 ->
                            "↑ Ve recto hacia $targetCardinal (${target.bearing.roundToInt()}°)"
                        turn > 0 ->
                            "↻ Gira $turn° a la DERECHA → $targetCardinal (${target.bearing.roundToInt()}°)"
                        else ->
                            "↺ Gira ${-turn}° a la IZQUIERDA → $targetCardinal (${target.bearing.roundToInt()}°)"
                    }
                    Text(
                        "→ ${target.label}: ${"%.1f".format(target.distance)} m",
                        color = NavOrange,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        steer,
                        color = NavYellow,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                state.position != null -> {
                    val pos = state.position
                    val hint = buildNavigationHint(pos.bearing, state.bearingDeg, pos.distance)
                    Text(
                        hint,
                        color = NavYellow,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp,
                    )
                }
                else -> {
                    Text(
                        "Gira 360° alrededor del escombro. La línea cyan = adónde apuntas ahora.",
                        color = Color(0xFFAAAAAA),
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
fun RadarPanel(
    state: VitalFiUiState,
    modifier: Modifier = Modifier,
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
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        RadarToolbar(
            viewport = state.radarViewport,
            isFullscreen = false,
            onZoomIn = onZoomIn,
            onZoomOut = onZoomOut,
            onResetView = onResetView,
            onToggleFullscreen = onToggleFullscreen,
        )
        CompassNavigationBanner(state)
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
                onVictimClick = onVictimClick,
                onViewportChange = onViewportChange,
                modifier = Modifier.fillMaxWidth().height(300.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = onViewAzimLeft) { Text("◀ Q", color = Cyan) }
                TextButton(onClick = onViewElevUp) { Text("▲ W", color = Cyan) }
                TextButton(onClick = onResetView3D) { Text("V reset", color = Color.Gray) }
                TextButton(onClick = onViewElevDown) { Text("▼ S", color = Cyan) }
                TextButton(onClick = onViewAzimRight) { Text("E ▶", color = Cyan) }
            }
        } else {
            RadarView(
                bearingDeg = state.bearingDeg,
                victims = state.victims,
                selectedVictimId = state.selectedVictim?.id,
                heatmap = state.heatmap,
                wifiCoverageRadiusM = state.wifiCoverageRadiusM,
                viewport = state.radarViewport,
                onVictimClick = onVictimClick,
                onViewportChange = onViewportChange,
                modifier = Modifier.fillMaxWidth().height(280.dp),
            )
        }
            Text(
                "Brújula N/E/S/O en radar · ▲ = frente del teléfono · naranja = hacia víctima",
                color = Color(0xFF666666),
                fontSize = 11.sp,
            )
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
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(BgDark)
            .zIndex(10f),
    ) {
        Column(Modifier.fillMaxSize()) {
            RadarToolbar(
                viewport = state.radarViewport,
                isFullscreen = true,
                onZoomIn = onZoomIn,
                onZoomOut = onZoomOut,
                onResetView = onResetView,
                onToggleFullscreen = onCloseFullscreen,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D140D))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            CompassNavigationBanner(
                state = state,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
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
                    onVictimClick = onVictimClick,
                    onViewportChange = onViewportChange,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    TextButton(onClick = onViewAzimLeft) { Text("◀ Q", color = Cyan) }
                    TextButton(onClick = onViewElevUp) { Text("▲ W", color = Cyan) }
                    TextButton(onClick = onResetView3D) { Text("V reset", color = Color.Gray) }
                    TextButton(onClick = onViewElevDown) { Text("▼ S", color = Cyan) }
                    TextButton(onClick = onViewAzimRight) { Text("E ▶", color = Cyan) }
                }
            } else {
                RadarView(
                    bearingDeg = state.bearingDeg,
                    victims = state.victims,
                    selectedVictimId = state.selectedVictim?.id,
                    heatmap = state.heatmap,
                    wifiCoverageRadiusM = state.wifiCoverageRadiusM,
                    viewport = state.radarViewport,
                    onVictimClick = onVictimClick,
                    onViewportChange = onViewportChange,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }

        if (state.victims.isNotEmpty()) {
            val target = navigationTarget(state.victims, state.selectedVictim?.id)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xCC101810)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
                    .fillMaxWidth(),
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Personas: ${state.victims.size} | Zoom ${"%.1f".format(state.radarViewport.zoom)}x",
                        color = RadarGreen,
                        fontSize = 12.sp,
                    )
                    target?.let {
                        Text(
                            text = "Objetivo ${it.label}: ${it.bearing.roundToInt()}° ${bearingToCardinal(it.bearing)}",
                            color = NavYellow,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
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
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (isFullscreen) "Radar pantalla completa" else "Radar",
            color = Cyan,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${"%.1f".format(viewport.zoom)}x",
                color = Color(0xFF888888),
                fontSize = 12.sp,
                modifier = Modifier.padding(end = 4.dp),
            )
            IconButton(onClick = onZoomOut) {
                Icon(Icons.Default.ZoomOut, "Alejar", tint = Cyan)
            }
            IconButton(onClick = onZoomIn) {
                Icon(Icons.Default.ZoomIn, "Acercar", tint = Cyan)
            }
            TextButton(onClick = onResetView) {
                Text("Reset", color = Color.Gray, fontSize = 11.sp)
            }
            IconButton(onClick = onToggleFullscreen) {
                Icon(
                    if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    if (isFullscreen) "Salir pantalla completa" else "Pantalla completa",
                    tint = Cyan,
                )
            }
        }
    }
}
