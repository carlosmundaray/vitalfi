package com.whofi.vitalfi.settings

import android.content.Context

class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var alertSoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_ALERT_SOUND, true)
        set(value) {
            prefs.edit().putBoolean(KEY_ALERT_SOUND, value).apply()
        }

    companion object {
        private const val PREFS_NAME = "vitalfi_settings"
        private const val KEY_ALERT_SOUND = "alert_sound_enabled"
    }
}
