package com.examshield.utils

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import com.examshield.data.models.RiskLevel
import kotlinx.coroutines.*

class SoundManager(private val context: Context) {

    companion object {
        private const val TAG = "SoundManager"
    }

    private var toneGenerator: ToneGenerator? = null
    private var currentBeepJob: Job? = null
    private var volume: Float = 0.8f
    private var isInitialized = false
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun initialize() {
        safeExecute("Initialize") {
            toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, (volume * 100).toInt())
            isInitialized = true
            Log.d(TAG, "SoundManager initialized (STREAM_ALARM)")
        }
        if (!isInitialized) {
            safeExecute("Init fallback") {
                toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
                isInitialized = true
                Log.d(TAG, "SoundManager initialized (STREAM_MUSIC fallback)")
            }
        }
        if (!isInitialized) {
            Log.e(TAG, "SoundManager failed to initialize")
        }
    }

    fun setVolume(vol: Float) {
        volume = vol.coerceIn(0f, 1f)
        if (isInitialized) {
            safeExecute("Set volume") {
                toneGenerator?.release()
                toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, (volume * 100).toInt())
            }
        }
    }

    fun playBeep() {
        if (!isInitialized) return
        safeExecute("Play beep") {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
        }
    }

    fun playFastBeep() {
        if (!isInitialized) return
        safeExecute("Play fast beep") {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 120)
        }
    }

    fun playAlertSound(riskLevel: RiskLevel) {
        Log.d(TAG, "playAlertSound called for risk: $riskLevel")
        if (!isInitialized) {
            initialize()
            if (!isInitialized) return
        }
        safeExecute("Play alert sound") {
            when (riskLevel) {
                RiskLevel.HIGH, RiskLevel.CRITICAL -> {
                    toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1000)
                }
                RiskLevel.MEDIUM -> {
                    toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_INCALL_LITE, 500)
                }
                RiskLevel.LOW -> playBeep()
            }
        }
    }

    fun playAlarm() {
        if (!isInitialized) return
        safeExecute("Play alarm") {
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 2000)
        }
    }

    fun stopAlarm() {
        safeExecute("Stop alarm") {
            toneGenerator?.stopTone()
        }
    }

    fun playBeepAtInterval(intervalMs: Long, scope: CoroutineScope) {
        currentBeepJob?.cancel()
        if (intervalMs <= 0L) {
            playAlarm()
            return
        }
        currentBeepJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                safeExecute("Beep interval") {
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 100)
                }
                delay(intervalMs)
            }
        }
    }

    fun stopBeeping() {
        currentBeepJob?.cancel()
        currentBeepJob = null
        stopAlarm()
    }

    fun playBeep(volume: Int, intervalMs: Long) {
        stopBeeping()
        currentBeepJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                try {
                    toneGenerator?.release()
                    toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, volume)

                    if (intervalMs < 200) {
                        toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1000)
                        delay(1000)
                    } else {
                        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
                        delay(intervalMs)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Proximity beep error: ${e.message}")
                    delay(intervalMs)
                }
            }
        }
    }

    fun stop() { stopBeeping() }

    fun release() {
        stopBeeping()
        scope.cancel()
        safeExecute("Release") {
            toneGenerator?.release()
            toneGenerator = null
        }
    }

    private fun safeExecute(operation: String, block: () -> Unit) {
        try {
            block()
        } catch (e: SecurityException) {
            Log.e(TAG, "Security: $operation", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed: $operation", e)
        }
    }
}
