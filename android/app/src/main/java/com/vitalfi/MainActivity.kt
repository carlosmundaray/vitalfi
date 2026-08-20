package com.vitalfi

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitalfi.ui.MainScreen
import com.vitalfi.settings.SettingsStore

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: VitalFiViewModel by viewModels()

    @Suppress("DEPRECATION")
    private fun getAppLocale(langCode: String): Locale {
        return if (langCode.isBlank()) {
            val sysConfig = android.content.res.Resources.getSystem().configuration
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                sysConfig.locales.get(0) ?: Locale.getDefault()
            } else {
                sysConfig.locale ?: Locale.getDefault()
            }
        } else when (langCode) {
            "zh", "zh-CN", "zh-Hans" -> Locale.SIMPLIFIED_CHINESE
            "zh-TW", "zh-Hant" -> Locale.TRADITIONAL_CHINESE
            "in", "id" -> Locale("in")
            "he", "iw" -> Locale("he")
            "tl", "fil" -> Locale("tl")
            else -> Locale.forLanguageTag(langCode)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val store = SettingsStore(newBase)
        val lang = store.appLanguage
        if (lang.isNotBlank()) {
            val locale = getAppLocale(lang)
            Locale.setDefault(locale)
            val conf = Configuration(newBase.resources.configuration).apply {
                setLocale(locale)
                setLayoutDirection(locale)
            }
            super.attachBaseContext(newBase.createConfigurationContext(conf))
        } else {
            super.attachBaseContext(newBase)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (hasRequiredPermissions(grants)) {
            viewModel.start()
        } else {
            Toast.makeText(
                this,
                getString(R.string.permission_location_needed),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = androidx.compose.ui.graphics.Color(0xFF0A0F0A),
                ),
            ) {
                DisposableEffect(Unit) {
                    onDispose { viewModel.stop() }
                }
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(state.appLanguage) {
                    val locale = getAppLocale(state.appLanguage)
                    Locale.setDefault(locale)
                    val conf = Configuration(resources.configuration).apply {
                        setLocale(locale)
                        setLayoutDirection(locale)
                    }
                    @Suppress("DEPRECATION")
                    resources.updateConfiguration(conf, resources.displayMetrics)
                }

                val currentConfig = LocalConfiguration.current
                val currentContext = LocalContext.current
                val localizedContext = remember(state.appLanguage, currentConfig) {
                    val locale = getAppLocale(state.appLanguage)
                    val conf = Configuration(currentConfig).apply {
                        setLocale(locale)
                        setLayoutDirection(locale)
                    }
                    currentContext.createConfigurationContext(conf)
                }
                val localizedConfig = remember(state.appLanguage, currentConfig) {
                    val locale = getAppLocale(state.appLanguage)
                    Configuration(currentConfig).apply {
                        setLocale(locale)
                        setLayoutDirection(locale)
                    }
                }

                key(state.appLanguage) {
                    CompositionLocalProvider(
                        LocalConfiguration provides localizedConfig,
                        LocalContext provides localizedContext,
                        androidx.activity.compose.LocalActivityResultRegistryOwner provides this@MainActivity,
                        androidx.activity.compose.LocalOnBackPressedDispatcherOwner provides this@MainActivity,
                    ) {
                        MainScreen(
                            state = state,
                            onReset = { viewModel.reset() },
                            onBearingLeft = { viewModel.bearingLeft() },
                            onBearingRight = { viewModel.bearingRight() },
                            onConnectedMode = { viewModel.setConnectedMode() },
                            onPassiveMode = { viewModel.openPassiveMode() },
                            onSelectNetwork = { viewModel.selectPassiveNetwork(it) },
                            onDismissPicker = { viewModel.dismissNetworkPicker() },
                            onToggleRadar3D = { viewModel.toggleRadar3D() },
                            onViewAzimLeft = { viewModel.rotateViewAzim(-12.0) },
                            onViewAzimRight = { viewModel.rotateViewAzim(12.0) },
                            onViewElevUp = { viewModel.rotateViewElev(6.0) },
                            onViewElevDown = { viewModel.rotateViewElev(-6.0) },
                            onResetView3D = { viewModel.resetView3D() },
                            onExportCsv = { shareCsv() },
                            onDismissToast = { viewModel.dismissToast() },
                            onSelectVictim = { viewModel.selectVictim(it) },
                            onDismissVictim = { viewModel.clearVictimSelection() },
                            onRenameVictim = { id, name -> viewModel.renameVictim(id, name) },
                        onViewportChange = { viewModel.setRadarViewport(it) },
                        onZoomIn = { viewModel.zoomRadarIn() },
                        onZoomOut = { viewModel.zoomRadarOut() },
                        onResetRadarView = { viewModel.resetRadarView() },
                        onToggleRadarFullscreen = { viewModel.toggleRadarFullscreen() },
                        onCloseRadarFullscreen = { viewModel.closeRadarFullscreen() },
                        onSetAlertSoundEnabled = { viewModel.setAlertSoundEnabled(it) },
                        onSetTrappedOnlyMode = { viewModel.setTrappedOnlyMode(it) },
                        onSetSensingEngineMode = { viewModel.setSensingEngineMode(it) },
                        onSetOpenWrtUdpPort = { viewModel.setOpenWrtUdpPort(it) },
                        onToggleArCamera = { viewModel.toggleArCamera() },
                        onLockArTarget = { viewModel.lockVictimTarget(it) },
                        onRescueArVictim = { victimId, label, depthM, respRate, heartRate, conf, sig ->
                            viewModel.rescueVictim(victimId, label, depthM, respRate, heartRate, conf, sig)
                        },
                        onClearRescuedVictims = { viewModel.clearRescuedVictims() },
                        onRemoveRescuedVictim = { viewModel.removeRescuedVictim(it) },
                        onMarkRubbleZone = { viewModel.markRubbleZoneAtCurrentBearing() },
                        onClearRubbleZone = { viewModel.clearRubbleZone() },
                        onExcludeRescuer = { viewModel.excludeRescuerAtCurrentBearing() },
                        onClearExclusions = { viewModel.clearRescuerExclusions() },
                        onAdjustElevation = { viewModel.adjustManualElevation(it) },
                        onResetElevation = { viewModel.resetElevation() },
                        onToggleAutoElevation = { viewModel.setAutoElevationActive(it) },
                        onToggleRadarLayer = { viewModel.toggleRadarLayer(it) },
                        onRadarLayersSoloPersona = { viewModel.radarLayersSoloPersona() },
                        onRadarLayersTodo = { viewModel.radarLayersTodo() },
                        onRadarLayersModoRescate = { viewModel.radarLayersModoRescate() },
                        onSetVibrationEnabled = { viewModel.setVibrationEnabled(it) },
                        onSetKeepScreenOn = { viewModel.setKeepScreenOn(it) },
                        onSetDetectionSensitivity = { viewModel.setDetectionSensitivity(it) },
                        onSetShowRadar3D = { viewModel.setShowRadar3D(it) },
                        onSetRubbleZoneRadius = { viewModel.setRubbleZoneRadiusDeg(it) },
                        onSetDefaultWifiMode = { viewModel.setDefaultWifiMode(it) },
                        onSetColorblindMode = { viewModel.setColorblindMode(it) },
                        onSetAppLanguage = { lang ->
                            viewModel.setAppLanguage(lang)
                            val locale = getAppLocale(lang)
                            Locale.setDefault(locale)
                            val conf = Configuration(resources.configuration).apply {
                                setLocale(locale)
                                setLayoutDirection(locale)
                            }
                            @Suppress("DEPRECATION")
                            resources.updateConfiguration(conf, resources.displayMetrics)
                            @Suppress("DEPRECATION")
                            applicationContext.resources.updateConfiguration(conf, applicationContext.resources.displayMetrics)
                            recreate()
                        },
                    )
                }
            }
        }
    }

        requestPermissionsAndStart()
    }

    override fun onResume() {
        super.onResume()
        if (hasLocationPermission()) {
            viewModel.start()
        }
    }

    override fun onPause() {
        viewModel.stop()
        super.onPause()
    }

    private fun shareCsv() {
        try {
            val file = viewModel.exportCsv() ?: return
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "VitalFi export")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share_chooser_title)))
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.share_error, e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    private fun requestPermissionsAndStart() {
        val needed = buildRequiredPermissions()
        if (needed.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            viewModel.start()
        } else {
            permissionLauncher.launch(needed)
        }
    }

    private fun buildRequiredPermissions(): Array<String> {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        // Pasos reales para acercar el punto del radar al caminar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            perms.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        return perms.toTypedArray()
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasRequiredPermissions(grants: Map<String, Boolean>): Boolean =
        grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
}
