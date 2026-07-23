package com.vitalfi.wifi

enum class WifiCollectMode {
    CONNECTED,
    PASSIVE_SCAN,
}

data class ScannedNetwork(
    val ssid: String,
    val bssid: String,
    val rssiDbm: Int,
    val frequencyMhz: Int,
) {
    val bandLabel: String
        get() = when {
            frequencyMhz in 2400..2500 -> "2.4 GHz"
            frequencyMhz > 5000 -> "5 GHz"
            else -> "${frequencyMhz} MHz"
        }
}
