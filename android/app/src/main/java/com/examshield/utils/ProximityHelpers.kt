package com.examshield.utils

import androidx.compose.ui.graphics.Color

enum class ProximityLevel {
    SEARCHING,
    OUT_OF_RANGE,
    VERY_FAR,
    FAR,
    MEDIUM,
    CLOSE,
    VERY_CLOSE,
    FOUND
}

fun formatDistance(distance: Double): String {
    return when {
        distance < 0.5 -> "You're right next to it!"
        distance < 1.0 -> "< 1 meter"
        distance < 10.0 -> "${String.format("%.1f", distance)} meters"
        distance < 100.0 -> "${distance.toInt()} meters"
        else -> "Very Far"
    }
}

fun getProximityText(level: ProximityLevel): String {
    return when (level) {
        ProximityLevel.SEARCHING -> "SEARCHING..."
        ProximityLevel.OUT_OF_RANGE -> "OUT OF RANGE"
        ProximityLevel.VERY_FAR -> "VERY FAR"
        ProximityLevel.FAR -> "FAR"
        ProximityLevel.MEDIUM -> "GETTING CLOSER"
        ProximityLevel.CLOSE -> "CLOSE"
        ProximityLevel.VERY_CLOSE -> "VERY CLOSE"
        ProximityLevel.FOUND -> "DEVICE FOUND!"
    }
}

fun getProximityColor(level: ProximityLevel): Color {
    return when (level) {
        ProximityLevel.SEARCHING -> Color.Gray
        ProximityLevel.OUT_OF_RANGE -> Color.Gray
        ProximityLevel.VERY_FAR -> Color(0xFFE53935)
        ProximityLevel.FAR -> Color(0xFFFF6F00)
        ProximityLevel.MEDIUM -> Color(0xFFFFA000)
        ProximityLevel.CLOSE -> Color(0xFFFFC107)
        ProximityLevel.VERY_CLOSE -> Color(0xFF8BC34A)
        ProximityLevel.FOUND -> Color(0xFF4CAF50)
    }
}

fun getBackgroundColor(level: ProximityLevel): Color {
    return getProximityColor(level).copy(alpha = 0.1f)
}
