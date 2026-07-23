package com.vitalfi.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import com.vitalfi.BuildConfig
import com.vitalfi.R
import com.vitalfi.VitalFiUiState
import com.vitalfi.dsp.HeatPoint
import com.vitalfi.dsp.PositionEstimate
import com.vitalfi.dsp.describePersonPosition
import com.vitalfi.dsp.TrappedVictim
import com.vitalfi.wifi.ScannedNetwork
import com.vitalfi.wifi.WifiCollectMode
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
    onRenameVictim: (Int, String) -> Unit,
    onViewportChange: (RadarViewport) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onResetRadarView: () -> Unit,
    onToggleRadarFullscreen: () -> Unit,
    onCloseRadarFullscreen: () -> Unit,
    onSetAlertSoundEnabled: (Boolean) -> Unit,
    onSetTrappedOnlyMode: (Boolean) -> Unit,
    onMarkRubbleZone: () -> Unit,
    onClearRubbleZone: () -> Unit,
    onExcludeRescuer: () -> Unit,
    onClearExclusions: () -> Unit,
    onAdjustElevation: (Double) -> Unit,
    onResetElevation: () -> Unit,
    onToggleAutoElevation: (Boolean) -> Unit,
    onToggleRadarLayer: (String) -> Unit,
    onRadarLayersSoloPersona: () -> Unit,
    onRadarLayersTodo: () -> Unit,
    onRadarLayersModoRescate: () -> Unit,
    onSetVibrationEnabled: (Boolean) -> Unit,
    onSetKeepScreenOn: (Boolean) -> Unit,
    onSetDetectionSensitivity: (Double) -> Unit,
    onSetShowRadar3D: (Boolean) -> Unit,
    onSetRubbleZoneRadius: (Double) -> Unit,
    onSetDefaultWifiMode: (WifiCollectMode) -> Unit,
    onSetColorblindMode: (Boolean) -> Unit,
) {
    var showAbout by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val view = LocalView.current

    DisposableEffect(state.keepScreenOn) {
        view.keepScreenOn = state.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

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

            TrappedFilterControls(
                state = state,
                onMarkRubbleZone = onMarkRubbleZone,
                onClearRubbleZone = onClearRubbleZone,
                onExcludeRescuer = onExcludeRescuer,
                onClearExclusions = onClearExclusions,
            )

            VictimsSummaryCard(state, onSelectVictim)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = state.showRadar3D,
                    onClick = onToggleRadar3D,
                    label = { Text(if (state.showRadar3D) "Vista 3D" else "Radar 2D") },
                    leadingIcon = { Icon(Icons.Default.ViewInAr, null) },
                )
                Button(
                    onClick = onReset,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.padding(end = 2.dp))
                    Text("Reiniciar", fontSize = 12.sp)
                }
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
                onAdjustElevation = onAdjustElevation,
                onResetElevation = onResetElevation,
                onToggleAutoElevation = onToggleAutoElevation,
                onToggleRadarLayer = onToggleRadarLayer,
                onRadarLayersSoloPersona = onRadarLayersSoloPersona,
                onRadarLayersTodo = onRadarLayersTodo,
                onRadarLayersModoRescate = onRadarLayersModoRescate,
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
                    Icon(Icons.AutoMirrored.Filled.RotateLeft, "Girar izq", tint = Cyan)
                }
                IconButton(onClick = onBearingRight) {
                    Icon(Icons.AutoMirrored.Filled.RotateRight, "Girar der", tint = Cyan)
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
            state = state,
            onToggleAlertSound = onSetAlertSoundEnabled,
            onToggleTrappedOnly = onSetTrappedOnlyMode,
            onToggleVibration = onSetVibrationEnabled,
            onToggleKeepScreenOn = onSetKeepScreenOn,
            onSetSensitivity = onSetDetectionSensitivity,
            onSetShowRadar3D = onSetShowRadar3D,
            onSetRubbleZoneRadius = onSetRubbleZoneRadius,
            onSetDefaultWifiMode = onSetDefaultWifiMode,
            onSetColorblindMode = onSetColorblindMode,
            onToggleRadarLayer = onToggleRadarLayer,
            onRadarLayersModoRescate = onRadarLayersModoRescate,
            onRadarLayersSoloPersona = onRadarLayersSoloPersona,
            onRadarLayersTodo = onRadarLayersTodo,
            onMarkRubbleZone = onMarkRubbleZone,
            onClearRubbleZone = onClearRubbleZone,
            onExcludeRescuer = onExcludeRescuer,
            onClearExclusions = onClearExclusions,
            onToggleAutoElevation = onToggleAutoElevation,
            onAdjustElevation = onAdjustElevation,
            onResetElevation = onResetElevation,
            onDismiss = { showSettings = false },
        )
    }

    state.selectedVictim?.let { victim ->
        VictimDetailDialog(
            victim = victim,
            onDismiss = onDismissVictim,
            onRename = { name -> onRenameVictim(victim.id, name) },
            phoneBearingDeg = state.bearingDeg,
            colorblind = state.colorblindMode,
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
            onAdjustElevation = onAdjustElevation,
            onResetElevation = onResetElevation,
            onToggleAutoElevation = onToggleAutoElevation,
            onToggleRadarLayer = onToggleRadarLayer,
            onRadarLayersSoloPersona = onRadarLayersSoloPersona,
            onRadarLayersTodo = onRadarLayersTodo,
            onRadarLayersModoRescate = onRadarLayersModoRescate,
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    hint: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Medium)
            Text(hint, color = Color(0xFF888888), fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        title,
        color = Cyan,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun SettingsOptionButtons(
    options: List<Pair<String, Double>>,
    selectedValue: Double,
    tolerance: Double = 5.0,
    onSelect: (Double) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (label, value) ->
            val selected = kotlin.math.abs(selectedValue - value) < tolerance
            Button(
                onClick = { onSelect(value) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selected) Cyan else Color(0xFF1A2A1A),
                    contentColor = if (selected) Color.Black else Color(0xFFAACCAA),
                ),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
            ) {
                Text(label, fontSize = 11.sp, maxLines = 2)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsDialog(
    state: VitalFiUiState,
    onToggleAlertSound: (Boolean) -> Unit,
    onToggleTrappedOnly: (Boolean) -> Unit,
    onToggleVibration: (Boolean) -> Unit,
    onToggleKeepScreenOn: (Boolean) -> Unit,
    onSetSensitivity: (Double) -> Unit,
    onSetShowRadar3D: (Boolean) -> Unit,
    onSetRubbleZoneRadius: (Double) -> Unit,
    onSetDefaultWifiMode: (WifiCollectMode) -> Unit,
    onSetColorblindMode: (Boolean) -> Unit,
    onToggleRadarLayer: (String) -> Unit,
    onRadarLayersModoRescate: () -> Unit,
    onRadarLayersSoloPersona: () -> Unit,
    onRadarLayersTodo: () -> Unit,
    onMarkRubbleZone: () -> Unit,
    onClearRubbleZone: () -> Unit,
    onExcludeRescuer: () -> Unit,
    onClearExclusions: () -> Unit,
    onToggleAutoElevation: (Boolean) -> Unit,
    onAdjustElevation: (Double) -> Unit,
    onResetElevation: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // —— Detección ——
                SettingsSectionTitle(stringResource(R.string.settings_section_detection))
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_trapped_only),
                    hint = stringResource(R.string.settings_trapped_only_hint),
                    checked = state.trappedOnlyMode,
                    onToggle = onToggleTrappedOnly,
                )
                Column {
                    Text(
                        stringResource(R.string.settings_sensitivity),
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        stringResource(R.string.settings_sensitivity_hint),
                        color = Color(0xFF888888),
                        fontSize = 11.sp,
                    )
                    SettingsOptionButtons(
                        options = listOf(
                            stringResource(R.string.settings_sensitivity_low) to 1.2,
                            stringResource(R.string.settings_sensitivity_normal) to 2.0,
                            stringResource(R.string.settings_sensitivity_high) to 3.0,
                        ),
                        selectedValue = state.detectionSensitivity,
                        tolerance = 0.3,
                        onSelect = onSetSensitivity,
                    )
                }

                // —— Zona de búsqueda ——
                SettingsSectionTitle(stringResource(R.string.settings_section_zone))
                Column {
                    Text(
                        stringResource(R.string.settings_rubble_radius),
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        stringResource(R.string.settings_rubble_radius_hint),
                        color = Color(0xFF888888),
                        fontSize = 11.sp,
                    )
                    SettingsOptionButtons(
                        options = listOf(
                            stringResource(R.string.settings_rubble_radius_narrow) to 35.0,
                            stringResource(R.string.settings_rubble_radius_normal) to 55.0,
                            stringResource(R.string.settings_rubble_radius_wide) to 90.0,
                        ),
                        selectedValue = state.rubbleZoneRadiusDeg,
                        onSelect = onSetRubbleZoneRadius,
                    )
                }
                state.rubbleZoneLabel?.let { label ->
                    Text(label, color = Cyan, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
                if (state.excludedRescuerCount > 0) {
                    Text(
                        "Sectores excluidos: ${state.excludedRescuerCount}",
                        color = Color(0xFF888888),
                        fontSize = 11.sp,
                    )
                }
                Text(
                    stringResource(R.string.mark_rubble_zone_hint),
                    color = Color(0xFF666666),
                    fontSize = 11.sp,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = onMarkRubbleZone) {
                        Text(stringResource(R.string.mark_rubble_zone), fontSize = 11.sp)
                    }
                    if (state.rubbleZoneLabel != null) {
                        TextButton(onClick = onClearRubbleZone) {
                            Text("Quitar zona", fontSize = 11.sp)
                        }
                    }
                    Button(onClick = onExcludeRescuer) {
                        Text(stringResource(R.string.exclude_rescuer), fontSize = 11.sp)
                    }
                    if (state.excludedRescuerCount > 0) {
                        TextButton(onClick = onClearExclusions) {
                            Text(stringResource(R.string.clear_exclusions), fontSize = 11.sp)
                        }
                    }
                }

                // —— Wi-Fi ——
                SettingsSectionTitle(stringResource(R.string.settings_section_wifi))
                Column {
                    Text(
                        stringResource(R.string.settings_wifi_default),
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        stringResource(R.string.settings_wifi_default_hint),
                        color = Color(0xFF888888),
                        fontSize = 11.sp,
                    )
                    SettingsOptionButtons(
                        options = listOf(
                            stringResource(R.string.settings_wifi_connected) to 0.0,
                            stringResource(R.string.settings_wifi_passive) to 1.0,
                        ),
                        selectedValue = if (state.defaultWifiMode == WifiCollectMode.CONNECTED) 0.0 else 1.0,
                        tolerance = 0.5,
                        onSelect = { v ->
                            onSetDefaultWifiMode(
                                if (v < 0.5) WifiCollectMode.CONNECTED else WifiCollectMode.PASSIVE_SCAN,
                            )
                        },
                    )
                }

                // —— Accesibilidad ——
                SettingsSectionTitle(stringResource(R.string.settings_section_accessibility))
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_colorblind),
                    hint = stringResource(R.string.settings_colorblind_hint),
                    checked = state.colorblindMode,
                    onToggle = onSetColorblindMode,
                )
                Text(
                    "Iconos: ◎ cerca · ◆ respira/viva · ▲ movimiento · ? señal · ←→ girar",
                    color = Color(0xFFAAAAAA),
                    fontSize = 11.sp,
                )

                // —— Alertas ——
                SettingsSectionTitle(stringResource(R.string.settings_section_alerts))
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_alert_sound),
                    hint = stringResource(R.string.settings_alert_sound_hint),
                    checked = state.alertSoundEnabled,
                    onToggle = onToggleAlertSound,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_vibration),
                    hint = stringResource(R.string.settings_vibration_hint),
                    checked = state.vibrationEnabled,
                    onToggle = onToggleVibration,
                )

                // —— Altura de escaneo ——
                SettingsSectionTitle(stringResource(R.string.settings_section_elevation))
                if (state.hasBarometer) {
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_auto_elevation),
                        hint = stringResource(R.string.settings_auto_elevation_hint),
                        checked = state.autoElevationActive,
                        onToggle = onToggleAutoElevation,
                    )
                } else {
                    Text(
                        stringResource(R.string.settings_no_barometer),
                        color = Color(0xFF888888),
                        fontSize = 11.sp,
                    )
                }
                if (!state.autoElevationActive || !state.hasBarometer) {
                    Column {
                        Text(
                            stringResource(R.string.settings_manual_elevation),
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            stringResource(R.string.settings_manual_elevation_hint),
                            color = Color(0xFF888888),
                            fontSize = 11.sp,
                        )
                        val sign = if (state.scanningElevationM >= 0) "+" else ""
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = { onAdjustElevation(-0.5) }) {
                                Text("−", color = Cyan, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                "$sign${"%.1f".format(state.scanningElevationM)} m",
                                color = Color(0xFF66FF99),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 16.sp,
                            )
                            IconButton(onClick = { onAdjustElevation(0.5) }) {
                                Text("+", color = Cyan, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = onResetElevation) {
                                Text("0 m", fontSize = 12.sp)
                            }
                        }
                    }
                }

                // —— Pantalla y radar ——
                SettingsSectionTitle(stringResource(R.string.settings_section_display))
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_keep_screen_on),
                    hint = stringResource(R.string.settings_keep_screen_on_hint),
                    checked = state.keepScreenOn,
                    onToggle = onToggleKeepScreenOn,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_radar_3d),
                    hint = stringResource(R.string.settings_radar_3d_hint),
                    checked = state.showRadar3D,
                    onToggle = onSetShowRadar3D,
                )
                Column {
                    Text(
                        stringResource(R.string.settings_radar_layers),
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        stringResource(R.string.settings_radar_layers_hint),
                        color = Color(0xFF888888),
                        fontSize = 11.sp,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            "Rescate" to onRadarLayersModoRescate,
                            "Solo persona" to onRadarLayersSoloPersona,
                            "Todo" to onRadarLayersTodo,
                        ).forEach { (label, action) ->
                            Button(
                                onClick = action,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1A2A1A),
                                    contentColor = Cyan,
                                ),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            ) {
                                Text(label, fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                }
                val layers = state.radarLayers
                val layerToggles = listOf(
                    "grid" to (stringResource(R.string.settings_layer_grid) to layers.grid),
                    "compass" to (stringResource(R.string.settings_layer_compass) to layers.compass),
                    "wifi" to (stringResource(R.string.settings_layer_wifi) to layers.wifi),
                    "heatmap" to (stringResource(R.string.settings_layer_heatmap) to layers.heatmap),
                    "guide" to (stringResource(R.string.settings_layer_guide) to layers.guide),
                    "depthRays" to ("Rayas profundidad" to layers.depthRays),
                    "rubble" to (stringResource(R.string.settings_layer_rubble) to layers.rubble),
                    "trail" to (stringResource(R.string.settings_layer_trail) to layers.trail),
                    "labels" to (stringResource(R.string.settings_layer_labels) to layers.labels),
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    layerToggles.forEach { (key, labelChecked) ->
                        val (label, checked) = labelChecked
                        FilterChip(
                            selected = checked,
                            onClick = { onToggleRadarLayer(key) },
                            label = { Text(label, fontSize = 11.sp) },
                        )
                    }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrappedFilterControls(
    state: VitalFiUiState,
    onMarkRubbleZone: () -> Unit,
    onClearRubbleZone: () -> Unit,
    onExcludeRescuer: () -> Unit,
    onClearExclusions: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101810)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (state.trappedOnlyMode) "Filtro anti-rescatista activo" else "Modo detección amplia",
                color = if (state.trappedOnlyMode) RadarGreen else Orange,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
            state.rubbleZoneLabel?.let { label ->
                Text(label, color = Cyan, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
            state.filterStatus?.let { filter ->
                Text(filter, color = Color(0xFFAAAAAA), fontSize = 11.sp)
            }
            if (state.excludedRescuerCount > 0) {
                Text(
                    "Sectores excluidos: ${state.excludedRescuerCount}",
                    color = Color(0xFF888888),
                    fontSize = 11.sp,
                )
            }
            Text(
                stringResource(R.string.mark_rubble_zone_hint),
                color = Color(0xFF666666),
                fontSize = 11.sp,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = onMarkRubbleZone) {
                    Text(stringResource(R.string.mark_rubble_zone), fontSize = 11.sp)
                }
                if (state.rubbleZoneLabel != null) {
                    TextButton(onClick = onClearRubbleZone) {
                        Text("Quitar zona", fontSize = 11.sp)
                    }
                }
                Button(onClick = onExcludeRescuer) {
                    Text(stringResource(R.string.exclude_rescuer), fontSize = 11.sp)
                }
                if (state.excludedRescuerCount > 0) {
                    TextButton(onClick = onClearExclusions) {
                        Text(stringResource(R.string.clear_exclusions), fontSize = 11.sp)
                    }
                }
            }
        }
    }
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
                Text(
                    stringResource(R.string.about_company),
                    color = Color(0xFFAAAAAA),
                    fontSize = 13.sp,
                )
                Text(
                    stringResource(R.string.about_license),
                    color = Color(0xFF666666),
                    fontSize = 11.sp,
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
                    text = "🗺️ Búsqueda: gira 360° despacio. Cuando aparezca el punto, alinea la flecha y camina hacia él (como un tesoro).",
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
    val depthLine = pos?.let { " | Prof. est. ~${"%.1f".format(-it.depth)} m (no confirmada)" } ?: ""
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
                            val kind = victimStatusKind(victim)
                            Text(
                                "${victim.label} ${victimStatusIconLabel(kind)} ${"%.1f".format(victim.distance)}m",
                                fontSize = 11.sp,
                            )
                        },
                    )
                }
            }
            Text(
                "Toca Persona A/B… para ver signos vitales o ponerle un nombre de campo.",
                color = Color(0xFF777777),
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun VictimDetailDialog(
    victim: TrappedVictim,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    phoneBearingDeg: Double,
    colorblind: Boolean = false,
) {
    val kind = victimStatusKind(victim)
    val color = victimColor(victim, colorblind)
    val rel = describePersonPosition(victim.x, victim.y, victim.depth, phoneBearingDeg)
    val horizDist = rel.distanceM
    val burialM = rel.depthM
    var nameDraft by remember(victim.id, victim.label) { mutableStateOf(victim.label) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "${victimStatusIconLabel(kind)} · ${victim.label}",
                color = color,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(victim.status, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                if (victim.signatureCode.isNotBlank()) {
                    VictimDetailRow("Firma Wi‑Fi", victim.signatureCode)
                }
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it.take(28) },
                    label = { Text("Nombre de campo") },
                    placeholder = { Text("Ej. Niño zona norte") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Etiqueta local (no identifica identidad civil). Vacío = Persona A/B.",
                    color = Color(0xFF777777),
                    fontSize = 10.sp,
                )
                Text(
                    "Mediciones Wi-Fi · iconos = estado (no solo color)",
                    color = if (colorblind) Color(0xFF80C0FF) else RadarGreen,
                    fontSize = 11.sp,
                )
                VictimDetailRow("Desde ti", rel.horizontal)
                VictimDetailRow("Vertical", rel.vertical)
                VictimDetailRow("Distancia horizontal", "${"%.2f".format(horizDist)} m")
                VictimDetailRow(
                    "Profundidad",
                    if (burialM >= 0.12) "~${"%.2f".format(burialM)} m estimados — no confirmados"
                    else "Profundidad desconocida",
                )
                VictimDetailRow("Distancia 3D (recta)", "${"%.2f".format(rel.slantRangeM)} m")
                VictimDetailRow(
                    "Rumbo",
                    "${victim.bearing.roundToInt()}° ${bearingToCardinal(victim.bearing)}",
                )
                VictimDetailRow("Zona", "${victim.proximity} (~${"%.1f".format(horizDist)} m)")
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
                Text(
                    rel.coordsLine,
                    color = Color(0xFF666666),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onRename(nameDraft)
                    onDismiss()
                },
            ) { Text("Guardar") }
        },
        dismissButton = {
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
    layers: RadarLayers = RadarLayers(),
    colorblind: Boolean = false,
    onVictimClick: (Int) -> Unit,
    onViewportChange: (RadarViewport) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sonarTransition = rememberInfiniteTransition(label = "sonar2d")
    val sonarPulse by sonarTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
        ),
        label = "sonarPulse",
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF050A05)),
        modifier = modifier,
        shape = CircleShape,
    ) {
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
        val divisor = RADAR_DEFAULT_RANGE_M

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

            if (layers.grid) {
                drawRadarGrid2D(divisor, viewport, labelPaint)
                drawRadarSonarPulse2D(sonarPulse, viewport)
            }
            if (layers.wifi) drawWifiCoverage2D(wifiCoverageRadiusM, divisor, viewport, labelPaint)
            if (layers.heatmap) drawHeatmap2D(heatmap, bearingDeg, divisor, viewport)
            if (layers.compass) drawCompassRose2D(bearingDeg, divisor, viewport, labelPaint)
            if (layers.guide) {
                drawNavigationGuide2D(
                    bearingDeg = bearingDeg,
                    target = navigationTarget(victims, selectedVictimId),
                    divisor = divisor,
                    viewport = viewport,
                    labelPaint = labelPaint,
                    tagPaint = tagPaint,
                    colorblind = colorblind,
                )
            }
            drawVictims2D(
                victims,
                bearingDeg,
                divisor,
                viewport,
                selectedVictimId,
                labelPaint,
                tagPaint,
                showLabels = layers.labels,
                colorblind = colorblind,
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
