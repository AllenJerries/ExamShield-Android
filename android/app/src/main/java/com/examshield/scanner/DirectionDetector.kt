package com.examshield.scanner

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

enum class Direction {
    UNKNOWN,
    FRONT,
    BACK,
    LEFT,
    RIGHT,
    STAY,
    SEARCHING,
    FOUND
}

class DirectionDetector(context: Context) : SensorEventListener {

    private val TAG = "DirectionDetector"

    private val sensorManager = context.getSystemService(
        Context.SENSOR_SERVICE
    ) as SensorManager

    private val accelerometer = sensorManager.getDefaultSensor(
        Sensor.TYPE_LINEAR_ACCELERATION
    )
    private val rotationSensor = sensorManager.getDefaultSensor(
        Sensor.TYPE_ROTATION_VECTOR
    )

    var currentHeading: Float = 0f
        private set

    private var accelMagnitude = 0f
    private var lastMovementTime = 0L

    // History with more data points
    private val history = mutableListOf<SignalPoint>()
    private val maxHistorySize = 30  // Increased
    private val minReadingsForAnalysis = 8  // Need more data

    var directionToDevice: Direction = Direction.SEARCHING
        private set

    var confidence: Float = 0f
        private set

    // Stability tracking
    private var lastDirection: Direction = Direction.SEARCHING
    private var directionStableCount = 0

    data class SignalPoint(
        val rssi: Int,
        val heading: Float,
        val timestamp: Long,
        val movement: Float
    )

    fun start() {
        lastMovementTime = System.currentTimeMillis()
        lastDirection = Direction.SEARCHING
        directionStableCount = 0
        rotationSensor?.let {
            sensorManager.registerListener(
                this, it, SensorManager.SENSOR_DELAY_UI
            )
        }
        accelerometer?.let {
            sensorManager.registerListener(
                this, it, SensorManager.SENSOR_DELAY_UI
            )
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        history.clear()
        directionToDevice = Direction.SEARCHING
        confidence = 0f
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                val rotationMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(
                    rotationMatrix, event.values
                )
                val orientation = FloatArray(3)
                SensorManager.getOrientation(rotationMatrix, orientation)

                currentHeading = Math.toDegrees(
                    orientation[0].toDouble()
                ).toFloat()
                if (currentHeading < 0) currentHeading += 360f
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                accelMagnitude = sqrt(
                    event.values[0] * event.values[0] +
                        event.values[1] * event.values[1] +
                        event.values[2] * event.values[2]
                )

                if (accelMagnitude > 0.3f) {
                    lastMovementTime = System.currentTimeMillis()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun addSignalReading(rssi: Int) {
        val now = System.currentTimeMillis()

        history.add(SignalPoint(
            rssi = rssi,
            heading = currentHeading,
            timestamp = now,
            movement = accelMagnitude
        ))

        if (history.size > maxHistorySize) {
            history.removeAt(0)
        }

        if (history.size >= minReadingsForAnalysis) {
            analyzeDirection()
        }
    }

    private fun analyzeDirection() {
        val timeSinceMovement = System.currentTimeMillis() - lastMovementTime

        // Check if user is moving
        if (timeSinceMovement > 3000) {
            // Not moving for 3 seconds
            directionToDevice = Direction.STAY
            confidence = 0f
            return
        }

        // Get recent readings (last 10 seconds)
        val cutoff = System.currentTimeMillis() - 10000
        val recent = history.filter { it.timestamp > cutoff }

        if (recent.size < 5) {
            directionToDevice = Direction.SEARCHING
            confidence = 20f
            return
        }

        // GUARD: if the phone rotated a lot during this window, the heading
        // (and therefore the direction label) is unreliable. Keep last known.
        val headings = recent.map { it.heading }
        val maxHeading = headings.max()
        val minHeading = headings.min()
        val headingSpread = when {
            maxHeading - minHeading <= 180f -> maxHeading - minHeading
            else -> 360f - (maxHeading - minHeading)
        }
        if (headingSpread > 60f) {
            confidence = (confidence * 0.7f).coerceIn(0f, 100f)
            return
        }

        // Calculate signal trend using LINEAR REGRESSION
        val trend = calculateTrend(recent)

        // Get dominant heading
        val avgHeading = recent.map { it.heading }.average().toFloat()
        val userDirection = getHeadingDirection(avgHeading)

        // Determine direction based on signal trend
        // Higher threshold => only a strong, real signal change flips direction
        val newDirection = when {
            abs(trend) < 0.08 -> {
                // No significant change - keep current direction
                directionToDevice
            }
            trend > 0 -> {
                // Signal getting stronger - device is in walk direction
                userDirection
            }
            else -> {
                // Signal getting weaker - device is opposite
                getOppositeDirection(userDirection)
            }
        }

        // STABILITY CHECK - only change direction if consistent
        if (newDirection == lastDirection) {
            directionStableCount++
        } else {
            directionStableCount = 0
            lastDirection = newDirection
        }

        // Only update direction after 5 consistent readings
        if (directionStableCount >= 5) {
            directionToDevice = newDirection
        }

        // Confidence based on trend strength and stability
        val trendConfidence = abs(trend).toFloat() * 100f
        confidence = (trendConfidence + directionStableCount * 10f)
            .coerceIn(0f, 100f)

        Log.d(TAG, "Direction: $directionToDevice, Trend: $trend, Confidence: $confidence")
    }

    /**
     * Calculate signal trend using linear regression
     * Returns positive if increasing, negative if decreasing
     */
    private fun calculateTrend(readings: List<SignalPoint>): Double {
        if (readings.size < 2) return 0.0

        val n = readings.size
        val xValues = readings.mapIndexed { i, _ -> i.toDouble() }
        val yValues = readings.map { it.rssi.toDouble() }

        val xMean = xValues.average()
        val yMean = yValues.average()

        var numerator = 0.0
        var denominator = 0.0

        for (i in 0 until n) {
            numerator += (xValues[i] - xMean) * (yValues[i] - yMean)
            denominator += (xValues[i] - xMean).pow(2)
        }

        return if (denominator != 0.0) numerator / denominator else 0.0
    }

    private fun getHeadingDirection(heading: Float): Direction {
        return when {
            heading < 45 || heading >= 315 -> Direction.FRONT
            heading in 45f..135f -> Direction.RIGHT
            heading in 135f..225f -> Direction.BACK
            heading in 225f..315f -> Direction.LEFT
            else -> Direction.UNKNOWN
        }
    }

    private fun getOppositeDirection(dir: Direction): Direction {
        return when (dir) {
            Direction.FRONT -> Direction.BACK
            Direction.BACK -> Direction.FRONT
            Direction.LEFT -> Direction.RIGHT
            Direction.RIGHT -> Direction.LEFT
            else -> Direction.UNKNOWN
        }
    }
}
