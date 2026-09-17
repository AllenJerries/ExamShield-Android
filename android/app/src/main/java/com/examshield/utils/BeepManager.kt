package com.examshield.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.*
import kotlin.math.max
import kotlin.math.min

/**
 * Dynamic proximity audio engine.
 *
 * Drives an [AudioTrack] on the ALARM usage stream (audible even with the
 * media volume down) and scales volume + cadence STRICTLY with the hunted
 * target's live RSSI:
 *
 *  - RSSI >= -50 dBm  (VERY_CLOSE) -> Volume 100%,  beep every  45 ms
 *  - RSSI -51..-65    (CLOSE)      -> Volume  75%,  beep every 180 ms
 *  - RSSI -66..-78    (MEDIUM)     -> Volume  45%,  beep every 350 ms
 *  - RSSI <  -78      (FAR)        -> Volume  15%,  beep every 600 ms
 *
 * The tier is re-evaluated on every tick (~30 ms) from the freshest smoothed
 * RSSI. Pitch climbs 500 -> 1600 Hz in step with proximity so even with the
 * cadence locked to a tier the tone still "rises" as the target approaches.
 *
 * If the [AudioTrack] cannot be created, the engine falls back to a
 * [ToneGenerator] whose stream volume is scaled tier-by-tier (100/75/45/15%)
 * by REBUILDING the generator every time the RSSI tier changes — ToneGenerator
 * exposes no runtime volume setter, so a fresh instance is the only way to
 * seek its output volume.
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
    private const val RSSI_CLOSE = -65
    private const val RSSI_MEDIUM = -78

    // Tier-locked stream gains: very close 100% / close 75% / medium 45% / far 15%.
    private const val GAIN_VERY_CLOSE = 1.0f
    private const val GAIN_CLOSE = 0.75f
    private const val GAIN_MEDIUM = 0.45f
    private const val GAIN_FAR = 0.15f

    // Tier-locked beep cadences (ms).
    private const val INTERVAL_VERY_CLOSE = 45L
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
     * (45/180/350/600 ms) are locked to the four RSSI tiers above; pitch rises
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

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var beepJob: Job? = null
    private val pcmCache = mutableMapOf<Int, ByteArray>()

    /**
     * Starts a continuous, dynamically-scaling beep loop. Call [stop] to end it.
     *
     * @param context used to route audio onto the alarm stream
     * @param getRssi invoked every tick for the freshest smoothed RSSI
     * @param volume  invigilator master volume 0..100 (scales the tier gain)
     */
    fun startProximityBeeping(
        context: Context,
        getRssi: () -> Int,
        volume: Int = DEFAULT_VOLUME
    ) {
        stop()

        val masterVolume = volume.coerceIn(0, 100) / 100f

        beepJob = scope.launch {
            val profileFor: suspend () -> BeepProfile = {
                getBeepProfile(safeRssi(getRssi))
            }

            val track = createAudioTrack()
            if (track == null) {
                runToneGeneratorLoop(context, getRssi, volume)
                return@launch
            }

            try {
                track.play()
                var lastProfile: BeepProfile? = null
                var nextBeepAt = SystemClock.elapsedRealtime()

                while (isActive) {
                    val rssi = safeRssi(getRssi)
                    val profile = profileFor()

                    // Volume is re-applied every tick (~30ms) from the freshest
                    // RSSI tier, so gain rises/drops in real time with the target.
                    track.setVolume(
                        (profile.volume * masterVolume).coerceIn(0f, 1f)
                    )

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
                                (liveProfile.volume * masterVolume).coerceIn(0f, 1f)
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
            }
        }

        Log.d(TAG, "Proximity beeping started")
    }

    fun stop() {
        beepJob?.cancel()
        beepJob = null
        Log.d(TAG, "Proximity beeping stopped")
    }

    fun isBeeping(): Boolean = beepJob?.isActive == true

    /**
     * Tears down the beep engine. Only call once (e.g. ViewModel
     * [androidx.lifecycle.ViewModel.onCleared]); the object is then unusable.
     */
    fun release() {
        stop()
        scope.cancel()
        pcmCache.clear()
    }

    private suspend fun runToneGeneratorLoop(
        context: Context,
        getRssi: () -> Int,
        volume: Int
    ) {
        val masterVolume = volume.coerceIn(0, 100)
        var generator = createToneGenerator(context, masterVolume)
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
                val profile = getBeepProfile(rssi)

                // ToneGenerator exposes no runtime volume setter -> rebuild the
                // generator synchronously whenever the tier-scaled stream gain
                // (100/75/45/15%) moves, so the ALARM stream volume scales
                // dynamically with the incoming RSSI tier change.
                val scaledGain =
                    (masterVolume * profile.volume).toInt().coerceIn(1, 100)
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