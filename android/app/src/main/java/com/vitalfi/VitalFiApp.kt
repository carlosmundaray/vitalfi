package com.vitalfi

import android.app.Application
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraXConfig

/**
 * Aplicación principal de VitalFi.
 * Provee la configuración de CameraX para garantizar inicialización segura y confiable en AR Mode.
 */
class VitalFiApp : Application(), CameraXConfig.Provider {
    override fun getCameraXConfig(): CameraXConfig {
        return Camera2Config.defaultConfig()
    }
}
