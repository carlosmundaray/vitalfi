package com.whofi.vitalfi

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
import com.whofi.vitalfi.ui.MainScreen

class MainActivity : ComponentActivity() {

    private val viewModel: VitalFiViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (hasRequiredPermissions(grants)) {
            viewModel.start()
        } else {
            Toast.makeText(
                this,
                "VitalFi necesita permiso de ubicación para leer señal Wi-Fi",
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
                    onViewportChange = { viewModel.setRadarViewport(it) },
                    onZoomIn = { viewModel.zoomRadarIn() },
                    onZoomOut = { viewModel.zoomRadarOut() },
                    onResetRadarView = { viewModel.resetRadarView() },
                    onToggleRadarFullscreen = { viewModel.toggleRadarFullscreen() },
                    onCloseRadarFullscreen = { viewModel.closeRadarFullscreen() },
                    onSetAlertSoundEnabled = { viewModel.setAlertSoundEnabled(it) },
                )
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
            startActivity(Intent.createChooser(intent, "Compartir CSV VitalFi"))
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo compartir: ${e.message}", Toast.LENGTH_LONG).show()
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
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.NEARBY_WIFI_DEVICES)
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
