package com.examshield.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.*
import kotlin.math.max
import kotlin.math.min

/**
 * Dynamic proximity engine: AUDIO + HAPTICS, both driven strictly by the hunted
 * target's live RSSI:
 *
 *  - RSSI >= -50 dBm  (VERY_CLOSE, <20cm) -> heavy repeating haptic pulses
 *    (`VibrationEffect.createOneShot(50, DEFAULT_AMPLITUDE)`), max 100% beep
 *    volume, beep every 45 ms.
 *  - RSSI -51..-68    (MEDIUM)             -> light subtle haptic pulses every
 *    250 ms, 65% beep volume, beep every 250 ms.
 *  - RSSI <  -68      (FAR)                -> sound only, ZERO vibration.
 *
 * Audio plays through an [AudioTrack] on the ALARM usage stream (audible even
 * with the media volume down) and volume + cadence scale strictly with the
 * RSSI tier. When the [AudioTrack] cannot be created the engine falls back to
 * a [ToneGenerator] whose stream gain is re-scaled tier-by-tier by rebuilding
 * the generator — ToneGenerator exposes no runtime volume setter.
 *
 * [updateProximity] is the single synchronous entry point that applies the
 * tier gain to the live track AND (re)arms the haptic schedule on every RSSI
 * frame, so callers on Dispatchers.Main.immediate get zero-lag audio & haptic
 * feedback. Vibration is cancelled automatically by [stopBeeping].
 */
object BeepManager {

    private const val TAG = "BeepManager"

    private const val BEEP_DURATION_MS = 120
    private const val SAMPLE_RATE = 44100
    private const val DEFAULT_VOLUME = 100

    // Control-loop granularity: the tier is re-evaluated and volume re-applied
    // every tick so the audio morphs with the live RSSI in real time.
    private const val AUDIO_TICK_MS = 30L
    private const val BEEP_FRAMES = (BEEP_DURATION_MS / AUDIO_TICK_MS).toInt()

    // RSSI tier boundaries (dBm).
    private const val RSSI_VERY_CLOSE = -50
    private const val RSSI_MEDIUM = -68

    // Tier-locked stream gains: very close 100% / medium 65% / far 15%.
    private const val GAIN_VERY_CLOSE = 1.0f
    private const val GAIN_MEDIUM = 0.65f
    private const val GAIN_FAR = 0.15f

    // Tier-locked beep cadences (ms).
    private const val INTERVAL_VERY_CLOSE = 45L
    private const val INTERVAL_MEDIUM = 250L
    private const val INTERVAL_FAR = 600L

    // Haptic schedules: heavy repeating pulses when very close, light subtle
    // pulses every 250 ms at medium, silence (sound only) when far.
    private const val HAPTIC_VERY_CLOSE_MS = 50L
    private const val HAPTIC_VERY_CLOSE_AMPLITUDE = VibrationEffect.DEFAULT_AMPLITUDE
    private const val HAPTIC_VERY_CLOSE_INTERVAL_MS = 100L
    private const val HAPTIC_MEDIUM_MS = 30L
    private const val HAPTIC_MEDIUM_AMPLITUDE = 60
    private const val HAPTIC_MEDIUM_INTERVAL_MS = 250L

    private val PITCH_LEVELS = intArrayOf(500, 800, 1200, 1600)

    enum class ProximityTier { VERY_CLOSE, CLOSE, MEDIUM, FAR }

    data class BeepProfile(
        val tier: ProximityTier,
        val volume: Float,
        val pitchHz: Int,
        val intervalMs: Long
    )

    /**
     * RSSI-driven proximity profile. Volume (100/65/15%) and beep cadence
     * (45/250/600 ms) are locked to the three RSSI tiers above; pitch rises
     * with closeness so the tone climbs in step with the signal.
     */
    fun getBeepProfileForRssi(rssi: Int): BeepProfile {
        val (tier, volume, intervalMs) = when {
            rssi >= RSSI_VERY_CLOSE ->
                Triple(ProximityTier.VERY_CLOSE, GAIN_VERY_CLOSE, INTERVAL_VERY_CLOSE)
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

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Audio state — read/written from the beep coroutine (Default) and
    // synchronously from updateProximity on the caller's dispatcher, hence
    // @Volatile.
    @Volatile private var beepJob: Job? = null
    @Volatile private var activeTrack: AudioTrack? = null
    @Volatile private var currentProfile: BeepProfile? = null
    @Volatile private var masterVolumeFloat = DEFAULT_VOLUME / 100f

    // Haptic state.
    @Volatile private var vibrator: Vibrator? = null
    @Volatile private var hapticJob: Job? = null
    @Volatile private var currentHapticTier: ProximityTier? = null

    private val pcmCache = mutableMapOf<Int, ByteArray>()

    /**
     * Synchronous per-frame proximity driver. Applies the tier gain to the
     * live [AudioTrack] (or the ToneGenerator rebuild reads [currentProfile]),
     * and (re)arms the haptic schedule for the current tier. Safe to call on
     * any dispatcher; runs entirely in the caller's thread so Main.immediate
     * callers get zero-lag feedback.
     */
    fun updateProximity(rssi: Int) {
        val profile = getBeepProfileForRssi(rssi)
        currentProfile = profile
        activeTrack?.setVolume(
            (profile.volume * masterVolumeFloat).coerceIn(0f, 1f)
        )
        updateHaptics(profile.tier)
    }

    /**
     * Starts a continuous, dynamically-scaling beep + haptic loop. Call
     * [stopBeeping] to end it.
     *
     * @param context used to route audio onto the alarm stream and to obtain
     *                the system [Vibrator] service
     * @param getRssi invoked every tick for the freshest smoothed RSSI
     * @param volume  invigilator master volume 0..100 (scales the tier gain)
     */
    fun startProximityBeeping(
        context: Context,
        getRssi: () -> Int,
        volume: Int = DEFAULT_VOLUME
    ) {
        stopBeeping()

        masterVolumeFloat = volume.coerceIn(0, 100) / 100f
        vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

        beepJob = scope.launch {
            val profileFor: suspend () -> BeepProfile = {
                val rssi = safeRssi(getRssi)
                updateProximity(rssi)
                currentProfile ?: getBeepProfile(rssi)
            }

            val track = createAudioTrack()
            if (track == null) {
                runToneGeneratorLoop(context, getRssi, volume)
                return@launch
            }
            activeTrack = track

            try {
                track.play()
                var lastProfile: BeepProfile? = null
                var nextBeepAt = SystemClock.elapsedRealtime()

                while (isActive) {
                    val rssi = safeRssi(getRssi)
                    val profile = profileFor()

                    // Volume + haptics are re-applied by updateProximity every
                    // tick (~30ms) so gain and pulse rate move with the live
                    // RSSI in real time, on the caller's dispatcher.
                    if (profile != lastProfile) {
                        Log.d(
                            TAG,
                            "Audio profile -> ${profile.tier} | ${profile.pitchHz}Hz | " +
                                "${String.format("%.0f", profile.volume * 100f)}% vol | " +
                                "every ${profile.intervalMs}ms (RSSI $rssi)"
                        )
                        lastProfile = profile
                    }

                    val now = SystemClock.elapsedRealtime()
                    if (now >= nextBeepAt) {
                        val beepStartedAt = SystemClock.elapsedRealtime()
                        var framesLeft = BEEP_FRAMES

                        // Write the beep as 30ms frames, re-evaluating the
                        // profile between frames so pitch + volume morph with
                        // the live RSSI even mid-beep.
                        while (framesLeft > 0 && currentCoroutineContext().isActive) {
                            val liveProfile = profileFor()
                            track.setVolume(
                                (liveProfile.volume * masterVolumeFloat).coerceIn(0f, 1f)
                            )
                            val chunk = pcmFor(liveProfile.pitchHz)
                            val offset =
                                (BEEP_FRAMES - framesLeft) * (chunk.size / BEEP_FRAMES)
                            track.write(
                                chunk, offset,
                                chunk.size / BEEP_FRAMES,
                                AudioTrack.WRITE_BLOCKING
                            )
                            framesLeft--
                        }

                        // Cadence measured from beep start, so very-close bursts
                        // (45ms) overlap the still-playing beep into a loud
                        // continuous pulse without adding dead air.
                        nextBeepAt = beepStartedAt + profile.intervalMs
                    } else {
                        delay(AUDIO_TICK_MS)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Proximity beep loop error", e)
            } finally {
                safeReleaseTrack(track)
                activeTrack = null
            }
        }

        Log.d(TAG, "Proximity beeping started")
    }

    /** Stops audio AND cancels any active vibration. */
    fun stopBeeping() {
        beepJob?.cancel()
        beepJob = null
        activeTrack = null
        currentProfile = null
        cancelHaptics()
        Log.d(TAG, "Proximity beeping + haptics stopped")
    }

    /** Alias kept for callers that still reference [stop]. */
    fun stop() {
        stopBeeping()
    }

    fun isBeeping(): Boolean = beepJob?.isActive == true

    /**
     * Tears down the beep/haptic engine. Only call once (e.g. ViewModel
     * [androidx.lifecycle.ViewModel.onCleared]); the object is then unusable.
     */
    fun release() {
        stopBeeping()
        scope.cancel()
        pcmCache.clear()
        vibrator = null
    }

    private fun updateHaptics(tier: ProximityTier) {
        val vib = vibrator
        if (vib == null || !vib.hasVibrator()) {
            cancelHaptics()
            return
        }
        when (tier) {
            ProximityTier.VERY_CLOSE -> {
                if (currentHapticTier != ProximityTier.VERY_CLOSE) {
                    currentHapticTier = ProximityTier.VERY_CLOSE
                    startHapticPulse(
                        vibrator = vib,
                        durationMs = HAPTIC_VERY_CLOSE_MS,
                        amplitude = HAPTIC_VERY_CLOSE_AMPLITUDE,
                        intervalMs = HAPTIC_VERY_CLOSE_INTERVAL_MS
                    )
                }
            }
            ProximityTier.MEDIUM -> {
                if (currentHapticTier != ProximityTier.MEDIUM) {
                    currentHapticTier = ProximityTier.MEDIUM
                    startHapticPulse(
                        vibrator = vib,
                        durationMs = HAPTIC_MEDIUM_MS,
                        amplitude = HAPTIC_MEDIUM_AMPLITUDE,
                        intervalMs = HAPTIC_MEDIUM_INTERVAL_MS
                    )
                }
            }
            ProximityTier.CLOSE, ProximityTier.FAR -> cancelHaptics()
        }
    }

    /** Repeating haptic pulse schedule for the active tier. Re-armed on tier change only. */
    private fun startHapticPulse(
        vibrator: Vibrator,
        durationMs: Long,
        amplitude: Int,
        intervalMs: Long
    ) {
        hapticJob?.cancel()
        hapticJob = scope.launch {
            while (isActive) {
                try {
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(durationMs, amplitude)
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Haptic pulse error: ${e.message}")
                }
                delay(intervalMs)
            }
        }
    }

    private fun cancelHaptics() {
        currentHapticTier = null
        hapticJob?.cancel()
        hapticJob = null
        vibrator?.cancel()
    }

    private suspend fun runToneGeneratorLoop(
        context: Context,
        getRssi: () -> Int,
        volume: Int
    ) {
        val master = volume.coerceIn(0, 100)
        var generator = createToneGenerator(context, master)
        if (generator == null) {
            Log.e(TAG, "No audio engine available — proximity beeps disabled")
            return
        }

        try {
            var lastProfile: BeepProfile? = null
            var lastScaledGain = -1

            fun releaseAndRebuild(index: Int) {
                releaseToneGenerator(generator)
                generator = createToneGenerator(context, index)
            }

            while (currentCoroutineContext().isActive) {
                val rssi = safeRssi(getRssi)
                updateProximity(rssi)
                val profile = currentProfile ?: getBeepProfile(rssi)

                // ToneGenerator exposes no runtime volume setter -> rebuild the
                // generator synchronously whenever the tier-scaled stream gain
                // (100/65/15%) moves, so the ALARM stream volume scales
                // dynamically with the incoming RSSI tier change.
                val scaledGain =
                    (master * profile.volume).toInt().coerceIn(1, 100)
                if (scaledGain != lastScaledGain) {
                    lastScaledGain = scaledGain
                    releaseAndRebuild(scaledGain)
                }
                // Capture into a stable non-null local so the null-check survives the
                // closure-rebuilt generator reference.
                val liveGenerator = generator ?: run {
                    Log.e(TAG, "ToneGenerator unavailable at gain $scaledGain")
                    return
                }

                if (profile != lastProfile) {
                    Log.d(
                        TAG,
                        "Tone cadence -> ${profile.intervalMs}ms " +
                            "${scaledGain}% stream gain (RSSI $rssi)"
                    )
                    lastProfile = profile
                }

                try {
                    liveGenerator.startTone(
                        ToneGenerator.TONE_PROP_BEEP2,
                        BEEP_DURATION_MS
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "startTone error: ${e.message}")
                }

                delay(profile.intervalMs)
            }
        } catch (e: CancellationException) {
            throw e
        } finally {
            releaseToneGenerator(generator)
        }
    }

    private suspend fun safeRssi(getRssi: () -> Int): Int {
        return try {
            getRssi()
        } catch (e: Exception) {
            Log.w(TAG, "getRssi error: ${e.message}")
            -100
        }
    }

    private fun createAudioTrack(): AudioTrack? {
        return try {
            val chunkBytes = (SAMPLE_RATE / 1000.0 * BEEP_DURATION_MS).toInt() * 2
            val minBuffer = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(max(minBuffer, chunkBytes * 2))
                .build()
        } catch (e: Exception) {
            Log.w(TAG, "AudioTrack init failed: ${e.message}")
            null
        }
    }

    /** Generates and caches one 16-bit PCM beep chunk per pitch. */
    private fun pcmFor(pitchHz: Int): ByteArray {
        return pcmCache.getOrPut(pitchHz) { generateTonePcm(pitchHz) }
    }

    private fun generateTonePcm(pitchHz: Int): ByteArray {
        val sampleCount = (SAMPLE_RATE / 1000.0 * BEEP_DURATION_MS).toInt()
        val fadeSamples = (SAMPLE_RATE / 1000.0 * 5.0).toInt()
        val amplitude = 32000.0

        val shorts = ShortArray(sampleCount)
        for (i in 0 until sampleCount) {
            val t = i.toDouble() / SAMPLE_RATE
            val envelope = when {
                i < fadeSamples -> i.toDouble() / fadeSamples
                i >= sampleCount - fadeSamples ->
                    (sampleCount - i).toDouble() / fadeSamples
                else -> 1.0
            }
            val sample = (amplitude * envelope *
                Math.sin(2.0 * Math.PI * pitchHz * t)).toInt()
            shorts[i] = sample.toShort()
        }

        val bytes = ByteArray(shorts.size * 2)
        for (i in shorts.indices) {
            val value = shorts[i].toInt()
            bytes[i * 2] = (value and 0xFF).toByte()
            bytes[i * 2 + 1] = ((value shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    private fun createToneGenerator(context: Context, volume: Int): ToneGenerator? {
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

    private fun releaseToneGenerator(generator: ToneGenerator?) {
        if (generator == null) return
        try {
            generator.stopTone()
            generator.release()
        } catch (e: Exception) {
            Log.w(TAG, "ToneGenerator release error: ${e.message}")
        }
    }

    private fun safeReleaseTrack(track: AudioTrack) {
        try {
            track.pause()
            track.flush()
            track.release()
        } catch (e: Exception) {
            Log.w(TAG, "AudioTrack release error: ${e.message}")
        }
    }
}