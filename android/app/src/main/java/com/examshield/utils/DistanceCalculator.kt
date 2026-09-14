package com.examshield.utils

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

object DistanceCalculator {

    private const val BLE_TX_POWER = -55
    private const val WIFI_TX_POWER = -40
    private const val INDOOR_PATH_LOSS = 2.5

    fun calculateDistance(
        rssi: Int,
        isWifi: Boolean = false
    ): Double {
        if (rssi == 0 || rssi <= -100) return 999.0

        val txPower = if (isWifi) WIFI_TX_POWER else BLE_TX_POWER

        val ratio = (txPower - rssi).toDouble() / (10 * INDOOR_PATH_LOSS)
        val distance = 10.0.pow(ratio)

        return distance.coerceIn(0.05, 100.0)
    }

    class DistanceSmoother {
        private var estimate: Double = 0.0
        private var errorEstimate: Double = 1.0
        private val processNoise: Double = 0.1
        private var initialized: Boolean = false

        fun update(measurement: Double): Double {
            if (!initialized) {
                estimate = measurement
                initialized = true
                return measurement
            }

            val change = abs(measurement - estimate)

            val measurementNoise = when {
                change > 5.0 -> 2.0
                change > 2.0 -> 1.0
                change > 1.0 -> 0.5
                else -> 0.3
            }

            errorEstimate += processNoise
            val kalmanGain = errorEstimate / (errorEstimate + measurementNoise)
            estimate += kalmanGain * (measurement - estimate)
            errorEstimate = (1 - kalmanGain) * errorEstimate

            return estimate
        }

        fun reset() {
            initialized = false
            estimate = 0.0
            errorEstimate = 1.0
        }
    }

    class RSSISmoother(private val bufferSize: Int = 5) {
        private val buffer = mutableListOf<Int>()

        fun addReading(rssi: Int): Int {
            synchronized(buffer) {
                buffer.add(rssi)
                if (buffer.size > bufferSize) {
                    buffer.removeAt(0)
                }

                if (buffer.size < 3) return rssi

                val sorted = buffer.sorted()
                return sorted[sorted.size / 2]
            }
        }

        fun reset() {
            synchronized(buffer) { buffer.clear() }
        }
    }

    fun formatDistance(distance: Double): String {
        return when {
            distance >= 999 -> "Searching..."
            distance < 0.1 -> "Right here!"
            distance < 0.3 -> "${(distance * 100).toInt()} cm!"
            distance < 1.0 -> "${(distance * 100).toInt()} cm"
            distance < 10.0 -> String.format("%.1f m", distance)
            distance < 100.0 -> "${distance.toInt()} m"
            else -> "Very far"
        }
    }

    fun getSignalStrength(rssi: Int): String {
        return when {
            rssi > -35 -> "Right here!"
            rssi > -50 -> "Excellent"
            rssi > -60 -> "Very Good"
            rssi > -70 -> "Good"
            rssi > -80 -> "Fair"
            rssi > -90 -> "Weak"
            else -> "Very Weak"
        }
    }

    fun getAccuracyEstimate(rssi: Int): Double {
        return when {
            rssi > -40 -> 98.0
            rssi > -55 -> 90.0
            rssi > -70 -> 75.0
            rssi > -85 -> 55.0
            else -> 30.0
        }
    }

    fun metersToApproxDbm(meters: Double): Int {
        if (meters <= 0) return 0
        val ratio = log10(meters) * (10 * INDOOR_PATH_LOSS)
        return (BLE_TX_POWER - ratio).toInt()
    }
}
