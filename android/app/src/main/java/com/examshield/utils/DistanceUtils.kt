package com.examshield.utils

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.examshield.data.models.DeviceSource
import com.examshield.data.models.DeviceType
import com.examshield.data.models.RiskLevel
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Source-aware Log-Distance Path Loss distance engine.
 *
 * Each radio type keeps its OWN calibrated propagation curve,
 * d = 10^((A - RSSI) / (10 * n)):
 *
 *  - Bluetooth / BLE / earphones:  A = -54.0 dBm, n = 1.9
 *    (-54 dBm is the 1 m reference for a phone-side BLE receiver; n = 1.9
 *    is a clean in-room BLE exponent that stays stable indoors.)
 *  - Wi-Fi / Hotspot:              A = -42.0 dBm, n = 2.4
 *    (AP TX power reference at 1 m; n = 2.4 approximates indoor multipath.)
 *
 * Hard ambient noise floor: any scan at or below -82 dBm is treated as weak
 * background RF (external / far-room interference, not a real target) and is
 * dropped entirely — it routes back to 999 ("Unknown") so no false positive
 * ever reaches the gauge, alerts or audio engine.
 *
 * Anti-stall fast path: per-target-MAC dynamic EMA (0.8 * raw + 0.2 * prev)
 * keeps distance output continuously moving the moment the signal shifts, and
 * [resetForNewHunt] flushes the whole cache when a hunt starts or switches
 * targets so stale numbers can never freeze on screen.
 */
private const val BT_TX_REFERENCE = -54.0
private const val BT_PATH_LOSS_EXPONENT = 1.9

private const val WIFI_TX_REFERENCE = -42.0
private const val WIFI_PATH_LOSS_EXPONENT = 2.4

private const val AMBIENT_NOISE_FLOOR_DBM = -82

private const val MAX_DISTANCE_METERS = 100.0

// Fast-path dynamic smoothing: 80% fresh sample + 20% previous, keyed per MAC
// so distance never stalls or freezes at a fixed value while hunting.
private const val FAST_PATH_MOTION_ALPHA = 0.8

// Smallest distance ever reported (3 cm = 0.03 m) so very-close targets render
// down to mm/cm granularity instead of flattening at the previous 5 cm floor.
private const val MIN_DISTANCE_METERS = 0.03

// Per-target fast-path cache keyed by MAC address; flushed on new hunts.
private val fastPathRssiCache = ConcurrentHashMap<String, Double>()

@Volatile
private var lastTargetMac: String? = null

fun isWifiSource(source: DeviceSource?): Boolean {
    return source == DeviceSource.WIFI_HOTSPOT || source == DeviceSource.WIFI_NETWORK
}

/**
 * Hard ambient-noise gate. Returns true when the sample is missing (0) or
 * sitting below -82 dBm — such readings are background RF, not a target.
 */
fun isBackgroundSignal(rssi: Int): Boolean {
    return rssi == 0 || rssi < AMBIENT_NOISE_FLOOR_DBM
}

/**
 * Flushes every cached per-target value — the fast-path smoothed RSSI cache,
 * the last tracked target MAC and (via the caller) the per-device smoothers.
 * MUST be called at the start of every hunt (and again when the hunt target
 * switches) so stale state can never stall or freeze distance at a fixed value.
 */
fun resetForNewHunt() {
    fastPathRssiCache.clear()
    lastTargetMac = null
}

/**
 * Fast-path dynamic smoothing, isolated per target MAC:
 * smoothed = 0.8 * rawRssi + 0.2 * prevSmoothed. Fresh samples dominate so the
 * output keeps sweeping with the live signal (no stall / freeze), while the
 * 20% memory damps sample-to-sample spikes. Returns the rounded smoothed dBm.
 */
fun smoothRssiFastPath(mac: String, rawRssi: Int): Int {
    val key = mac.uppercase()
    val prev = fastPathRssiCache[key]
    val smoothed = if (prev == null) {
        rawRssi.toDouble()
    } else {
        FAST_PATH_MOTION_ALPHA * rawRssi + (1.0 - FAST_PATH_MOTION_ALPHA) * prev
    }
    fastPathRssiCache[key] = smoothed
    lastTargetMac = key
    return kotlin.math.round(smoothed).toInt()
}

/** Last MAC fed through [smoothRssiFastPath] (used for per-target tracking). */
fun currentTargetMac(): String? = lastTargetMac

/**
 * Source-aware Log-Distance Path Loss. Bluetooth / BLE / earphones run their
 * own A = -54 / n = 1.9 curve; Wi-Fi / Hotspots run A = -42 / n = 2.4. Every
 * call recomputes from fresh (per-MAC median+fast-path+adaptive filtered) RSSI
 * so output never caches stale data. Crosses the -82 dBm noise floor and
 * (999 = unknown) returns 999.0 — the device is simply not a live signal.
 */
fun calculateDistanceFromRssi(rssi: Int, source: DeviceSource? = null): Double {
    if (isBackgroundSignal(rssi)) return 999.0
    return if (isWifiSource(source)) {
        wifiLogDistance(rssi)
    } else {
        bluetoothLogDistance(rssi)
    }
}

private fun bluetoothLogDistance(rssi: Int): Double {
    return Math.pow(
        10.0,
        (BT_TX_REFERENCE - rssi) / (10.0 * BT_PATH_LOSS_EXPONENT)
    ).coerceIn(MIN_DISTANCE_METERS, MAX_DISTANCE_METERS)
}

private fun wifiLogDistance(rssi: Int): Double {
    return Math.pow(
        10.0,
        (WIFI_TX_REFERENCE - rssi) / (10.0 * WIFI_PATH_LOSS_EXPONENT)
    ).coerceIn(MIN_DISTANCE_METERS, MAX_DISTANCE_METERS)
}

/**
 * Converts a raw RSSI reading into a human-readable physical distance
 * using the source-calibrated Log-Distance Path Loss model above.
 * Maps to mm (<0.1 m), cm (<1.0 m) and m (>=1.0 m) down to a 3 cm floor —
 * fully continuous and un-cached. Recomputes the distance from raw RSSI on
 * every call, so rendered output tracks an RSSI change with the caller's
 * update rate (<=100ms in hunt mode) and never caches stale data.
 * Any ambient reading below the -82 dBm noise floor renders as "Unknown".
 */
fun formatDistanceHuman(rssi: Int, source: DeviceSource? = null): String {
    if (isBackgroundSignal(rssi)) return "Unknown"
    val d = calculateDistanceFromRssi(rssi, source)
    return renderDistanceCompact(d)
}

/**
 * Radio-type overload: Wi-Fi / Hotspots resolve against A = -42 / n = 2.4,
 * everything else against A = -54 / n = 1.9. The MAC is accepted so callers
 * can pipe the hunted target's address through without any caching — output
 * is recomputed fresh from RSSI on every call (continuous, never stales).
 */
fun formatDistanceHuman(rssi: Int, isWifi: Boolean, mac: String? = null): String {
    if (isBackgroundSignal(rssi)) return "Unknown"
    val d = calculateDistanceFromRssi(
        rssi,
        if (isWifi) DeviceSource.WIFI_NETWORK else DeviceSource.BLUETOOTH
    )
    return renderDistanceCompact(d)
}

/** Shared mm / cm / m renderer — 3 cm floor, continuous, no caching. */
private fun renderDistanceCompact(d: Double): String {
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
 * Per-MAC RSSI isolation: keeps an independent 5-sample sliding window of raw
 * RSSI history for EVERY MAC address, so bursts of interference from one
 * device can never bleed into the distance estimate of another. For each
 * incoming sample the median of the last N readings is returned, stripping
 * single-sample noise spikes before any distance / direction / audio math.
 */
class PerMacRssiMedian(private val windowSize: Int = 5) {

    private val rssiHistory = ConcurrentHashMap<String, ArrayDeque<Int>>()

    @Synchronized
    fun medianFor(mac: String, rssi: Int): Int {
        val deque = rssiHistory.getOrPut(mac.uppercase()) { ArrayDeque() }
        deque.addLast(rssi)
        while (deque.size > windowSize) {
            deque.removeFirst()
        }
        val sorted = deque.sorted()
        return if (sorted.size % 2 == 1) {
            sorted[sorted.size / 2]
        } else {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
        }
    }

    @Synchronized
    fun removeMac(mac: String) {
        rssiHistory.remove(mac.uppercase())
    }

    @Synchronized
    fun clear() {
        rssiHistory.clear()
    }

    @Synchronized
    fun historyFor(mac: String): List<Int> {
        return rssiHistory[mac.uppercase()]?.toList() ?: emptyList()
    }
}

/**
 * Adaptive Moving Average (AMA) RSSI filter.
 *
 *  - |ΔRSSI| > 2.0 dBm (device physically moving): alpha = 0.95 — the estimate
 *    snaps almost entirely to the live reading for INSTANT motion response,
 *    so the gauge / arrow / audio engine react the moment the invigilator steps
 *    toward or away from the target.
 *  - |ΔRSSI| <= 2.0 dBm (target holding still, multipath jitter): alpha = 0.40
 *    — heavier static averaging strips single-sample multipath noise so the
 *    rendered distance stays rock-steady while the user is stationary.
 *
 *  smoothed = if (prev == null) raw else prev + alpha * (raw - prev)
 */
class AdaptiveRssiSmoother(
    private val motionAlpha: Double = 0.95,
    private val staticAlpha: Double = 0.40,
    private val deltaThresholdDbm: Double = 2.0
) {

    private var prev: Double? = null

    var smoothed: Double = 0.0
        private set

    fun addReading(rawRssi: Int): Int {
        val value = prev?.let { previous ->
            val alpha = if (abs(rawRssi - previous) > deltaThresholdDbm) {
                motionAlpha
            } else {
                staticAlpha
            }
            previous + alpha * (rawRssi - previous)
        } ?: rawRssi.toDouble()
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
