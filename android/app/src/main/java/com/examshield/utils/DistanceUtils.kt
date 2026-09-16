package com.examshield.utils

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.examshield.data.models.DeviceSource
import com.examshield.data.models.DeviceType
import com.examshield.data.models.RiskLevel

/**
 * Radio-calibrated indoor Log-Distance Path Loss model.
 *
 *  d = 10^((A - RSSI) / (10 * n))
 *
 *  Bluetooth (BLE / Classic): A = -55 dBm @1m, n = 2.0 (near-field tuned)
 *  Wi-Fi / Hotspot:          A = -45 dBm @1m, n = 2.6 (higher obstruction)
 *
 * Near-field calibration: for BLE radios, RSSI >= -55 dBm is forced strictly
 * under 0.5 m and RSSI >= -40 dBm strictly under 0.15 m, so a device held
 * centimetres away can never read as "far away".
 */
private const val BLE_TX_REFERENCE = -55.0
private const val BLE_PATH_LOSS_EXPONENT = 2.0
private const val WIFI_TX_REFERENCE = -45.0
private const val WIFI_PATH_LOSS_EXPONENT = 2.6

fun isWifiSource(source: DeviceSource?): Boolean {
    return source == DeviceSource.WIFI_HOTSPOT || source == DeviceSource.WIFI_NETWORK
}

private fun referencePower(source: DeviceSource?): Double {
    return if (isWifiSource(source)) WIFI_TX_REFERENCE else BLE_TX_REFERENCE
}

private fun pathLossExponent(source: DeviceSource?): Double {
    return if (isWifiSource(source)) WIFI_PATH_LOSS_EXPONENT else BLE_PATH_LOSS_EXPONENT
}

/**
 * Indoor Log-Distance Path Loss with radio-specific calibration:
 * distance = 10^((A - rssi) / (10 * n)).
 *
 * Near-field clamping (BLE): RSSI >= -55 dBm forces a distance strictly below
 * 0.5 m, and RSSI >= -40 dBm forces it strictly below 0.15 m, so centimetre
 * contact never displays as metres away. Wi-Fi keeps its own curve.
 */
fun calculateDistanceFromRssi(rssi: Int, source: DeviceSource? = null): Double {
    if (rssi == 0 || rssi <= -100) return 999.0
    val calibrated = isWifiSource(source)
    val d = Math.pow(
        10.0,
        (referencePower(source) - rssi) / (10.0 * pathLossExponent(source))
    ).coerceIn(0.05, 100.0)

    return when {
        !calibrated && rssi >= -40 -> d.coerceAtMost(0.149) // strictly < 0.15 m
        !calibrated && rssi >= -55 -> d.coerceAtMost(0.499) // strictly < 0.50 m
        else -> d
    }
}

/**
 * Converts a raw RSSI reading into a human-readable physical distance
 * using the source-calibrated Log-Distance Path Loss model above.
 * Maps to mm (<0.1 m), cm (<1.0 m) and m (>=1.0 m). Recomputes the distance
 * from raw RSSI on every call, so rendered output tracks an RSSI change with
 * the caller's update rate (<=100ms in hunt mode) and never caches stale data.
 */
fun formatDistanceHuman(rssi: Int, source: DeviceSource? = null): String {
    if (rssi == 0 || rssi <= -100) return "Unknown"
    val d = calculateDistanceFromRssi(rssi, source)
    return when {
        d < 0.1 -> "${(d * 1000).toInt()} mm"
        d < 1.0 -> "${(d * 100).toInt()} cm"
        else -> String.format("%.1f m", d)
    }
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

/**
 * Asymmetric Exponential Moving Average (EMA) RSSI filter.
 *
 *  - Raw RSSI INCREASES (signal strengthens, device getting closer):
 *    the raw reading is used INSTANTLY (alpha = 1.0) — zero smoothing delay,
 *    so distance falls the moment the invigilator steps toward the target.
 *  - Raw RSSI DECREASES (device moving away): standard EMA with alpha = 0.75.
 *
 *  smoothedRssi = if (raw > prev) raw else 0.75*raw + 0.25*prev
 *
 * The symmetric delta between the two states recalibrates the estimate 1:1 on
 * approach (needed for close-range verdicts) while still damping retreat-side
 * single-sample BLE / Wi-Fi jitter on the way down.
 */
class RssiEmaSmoother(private val alpha: Double = 0.75) {

    private var prev: Double? = null

    var smoothed: Double = 0.0
        private set

    fun addReading(rawRssi: Int): Int {
        val previous = prev
        val value = if (previous == null) {
            rawRssi.toDouble()
        } else if (rawRssi > previous) {
            // Approaching: trust the live reading over any history.
            rawRssi.toDouble()
        } else {
            alpha * rawRssi + (1 - alpha) * previous
        }
        prev = value
        smoothed = value
        return kotlin.math.round(value).toInt()
    }

    fun reset() {
        prev = null
        smoothed = 0.0
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
