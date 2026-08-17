package com.vitalfi.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val minRequiredVersionCode: Int,
    val forceUpdate: Boolean,
    val title: String,
    val message: String,
    val downloadUrl: String,
)

object UpdateChecker {
    private val UPDATE_URLS = listOf(
        "https://solvitco.com/vitalfi/version.json",
        "https://raw.githubusercontent.com/carlosmundaray/vitalfi/main/version.json",
    )

    suspend fun checkUpdate(currentVersionCode: Int): UpdateInfo? = withContext(Dispatchers.IO) {
        for (urlString in UPDATE_URLS) {
            try {
                val url = URL(urlString)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3000
                    readTimeout = 3000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "VitalFi-Android")
                }
                if (conn.responseCode == 200) {
                    val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(jsonStr)
                    val latestCode = json.optInt("latestVersionCode", 0)
                    val minCode = json.optInt("minRequiredVersionCode", latestCode)
                    val force = json.optBoolean("forceUpdate", false)
                    val latestName = json.optString("latestVersionName", "")
                    val title = json.optString("title", "Actualización requerida")
                    val msg = json.optString("message", "Por favor actualiza a la versión más reciente.")
                    val download = json.optString("downloadUrl", "https://solvitco.com/vitalfi/")

                    val isMandatory = (currentVersionCode < minCode) || (force && currentVersionCode < latestCode)
                    if (isMandatory) {
                        return@withContext UpdateInfo(
                            latestVersionCode = latestCode,
                            latestVersionName = latestName,
                            minRequiredVersionCode = minCode,
                            forceUpdate = force,
                            title = title,
                            message = msg,
                            downloadUrl = download,
                        )
                    } else {
                        return@withContext null
                    }
                }
            } catch (_: Exception) {
                // Silently ignore network errors (100% offline friendly)
            }
        }
        null
    }
}
