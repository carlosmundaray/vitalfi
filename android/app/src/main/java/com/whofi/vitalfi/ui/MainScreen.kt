package com.whofi.vitalfi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiFind
import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whofi.vitalfi.BuildConfig
import com.whofi.vitalfi.R
import com.whofi.vitalfi.VitalFiUiState
import com.whofi.vitalfi.dsp.HeatPoint
import com.whofi.vitalfi.dsp.PositionEstimate
import com.whofi.vitalfi.dsp.TrappedVictim
import com.whofi.vitalfi.wifi.ScannedNetwork
import com.whofi.vitalfi.wifi.WifiCollectMode
import androidx.compose.ui.unit.IntSize
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val BgDark = Color(0xFF0A0F0A)
private val Cyan = Color(0xFF00FFCC)
private val Red = Color(0xFFFF3366)
private val Orange = Color(0xFFFF9900)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    state: VitalFiUiState,
    onReset: () -> Unit,
    onBearingLeft: () -> Unit,
    onBearingRight: () -> Unit,
    onConnectedMode: () -> Unit,
    onPassiveMode: () -> Unit,
    onSelectNetwork: (ScannedNetwork) -> Unit,
    onDismissPicker: () -> Unit,
    onToggleRadar3D: () -> Unit,
    onViewAzimLeft: () -> Unit,
    onViewAzimRight: () -> Unit,
    onViewElevUp: () -> Unit,
    onViewElevDown: () -> Unit,
    onResetView3D: () -> Unit,
    onExportCsv: () -> Unit,
    onDismissToast: () -> Unit,
    onSelectVictim: (Int) -> Unit,
    onDismissVictim: () -> Unit,
    onViewportChange: (RadarViewport) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onResetRadarView: () -> Unit,
    onToggleRadarFullscreen: () -> Unit,
    onCloseRadarFullscreen: () -> Unit,
    onSetAlertSoundEnabled: (Boolean) -> Unit,
) {
    var showAbout by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val view = LocalView.current

    DisposableEffect(state.radarFullscreen) {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        if (state.radarFullscreen) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Box(Modifier.fillMaxSize().background(BgDark)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "VitalFi — Detección bajo escombros",
                    color = Cyan,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { showAbout = true }) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Cyan)
                    Text(stringResource(R.string.about_button), color = Cyan, fontSize = 12.sp)
                }
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_title), tint = Cyan)
                }
            }

            Text(
                text = stringResource(R.string.offline_badge),
                color = Color(0xFF66FF99),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(R.string.offline_hint),
                color = Color(0xFF557755),
                fontSize = 11.sp,
            )

            ModeSelector(state, onConnectedMode, onPassiveMode)

            StatusCard(state)

            VictimsSummaryCard(state, onSelectVictim)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = state.showRadar3D,
                    onClick = onToggleRadar3D,
                    label = { Text(if (state.showRadar3D) "Radar 3D" else "Radar 2D") },
                    leadingIcon = { Icon(Icons.Default.ViewInAr, null) },
                )
                Button(onClick = onExportCsv) {
                    Icon(Icons.Default.Download, null)
                    Text(" CSV")
                }
            }

            RadarPanel(
                state = state,
                onVictimClick = onSelectVictim,
                onViewportChange = onViewportChange,
                onZoomIn = onZoomIn,
                onZoomOut = onZoomOut,
                onResetView = onResetRadarView,
                onToggleFullscreen = onToggleRadarFullscreen,
                onViewAzimLeft = onViewAzimLeft,
                onViewAzimRight = onViewAzimRight,
                onViewElevUp = onViewElevUp,
                onViewElevDown = onViewElevDown,
                onResetView3D = onResetView3D,
            )

            SignalChart(
                samples = state.recentQuality,
                modifier = Modifier.fillMaxWidth().height(120.dp),
            )

            NavigationHint(state)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                IconButton(onClick = onBearingLeft) {
                    Icon(Icons.Default.RotateLeft, "Girar izq", tint = Cyan)
                }
                Button(onClick = onReset) {
                    Icon(Icons.Default.Refresh, null)
                    Text(" Reiniciar")
                }
                IconButton(onClick = onBearingRight) {
                    Icon(Icons.Default.RotateRight, "Girar der", tint = Cyan)
                }
            }

            Text(
                text = when (state.collectMode) {
                    WifiCollectMode.CONNECTED ->
                        stringResource(R.string.mode_connected_hint) + " Gira 360° alrededor del escombro."
                    WifiCollectMode.PASSIVE_SCAN ->
                        stringResource(R.string.mode_passive_hint) + " Gira 360° para ubicar en el radar."
                },
                color = Color(0xFF888888),
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }

        state.toastMessage?.let { msg ->
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                action = { TextButton(onClick = onDismissToast) { Text("OK") } },
            ) { Text(msg) }
            LaunchedEffect(msg) {
                kotlinx.coroutines.delay(4000)
                onDismissToast()
            }
        }
    }

    if (state.showNetworkPicker) {
        NetworkPickerDialog(
            networks = state.scannedNetworks,
            isScanning = state.isScanningNetworks,
            onSelect = onSelectNetwork,
            onDismiss = onDismissPicker,
        )
    }

    if (showAbout) {
        AboutDialog(
            onDismiss = { showAbout = false },
            onOpenUrl = { url ->
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            },
        )
    }

    if (showSettings) {
        SettingsDialog(
            alertSoundEnabled = state.alertSoundEnabled,
            onToggleAlertSound = onSetAlertSoundEnabled,
            onDismiss = { showSettings = false },
        )
    }

    state.selectedVictim?.let { victim ->
        VictimDetailDialog(
            victim = victim,
            onDismiss = onDismissVictim,
        )
    }

    if (state.radarFullscreen) {
        RadarFullscreenOverlay(
            state = state,
            onVictimClick = onSelectVictim,
            onViewportChange = onViewportChange,
            onZoomIn = onZoomIn,
            onZoomOut = onZoomOut,
            onResetView = onResetRadarView,
            onCloseFullscreen = onCloseRadarFullscreen,
            onViewAzimLeft = onViewAzimLeft,
            onViewAzimRight = onViewAzimRight,
            onViewElevUp = onViewElevUp,
            onViewElevDown = onViewElevDown,
            onResetView3D = onResetView3D,
        )
    }
}

@Composable
private fun SettingsDialog(
    alertSoundEnabled: Boolean,
    onToggleAlertSound: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settings_alert_sound),
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            stringResource(R.string.settings_alert_sound_hint),
                            color = Color(0xFF888888),
                            fontSize = 11.sp,
                        )
                    }
                    Switch(
                        checked = alertSoundEnabled,
                        onCheckedChange = onToggleAlertSound,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.about_close))
            }
        },
    )
}

@Composable
private fun AboutDialog(
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val whatsappUrl = stringResource(R.string.about_whatsapp_url)
    val facebookUrl = stringResource(R.string.about_facebook_url)
    val websiteUrl = stringResource(R.string.about_website_url)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                    color = Color.Gray,
                    fontSize = 12.sp,
                )
                Text(
                    stringResource(R.string.about_developer),
                    color = Color(0xFF888888),
                    fontSize = 12.sp,
                )
                Text(
                    stringResource(R.string.about_name),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
                AboutLink(
                    label = stringResource(R.string.about_whatsapp),
                    onClick = { onOpenUrl(whatsappUrl) },
                )
                AboutLink(
                    label = stringResource(R.string.about_facebook),
                    onClick = { onOpenUrl(facebookUrl) },
                )
                AboutLink(
                    label = stringResource(R.string.about_website),
                    onClick = { onOpenUrl(websiteUrl) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.about_close))
            }
        },
    )
}

@Composable
private fun AboutLink(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = Cyan,
        fontSize = 14.sp,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun ModeSelector(
    state: VitalFiUiState,
    onConnected: () -> Unit,
    onPassive: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = state.collectMode == WifiCollectMode.CONNECTED,
            onClick = onConnected,
            label = { Text("Conectado") },
            leadingIcon = { Icon(Icons.Default.Wifi, null) },
        )
        FilterChip(
            selected = state.collectMode == WifiCollectMode.PASSIVE_SCAN,
            onClick = onPassive,
            label = { Text("Escaneo pasivo") },
            leadingIcon = { Icon(Icons.Default.WifiFind, null) },
        )
    }
}

@Composable
private fun NetworkPickerDialog(
    networks: List<ScannedNetwork>,
    isScanning: Boolean,
    onSelect: (ScannedNetwork) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Selecciona red del router") },
        text = {
            if (networks.isEmpty()) {
                Text(
                    if (isScanning) "Buscando redes Wi-Fi..."
                    else stringResource(R.string.wifi_required),
                )
            } else {
                LazyColumn {
                    items(networks) { net ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(net) }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(net.ssid, fontWeight = FontWeight.SemiBold)
                                Text(net.bandLabel, fontSize = 11.sp, color = Color.Gray)
                            }
                            Text("${net.rssiDbm} dBm", fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

@Composable
private fun StatusCard(state: VitalFiUiState) {
    val statusColor = when {
        state.isBreathing -> Red
        state.isActivity -> Orange
        state.calibrating -> Color(0xFFFFCC00)
        else -> Cyan
    }
    val modeLabel = when (state.collectMode) {
        WifiCollectMode.CONNECTED -> "conectado"
        WifiCollectMode.PASSIVE_SCAN -> "escaneo pasivo"
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121A12)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Red: ${state.ssid.ifBlank { "—" }} [$modeLabel]", color = Color.White, fontSize = 13.sp)
            Text(
                "Wi-Fi: ${if (state.wifiEnabled) "activo" else "apagado"} | " +
                    "Monitoreo: ${if (state.monitoring) "sí" else "no"}" +
                    (if (state.wifiWithoutInternet) " (sin internet)" else "") +
                    " | ${state.compassLabel}",
                color = Color(0xFF888888),
                fontSize = 11.sp,
            )
            Text("RSSI: ${state.rssiDbm?.let { "$it dBm" } ?: "—"}", color = Color(0xFFAAAAAA), fontSize = 12.sp)
            Text(
                state.mlStatus,
                color = if (state.mlReady) Color(0xFF66FF99) else Color(0xFF777777),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
            Text(state.status, color = statusColor, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            if (state.calibrating) {
                Text("Calibrando... ${state.calibRemainingSec}s", color = Color(0xFFFFCC00), fontSize = 12.sp)
            }
            if (state.rubbleConfidence > 0.05 && !state.calibrating) {
                Text(
                    "Confianza escombros: ${"%.0f".format(state.rubbleConfidence * 100)}%",
                    color = if (state.isBreathing) Red else Orange,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (state.victims.isNotEmpty()) {
                Text(
                    "Personas en sitio: ${state.victims.size} — toca un punto en el radar",
                    color = RadarGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Text(state.hint, color = Color(0xFFAAAAAA), fontSize = 12.sp)
            if (state.isBreathing || state.isActivity || state.heartBeating || state.position != null) {
                Text(
                    "Resp: ${if (state.isBreathing) "SÍ" else "no"} ${"%.0f".format(state.respRate)} rpm | " +
                        "Pulso: ${if (state.heartBeating) "SÍ" else "no"} ${"%.0f".format(state.heartRateBpm)} lpm | " +
                        "Conf: ${"%.0f".format(state.confidence * 100)}% | ${state.proximity}",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Text(
                "Muestras: ${state.sampleCount}/${state.maxSamples} | Rumbo: ${state.bearingDeg.roundToInt()}° ${bearingToCardinal(state.bearingDeg)}",
                color = Color(0xFF666666),
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun NavigationHint(state: VitalFiUiState) {
    val pos = state.position
    val target = state.selectedVictim ?: state.victims.firstOrNull()
    if (pos == null && target == null) {
        if (state.rubbleConfidence > 0.12 && (state.isActivity || state.isBreathing)) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2218))) {
                Text(
                    text = "Gira 360° lentamente alrededor del escombro. El radar muestra Norte (N) y tu rumbo actual.",
                    modifier = Modifier.padding(10.dp),
                    color = Color(0xFFFFCC00),
                    fontSize = 13.sp,
                )
            }
        }
        return
    }

    val targetBearing = target?.bearing ?: pos!!.bearing
    val targetDistance = target?.distance ?: pos!!.distance
    val steer = buildNavigationHint(targetBearing, state.bearingDeg, targetDistance)
    val depthLine = pos?.let { " | Prof: ${"%.1f".format(-it.depth)} m bajo escombros" } ?: ""
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2218))) {
        Text(
            text = "$steer$depthLine\nTu rumbo: ${state.bearingDeg.roundToInt()}° ${bearingToCardinal(state.bearingDeg)}",
            modifier = Modifier.padding(10.dp),
            color = Color(0xFFFFCC00),
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VictimsSummaryCard(state: VitalFiUiState, onSelectVictim: (Int) -> Unit) {
    if (state.victims.isEmpty()) return
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101810)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Atrapadas en el sitio: ${state.victims.size}",
                color = RadarGreen,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                state.victims.forEach { victim ->
                    FilterChip(
                        selected = state.selectedVictim?.id == victim.id,
                        onClick = { onSelectVictim(victim.id) },
                        label = {
                            Text(
                                "${victim.label} (${"%.1f".format(victim.x)},${"%.1f".format(victim.y)})",
                                fontSize = 11.sp,
                            )
                        },
                    )
                }
            }
            Text(
                "Toca un punto P1, P2… en el radar para ver pulso, respiración y coordenadas.",
                color = Color(0xFF777777),
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun VictimDetailDialog(victim: TrappedVictim, onDismiss: () -> Unit) {
    val color = victimColor(victim)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("${victim.label} — ${victim.status}", color = color, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("100% offline — datos locales Wi-Fi", color = RadarGreen, fontSize = 11.sp)
                VictimDetailRow("Coordenadas X", "${"%.2f".format(victim.x)} m")
                VictimDetailRow("Coordenadas Y", "${"%.2f".format(victim.y)} m")
                VictimDetailRow("Profundidad", "${"%.2f".format(-victim.depth)} m bajo escombros")
                VictimDetailRow("Rumbo", "${victim.bearing.roundToInt()}° ${bearingToCardinal(victim.bearing)}")
                VictimDetailRow("Distancia", "${"%.2f".format(victim.distance)} m")
                VictimDetailRow("Proximidad", victim.proximity)
                VictimDetailRow(
                    "Respirando",
                    if (victim.isBreathing) "SÍ — ${"%.1f".format(victim.respRate)} rpm" else "No detectado",
                )
                VictimDetailRow(
                    "Corazón latiendo",
                    if (victim.heartBeating) "SÍ — ${"%.0f".format(victim.heartRateBpm)} lpm" else "No detectado",
                )
                VictimDetailRow("Actividad", if (victim.isActivity) "Sí" else "No")
                VictimDetailRow("Confianza vital", "${"%.0f".format(victim.confidence * 100)}%")
                VictimDetailRow("Confianza escombros", "${"%.0f".format(victim.rubbleConfidence * 100)}%")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        },
    )
}

@Composable
private fun VictimDetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color(0xFF888888), fontSize = 13.sp)
        Text(value, color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun RadarView(
    bearingDeg: Double,
    victims: List<TrappedVictim>,
    selectedVictimId: Int?,
    heatmap: List<HeatPoint>,
    wifiCoverageRadiusM: Double,
    viewport: RadarViewport,
    onVictimClick: (Int) -> Unit,
    onViewportChange: (RadarViewport) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0D140D)), modifier = modifier) {
        val labelPaint = remember {
            android.graphics.Paint().apply {
                color = android.graphics.Color.GRAY
                textSize = 24f
            }
        }
        val tagPaint = remember {
            android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 26f
                isFakeBoldText = true
            }
        }
        var canvasSize by remember { mutableStateOf(IntSize.Zero) }
        val divisor = 6f

        fun projectVictim(victim: TrappedVictim): Offset {
            val cx = canvasSize.width / 2f + viewport.panX
            val cy = canvasSize.height / 2f + viewport.panY
            val half = minOf(canvasSize.width, canvasSize.height) / 2f
            val scale = half / divisor * viewport.zoom
            val (rx, ry) = rotateMapCoords(victim.x, victim.y, bearingDeg)
            return Offset(cx + rx.toFloat() * scale, cy - ry.toFloat() * scale)
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .padding(8.dp)
                .radarGestures(
                    viewport = viewport,
                    victims = victims,
                    projectVictim = ::projectVictim,
                    onVictimClick = onVictimClick,
                    onViewportChange = onViewportChange,
                ),
        ) {
            canvasSize = IntSize(size.width.toInt(), size.height.toInt())

            drawRadarGrid2D(divisor, viewport, labelPaint)
            drawWifiCoverage2D(wifiCoverageRadiusM, divisor, viewport, labelPaint)
            drawHeatmap2D(heatmap, bearingDeg, divisor, viewport)
            drawCompassRose2D(bearingDeg, divisor, viewport, labelPaint)
            drawNavigationGuide2D(
                bearingDeg = bearingDeg,
                target = navigationTarget(victims, selectedVictimId),
                divisor = divisor,
                viewport = viewport,
                labelPaint = labelPaint,
                tagPaint = tagPaint,
            )
            drawVictims2D(victims, bearingDeg, divisor, viewport, selectedVictimId, labelPaint, tagPaint)

            drawContext.canvas.nativeCanvas.drawText(
                "Radar 2D — ▲ frente del teléfono | N rojo = Norte real | gira para orientarte",
                8f,
                size.height - 8f,
                labelPaint,
            )
        }
    }
}

@Composable
fun SignalChart(samples: List<Double>, modifier: Modifier = Modifier) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0D140D)), modifier = modifier) {
        if (samples.size < 2) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Esperando señal Wi-Fi (dBm)...", color = Color.Gray, fontSize = 12.sp)
            }
        } else {
            Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                val minV = samples.minOrNull() ?: -90.0
                val maxV = samples.maxOrNull() ?: -30.0
                val range = (maxV - minV).coerceAtLeast(0.5)
                val path = Path()
                samples.forEachIndexed { i, v ->
                    val x = i.toFloat() / (samples.size - 1) * size.width
                    val y = size.height - ((v - minV) / range).toFloat() * size.height
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, Cyan, style = Stroke(2f))
                drawContext.canvas.nativeCanvas.drawText(
                    "RSSI dBm",
                    4f,
                    20f,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.GRAY
                        textSize = 24f
                    },
                )
            }
        }
    }
}
