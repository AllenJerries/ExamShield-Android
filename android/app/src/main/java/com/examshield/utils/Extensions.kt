package com.examshield.utils

import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

fun Long.toFormattedDate(): String {
    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    return sdf.format(Date(this))
}

fun Long.toFormattedDateTime(): String {
    val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    return sdf.format(Date(this))
}

fun Long.toFormattedTime(): String {
    val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
    return sdf.format(Date(this))
}

fun Long.toFormattedTimeWithSeconds(): String {
    val sdf = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
    return sdf.format(Date(this))
}

fun Long.toApproxTimeAgo(): String {
    val elapsed = System.currentTimeMillis() - this
    return when {
        elapsed < 1000 -> "Just now"
        elapsed < 60_000 -> "${elapsed / 1000}s ago"
        elapsed < 3_600_000 -> "${elapsed / 60_000}m ago"
        elapsed < 86_400_000 -> "${elapsed / 3_600_000}h ago"
        else -> "${elapsed / 86_400_000}d ago"
    }
}

fun Int.formatDuration(): String {
    val hours = this / 60
    val minutes = this % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

fun Long.elapsedTimeFormatted(): String {
    val elapsed = System.currentTimeMillis() - this
    val seconds = (elapsed / 1000) % 60
    val minutes = (elapsed / (1000 * 60)) % 60
    val hours = elapsed / (1000 * 60 * 60)
    return String.format("%02d:%02d:%02d", hours, minutes, seconds)
}

fun Int.rssiToPercentage(): Int {
    val strength = abs(this)
    return when {
        strength <= 30 -> 100
        strength >= 90 -> 0
        else -> ((90 - strength) * 100 / 60).coerceIn(0, 100)
    }
}

fun String.isValidMacAddress(): Boolean {
    val macRegex = "^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$".toRegex()
    return macRegex.matches(this)
}
