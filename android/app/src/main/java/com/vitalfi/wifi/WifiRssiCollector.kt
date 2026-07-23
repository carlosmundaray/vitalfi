package com.vitalfi.wifi

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.ArrayDeque
import kotlin.math.max

data class RssiSample(
    val timestampMs: Long,
    val rssiDbm: Int,
    val quality: Double,
    val ssid: String,
    val bssid: String,
    val source: String = "connected",
)

class WifiRssiCollector(
    private val context: Context,
    val sampleRateHz: Double = 2.0,
    val maxSamples: Int = 180,
) {
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val buffer = ArrayDeque<RssiSample>()
    private var pollJob: Job? = null
    private var scanReceiver: BroadcastReceiver? = null
    private var rssiReceiver: BroadcastReceiver? = null
    private var wifiNetworkCallback: ConnectivityManager.NetworkCallback? = null
    private var lastConnectedRssiMs: Long = 0L

    var mode: WifiCollectMode = WifiCollectMode.PASSIVE_SCAN
    var targetSsid: String? = null
    var targetBssid: String? = null

    var onSample: ((RssiSample) -> Unit)? = null

    @Synchronized
    fun samplesSnapshot(): List<RssiSample> = buffer.toList()

    @Synchronized
    fun rssiSeriesSnapshot(): DoubleArray {
        val arr = DoubleArray(buffer.size)
        buffer.forEachIndexed { i, s -> arr[i] = s.rssiDbm.toDouble() }
        return arr
    }

    @Synchronized
    fun qualitySeriesSnapshot(): DoubleArray = rssiSeriesSnapshot()

    fun estimateSampleRateHz(samples: List<RssiSample>): Double {
        if (samples.size < 2) return sampleRateHz
        val spanMs = samples.last().timestampMs - samples.first().timestampMs
        if (spanMs <= 0L) return sampleRateHz
        return max(0.05, (samples.size - 1) * 1000.0 / spanMs)
    }

    /**
     * True when the phone is joined to a Wi-Fi AP at link layer.
     * Works even if Android routes traffic via mobile data because the router has no internet.
     * SSID may be hidden on some devices; BSSID + valid RSSI also count as associated.
     */
    fun isAssociatedWithWifi(): Boolean {
        if (!isWifiEnabled()) return false
        val (ssid, bssid) = readWifiAssociation()
        if (ssid.isNotBlank()) return true
        if (bssid.isNotBlank() && bssid != "02:00:00:00:00:00") return true
        val rssi = readRawConnectionRssi()
        return rssi != null
    }

    /** True when Wi-Fi is the active default network (usually means validated internet). */
    fun isWifiDefaultNetwork(): Boolean {
        val network = connectivity.activeNetwork ?: return false
        val caps = connectivity.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun isWifiConnected(): Boolean = isWifiDefaultNetwork() || isAssociatedWithWifi()

    fun usesWifiWithoutInternet(): Boolean =
        isAssociatedWithWifi() && !isWifiDefaultNetwork()

    @Suppress("DEPRECATION")
    fun isWifiEnabled(): Boolean = wifiManager.isWifiEnabled

    fun isMonitoring(): Boolean = when (mode) {
        WifiCollectMode.CONNECTED -> isAssociatedWithWifi() || readRawConnectionRssi() != null
        WifiCollectMode.PASSIVE_SCAN -> !targetSsid.isNullOrBlank()
    }

    fun connectionInfo(): Pair<String, String> {
        if (mode == WifiCollectMode.PASSIVE_SCAN) {
            return (targetSsid ?: "") to (targetBssid ?: "")
        }
        val (ssid, bssid) = readWifiAssociation()
        if (ssid.isNotBlank()) return ssid to bssid
        if (bssid.isNotBlank()) return "(red Wi-Fi)" to bssid
        return "" to ""
    }

    fun currentRssi(): Int? {
        if (mode == WifiCollectMode.PASSIVE_SCAN) {
            return samplesSnapshot().lastOrNull()?.rssiDbm
        }
        return readRawConnectionRssi()
    }

    @Suppress("DEPRECATION")
    private fun readRawConnectionRssi(): Int? {
        val rssi = wifiManager.connectionInfo?.rssi ?: return null
        return if (rssi <= -127) null else rssi
    }

    fun rssiToQuality(rssi: Int): Double {
        @Suppress("DEPRECATION")
        val level = WifiManager.calculateSignalLevel(rssi, 100)
        return level * 2.55
    }

    fun start(scope: CoroutineScope) {
        stop()
        when (mode) {
            WifiCollectMode.CONNECTED -> {
                startWifiBinding()
                startConnected(scope)
            }
            WifiCollectMode.PASSIVE_SCAN -> {
                if (!targetSsid.isNullOrBlank()) startPassive(scope)
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
        unregisterReceiver(scanReceiver)
        scanReceiver = null
        unregisterReceiver(rssiReceiver)
        rssiReceiver = null
        stopWifiBinding()
    }

    @Synchronized
    fun clear() {
        buffer.clear()
        lastConnectedRssiMs = 0L
    }

    @SuppressLint("MissingPermission")
    suspend fun scanNetworksAwait(timeoutMs: Long = 12_000L): List<ScannedNetwork> =
        withContext(Dispatchers.IO) {
            val deferred = CompletableDeferred<List<ScannedNetwork>>()
            val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    if (!deferred.isCompleted) {
                        deferred.complete(parseScanResults())
                    }
                }
            }
            registerReceiver(receiver, filter)
            try {
                @Suppress("DEPRECATION")
                wifiManager.startScan()
                delay(400)
                val cached = parseScanResults()
                if (cached.isNotEmpty() && !deferred.isCompleted) {
                    deferred.complete(cached)
                }
                withTimeoutOrNull(timeoutMs) { deferred.await() } ?: cached
            } finally {
                unregisterReceiver(receiver)
            }
        }

    fun selectPassiveTarget(network: ScannedNetwork) {
        targetSsid = network.ssid
        targetBssid = network.bssid
        mode = WifiCollectMode.PASSIVE_SCAN
        clear()
    }

    fun setConnectedMode() {
        mode = WifiCollectMode.CONNECTED
        targetSsid = null
        targetBssid = null
        clear()
    }

    private fun startConnected(scope: CoroutineScope) {
        registerRssiReceiver()
        val intervalMs = (1000.0 / sampleRateHz).toLong().coerceAtLeast(250L)
        pollJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                bindToExistingWifiNetwork()
                readConnected()
                delay(intervalMs)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startPassive(scope: CoroutineScope) {
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        scanReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val updated = intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) ?: false
                if (updated) readPassiveFromScan()
            }
        }
        registerReceiver(scanReceiver, filter)

        val scanIntervalMs = 8000L
        pollJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                @Suppress("DEPRECATION")
                wifiManager.startScan()
                readPassiveFromScan()
                delay(scanIntervalMs)
            }
        }
    }

    @Synchronized
    private fun readConnected() {
        val rssi = currentRssi() ?: return
        val now = System.currentTimeMillis()
        if (now - lastConnectedRssiMs < 200L) return
        lastConnectedRssiMs = now
        val (ssid, bssid) = connectionInfo()
        storeSample(rssi, ssid, bssid, "connected")
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    private fun readPassiveFromScan() {
        val ssid = targetSsid ?: return
        @Suppress("DEPRECATION")
        val results = wifiManager.scanResults ?: return
        val match = results.firstOrNull { r ->
            scanResultSsid(r) == ssid && (targetBssid.isNullOrBlank() || r.BSSID == targetBssid)
        } ?: results.firstOrNull { r -> scanResultSsid(r) == ssid } ?: return

        storeSample(match.level, scanResultSsid(match), match.BSSID ?: "", "passive-scan")
    }

    @Synchronized
    private fun storeSample(rssi: Int, ssid: String, bssid: String, source: String) {
        val sample = RssiSample(
            timestampMs = System.currentTimeMillis(),
            rssiDbm = rssi,
            quality = rssiToQuality(rssi),
            ssid = ssid,
            bssid = bssid,
            source = source,
        )
        buffer.addLast(sample)
        while (buffer.size > maxSamples) buffer.removeFirst()
        onSample?.invoke(sample)
    }

    @SuppressLint("MissingPermission")
    private fun readWifiAssociation(): Pair<String, String> {
        @Suppress("DEPRECATION")
        val info = wifiManager.connectionInfo ?: return "" to ""
        val ssid = info.ssid?.trim('"') ?: ""
        val bssid = info.bssid ?: ""
        if (ssid == "<unknown ssid>" || ssid.startsWith("0x")) return "" to ""
        return ssid to bssid
    }

    private fun startWifiBinding() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        stopWifiBinding()
        bindToExistingWifiNetwork()
        // Bind process to Wi-Fi even when cellular is the default route (router without WAN).
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        wifiNetworkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                connectivity.bindProcessToNetwork(network)
            }

            override fun onLost(network: Network) {
                // Android may fire onLost when Wi-Fi loses internet validation while still associated.
                if (!isAssociatedWithWifi()) {
                    connectivity.bindProcessToNetwork(null)
                } else {
                    bindToExistingWifiNetwork()
                }
            }
        }
        try {
            connectivity.requestNetwork(request, wifiNetworkCallback!!)
        } catch (_: Exception) {
            connectivity.registerNetworkCallback(request, wifiNetworkCallback!!)
        }
    }

    private fun stopWifiBinding() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            wifiNetworkCallback?.let { callback ->
                try {
                    connectivity.unregisterNetworkCallback(callback)
                } catch (_: IllegalArgumentException) {
                }
                try {
                    connectivity.bindProcessToNetwork(null)
                } catch (_: Exception) {
                }
            }
        }
        wifiNetworkCallback = null
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun bindToExistingWifiNetwork() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        for (network in connectivity.allNetworks) {
            val caps = connectivity.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                connectivity.bindProcessToNetwork(network)
                return
            }
        }
    }

    private fun registerRssiReceiver() {
        val filter = IntentFilter(WifiManager.RSSI_CHANGED_ACTION)
        rssiReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                readConnected()
            }
        }
        registerReceiver(rssiReceiver, filter)
    }

    @SuppressLint("MissingPermission")
    private fun parseScanResults(): List<ScannedNetwork> {
        @Suppress("DEPRECATION")
        val results = wifiManager.scanResults ?: return emptyList()
        return results
            .mapNotNull { r ->
                val ssid = scanResultSsid(r)
                if (ssid.isBlank()) return@mapNotNull null
                ScannedNetwork(
                    ssid = ssid,
                    bssid = r.BSSID ?: "",
                    rssiDbm = r.level,
                    frequencyMhz = r.frequency,
                )
            }
            .distinctBy { it.bssid }
            .sortedByDescending { it.rssiDbm }
    }

    @Suppress("DEPRECATION")
    private fun scanResultSsid(result: android.net.wifi.ScanResult): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return result.wifiSsid?.toString()?.trim('"') ?: ""
        }
        return result.SSID
    }

    private fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter) {
        if (receiver == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.applicationContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.applicationContext.registerReceiver(receiver, filter)
        }
    }

    private fun unregisterReceiver(receiver: BroadcastReceiver?) {
        if (receiver == null) return
        try {
            context.applicationContext.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
        }
    }
}
