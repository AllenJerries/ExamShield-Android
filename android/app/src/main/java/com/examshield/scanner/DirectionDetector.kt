package com.examshield.scanner

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.abs
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
 * Combines device orientation (rotation vector + accelerometer/magnetometer
 * fallback at SENSOR_DELAY_GAME) with a 2-sample signal gradient (deltaRSSI)
 * to produce a near-zero-lag direction pointer.
 *
 * Gradient rules:
 *  - deltaRSSI >  +0.8 dBm -> signal strengthening -> arrow toward current azimuth
 *  - deltaRSSI <  -0.8 dBm -> signal weakening   -> arrow rotated 180 degrees
 *  - |deltaRSSI| <= 0.8    -> no azimuth update (pointer held)
 *
 * The 2-sample window (last smoothed RSSI minus the one before it) flips the
 * arrow within 1-2 steps. The detector leaves "SEARCHING..." after 2
 * consecutive compass heading updates; when the compass heading fluctuates
 * (large spread) it falls back to the last stable heading combined with the
 * smoothed RSSI gradient.
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

    // Raw accelerometer + magnetometer fallback heading source. Rotation
    // vector fuses all three internally, but when it is unavailable (some
    // devices / emulators) we reconstruct the heading the classic way.
    private val rawAccelerometer = sensorManager.getDefaultSensor(
        Sensor.TYPE_ACCELEROMETER
    )
    private val magnetometer = sensorManager.getDefaultSensor(
        Sensor.TYPE_MAGNETIC_FIELD
    )
    private val gravityValues = FloatArray(3)
    private val magneticValues = FloatArray(3)
    private var haveGravity = false
    private var haveMagnetic = false
    private val MAG_LOW_PASS_ALPHA = 0.18f
    private var fallbackHeading = 0f

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

    // Calibrated (low) thresholds so SEARCHING clears within 1-2 steps
    private val minReadingsForAnalysis = 2
    private val minRecentReadings = 2
    private val recentWindowMs = 8_000L
    private val minStableDirectionCount = 3
    private val maxHeadingSpreadForTrust = 60f

    // Signal-gradient rule: 2-sample sliding window of smoothed RSSI keeps the
    // arrow reacting to the very last RSSI delta (1-2 steps of movement).
    private val RSSI_WINDOW_SIZE = 2
    private val DELTA_RSSI_THRESHOLD = 0.8
    private val rssiWindow = ArrayDeque<Int>()

    // Compass lock — leaves SEARCHING after 2 consecutive heading updates.
    private var compassUpdateCount = 0
    private val compassLock: Boolean get() = compassUpdateCount >= 2

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
        haveGravity = false
        haveMagnetic = false
        rssiWindow.clear()
        compassUpdateCount = 0

        // SENSOR_DELAY_GAME (~50Hz) — instant orientation response when turning
        // the phone so the arrow tracks the compass without perceptible lag.
        val delay = SensorManager.SENSOR_DELAY_GAME
        rotationSensor?.let { sensorManager.registerListener(this, it, delay) }
        accelerometer?.let { sensorManager.registerListener(this, it, delay) }
        // Fallback sensors for devices lacking TYPE_ROTATION_VECTOR.
        rawAccelerometer?.let { sensorManager.registerListener(this, it, delay) }
        magnetometer?.let { sensorManager.registerListener(this, it, delay) }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        history.clear()
        rssiWindow.clear()
        compassUpdateCount = 0
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

                updateSmoothedHeading(rawHeading)
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
            Sensor.TYPE_ACCELEROMETER -> {
                // Low-pass gravity estimate for the magnetometer fusion path.
                for (i in 0..2) {
                    gravityValues[i] = gravityValues[i] * MAG_LOW_PASS_ALPHA +
                        event.values[i] * (1f - MAG_LOW_PASS_ALPHA)
                    if (!haveGravity) gravityValues[i] = event.values[i]
                }
                haveGravity = true
                computeFallbackHeading()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                for (i in 0..2) {
                    magneticValues[i] = magneticValues[i] * MAG_LOW_PASS_ALPHA +
                        event.values[i] * (1f - MAG_LOW_PASS_ALPHA)
                    if (!haveMagnetic) magneticValues[i] = event.values[i]
                }
                haveMagnetic = true
                computeFallbackHeading()
            }
        }
    }

    private fun computeFallbackHeading() {
        if (!haveGravity || !haveMagnetic) return

        val rotationMatrix = FloatArray(9)
        if (!SensorManager.getRotationMatrix(
                rotationMatrix, null, gravityValues, magneticValues
            )
        ) return

        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientation)

        var rawHeading = Math.toDegrees(
            orientation[0].toDouble()
        ).toFloat()
        if (rawHeading < 0) rawHeading += 360f

        fallbackHeading = rawHeading

        // If the fused rotation sensor is absent, use the magnetometer
        // heading as the primary source.
        if (rotationSensor == null) {
            updateSmoothedHeading(rawHeading)
        }
    }

    private fun updateSmoothedHeading(rawHeading: Float) {
        compassUpdateCount++
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

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun addSignalReading(rssi: Int) {
        val now = System.currentTimeMillis()

        // 2-sample sliding window of smoothed RSSI values for deltaRSSI.
        rssiWindow.addLast(rssi)
        while (rssiWindow.size > RSSI_WINDOW_SIZE) {
            rssiWindow.removeFirst()
        }

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

        // Signal gradient from the 2-sample sliding window of smoothed RSSI.
        val deltaRssi = windowDeltaRssi()

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

        // 2) Gradient rule for the target direction angle:
        //      deltaRSSI >  +0.8 -> signal strengthening -> arrow toward azimuth
        //      deltaRSSI <  -0.8 -> signal weakening   -> arrow rotated 180 deg
        //      |deltaRSSI| <= 0.8 -> no azimuth update
        val trendDirection = when {
            abs(deltaRssi) < DELTA_RSSI_THRESHOLD -> null
            deltaRssi > 0 -> userDirection
            else -> getOppositeDirection(userDirection)
        }

        if (trendDirection != null) {
            if (trendDirection == lastDirection) {
                directionStableCount++
            } else {
                lastDirection = trendDirection
                directionStableCount = 0
            }

            // Once the compass has produced 2 consecutive heading updates a
            // single gradient reading is enough to clear the SEARCHING state.
            val requiredStability = if (compassLock) 1 else minStableDirectionCount
            if (directionStableCount >= requiredStability) {
                directionToDevice = trendDirection
            }

            val trendConfidence = abs(deltaRssi).toFloat() * 100f
            confidence = (
                trendConfidence + directionStableCount * 12f
                ) * (if (usingFallback) 0.7f else 1.0f)
                .coerceIn(5f, 100f)
        } else {
            // No meaningful gradient yet. Hold the current pointer instead of
            // dropping back to SEARCHING once a cardinal direction is known.
            if (directionToDevice == Direction.SEARCHING) {
                if (compassLock) {
                    // 2 compass heading updates received -> exit SEARCHING and
                    // aim the arrow along the current azimuth.
                    directionToDevice = userDirection
                    confidence = 20f
                } else {
                    confidence = 15f
                }
            } else {
                confidence = (confidence * 0.9f).coerceAtLeast(5f)
            }
        }

        Log.d(
            TAG,
            "Direction: $directionToDevice | deltaRSSI: $deltaRssi | " +
                "Heading: $reliableHeading | Conf: ${confidence.toInt()} | " +
                "CompassLock: $compassLock"
        )
    }

    /**
     * Newest smoothed RSSI minus the previous smoothed RSSI inside the
     * 2-sample sliding window. Live as soon as 2 readings have arrived.
     */
    private fun windowDeltaRssi(): Double {
        if (rssiWindow.size < RSSI_WINDOW_SIZE) return 0.0
        return (rssiWindow.last() - rssiWindow.first()).toDouble()
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