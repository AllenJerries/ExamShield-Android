package com.examshield.utils

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import kotlinx.coroutines.*
import kotlin.math.max
import kotlin.math.min

/**
 * Dynamic proximity audio beep engine.
 *
 * Drives a [ToneGenerator] (on the ALARM stream so beeps are audible even
 * with the media volume down) and continuously rescales the beep cadence to
 * the target's live RSSI:
 *
 *  - RSSI > -50 dBm (Very Close) -> beep every 90 ms
 *  - RSSI in (-75, -50] dBm     -> beep every 350 ms
 *  - RSSI <= -75 dBm (Far)      -> beep every 1000 ms
 *
 * The interval is re-evaluated on every tick by pulling the current RSSI
 * through [getRssi], so acceleration/deceleration tracks movement in real
 * time. Only one beep loop may run at a time.
 */
object BeepManager {

    private const val TAG = "BeepManager"

    private const val BEEP_DURATION_MS = 140
    private const val DEFAULT_VOLUME = 80

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var beepJob: Job? = null
    private var toneGenerator: ToneGenerator? = null
    private var lastInterval = -1L

    fun getBeepIntervalMs(rssi: Int): Long {
        return when {
            rssi > -50 -> 90L
            rssi > -75 -> 350L
            else -> 1000L
        }
    }

    /**
     * Starts a continuous, cadence-scaling beep loop. Call [stop] to end it.
     *
     * @param context used to select/dial the alarm stream volume
     * @param getRssi invoked each tick to fetch the freshest target RSSI
     * @param volume  0..100 dial attenuation (defaults to 80)
     */
    fun startProximityBeeping(
        context: Context,
        getRssi: () -> Int,
        volume: Int = DEFAULT_VOLUME
    ) {
        stop()

        toneGenerator = createToneGenerator(context, volume)

        beepJob = scope.launch {
            while (isActive) {
                val rssi = try {
                    getRssi()
                } catch (e: Exception) {
                    Log.w(TAG, "getRssi error: ${e.message}")
                    -100
                }

                val interval = getBeepIntervalMs(rssi)
                if (interval != lastInterval) {
                    Log.d(TAG, "Beep cadence -> ${interval}ms (RSSI $rssi)")
                    lastInterval = interval
                }

                try {
                    toneGenerator?.startTone(
                        ToneGenerator.TONE_PROP_BEEP2,
                        BEEP_DURATION_MS
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "startTone error: ${e.message}")
                }

                delay(interval)
            }
        }

        Log.d(TAG, "Proximity beeping started")
    }

    fun stop() {
        beepJob?.cancel()
        beepJob = null
        lastInterval = -1L
        try {
            toneGenerator?.stopTone()
        } catch (e: Exception) {
            Log.w(TAG, "stopTone error: ${e.message}")
        }
        Log.d(TAG, "Proximity beeping stopped")
    }

    fun isBeeping(): Boolean = beepJob?.isActive == true

    /**
     * Tears down the beep generator. Only call once (e.g. ViewModel
     * [androidx.lifecycle.ViewModel.onCleared]); the object is then unusable.
     */
    fun release() {
        stop()
        scope.cancel()
        toneGenerator?.release()
        toneGenerator = null
    }

    private fun createToneGenerator(
        context: Context,
        volume: Int
    ): ToneGenerator? {
        try {
            val dial = min(100, max(1, volume.coerceIn(0, 100)))
            return ToneGenerator(AudioManager.STREAM_ALARM, dial)
        } catch (e: Exception) {
            Log.w(TAG, "ToneGenerator (STREAM_ALARM) failed: ${e.message}")
        }
        try {
            return ToneGenerator(AudioManager.STREAM_MUSIC, 60)
        } catch (e: Exception) {
            Log.e(TAG, "ToneGenerator fallback failed", e)
            return null
        }
    }
}