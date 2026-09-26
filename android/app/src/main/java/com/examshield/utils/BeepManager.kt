package com.examshield.utils

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Native proximity audio engine — re-armed to open reliably on EVERY hunt
 * (1st, 2nd, or target switch).
 *
 * Lifecycle contract:
 *
 *  - [startBeeping] force-resets state every call: cancels any previous job,
 *    sets [isPlaying] = true, creates a FRESH [ToneGenerator] on the ALARM
 *    stream at 100% and launches a fresh loop on the CALLER-provided scope
 *    (see [ProximityViewModel]). Every hunt opens with a clean native slate.
 *  - [updateProximity] feeds [currentRssi] synchronously (no native churn).
 *    The loop continuously derives cadence AND the volume tier from it:
 *
 *      RSSI >= -50 dBm   -> 60 ms cadence, 100% stream volume
 *      RSSI -51..-65     -> 180 ms cadence, 75% stream volume
 *      RSSI -66..-78     -> 350 ms cadence, 45% stream volume
 *      RSSI <  -78       -> 600 ms cadence, 15% stream volume
 *
 *    ToneGenerator exposes no runtime volume setter, so the single instance is
 *    replaced ONLY on a discrete tier TRANSITION (very rare during a hunt) —
 *    never on per-update frames, which is what exhausted native audio memory.
 *  - [stopBeeping] sets [isPlaying] = false, cancels the job, stops the tone,
 *    releases the ToneGenerator and nulls it so the next hunt starts clean.
 */
object BeepManager {

    private const val TAG = "BeepManager"

    private const val BEEP_DURATION_MS = 120

    // RSSI tier boundaries (dBm).
    private const val RSSI_VERY_CLOSE = -50
    private const val RSSI_CLOSE = -65
    private const val RSSI_MEDIUM = -78

    // Tier-locked stream volumes (ToneGenerator constructor dial 1..100).
    private const val GAIN_VERY_CLOSE = 1.0f
    private const val GAIN_CLOSE = 0.75f
    private const val GAIN_MEDIUM = 0.45f
    private const val GAIN_FAR = 0.15f

    // Tier-locked beep cadences (ms) — re-tuned continuously from the RSSI.
    private const val INTERVAL_VERY_CLOSE = 60L
    private const val INTERVAL_CLOSE = 180L
    private const val INTERVAL_MEDIUM = 350L
    private const val INTERVAL_FAR = 600L

    private val PITCH_LEVELS = intArrayOf(500, 800, 1200, 1600)

    enum class ProximityTier { VERY_CLOSE, CLOSE, MEDIUM, FAR }

    data class BeepProfile(
        val tier: ProximityTier,
        val volume: Float,
        val pitchHz: Int,
        val intervalMs: Long
    )

    /**
     * RSSI-driven proximity profile. Volume (100/75/45/15%) and beep cadence
     * (60/180/350/600 ms) are locked to the four RSSI tiers above; pitch rises
     * with closeness so the tone climbs in step with the signal.
     */
    fun getBeepProfileForRssi(rssi: Int): BeepProfile {
        val (tier, volume, intervalMs) = when {
            rssi >= RSSI_VERY_CLOSE ->
                Triple(ProximityTier.VERY_CLOSE, GAIN_VERY_CLOSE, INTERVAL_VERY_CLOSE)
            rssi >= RSSI_CLOSE ->
                Triple(ProximityTier.CLOSE, GAIN_CLOSE, INTERVAL_CLOSE)
            rssi >= RSSI_MEDIUM ->
                Triple(ProximityTier.MEDIUM, GAIN_MEDIUM, INTERVAL_MEDIUM)
            else ->
                Triple(ProximityTier.FAR, GAIN_FAR, INTERVAL_FAR)
        }
        val pitchHz = when (tier) {
            ProximityTier.VERY_CLOSE -> PITCH_LEVELS[3]
            ProximityTier.CLOSE -> PITCH_LEVELS[2]
            ProximityTier.MEDIUM -> PITCH_LEVELS[1]
            ProximityTier.FAR -> PITCH_LEVELS[0]
        }
        return BeepProfile(tier, volume, pitchHz, intervalMs)
    }

    /** Compatibility wrapper: routes an RSSI straight to the tier engine. */
    fun getBeepProfile(rssi: Int): BeepProfile {
        return getBeepProfileForRssi(rssi)
    }

    fun getBeepIntervalMs(rssi: Int): Long = getBeepProfile(rssi).intervalMs

    /** True while a beep loop is armed. */
    @Volatile
    var isPlaying: Boolean = false
        private set

    /** Freshest hunted RSSI, written by [updateProximity] on the caller's
     * dispatcher and read by the beep loop. */
    @Volatile
    var currentRssi: Int = -100
        private set

    private var beepJob: Job? = null
    private var toneGenerator: ToneGenerator? = null
    private var currentVolumeTier = -1

    /**
     * Pure-RSSI ingress. Updates [currentRssi] instantly and does NOT touch the
     * ToneGenerator — native audio is left alone during an active hunt so sound
     * never dies on the 2nd or 3rd hunt.
     */
    fun updateProximity(rssi: Int) {
        currentRssi = rssi
    }

    /**
     * Arms the proximity beep engine on the provided scope. Force-resets
     * state (cancels any previous job, [isPlaying] = true), creates a fresh
     * [ToneGenerator] at 100% and launches a loop that continuously re-tunes
     * cadence + volume from [currentRssi].
     *
     * @param scope lifecycle-bound scope (e.g. viewModelScope); the loop dies
     *              with it and is fully torn down again by [stopBeeping].
     */
    fun startBeeping(scope: CoroutineScope) {
        // Force-reset: cancel any previous hunt's job + native generator before
        // arming a fresh session so open/close cycles never accumulate state.
        stopBeeping()

        isPlaying = true
        currentVolumeTier = -1
        toneGenerator = createToneGenerator(100)
        if (toneGenerator == null) {
            Log.e(TAG, "No ToneGenerator available — proximity beeps disabled")
            isPlaying = false
            return
        }

        beepJob = scope.launch {
            while (isActive && isPlaying) {
                val rssi = currentRssi
                val profile = getBeepProfileForRssi(rssi)
                val volumePct = (profile.volume * 100).toInt().coerceIn(1, 100)

                // Volume modulation: ToneGenerator cannot change volume live,
                // so replace the instance ONLY when the volume tier moves (a
                // discrete transition mid-hunt, never per-update drift). The
                // old generator is stopped + released first, so native memory
                // use stays flat and sound never crashes on repeat hunts.
                if (volumePct != currentVolumeTier) {
                    currentVolumeTier = volumePct
                    rebuildToneGenerator(volumePct)
                }

                try {
                    toneGenerator?.startTone(
                        ToneGenerator.TONE_PROP_BEEP2,
                        BEEP_DURATION_MS
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "startTone error: ${e.message}")
                }

                delay(profile.intervalMs)
            }
        }

        Log.d(TAG, "Proximity beeping armed (isPlaying=$isPlaying)")
    }

    /**
     * Tears the engine down completely: [isPlaying] = false, job cancelled,
     * tone stopped, ToneGenerator released and nulled so the next hunt opens
     * on a clean slate.
     */
    fun stopBeeping() {
        isPlaying = false
        beepJob?.cancel()
        beepJob = null

        toneGenerator?.let { generator ->
            try {
                generator.stopTone()
                generator.release()
            } catch (e: Exception) {
                Log.w(TAG, "ToneGenerator release error: ${e.message}")
            }
        }
        toneGenerator = null
        currentVolumeTier = -1
        currentRssi = -100

        Log.d(TAG, "Beeping stopped, ToneGenerator released")
    }

    /** Alias kept for callers that still reference [stop]. */
    fun stop() {
        stopBeeping()
    }

    fun isBeeping(): Boolean = isPlaying

    /**
     * Tears down the engine. Safe to call from ViewModel.onCleared; the scope
     * passed to [startBeeping] is owned by the caller and is NOT cancelled here.
     */
    fun release() {
        stopBeeping()
    }

    private fun rebuildToneGenerator(volumePct: Int) {
        toneGenerator?.let { generator ->
            try {
                generator.stopTone()
                generator.release()
            } catch (e: Exception) {
                Log.w(TAG, "ToneGenerator rebuild release error: ${e.message}")
            }
        }
        toneGenerator = createToneGenerator(volumePct)
    }

    /** ONE native ToneGenerator on the ALARM stream at the given volume (1..100). */
    private fun createToneGenerator(volumePct: Int): ToneGenerator? {
        val dial = volumePct.coerceIn(1, 100)
        return try {
            ToneGenerator(AudioManager.STREAM_ALARM, dial)
        } catch (e: Exception) {
            Log.w(TAG, "ToneGenerator (STREAM_ALARM) failed: ${e.message}")
            try {
                ToneGenerator(AudioManager.STREAM_MUSIC, dial)
            } catch (e2: Exception) {
                Log.e(TAG, "ToneGenerator fallback failed", e2)
                null
            }
        }
    }
}