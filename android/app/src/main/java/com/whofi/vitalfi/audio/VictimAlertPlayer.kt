package com.whofi.vitalfi.audio

import android.media.AudioManager
import android.media.ToneGenerator

class VictimAlertPlayer {
    private var toneGenerator: ToneGenerator? = null

    fun playTrappedAlert() {
        try {
            val generator = toneGenerator ?: ToneGenerator(
                AudioManager.STREAM_ALARM,
                85,
            ).also { toneGenerator = it }
            generator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 350)
        } catch (_: Exception) {
            // Ignore if audio is unavailable on device.
        }
    }

    fun release() {
        toneGenerator?.release()
        toneGenerator = null
    }
}
