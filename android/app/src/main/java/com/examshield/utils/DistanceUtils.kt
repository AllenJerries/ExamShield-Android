package com.examshield.utils

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.examshield.data.models.DeviceType
import com.examshield.data.models.RiskLevel

fun calculateDistanceFromRssi(rssi: Int): Double {
    if (rssi == 0 || rssi <= -100) return 999.0
    val txPower = -59
    val ratio = (txPower - rssi).toDouble() / 20.0
    return Math.pow(10.0, ratio)
}

fun formatDistanceShort(distance: Double): String {
    return when {
        distance >= 999 -> "Unknown"
        distance < 0.3 -> "Right here"
        distance < 1.0 -> "${(distance * 100).toInt()}cm"
        distance < 10.0 -> "~${String.format("%.1f", distance)}m"
        distance < 100.0 -> "~${distance.toInt()}m"
        else -> "Very Far"
    }
}

fun formatDistanceLong(distance: Double): String {
    return when {
        distance >= 999 -> "Searching..."
        distance < 0.3 -> "Right Here!"
        distance < 1.0 -> "${(distance * 100).toInt()} cm away"
        distance < 10.0 -> "${String.format("%.1f", distance)} meters"
        distance < 100.0 -> "${distance.toInt()} meters"
        else -> "Very Far"
    }
}

fun getHelperText(distance: Double): String {
    return when {
        distance >= 999 -> "Move around to detect device"
        distance < 0.5 -> "Device is right here! Check nearby"
        distance < 1.0 -> "You're very close! Look around"
        distance < 3.0 -> "Getting warmer - keep moving"
        distance < 5.0 -> "In this area - continue searching"
        distance < 10.0 -> "Getting closer to the device"
        distance < 20.0 -> "Device is in this room somewhere"
        else -> "Move around to find signal"
    }
}

fun getDeviceIcon(type: DeviceType): ImageVector {
    return DeviceIcons.getIcon(type)
}

fun getDeviceIconFromString(type: String): ImageVector {
    return when {
        type.contains("PHONE_ANDROID") -> Icons.Default.Smartphone
        type.contains("PHONE_IOS") -> Icons.Default.PhoneIphone
        type.contains("SMARTWATCH") -> Icons.Default.Watch
        type.contains("EARPHONE") -> Icons.Default.Headphones
        type.contains("HIDDEN_EARPIECE") -> Icons.Default.Hearing
        type.contains("MOBILE_HOTSPOT") -> Icons.Default.WifiTethering
        type.contains("WIFI_DEVICE") -> Icons.Default.Wifi
        type.contains("UNKNOWN") -> Icons.Default.HelpOutline
        type.contains("OTHER") -> Icons.Default.Bluetooth
        else -> Icons.Default.HelpOutline
    }
}

fun getRiskColor(risk: RiskLevel): Color {
    return when (risk) {
        RiskLevel.HIGH -> Color(0xFFE53935)
        RiskLevel.MEDIUM -> Color(0xFFFFA000)
        RiskLevel.LOW -> Color(0xFF4CAF50)
        RiskLevel.CRITICAL -> Color(0xFFB71C1C)
    }
}

fun getRiskBackgroundColor(risk: RiskLevel): Color {
    return when (risk) {
        RiskLevel.HIGH -> Color(0xFFE53935).copy(alpha = 0.1f)
        RiskLevel.MEDIUM -> Color(0xFFFFA000).copy(alpha = 0.1f)
        RiskLevel.LOW -> Color.Transparent
        RiskLevel.CRITICAL -> Color(0xFFB71C1C).copy(alpha = 0.1f)
    }
}

fun getRiskColorFromString(risk: String): Color {
    return when (risk) {
        "HIGH" -> Color(0xFFE53935)
        "MEDIUM" -> Color(0xFFFFA000)
        "LOW" -> Color(0xFF4CAF50)
        "CRITICAL" -> Color(0xFFB71C1C)
        else -> Color(0xFF4CAF50)
    }
}

fun parseDeviceType(typeString: String): DeviceType {
    return try {
        DeviceType.valueOf(typeString)
    } catch (_: Exception) {
        DeviceType.UNKNOWN
    }
}
