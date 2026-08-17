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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import com.vitalfi.dsp.localizeDynamicStatus
import com.vitalfi.dsp.localizeDynamicProximity
import com.vitalfi.dsp.localizeDynamicHint
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
    onSetAppLanguage: (String) -> Unit,
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
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "VitalFi",
                        color = Cyan,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    )
                    Text(
                        text = stringResource(R.string.app_tagline),
                        color = Color(0xFFA0C4A0),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { showAbout = true },
                        modifier = Modifier.background(Color(0xFF142418), CircleShape),
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = stringResource(R.string.about_title),
                            tint = Cyan,
                        )
                    }
                    IconButton(
                        onClick = { showSettings = true },
                        modifier = Modifier.background(Color(0xFF142418), CircleShape),
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings_title),
                            tint = Cyan,
                        )
                    }
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
                    label = { Text(if (state.showRadar3D) stringResource(R.string.action_view_3d) else stringResource(R.string.action_radar_2d)) },
                    leadingIcon = { Icon(Icons.Default.ViewInAr, null) },
                )
                Button(
                    onClick = onReset,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.padding(end = 2.dp))
                    Text(stringResource(R.string.action_reset), fontSize = 12.sp)
                }
                Button(onClick = onExportCsv) {
                    Icon(Icons.Default.Download, null)
                    Text(" " + stringResource(R.string.action_csv))
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
                    Icon(Icons.AutoMirrored.Filled.RotateLeft, stringResource(R.string.action_rotate_left), tint = Cyan)
                }
                IconButton(onClick = onBearingRight) {
                    Icon(Icons.AutoMirrored.Filled.RotateRight, stringResource(R.string.action_rotate_right), tint = Cyan)
                }
            }

            Text(
                text = when (state.collectMode) {
                    WifiCollectMode.CONNECTED -> stringResource(R.string.mode_connected_hint)
                    WifiCollectMode.PASSIVE_SCAN -> stringResource(R.string.mode_passive_hint)
                },
                color = Color(0xFF888888),
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }

        state.toastMessage?.let { msg ->
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                action = { TextButton(onClick = onDismissToast) { Text(stringResource(R.string.action_ok)) } },
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

    state.forceUpdateInfo?.let { updateInfo ->
        ForceUpdateDialog(
            updateInfo = updateInfo,
            onOpenUrl = { url ->
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            },
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
            onSetAppLanguage = onSetAppLanguage,
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
    onSetAppLanguage: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var showLanguagePicker by remember { mutableStateOf(false) }

    if (showLanguagePicker) {
        LanguagePickerDialog(
            currentLanguageCode = state.appLanguage,
            onLanguageSelected = onSetAppLanguage,
            onDismiss = { showLanguagePicker = false },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // —— Idioma ——
                SettingsSectionTitle(stringResource(R.string.settings_section_language))
                Text(
                    stringResource(R.string.settings_language_hint),
                    color = Color(0xFF888888),
                    fontSize = 11.sp,
                )
                val currentLang = SUPPORTED_LANGUAGES.find { it.code == state.appLanguage } ?: SUPPORTED_LANGUAGES.first()
                Card(
                    onClick = { showLanguagePicker = true },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF18281A)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(currentLang.flag, fontSize = 22.sp)
                            Column {
                                Text(
                                    text = if (currentLang.code.isEmpty()) stringResource(R.string.settings_language_auto) else currentLang.nativeName,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                )
                                if (currentLang.englishName != currentLang.nativeName && currentLang.code.isNotEmpty()) {
                                    Text(
                                        text = currentLang.englishName,
                                        color = Color(0xFFA0C4A0),
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = stringResource(R.string.language_picker_title),
                            tint = Cyan,
                        )
                    }
                }

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
                        stringResource(R.string.status_excluded_sectors, state.excludedRescuerCount),
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
                            Text(stringResource(R.string.status_remove_zone), fontSize = 11.sp)
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
                            stringResource(R.string.radar_preset_rescue) to onRadarLayersModoRescate,
                            stringResource(R.string.radar_preset_person_only) to onRadarLayersSoloPersona,
                            stringResource(R.string.radar_preset_all) to onRadarLayersTodo,
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
                    "depthRays" to (stringResource(R.string.radar_layer_depth_rays_short) to layers.depthRays),
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
                if (state.trappedOnlyMode) stringResource(R.string.status_filter_trapped_active) else stringResource(R.string.status_filter_wide_mode),
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
                    stringResource(R.string.status_excluded_sectors, state.excludedRescuerCount),
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
                        Text(stringResource(R.string.status_remove_zone), fontSize = 11.sp)
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
        containerColor = Color(0xFF101A12),
        title = {
            Text(
                stringResource(R.string.about_title),
                color = Cyan,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                    color = Color(0xFF88CC88),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    stringResource(R.string.about_developer),
                    color = Color(0xFFAAAAAA),
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
                    color = Color(0xFFCCEEDD),
                    fontSize = 13.sp,
                )
                Text(
                    stringResource(R.string.about_license),
                    color = Color(0xFF88AA88),
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
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1C2E20),
                    contentColor = Cyan,
                ),
            ) {
                Text(stringResource(R.string.about_close))
            }
        },
    )
}

@Composable
private fun ForceUpdateDialog(
    updateInfo: com.vitalfi.update.UpdateInfo,
    onOpenUrl: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = {}, // Bloqueo total: no descartable
        containerColor = Color(0xFF141F14),
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = null,
                    tint = Cyan,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = updateInfo.title.ifBlank { stringResource(R.string.update_required_title) },
                    color = Cyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = updateInfo.message.ifBlank { stringResource(R.string.update_required_msg) },
                    color = Color.White,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0D160D)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "${stringResource(R.string.update_current_ver)}: ${BuildConfig.VERSION_NAME} (v${BuildConfig.VERSION_CODE})",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            text = "${stringResource(R.string.update_new_ver)}: ${updateInfo.latestVersionName} (v${updateInfo.latestVersionCode})",
                            color = Color(0xFF66FF99),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onOpenUrl(updateInfo.downloadUrl) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Cyan,
                    contentColor = Color.Black,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(R.string.action_update_now),
                    fontWeight = FontWeight.Bold,
                )
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
            label = { Text(stringResource(R.string.mode_connected)) },
            leadingIcon = { Icon(Icons.Default.Wifi, null) },
        )
        FilterChip(
            selected = state.collectMode == WifiCollectMode.PASSIVE_SCAN,
            onClick = onPassive,
            label = { Text(stringResource(R.string.mode_passive)) },
            leadingIcon = { Icon(Icons.Default.WifiFind, null) },
        )
    }
}

@Composable
private fun LanguagePickerDialog(
    currentLanguageCode: String,
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredLanguages = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            SUPPORTED_LANGUAGES
        } else {
            val q = searchQuery.trim().lowercase()
            SUPPORTED_LANGUAGES.filter {
                it.nativeName.lowercase().contains(q) ||
                it.englishName.lowercase().contains(q) ||
                it.code.lowercase().contains(q)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.language_picker_title),
                fontWeight = FontWeight.Bold,
                color = Cyan,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.language_search_placeholder)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Cyan) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray)
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (filteredLanguages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.language_search_no_results),
                            color = Color.Gray,
                            fontSize = 13.sp,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(filteredLanguages) { lang ->
                            val isSelected = lang.code == currentLanguageCode
                            Card(
                                onClick = {
                                    onLanguageSelected(lang.code)
                                    onDismiss()
                                },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) Color(0xFF1E3A24) else Color(0xFF141F14),
                                ),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(lang.flag, fontSize = 18.sp)
                                        Column {
                                            Text(
                                                text = if (lang.code.isEmpty()) stringResource(R.string.settings_language_auto) else lang.nativeName,
                                                color = if (isSelected) Cyan else Color.White,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 13.sp,
                                            )
                                            if (lang.englishName != lang.nativeName && lang.code.isNotEmpty()) {
                                                Text(
                                                    text = lang.englishName,
                                                    color = Color(0xFF888888),
                                                    fontSize = 11.sp,
                                                )
                                            }
                                        }
                                    }
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Cyan,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
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
        title = { Text(stringResource(R.string.picker_select_router)) },
        text = {
            if (networks.isEmpty()) {
                Text(
                    if (isScanning) stringResource(R.string.picker_scanning)
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
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun StatusCard(state: VitalFiUiState) {
    val context = LocalContext.current
    val statusColor = when {
        state.isBreathing -> Red
        state.isActivity -> Orange
        state.calibrating -> Color(0xFFFFCC00)
        else -> Cyan
    }
    val modeLabel = when (state.collectMode) {
        WifiCollectMode.CONNECTED -> stringResource(R.string.status_wifi_connected_label)
        WifiCollectMode.PASSIVE_SCAN -> stringResource(R.string.status_wifi_passive_label)
    }
    val wifiStateStr = if (state.wifiEnabled) stringResource(R.string.status_wifi_active) else stringResource(R.string.status_wifi_disabled)
    val monitoringStr = if (state.monitoring) stringResource(R.string.status_monitoring_yes) else stringResource(R.string.status_monitoring_no)
    val withoutInternetStr = if (state.wifiWithoutInternet) " " + stringResource(R.string.status_without_internet) else ""
    val localizedStatus = localizeDynamicStatus(state.status, state.isBreathing, state.isActivity, state.wifiEnabled, context)
    val localizedHint = localizeDynamicHint(state.hint, context)
    val localizedProx = localizeDynamicProximity(state.proximity, context)
    val mlStatusStr = if (state.mlReady) stringResource(R.string.ml_status_active) else stringResource(R.string.ml_status_disabled)

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121A12)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.status_network, state.ssid.ifBlank { "—" }, modeLabel), color = Color.White, fontSize = 13.sp)
            Text(
                "Wi-Fi: $wifiStateStr | Monitoreo: $monitoringStr$withoutInternetStr | ${state.compassLabel}",
                color = Color(0xFF888888),
                fontSize = 11.sp,
            )
            Text("RSSI: ${state.rssiDbm?.let { "$it dBm" } ?: "—"}", color = Color(0xFFAAAAAA), fontSize = 12.sp)
            Text(
                mlStatusStr,
                color = if (state.mlReady) Color(0xFF66FF99) else Color(0xFF777777),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
            Text(localizedStatus, color = statusColor, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            if (state.calibrating) {
                Text(stringResource(R.string.status_calibrating, state.calibRemainingSec), color = Color(0xFFFFCC00), fontSize = 12.sp)
            }
            if (state.rubbleConfidence > 0.05 && !state.calibrating) {
                Text(
                    stringResource(R.string.status_rubble_confidence, (state.rubbleConfidence * 100).roundToInt()),
                    color = if (state.isBreathing) Red else Orange,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (state.victims.isNotEmpty()) {
                Text(
                    stringResource(R.string.status_victims_count, state.victims.size),
                    color = RadarGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Text(localizedHint, color = Color(0xFFAAAAAA), fontSize = 12.sp)
            if (state.isBreathing || state.isActivity || state.heartBeating || state.position != null) {
                Text(
                    stringResource(
                        R.string.status_vitals_summary,
                        if (state.isBreathing) stringResource(R.string.val_yes) else stringResource(R.string.val_no),
                        "%.0f".format(state.respRate),
                        if (state.heartBeating) stringResource(R.string.val_yes) else stringResource(R.string.val_no),
                        "%.0f".format(state.heartRateBpm),
                        "%.0f".format(state.confidence * 100),
                        localizedProx,
                    ),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Text(
                stringResource(R.string.status_samples_bearing, state.sampleCount, state.maxSamples, state.bearingDeg.roundToInt(), bearingToCardinal(state.bearingDeg)),
                color = Color(0xFF666666),
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun NavigationHint(state: VitalFiUiState) {
    val context = LocalContext.current
    val pos = state.position
    val target = state.selectedVictim ?: state.victims.firstOrNull()
    if (pos == null && target == null) {
        if (state.rubbleConfidence > 0.12 && (state.isActivity || state.isBreathing)) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2218))) {
                Text(
                    text = stringResource(R.string.status_nav_search_prompt),
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
    val steer = buildNavigationHint(context, targetBearing, state.bearingDeg, targetDistance)
    val depthLine = pos?.let { " | " + stringResource(R.string.pos_depth_est, "%.1f".format(-it.depth)) } ?: ""
    val headingStr = stringResource(R.string.radar_heading, state.bearingDeg.roundToInt(), bearingToCardinal(state.bearingDeg))
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2218))) {
        Text(
            text = "$steer$depthLine\n$headingStr",
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
                stringResource(R.string.victims_trapped_count, state.victims.size),
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
                stringResource(R.string.victims_touch_hint),
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
    val context = LocalContext.current
    val kind = victimStatusKind(victim)
    val color = victimColor(victim, colorblind)
    val rel = describePersonPosition(context, victim.x, victim.y, victim.depth, phoneBearingDeg)
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
                val localizedVictimStatus = localizeDynamicStatus(victim.status, victim.isBreathing, victim.isActivity, true, context)
                Text(localizedVictimStatus, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                if (victim.signatureCode.isNotBlank()) {
                    VictimDetailRow(stringResource(R.string.victim_wifi_signature), victim.signatureCode)
                }
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it.take(28) },
                    label = { Text(stringResource(R.string.victim_field_name)) },
                    placeholder = { Text(stringResource(R.string.victim_field_name_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.victim_label_hint),
                    color = Color(0xFF777777),
                    fontSize = 10.sp,
                )
                Text(
                    stringResource(R.string.victim_measurements_hint),
                    color = if (colorblind) Color(0xFF80C0FF) else RadarGreen,
                    fontSize = 11.sp,
                )
                VictimDetailRow(stringResource(R.string.victim_from_you), rel.horizontal)
                VictimDetailRow(stringResource(R.string.victim_vertical), rel.vertical)
                VictimDetailRow(stringResource(R.string.victim_horizontal_distance), "${"%.2f".format(horizDist)} m")
                VictimDetailRow(
                    stringResource(R.string.victim_depth),
                    if (burialM >= 0.12) stringResource(R.string.victim_depth_estimated, "%.2f".format(burialM))
                    else stringResource(R.string.victim_depth_unknown),
                )
                VictimDetailRow(stringResource(R.string.victim_distance_3d), "${"%.2f".format(rel.slantRangeM)} m")
                VictimDetailRow(
                    stringResource(R.string.victim_heading),
                    "${victim.bearing.roundToInt()}° ${bearingToCardinal(victim.bearing)}",
                )
                VictimDetailRow(stringResource(R.string.victim_zone), "${victim.proximity} (~${"%.1f".format(horizDist)} m)")
                VictimDetailRow(
                    stringResource(R.string.victim_breathing),
                    if (victim.isBreathing) "${stringResource(R.string.val_yes)} — ${"%.1f".format(victim.respRate)} rpm" else stringResource(R.string.val_not_detected),
                )
                VictimDetailRow(
                    stringResource(R.string.victim_heart_beating),
                    if (victim.heartBeating) "${stringResource(R.string.val_yes)} — ${"%.0f".format(victim.heartRateBpm)} lpm" else stringResource(R.string.val_not_detected),
                )
                VictimDetailRow(stringResource(R.string.victim_activity), if (victim.isActivity) stringResource(R.string.val_yes) else stringResource(R.string.val_no))
                VictimDetailRow(stringResource(R.string.victim_vital_confidence), "${"%.0f".format(victim.confidence * 100)}%")
                VictimDetailRow(stringResource(R.string.victim_rubble_confidence), "${"%.0f".format(victim.rubbleConfidence * 100)}%")
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
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
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
    rubbleZoneCenterDeg: Double? = null,
    rubbleZoneRadiusDeg: Double = 55.0,
    environmentCells: List<com.vitalfi.dsp.EnvironmentCell> = emptyList(),
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
        val context = LocalContext.current
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
            if (layers.wifi) {
                drawWifiCoverage2D(wifiCoverageRadiusM, divisor, viewport, labelPaint, sonarPulse, context)
                drawRfEnvironment2D(
                    cells = environmentCells,
                    phoneBearingDeg = bearingDeg,
                    divisor = divisor,
                    viewport = viewport,
                    labelPaint = labelPaint,
                )
            }
            if (layers.heatmap) drawHeatmap2D(heatmap, bearingDeg, divisor, viewport)
            if (layers.rubble && rubbleZoneCenterDeg != null) {
                drawRubbleSector2D(
                    centerDeg = rubbleZoneCenterDeg,
                    radiusDeg = rubbleZoneRadiusDeg,
                    phoneBearingDeg = bearingDeg,
                    viewport = viewport,
                )
            }
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
                    context = context,
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
                Text(stringResource(R.string.waiting_wifi_signal), color = Color.Gray, fontSize = 12.sp)
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
