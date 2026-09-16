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

/**
 * Combines device orientation (rotation vector + linear acceleration) with
 * live signal-strength deltas to produce a movement-aware direction pointer.
 *
 * Calibration notes:
 *  - Sample & stability thresholds are tuned so the pointer leaves SEARCHING
 *    after ~3-5 walking steps (a handful of RSSI readings).
 *  - When the compass heading fluctuates (large spread inside the analysis
 *    window) the detector falls back to the last *stable* heading combined
 *    with the smoothed RSSI gradient, so the arrow still points instead of
 *    freezing in "SEARCHING...".
 */
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
    private var lastMovementHeading = 0f

    // Heading smoothing + stable-heading tracking (fallback source)
    private var smoothedHeading = 0f
    private var headingInitialized = false
    private var lastHeadingSample = 0f
    private var headingSampleCount = 0
    private var lastStableHeading = -1f

    // History
    private val history = mutableListOf<SignalPoint>()
    private val maxHistorySize = 24

    // Calibrated (lower) thresholds so SEARCHING clears within a few steps
    private val minReadingsForAnalysis = 3
    private val minRecentReadings = 3
    private val recentWindowMs = 8_000L
    private val minStableDirectionCount = 3
    private val minTrendMagnitude = 0.05
    private val maxHeadingSpreadForTrust = 60f

    var directionToDevice: Direction = Direction.SEARCHING
        private set

    var confidence: Float = 0f
        private set

    private var lastDirection: Direction = Direction.SEARCHING
    private var directionStableCount = 0
    private var usingFallback = false

    data class SignalPoint(
        val rssi: Int,
        val heading: Float,
        val timestamp: Long,
        val movement: Float
    )

    fun start() {
        lastMovementTime = System.currentTimeMillis()
        lastMovementHeading = 0f
        lastDirection = Direction.SEARCHING
        directionStableCount = 0
        headingInitialized = false
        headingSampleCount = 0
        lastStableHeading = -1f
        usingFallback = false
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        history.clear()
        directionToDevice = Direction.SEARCHING
        confidence = 0f
        usingFallback = false
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

                var rawHeading = Math.toDegrees(
                    orientation[0].toDouble()
                ).toFloat()
                if (rawHeading < 0) rawHeading += 360f

                if (!headingInitialized) {
                    smoothedHeading = rawHeading
                    lastHeadingSample = rawHeading
                    headingInitialized = true
                } else {
                    var delta = rawHeading - smoothedHeading
                    if (delta > 180f) delta -= 360f
                    if (delta < -180f) delta += 360f
                    smoothedHeading += delta * 0.3f
                    if (smoothedHeading < 0f) smoothedHeading += 360f
                    if (smoothedHeading >= 360f) smoothedHeading -= 360f
                }
                currentHeading = smoothedHeading

                var sampleDelta = rawHeading - lastHeadingSample
                if (sampleDelta > 180f) sampleDelta -= 360f
                if (sampleDelta < -180f) sampleDelta += 360f
                lastHeadingSample = rawHeading

                if (abs(sampleDelta) < 2.0f) {
                    headingSampleCount++
                    if (headingSampleCount >= 20) {
                        lastStableHeading = smoothedHeading
                    }
                } else {
                    headingSampleCount = 0
                }
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                accelMagnitude = sqrt(
                    event.values[0] * event.values[0] +
                        event.values[1] * event.values[1] +
                        event.values[2] * event.values[2]
                )

                if (accelMagnitude > 0.3f) {
                    lastMovementTime = System.currentTimeMillis()
                    lastMovementHeading = currentHeading
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

        // User is stationary -> no meaningful walking vector yet
        if (timeSinceMovement > 3000) {
            directionToDevice = Direction.STAY
            confidence = 0f
            return
        }

        val cutoff = System.currentTimeMillis() - recentWindowMs
        val recent = history.filter { it.timestamp > cutoff }

        if (recent.size < minRecentReadings) {
            directionToDevice = Direction.SEARCHING
            confidence = 15f
            return
        }

        val trend = calculateTrend(recent)

        val headings = recent.map { it.heading }
        val avgHeading = headings.average().toFloat()
        val headingSpread = computeHeadingSpread(headings)

        // 1) Choose a trustworthy heading. If the compass swung around inside
        //    the window, fall back to the last stable heading (or the heading
        //    captured at the last step) so the arrow still has an anchor.
        val reliableHeading = when {
            headingSpread <= maxHeadingSpreadForTrust -> avgHeading
            lastStableHeading >= 0f -> lastStableHeading
            lastMovementHeading != 0f -> lastMovementHeading
            directionToDevice in DIRECTIONAL -> headingFromDirection(directionToDevice)
            else -> avgHeading
        }
        usingFallback = headingSpread > maxHeadingSpreadForTrust

        val userDirection = getHeadingDirection(reliableHeading)

        val trendDirection = when {
            abs(trend) < minTrendMagnitude -> null
            trend > 0 -> userDirection
            else -> getOppositeDirection(userDirection)
        }

        if (trendDirection != null) {
            if (trendDirection == lastDirection) {
                directionStableCount++
            } else {
                lastDirection = trendDirection
                directionStableCount = 0
            }

            if (directionStableCount >= minStableDirectionCount) {
                directionToDevice = trendDirection
            }

            val trendConfidence = abs(trend).toFloat() * 100f
            confidence = (
                trendConfidence + directionStableCount * 12f
                ) * (if (usingFallback) 0.7f else 1.0f)
                .coerceIn(5f, 100f)
        } else {
            // No meaningful gradient yet — hold current pointer instead of
            // dropping back to SEARCHING once a cardinal direction is known.
            if (directionToDevice == Direction.SEARCHING) {
                confidence = 15f
            } else {
                confidence = (confidence * 0.9f).coerceAtLeast(5f)
            }
        }

        Log.d(
            TAG,
            "Direction: $directionToDevice | Trend: $trend | " +
                "Conf: ${confidence.toInt()} | Fallback: $usingFallback"
        )
    }

    /**
     * Signal strength trend (linear regression over reading index).
     * Positive => increasing signal (closer), negative => decreasing.
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

    private fun computeHeadingSpread(headings: List<Float>): Float {
        if (headings.isEmpty()) return 0f
        val maxHeading = headings.max()
        val minHeading = headings.min()
        return when {
            maxHeading - minHeading <= 180f -> maxHeading - minHeading
            else -> 360f - (maxHeading - minHeading)
        }
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

    private fun headingFromDirection(dir: Direction): Float {
        return when (dir) {
            Direction.FRONT -> 0f
            Direction.RIGHT -> 90f
            Direction.BACK -> 180f
            Direction.LEFT -> 270f
            else -> currentHeading
        }
    }

    companion object {
        private val DIRECTIONAL = setOf(
            Direction.FRONT,
            Direction.BACK,
            Direction.LEFT,
            Direction.RIGHT
        )
    }
}