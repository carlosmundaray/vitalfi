package com.vitalfi.settings

import android.content.Context
import com.vitalfi.ui.RadarLayers

class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var alertSoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_ALERT_SOUND, true)
        set(value) {
            prefs.edit().putBoolean(KEY_ALERT_SOUND, value).apply()
        }

    var trappedOnlyMode: Boolean
        // Clave nueva: fuerza default OFF (antes el filtro anti-rescatista ocultaba personas caminando)
        get() = prefs.getBoolean(KEY_TRAPPED_ONLY_V2, false)
        set(value) {
            prefs.edit().putBoolean(KEY_TRAPPED_ONLY_V2, value).apply()
        }

    var vibrationEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIBRATION, true)
        set(value) {
            prefs.edit().putBoolean(KEY_VIBRATION, value).apply()
        }

    var keepScreenOn: Boolean
        get() = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true)
        set(value) {
            prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, value).apply()
        }

    /** Sensibilidad del detector: 1.2 baja · 2.0 normal · 3.0 alta. */
    var detectionSensitivity: Double
        get() = prefs.getFloat(KEY_SENSITIVITY, 2.0f).toDouble()
        set(value) {
            prefs.edit().putFloat(KEY_SENSITIVITY, value.toFloat()).apply()
        }

    var showRadar3D: Boolean
        get() = prefs.getBoolean(KEY_RADAR_3D, true)
        set(value) {
            prefs.edit().putBoolean(KEY_RADAR_3D, value).apply()
        }

    var rubbleZoneCenterDeg: Double?
        get() {
            if (!prefs.contains(KEY_RUBBLE_ZONE_CENTER)) return null
            return prefs.getFloat(KEY_RUBBLE_ZONE_CENTER, 0f).toDouble()
        }
        set(value) {
            prefs.edit().apply {
                if (value == null) {
                    remove(KEY_RUBBLE_ZONE_CENTER)
                } else {
                    putFloat(KEY_RUBBLE_ZONE_CENTER, value.toFloat())
                }
            }.apply()
        }

    var rubbleZoneRadiusDeg: Double
        get() = prefs.getFloat(KEY_RUBBLE_ZONE_RADIUS, 55f).toDouble()
        set(value) {
            prefs.edit().putFloat(KEY_RUBBLE_ZONE_RADIUS, value.toFloat()).apply()
        }

    /** Altura automática con barómetro (si el dispositivo lo tiene). */
    var autoElevationActive: Boolean
        get() = prefs.getBoolean(KEY_AUTO_ELEVATION, true)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_ELEVATION, value).apply()
        }

    /** Altura manual de escaneo (m) cuando el barómetro está desactivado o no existe. */
    var manualElevationM: Double
        get() = prefs.getFloat(KEY_MANUAL_ELEVATION, 0f).toDouble()
        set(value) {
            prefs.edit().putFloat(KEY_MANUAL_ELEVATION, value.toFloat()).apply()
        }

    /** Modo Wi-Fi preferido: CONNECTED o PASSIVE_SCAN. */
    var defaultWifiMode: String
        get() = prefs.getString(KEY_DEFAULT_WIFI_MODE, "PASSIVE_SCAN") ?: "PASSIVE_SCAN"
        set(value) {
            prefs.edit().putString(KEY_DEFAULT_WIFI_MODE, value).apply()
        }

    /** Modo daltónico: iconos + paleta azul/ámbar/blanco (sin depender del rojo/verde). */
    var colorblindMode: Boolean
        get() = prefs.getBoolean(KEY_COLORBLIND, true)
        set(value) {
            prefs.edit().putBoolean(KEY_COLORBLIND, value).apply()
        }

    /** Idioma seleccionado: "" para sistema, "es", "en", "zh", etc. */
    var appLanguage: String
        get() = prefs.getString(KEY_APP_LANGUAGE, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_APP_LANGUAGE, value).apply()
        }

    fun loadRadarLayers(): RadarLayers = RadarLayers(
        grid = prefs.getBoolean(KEY_LAYER_GRID, true),
        compass = prefs.getBoolean(KEY_LAYER_COMPASS, true),
        wifi = prefs.getBoolean(KEY_LAYER_WIFI, true),
        heatmap = prefs.getBoolean(KEY_LAYER_HEATMAP, false),
        guide = prefs.getBoolean(KEY_LAYER_GUIDE, true),
        rubble = prefs.getBoolean(KEY_LAYER_RUBBLE, false),
        trail = prefs.getBoolean(KEY_LAYER_TRAIL, false),
        labels = prefs.getBoolean(KEY_LAYER_LABELS, true),
        depthRays = prefs.getBoolean(KEY_LAYER_DEPTH_RAYS, false),
    )

    fun saveRadarLayers(layers: RadarLayers) {
        prefs.edit()
            .putBoolean(KEY_LAYER_GRID, layers.grid)
            .putBoolean(KEY_LAYER_COMPASS, layers.compass)
            .putBoolean(KEY_LAYER_WIFI, layers.wifi)
            .putBoolean(KEY_LAYER_HEATMAP, layers.heatmap)
            .putBoolean(KEY_LAYER_GUIDE, layers.guide)
            .putBoolean(KEY_LAYER_RUBBLE, layers.rubble)
            .putBoolean(KEY_LAYER_TRAIL, layers.trail)
            .putBoolean(KEY_LAYER_LABELS, layers.labels)
            .putBoolean(KEY_LAYER_DEPTH_RAYS, layers.depthRays)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "vitalfi_settings"
        private const val KEY_ALERT_SOUND = "alert_sound_enabled"
        private const val KEY_TRAPPED_ONLY_V2 = "trapped_only_mode_v2"
        private const val KEY_VIBRATION = "vibration_enabled"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_SENSITIVITY = "detection_sensitivity"
        private const val KEY_RADAR_3D = "show_radar_3d"
        private const val KEY_RUBBLE_ZONE_CENTER = "rubble_zone_center_deg"
        private const val KEY_RUBBLE_ZONE_RADIUS = "rubble_zone_radius_deg"
        private const val KEY_AUTO_ELEVATION = "auto_elevation_active"
        private const val KEY_MANUAL_ELEVATION = "manual_elevation_m"
        private const val KEY_DEFAULT_WIFI_MODE = "default_wifi_mode"
        private const val KEY_COLORBLIND = "colorblind_mode"
        private const val KEY_APP_LANGUAGE = "app_language"
        private const val KEY_LAYER_GRID = "radar_layer_grid"
        private const val KEY_LAYER_COMPASS = "radar_layer_compass"
        private const val KEY_LAYER_WIFI = "radar_layer_wifi"
        private const val KEY_LAYER_HEATMAP = "radar_layer_heatmap"
        private const val KEY_LAYER_GUIDE = "radar_layer_guide"
        private const val KEY_LAYER_RUBBLE = "radar_layer_rubble"
        private const val KEY_LAYER_TRAIL = "radar_layer_trail"
        private const val KEY_LAYER_LABELS = "radar_layer_labels"
        private const val KEY_LAYER_DEPTH_RAYS = "radar_layer_depth_rays"
    }
}
