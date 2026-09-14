package com.examshield.scanner

import kotlin.math.abs

enum class Direction {
    SEARCHING,
    GETTING_CLOSER,
    MOVING_AWAY,
    STAY
}

class DirectionDetector {
    private val history = mutableListOf<Int>()
    var directionToDevice: Direction = Direction.SEARCHING
        private set

    var confidence: Int = 0
        private set

    fun addSignalReading(rssi: Int) {
        history.add(rssi)
        if (history.size > 6) history.removeAt(0)
        analyzeDirection()
    }

    private fun analyzeDirection() {
        if (history.size < 3) {
            directionToDevice = Direction.SEARCHING
            confidence = 0
            return
        }

        val firstAvg = (history[0] + history[1]) / 2
        val lastAvg = (history[history.size - 1] + history[history.size - 2]) / 2
        val diff = lastAvg - firstAvg

        directionToDevice = when {
            diff >= 3 -> Direction.GETTING_CLOSER // Signal getting stronger
            diff <= -3 -> Direction.MOVING_AWAY   // Signal getting weaker
            else -> Direction.STAY                // Signal stable
        }

        confidence = when (directionToDevice) {
            Direction.STAY -> 50
            else -> (abs(diff) * 15).coerceIn(40, 100)
        }
    }

    fun stop() {
        history.clear()
        directionToDevice = Direction.SEARCHING
        confidence = 0
    }
}