package com.examshield.data.models

data class ScanResult(
    val macAddress: String,
    val deviceName: String,
    val rssi: Int,
    val isBluetooth: Boolean,
    val isWifi: Boolean = false,
    val manufacturer: String = "Unknown",
    val scanRecord: ByteArray? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ScanResult) return false
        return macAddress == other.macAddress
    }

    override fun hashCode(): Int = macAddress.hashCode()
}
