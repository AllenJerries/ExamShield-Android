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
 * media volume down) and continuously rescales volume, tone pitch and pulse
 * interval from the target's live RSSI / physical distance:
 *
 *  - RSSI >= -50 dBm (Very Close, < 0.3 m): Volume = 1.0f,   Pitch = 1600 Hz, every 45 ms
 *  - RSSI -51..-70 dBm (Medium, 0.3-1.5 m): Volume = 0.6f,   Pitch = 1000 Hz, every 180 ms
 *  - RSSI <  -70 dBm (Far, > 1.5 m)        : Volume = 0.2f,   Pitch = 500 Hz,  every 600 ms
 *
 * The profile is re-selected every ~30 ms tick, so volume continuously turns
 * UP as the user approaches the device and turns DOWN as distance increases,
 * and the pitch/cadence of the next beep reflects the freshest reading. If
 * the [AudioTrack] cannot be created, the engine falls back to a
 * [ToneGenerator] that still scales the beep cadence.
 */
object BeepManager {

    private const val TAG = "BeepManager"

    private const val BEEP_DURATION_MS = 120
    private const val SAMPLE_RATE = 44100
    private const val DEFAULT_VOLUME = 100

    // Control-loop granularity: volume + profile are re-evaluated every tick,
    // and beeps are written as 30ms frames so audio can morph mid-beep.
    private const val AUDIO_TICK_MS = 30L
    private const val BEEP_FRAMES = (BEEP_DURATION_MS / AUDIO_TICK_MS).toInt()

    enum class ProximityTier { VERY_CLOSE, CLOSE, FAR }

    data class BeepProfile(
        val tier: ProximityTier,
        val volume: Float,
        val pitchHz: Int,
        val intervalMs: Long
    )

    private val VERY_CLOSE_PROFILE = BeepProfile(
        tier = ProximityTier.VERY_CLOSE,
        volume = 1.0f,
        pitchHz = 1600,
        intervalMs = 45L
    )

    private val CLOSE_PROFILE = BeepProfile(
        tier = ProximityTier.CLOSE,
        volume = 0.6f,
        pitchHz = 1000,
        intervalMs = 180L
    )

    private val FAR_PROFILE = BeepProfile(
        tier = ProximityTier.FAR,
        volume = 0.2f,
        pitchHz = 500,
        intervalMs = 600L
    )

    /** Resolves the audio profile for the given (live raw) RSSI. */
    fun getBeepProfile(rssi: Int): BeepProfile {
        return when {
            rssi >= -50 -> VERY_CLOSE_PROFILE
            rssi >= -70 -> CLOSE_PROFILE
            else -> FAR_PROFILE
        }
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
                    val profile = getBeepProfile(rssi)

                    // Volume is re-applied every tick (~30ms) from the freshest
                    // RSSI, so gain rises/drops immediately with distance.
                    track.setVolume(
                        (profile.volume * masterVolume).coerceIn(0f, 1f)
                    )

                    if (profile != lastProfile) {
                        Log.d(
                            TAG,
                            "Audio profile -> ${profile.tier} | ${profile.pitchHz}Hz | " +
                                "${profile.volume}vol | every ${profile.intervalMs}ms (RSSI $rssi)"
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
                            val liveProfile = getBeepProfile(safeRssi(getRssi))
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
                        // (60ms) can overlap the still-playing beep into a loud
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
        val generator = createToneGenerator(context, volume)
        if (generator == null) {
            Log.e(TAG, "No audio engine available — proximity beeps disabled")
            return
        }

        try {
            var lastProfile: BeepProfile? = null
            while (currentCoroutineContext().isActive) {
                val rssi = safeRssi(getRssi)
                val profile = getBeepProfile(rssi)

                if (profile != lastProfile) {
                    Log.d(TAG, "Tone cadence -> ${profile.intervalMs}ms (RSSI $rssi)")
                    lastProfile = profile
                }

                try {
                    generator.startTone(
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
            try {
                generator.stopTone()
                generator.release()
            } catch (e: Exception) {
                Log.w(TAG, "ToneGenerator release error: ${e.message}")
            }
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