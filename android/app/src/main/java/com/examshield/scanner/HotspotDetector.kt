package com.examshield.scanner

import android.util.Log

/**
 * Shared heuristic engine used by every WiFi scanning path (continuous,
 * baseline, and unified) to classify an access point / scan result as a
 * mobile hotspot instead of a plain WiFi network.
 *
 * A network is treated as a hotspot when its SSID, capability tags, BSSID
 * OUI, or frequency/WPA pattern strongly indicate a phone or portable
 * tethering device.
 */
object HotspotDetector {

    private const val TAG = "HotspotDetector"

    private val HOTSPOT_SSID_KEYWORDS = listOf(
        "androidap", "android_hotspot", "mywifi",
        "iphone", "galaxy", "redmi",
        "vivo", "oppo", "realme", "oneplus",
        "poco", "mi ", "samsung", "moto",
        "pixel", "nothing", "iqoo", "infinix",
        "tecno", "hotspot", "phone", "mobile",
        "personal hotspot", "portable", "tethering"
    )

    private val HOTSPOT_CAPABILITY_KEYWORDS = listOf(
        "androidap", "hotspot", "tethering", "tether",
        "wifidirect", "p2p", "direct-"
    )

    private val MOBILE_OUIS = listOf(
        "F0C77F", "00265C", "D0176A", "8425DB", "34145F", "3413E8", "5C0A5B",
        "00259C", "D89B3B", "F0DBE2", "68967B", "F41BA1", "5CF7E6",
        "A0999B", "8C1D96", "0C1105", "50EC50", "68DFDD", "742344",
        "94E979", "38A28C", "68D247", "A81B18", "8CBFA6",
        "2C6E85", "9CE33F", "94652D",
        "D46A6A", "3096FB", "80B03D",
        "0865A9", "5C7188", "60B4F7",
        "F4F5D8", "34E12D", "3417EB",
        "0421B0", "0022FB", "24E314",
        "60ABD2", "1CAFF7", "4C34B3", "F09E63"
    )

    fun isMobileHotspot(
        ssid: String,
        bssid: String,
        frequency: Int = 0,
        capabilities: String = ""
    ): Boolean {
        val ssidLower = ssid.lowercase()

        if (ssidLower.startsWith("direct-")) {
            Log.d(TAG, "Hotspot by WiFi-Direct SSID: $ssid")
            return true
        }

        if (HOTSPOT_SSID_KEYWORDS.any { ssidLower.contains(it) }) {
            Log.d(TAG, "Hotspot by name: $ssid")
            return true
        }

        val capLower = capabilities.lowercase()
        if (HOTSPOT_CAPABILITY_KEYWORDS.any { capLower.contains(it) }) {
            Log.d(TAG, "Hotspot by capability tag: $ssid [$capabilities]")
            return true
        }

        if (isMobileMac(bssid)) {
            Log.d(TAG, "Hotspot by MAC: $bssid")
            return true
        }

        val is2_4GHz = frequency in 2412..2484
        val isWPA2 = capabilities.contains("WPA2")
        val hasNumberPattern = ssid.matches(Regex(".*[0-9]{4,}.*"))

        if (is2_4GHz && isWPA2 && hasNumberPattern) {
            Log.d(TAG, "Hotspot by pattern: $ssid ($frequency MHz)")
            return true
        }

        return false
    }

    private fun isMobileMac(mac: String): Boolean {
        if (mac.length < 8) return false
        val oui = mac.substring(0, 8).replace(":", "").uppercase()
        return MOBILE_OUIS.any { oui.startsWith(it) }
    }
}