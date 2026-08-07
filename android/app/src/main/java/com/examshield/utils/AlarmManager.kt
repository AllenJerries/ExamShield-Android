package com.examshield.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.*
import kotlin.math.PI
import kotlin.math.sin

object AlarmManager {

    private const val TAG = "AlarmManager"
    private const val SAMPLE_RATE = 44100

    private var audioManager: AudioManager? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var alertJob: Job? = null
    private var beepJob: Job? = null

    fun playAlert(
        context: Context,
        urgency: AlertUrgency = AlertUrgency.HIGH,
        continuous: Boolean = true
    ) {
        alertJob?.cancel()
        alertJob = scope.launch {
            try {
                stopAlert()
                setupMaxVolume(context)

                val (startFreq, endFreq, duration) = when (urgency) {
                    AlertUrgency.CRITICAL -> Triple(400.0, 1200.0, 600)
                    AlertUrgency.HIGH -> Triple(500.0, 1000.0, 500)
                    AlertUrgency.MEDIUM -> Triple(600.0, 900.0, 400)
                    AlertUrgency.LOW -> Triple(700.0, 800.0, 300)
                }

                Log.d(TAG, "Playing ${urgency.name} alert")

                if (continuous) {
                    while (isActive) {
                        playSiren(startFreq, endFreq, duration, 1.0f)
                        delay(300)
                    }
                } else {
                    playSiren(startFreq, endFreq, duration, 1.0f)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Alert error", e)
            }
        }
    }

    fun playProximityBeep(context: Context, distance: Double) {
        beepJob?.cancel()
        beepJob = scope.launch {
            try {
                setupMaxVolume(context)

                val (frequency, duration, volume) = getBeepParameters(distance)

                Log.d(TAG, "Beep: ${frequency}Hz ${duration}ms vol=$volume dist=${distance}m")

                playTone(frequency, duration, volume)

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Beep error", e)
            }
        }
    }

    private suspend fun playSiren(
        startFreq: Double,
        endFreq: Double,
        durationMs: Int,
        volume: Float
    ) {
        try {
            val numSamples = SAMPLE_RATE * durationMs / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val progress = i.toDouble() / numSamples
                val frequency = startFreq + (endFreq - startFreq) * progress
                val angle = 2.0 * PI * i * frequency / SAMPLE_RATE
                val sample = (sin(angle) * Short.MAX_VALUE * volume * 0.8).toInt()
                buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }

            applyEnvelope(buffer)
            playBuffer(buffer)
        } catch (e: Exception) {
            Log.e(TAG, "Siren error", e)
        }
    }

    private suspend fun playTone(
        frequency: Double,
        durationMs: Int,
        volume: Float
    ) {
        try {
            val numSamples = SAMPLE_RATE * durationMs / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val angle = 2.0 * PI * i * frequency / SAMPLE_RATE
                val sample = (sin(angle) * Short.MAX_VALUE * volume * 0.9).toInt()
                buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }

            applyEnvelope(buffer)
            playBuffer(buffer)
        } catch (e: Exception) {
            Log.e(TAG, "Tone error", e)
        }
    }

    private fun applyEnvelope(buffer: ShortArray) {
        val fadeLength = (buffer.size * 0.1).toInt()

        for (i in 0 until fadeLength) {
            val factor = i.toFloat() / fadeLength
            buffer[i] = (buffer[i] * factor).toInt().toShort()
            val idx = buffer.size - 1 - i
            buffer[idx] = (buffer[idx] * factor).toInt().toShort()
        }
    }

    private fun playBuffer(buffer: ShortArray) {
        var track: AudioTrack? = null
        try {
            val bufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val actualBufferSize = maxOf(bufferSize, buffer.size * 2)

            track = AudioTrack.Builder()
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
                .setBufferSizeInBytes(actualBufferSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.setVolume(1.0f)
            track.write(buffer, 0, buffer.size)
            track.play()

            val playbackTime = (buffer.size * 1000L / SAMPLE_RATE) + 50
            Thread.sleep(playbackTime)

        } catch (e: Exception) {
            Log.e(TAG, "PlayBuffer error", e)
        } finally {
            try {
                track?.stop()
                track?.release()
            } catch (e: Exception) { }
        }
    }

    private fun getBeepParameters(distance: Double): Triple<Double, Int, Float> {
        val frequency = when {
            distance < 0.3 -> 1500.0
            distance < 0.5 -> 1200.0
            distance < 1.0 -> 1000.0
            distance < 2.0 -> 900.0
            distance < 3.0 -> 800.0
            distance < 5.0 -> 700.0
            distance < 10.0 -> 600.0
            else -> 500.0
        }

        val duration = when {
            distance < 0.3 -> 250
            distance < 1.0 -> 200
            distance < 3.0 -> 180
            else -> 150
        }

        val volume = when {
            distance < 0.3 -> 1.0f
            distance < 0.5 -> 1.0f
            distance < 1.0 -> 0.95f
            distance < 2.0 -> 0.9f
            distance < 3.0 -> 0.85f
            distance < 5.0 -> 0.8f
            distance < 10.0 -> 0.7f
            else -> 0.6f
        }

        return Triple(frequency, duration, volume)
    }

    private fun setupMaxVolume(context: Context) {
        try {
            audioManager = context.getSystemService(
                Context.AUDIO_SERVICE
            ) as? AudioManager

            audioManager?.let { am ->
                val maxAlarm = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                am.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarm, 0)

                val maxMusic = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusic, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Volume error", e)
        }
    }

    fun stopAlert() {
        alertJob?.cancel()
        alertJob = null
    }

    fun stopBeep() {
        beepJob?.cancel()
        beepJob = null
    }

    fun stopAll() {
        stopAlert()
        stopBeep()
    }

    fun testSound(context: Context) {
        scope.launch {
            setupMaxVolume(context)
            playSiren(500.0, 1200.0, 600, 1.0f)
            delay(200)
            playSiren(1200.0, 500.0, 600, 1.0f)
        }
    }

    fun testDifferentTones(context: Context) {
        scope.launch {
            setupMaxVolume(context)
            playSiren(500.0, 800.0, 500, 0.8f)
            delay(300)
            playSiren(600.0, 900.0, 500, 0.9f)
            delay(300)
            playSiren(700.0, 1000.0, 500, 1.0f)
        }
    }
}

enum class AlertUrgency {
    LOW, MEDIUM, HIGH, CRITICAL
}
