package com.examshield.utils

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.examshield.data.models.DeviceType

object DeviceIcons {

    fun getIcon(type: DeviceType): ImageVector {
        return when (type) {
            DeviceType.PHONE_ANDROID -> Icons.Default.Smartphone
            DeviceType.PHONE_IOS -> Icons.Default.PhoneIphone
            DeviceType.SMARTWATCH -> Icons.Default.Watch
            DeviceType.EARPHONE -> Icons.Default.Headphones
            DeviceType.HIDDEN_EARPIECE -> Icons.Default.Hearing
            DeviceType.TRACKING_BEACON -> Icons.Default.LocationOn
            DeviceType.MOBILE_HOTSPOT -> Icons.Default.WifiTethering
            DeviceType.WIFI_DEVICE -> Icons.Default.Wifi
            DeviceType.UNKNOWN -> Icons.Default.HelpOutline
            DeviceType.OTHER -> Icons.Default.Bluetooth
        }
    }

    fun getDisplayName(type: DeviceType): String {
        return when (type) {
            DeviceType.PHONE_ANDROID -> "Android Phone"
            DeviceType.PHONE_IOS -> "iPhone"
            DeviceType.SMARTWATCH -> "Smartwatch"
            DeviceType.EARPHONE -> "Earphone"
            DeviceType.HIDDEN_EARPIECE -> "Hidden Earpiece"
            DeviceType.TRACKING_BEACON -> "Tracking Beacon"
            DeviceType.MOBILE_HOTSPOT -> "Mobile Hotspot"
            DeviceType.WIFI_DEVICE -> "WiFi Network"
            DeviceType.UNKNOWN -> "Unknown Device"
            DeviceType.OTHER -> "Bluetooth Device"
        }
    }

    fun getShortName(type: DeviceType): String {
        return when (type) {
            DeviceType.PHONE_ANDROID -> "Phone"
            DeviceType.PHONE_IOS -> "iPhone"
            DeviceType.SMARTWATCH -> "Watch"
            DeviceType.EARPHONE -> "Earphone"
            DeviceType.HIDDEN_EARPIECE -> "Hidden Device"
            DeviceType.TRACKING_BEACON -> "Beacon"
            DeviceType.MOBILE_HOTSPOT -> "Hotspot"
            DeviceType.WIFI_DEVICE -> "WiFi"
            DeviceType.UNKNOWN -> "Unknown"
            DeviceType.OTHER -> "BT Device"
        }
    }

    fun getSearchHint(type: DeviceType): String {
        return when (type) {
            DeviceType.PHONE_ANDROID ->
                "Check students' pockets and bags for Android phones"
            DeviceType.PHONE_IOS ->
                "Check students' pockets and bags for iPhones"
            DeviceType.SMARTWATCH ->
                "Check students' wrists for smartwatches"
            DeviceType.EARPHONE ->
                "Check students' ears carefully for wireless earphones"
            DeviceType.HIDDEN_EARPIECE ->
                "SUSPICIOUS! Check ears for hidden earpieces"
            DeviceType.TRACKING_BEACON ->
                "Hidden tracking beacon detected - search bags and desks"
            DeviceType.MOBILE_HOTSPOT ->
                "Someone is sharing internet from their phone"
            DeviceType.WIFI_DEVICE ->
                "WiFi network detected in area"
            DeviceType.UNKNOWN ->
                "Unknown device - could be hidden cheating tool"
            DeviceType.OTHER ->
                "Follow signal to locate device"
        }
    }

    fun getRiskLevel(type: DeviceType): String {
        return when (type) {
            DeviceType.PHONE_ANDROID,
            DeviceType.PHONE_IOS,
            DeviceType.SMARTWATCH,
            DeviceType.EARPHONE,
            DeviceType.HIDDEN_EARPIECE,
            DeviceType.TRACKING_BEACON,
            DeviceType.MOBILE_HOTSPOT -> "CRITICAL - Common Cheating Device"
            DeviceType.WIFI_DEVICE -> "MEDIUM - Possible Threat"
            DeviceType.UNKNOWN -> "SUSPICIOUS - Investigate"
            DeviceType.OTHER -> "LOW - Standard Device"
        }
    }
}
