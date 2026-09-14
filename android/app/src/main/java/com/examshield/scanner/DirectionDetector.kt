package com.examshield.scanner

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

    fun addSignalReading(rssi: Int) {
        history.add(rssi)
        if (history.size > 5) history.removeAt(0)
        analyzeDirection()
    }

    private fun analyzeDirection() {
        if (history.size < 3) {
            directionToDevice = Direction.SEARCHING
            return
        }

        val oldest = history.first()
        val newest = history.last()
        val diff = newest - oldest

        directionToDevice = when {
            diff >= 2 -> Direction.GETTING_CLOSER // Signal stronger
            diff <= -2 -> Direction.MOVING_AWAY   // Signal weaker
            else -> Direction.STAY                // Signal stable
        }
    }

    fun stop() {
        history.clear()
        directionToDevice = Direction.SEARCHING
    }
}