package com.whofi.vitalfi.wifi

object WifiCoverageEstimator {
    /**
     * Estimates usable Wi-Fi monitoring radius in meters from current RSSI.
     * Stronger signal generally means better local coverage for detection.
     */
    fun estimateRadiusM(rssiDbm: Int?): Double {
        if (rssiDbm == null) return 0.0
        val clamped = rssiDbm.coerceIn(-92, -35)
        val normalized = (clamped + 92) / 57.0
        return 1.8 + normalized * 6.2
    }
}
