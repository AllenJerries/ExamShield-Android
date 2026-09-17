package com.examshield.data.models

enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

enum class DeviceType {
    PHONE_ANDROID,
    PHONE_IOS,
    SMARTWATCH,
    EARPHONE,
    HIDDEN_EARPIECE,
    TRACKING_BEACON,
    MOBILE_HOTSPOT,
    WIFI_DEVICE,
    UNKNOWN,
    OTHER
}

data class DeviceRisk(
    val level: RiskLevel,
    val description: String,
    val icon: String = "bluetooth"
)

enum class DeviceSource {
    BLUETOOTH,
    WIFI_NETWORK,
    WIFI_HOTSPOT
}

data class UnifiedDevice(
    val macAddress: String,
    val name: String,
    val rssi: Int,
    val source: DeviceSource,
    val deviceType: DeviceType,
    val riskLevel: RiskLevel,
    val manufacturer: String = "Unknown",
    val isHotspot: Boolean = false,
    val frequency: Int = 0,
    val firstSeen: Long = System.currentTimeMillis(),
    var lastSeen: Long = System.currentTimeMillis(),
    val scanRecord: ByteArray? = null,
    val serviceUuids: List<String> = emptyList(),
    val description: String = ""
) {
    val estimatedDistance: Double
        get() = calculateDistance(rssi)

    val proximityScore: Double
        get() = when {
            rssi > -40 -> 0.5
            rssi > -50 -> 1.0
            rssi > -60 -> 2.0
            rssi > -70 -> 5.0
            rssi > -80 -> 10.0
            rssi > -90 -> 20.0
            else -> 50.0
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UnifiedDevice) return false
        return macAddress == other.macAddress
    }

    override fun hashCode(): Int = macAddress.hashCode()

    companion object {
        fun calculateDistance(rssi: Int): Double {
            if (rssi == 0 || rssi <= -100) return 999.0
            // Bluetooth-calibrated Log-Distance Path Loss: A = -59 dBm, n = 2.2
            val ratio = (-59.0 - rssi) / (10.0 * 2.2)
            return Math.pow(10.0, ratio).coerceIn(0.05, 100.0)
        }
    }
}
