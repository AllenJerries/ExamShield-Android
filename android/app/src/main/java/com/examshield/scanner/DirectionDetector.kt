package com.examshield.scanner

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

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
 * Compass-Polar Peak Bearing direction engine.
 *
 * The compass heading (azimuth 0-360 deg) is divided into 12 polar sectors of
 * 30 degrees each. Each sector keeps the SMOOTHED RSSI peak observed while the
 * phone was pointing at it. The bearing with the highest RSSI peak is the
 * estimated direction to the hunted target, so the arrow points at the compass
 * sector holding the global maximum.
 *
 * Walking lock:
 *  - While the user walks and the live RSSI keeps RISING, the arrow locks
 *    FORWARD (0 degrees relative to the phone) — walking in the right direction
 *    feeds a continuously stronger signal.
 *  - If the live RSSI drops by more than 4 dBm mid-stride, the lock releases
 *    and the arrow smoothly rotates back toward the peak-bearing sector.
 *
 * The peak sector must persist across 2 consecutive readings before the arrow
 * commits, and peaks decay exponentially over ~18 s so a stale direction never
 * outlives the signal that produced it.
 */
class DirectionDetector(context: Context) : SensorEventListener {

    private val TAG = "DirectionDetector"

    private val sensorManager = context.getSystemService(
        Context.SENSOR_SERVICE
    ) as SensorManager

    // Primary heading source: fused rotation vector (compass). SENSOR_DELAY_FASTEST
    // (~200Hz) gives frame-by-frame azimuth updates so the polar sectors track the
    // phone's rotation with zero perceptible lag.
    private val rotationSensor = sensorManager.getDefaultSensor(
        Sensor.TYPE_ROTATION_VECTOR
    )
    private val magnetometer = sensorManager.getDefaultSensor(
        Sensor.TYPE_MAGNETIC_FIELD
    )
    private val rawAccelerometer = sensorManager.getDefaultSensor(
        Sensor.TYPE_ACCELEROMETER
    )

    // Compass-polar storage: one smoothed RSSI peak per 30-degree sector.
    private val SECTOR_COUNT = 12
    private val SECTOR_DEGREES = 30
    private val sectorRssi = FloatArray(SECTOR_COUNT)
    private val sectorLastUpdate = LongArray(SECTOR_COUNT)
    private val PEAK_DECAY_MS = 18_000L

    // Walking-lock thresholds.
    private val RSSI_RISE_TO_LOCK = 1.2f   // dBm gain while walking -> lock forward
    private val RSSI_DROP_TO_UNLOCK = 4.0f // dBm drop while walking -> steer to peak

    // Compass hysteresis: the raw azimuth must move at least 4 degrees before
    // the needle follows, locking it solid and killing micro-hand-shake jitter.
    private val MIN_AZIMUTH_DELTA = 4.0f

    var currentHeading: Float = 0f
        private set

    /**
     * Raw compass azimuth (0-360 deg) updated on EVERY rotation-vector sensor
     * frame with no smoothing and no lag. Consumers that need the instant
     * facing direction (arrow, azimuth readout) read this; [currentHeading]
     * stays smoothed for peak-sector bucketing stability.
     */
    var currentAzimuth: Float = 0f
        private set

    var directionToDevice: Direction = Direction.SEARCHING
        private set

    var confidence: Float = 0f
        private set

    /** Copy of the per-sector smoothed RSSI peaks (12 entries) for UI/debug. */
    val sectorRssiPeaks: List<Float>
        get() = sectorRssi.toList()

    private var readingCount = 0
    private var previousRssi = -100f

    // Walking-lock state.
    private var lockForward = false
    private var rssiAtLock = -100f

    // Peak-sector stability (commit only after 2 consecutive identical peaks).
    private var lastPeakSector = -1
    private var stablePeakCount = 0
    private var lastEmittedCardinal = -1

    // Motion state (from accelerometer magnitude) drives the walking lock.
    private var lastMovementTime = 0L
    private var accelMagnitude = 0f

    // Heading smoothing + fallback heading (magnetometer fusion path).
    private var smoothedHeading = 0f
    private var headingInitialized = false
    private var compassUpdateCount = 0
    private val compassLock: Boolean get() = compassUpdateCount >= 2

    private val gravityValues = FloatArray(3)
    private val magneticValues = FloatArray(3)
    private var haveGravity = false
    private var haveMagnetic = false
    private val MAG_LOW_PASS_ALPHA = 0.18f
    private var fallbackHeading = 0f

    /** Index of the polar sector (0..11) containing the given azimuth. */
    private fun azimuthToSector(azimuth: Float): Int {
        var az = azimuth % 360f
        if (az < 0f) az += 360f
        return (az / SECTOR_DEGREES).toInt() % SECTOR_COUNT
    }

    private fun sectorCenterDegrees(sector: Int): Float =
        sector * SECTOR_DEGREES + SECTOR_DEGREES / 2f

    fun start() {
        readingCount = 0
        previousRssi = -100f
        lockForward = false
        rssiAtLock = -100f
        lastPeakSector = -1
        stablePeakCount = 0
        lastEmittedCardinal = -1
        compassUpdateCount = 0
        headingInitialized = false
        haveGravity = false
        haveMagnetic = false
        currentAzimuth = 0f
        lastMovementTime = System.currentTimeMillis()
        directionToDevice = Direction.SEARCHING
        confidence = 0f
        for (i in 0 until SECTOR_COUNT) {
            sectorRssi[i] = -200f
            sectorLastUpdate[i] = 0L
        }

        val delay = SensorManager.SENSOR_DELAY_GAME
        if (rotationSensor != null) {
            // Rotor frames drive the no-lag azimuth readout -> FASTEST.
            sensorManager.registerListener(this, rotationSensor,
                SensorManager.SENSOR_DELAY_FASTEST)
        }
        magnetometer?.let { sensorManager.registerListener(this, it, delay) }
        rawAccelerometer?.let { sensorManager.registerListener(this, it, delay) }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        directionToDevice = Direction.SEARCHING
        confidence = 0f
        readingCount = 0
        lockForward = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                val rotationMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                val orientation = FloatArray(3)
                SensorManager.getOrientation(rotationMatrix, orientation)
                var rawHeading = Math.toDegrees(orientation[0].toDouble()).toFloat()
                if (rawHeading < 0) rawHeading += 360f
                // Compass hysteresis: only move the needle when the raw azimuth
                // actually moved past MIN_AZIMUTH_DELTA degrees, locking it solid
                // against micro-hand-shake jitter while staying zero-lag on real turns.
                if (abs(angleDelta(currentAzimuth, rawHeading)) > MIN_AZIMUTH_DELTA) {
                    currentAzimuth = rawHeading
                }
                updateSmoothedHeading(rawHeading)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val magnitude = Math.sqrt(
                    event.values[0].toDouble() * event.values[0] +
                        event.values[1] * event.values[1] +
                        event.values[2] * event.values[2]
                ).toFloat()
                accelMagnitude = magnitude
                // Movement = deviation from ~1g gravity footprint.
                if (abs(magnitude - 9.81f) > 0.5f) {
                    lastMovementTime = System.currentTimeMillis()
                }
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
        var rawHeading = Math.toDegrees(orientation[0].toDouble()).toFloat()
        if (rawHeading < 0) rawHeading += 360f
        fallbackHeading = rawHeading

        // If the fused rotation sensor is absent, use the magnetometer heading.
        if (rotationSensor == null) {
            updateSmoothedHeading(rawHeading)
        }
    }

    /** Signed shortest rotation [from] -> [to] in degrees, wrapped to (-180, 180]. */
    private fun angleDelta(from: Float, to: Float): Float {
        var delta = (to - from) % 360f
        if (delta > 180f) delta -= 360f
        if (delta <= -180f) delta += 360f
        return delta
    }

    private fun updateSmoothedHeading(rawHeading: Float) {
        compassUpdateCount++
        if (!headingInitialized) {
            smoothedHeading = rawHeading
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
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /**
     * Feeds one filtered RSSI sample of the hunted target ONLY (the caller
     * guarantees strict MAC isolation). The sample is stored into the polar
     * sector matching the current compass heading, the walking lock is
     * re-evaluated, and the arrow bearing is re-emitted.
     */
    fun addSignalReading(rssi: Int) {
        readingCount++
        val now = System.currentTimeMillis()

        // Stale sector peaks fade exponentially so a direction only survives as
        // long as the signal that produced it proves that bearing.
        decayStaleSectors(now)

        val prev = previousRssi
        previousRssi = rssi.toFloat()

        // Record the smoothed RSSI peak into the polar sector matching the
        // phone's current compass heading.
        val sector = azimuthToSector(currentHeading)
        sectorRssi[sector] = max(sectorRssi[sector], rssi.toFloat())
        sectorLastUpdate[sector] = now

        evaluateForwardLock(rssi, prev, now)
        emitBestDirection(now)
    }

    private fun decayStaleSectors(now: Long) {
        for (i in 0 until SECTOR_COUNT) {
            if (sectorLastUpdate[i] == 0L) continue
            val dt = (now - sectorLastUpdate[i]).toFloat()
            if (dt <= 0f) continue
            // Continuous exponential fade down to the -200 dBm floor.
            val factor = exp(-dt.toDouble() / PEAK_DECAY_MS).toFloat()
            val value = -200f + (sectorRssi[i] + 200f) * factor
            sectorRssi[i] = value
            if (value <= -199f) {
                sectorRssi[i] = -200f
                sectorLastUpdate[i] = 0L
            }
        }
    }

    private fun evaluateForwardLock(rssi: Int, prev: Float, now: Long) {
        val moving = now - lastMovementTime < 1500

        if (moving && rssi > prev + RSSI_RISE_TO_LOCK) {
            // Walking toward the target: live RSSI keeps climbing -> point the
            // arrow straight ahead of the phone.
            lockForward = true
            rssiAtLock = max(rssiAtLock, rssi.toFloat())
        } else if (lockForward && rssiAtLock - rssi > RSSI_DROP_TO_UNLOCK) {
            // Lost more than 4 dBm while walking -> released, steer to the
            // sector holding the strongest peak.
            lockForward = false
        } else if (rssi > rssiAtLock) {
            rssiAtLock = rssi.toFloat()
        }
    }

    private fun emitBestDirection(now: Long) {
        // Need at least a couple of target readings before committing a bearing.
        if (readingCount < 2) {
            if (directionToDevice == Direction.SEARCHING) confidence = 15f
            return
        }

        // Stationary user has no meaningful walking vector to compute.
        if (now - lastMovementTime > 3000) {
            directionToDevice = Direction.STAY
            confidence = 0f
            return
        }

        // Compass not fused yet (no heading update received) -> keep SEARCHING.
        if (!compassLock) {
            confidence = (confidence * 0.9f).coerceAtLeast(5f)
            return
        }

        if (lockForward) {
            commitCardinal(Direction.FRONT, 90f)
            Log.d(
                TAG,
                "Direction: ${directionToDevice} (LOCKED FORWARD) RSSI=$previousRssi"
            )
            return
        }

        val peak = peakSector()
        if (peak == null) {
            directionToDevice = Direction.SEARCHING
            confidence = 15f
            return
        }

        // Require the same peak sector on 2 consecutive readings so the arrow
        // does not flap between neighbouring sectors on a single noisy sample.
        if (peak.sectorIndex == lastPeakSector) {
            stablePeakCount++
        } else {
            lastPeakSector = peak.sectorIndex
            stablePeakCount = 1
        }

        if (stablePeakCount < 2) {
            confidence = (confidence * 0.9f).coerceAtLeast(5f)
            return
        }

        val cardinal = cardinalForAzimuth(peak.centerDegrees)
        val otherAvg = averageOtherSectors(peak.sectorIndex)
        val peakConfidence = (55f + (peak.peakRssi - otherAvg) * 4f)
            .coerceIn(15f, 95f)
        commitCardinal(cardinal, peakConfidence)

        Log.d(
            TAG,
            "Direction: $cardinal | peakSector=${peak.sectorIndex} " +
                "(${peak.centerDegrees}deg) peak=${peak.peakRssi}dBm | " +
                "azimuth=${currentHeading} | conf=${peakConfidence.toInt()}"
        )
    }

    private fun commitCardinal(dir: Direction, confidenceValue: Float) {
        val cardinal = when (dir) {
            Direction.FRONT -> 0
            Direction.RIGHT -> 1
            Direction.BACK -> 2
            Direction.LEFT -> 3
            else -> lastEmittedCardinal
        }
        if (cardinal != lastEmittedCardinal) {
            // Direction changed -> the UI's 600ms animation sweeps the arrow
            // smoothly from the old angle toward the new peak-bearing sector.
            lastEmittedCardinal = cardinal
        }
        directionToDevice = dir
        confidence = confidenceValue
    }

    private fun peakSector(): SectorPeak? {
        var idx = -1
        var best = -200f
        for (i in 0 until SECTOR_COUNT) {
            if (sectorRssi[i] > best) {
                best = sectorRssi[i]
                idx = i
            }
        }
        return if (idx < 0) {
            null
        } else {
            SectorPeak(idx, sectorCenterDegrees(idx), best)
        }
    }

    private fun averageOtherSectors(peakIdx: Int): Float {
        var sum = 0f
        var n = 0
        for (i in 0 until SECTOR_COUNT) {
            if (i != peakIdx) {
                sum += sectorRssi[i]
                n++
            }
        }
        return if (n == 0) -100f else sum / n
    }

    private fun cardinalForAzimuth(deg: Float): Direction {
        return when {
            deg < 45f || deg >= 315f -> Direction.FRONT
            deg in 45f..135f -> Direction.RIGHT
            deg in 135f..225f -> Direction.BACK
            deg in 225f..315f -> Direction.LEFT
            else -> Direction.UNKNOWN
        }
    }

    data class SectorPeak(
        val sectorIndex: Int,
        val centerDegrees: Float,
        val peakRssi: Float
    )
}