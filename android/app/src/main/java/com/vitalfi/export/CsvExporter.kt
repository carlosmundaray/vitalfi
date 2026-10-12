package com.vitalfi.export

import android.content.Context
import com.vitalfi.wifi.RssiSample
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SessionLogEntry(
    val timestampMs: Long,
    val rssiDbm: Int?,
    val quality: Double?,
    val bearingDeg: Double,
    val status: String,
    val isBreathing: Boolean,
    val isActivity: Boolean,
    val respRate: Double,
    val confidence: Double,
    val rubbleConfidence: Double,
    val posX: Double?,
    val posY: Double?,
    val posDepth: Double?,
    val mode: String,
)

object CsvExporter {

    fun exportSession(
        context: Context,
        samples: List<RssiSample>,
        log: List<SessionLogEntry>,
        ssid: String,
        mode: String,
    ): File {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safeSsid = ssid.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(20)
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "vitalfi_${safeSsid}_$ts.csv")

        file.bufferedWriter().use { w ->
            w.appendLine("# VitalFi export — $ssid — modo: $mode")
            w.appendLine("# timestamp_iso,rssi_dbm,quality,ssid,bssid,source")
            val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)
            for (s in samples) {
                val qStr = String.format(Locale.US, "%.2f", s.quality)
                w.appendLine(
                    "${iso.format(Date(s.timestampMs))},${s.rssiDbm},$qStr," +
                        "\"${s.ssid}\",\"${s.bssid}\",${s.source}",
                )
            }
            w.appendLine()
            w.appendLine("# analysis_log")
            w.appendLine(
                "timestamp_iso,bearing_deg,status,is_breathing,is_activity,resp_rate," +
                    "confidence,rubble_conf,pos_x,pos_y,pos_depth,mode",
            )
            for (e in log) {
                val bStr = String.format(Locale.US, "%.1f", e.bearingDeg)
                val rStr = String.format(Locale.US, "%.2f", e.respRate)
                val cStr = String.format(Locale.US, "%.3f", e.confidence)
                val rcStr = String.format(Locale.US, "%.3f", e.rubbleConfidence)
                val pxStr = e.posX?.let { String.format(Locale.US, "%.3f", it) } ?: ""
                val pyStr = e.posY?.let { String.format(Locale.US, "%.3f", it) } ?: ""
                val pzStr = e.posDepth?.let { String.format(Locale.US, "%.3f", it) } ?: ""
                w.appendLine(
                    "${iso.format(Date(e.timestampMs))},$bStr,\"${e.status}\"," +
                        "${e.isBreathing},${e.isActivity},$rStr,$cStr,$rcStr," +
                        "$pxStr,$pyStr,$pzStr,${e.mode}",
                )
            }
        }
        return file
    }
}
