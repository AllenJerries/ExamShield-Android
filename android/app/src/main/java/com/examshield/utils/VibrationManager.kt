package com.examshield.utils

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import com.examshield.data.models.RiskLevel
import kotlinx.coroutines.*

class VibrationManager(private val context: Context) {

    companion object {
        private const val TAG = "VibrationManager"
    }

    private var vibrator: Vibrator? = null
    private var vibrationJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        vibrator = getVibrator()
    }

    private fun getVibrator(): Vibrator? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(
                    Context.VIBRATOR_MANAGER_SERVICE
                ) as? android.os.VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get vibrator error", e)
            null
        }
    }

    @SuppressLint("MissingPermission")
    fun vibrateOnce(durationMs: Long = 200) {
        safeExecute("Vibrate once") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun triggerVibration(riskLevel: RiskLevel) {
        val pattern = when (riskLevel) {
            RiskLevel.HIGH, RiskLevel.CRITICAL -> longArrayOf(0, 500, 200, 500, 200, 500)
            RiskLevel.MEDIUM -> longArrayOf(0, 300, 200, 300)
            RiskLevel.LOW -> longArrayOf(0, 100)
        }
        Log.d(TAG, "Triggering vibration for $riskLevel risk")
        startVibrationPattern(pattern)
    }

    @SuppressLint("MissingPermission")
    fun startVibrationPattern(pattern: LongArray, repeat: Int = 0) {
        safeExecute("Start pattern") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, repeat))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, repeat)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun vibrateAtInterval(intervalMs: Long, scope: CoroutineScope) {
        vibrationJob?.cancel()
        if (intervalMs <= 0L) {
            startVibrationPattern(longArrayOf(0, 500), 0)
            return
        }
        vibrationJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                vibrateOnce(100)
                delay(intervalMs)
            }
        }
    }

    fun stopVibration() {
        vibrationJob?.cancel()
        vibrationJob = null
        safeExecute("Stop vibration") { vibrator?.cancel() }
    }

    @SuppressLint("MissingPermission")
    fun vibrate(intervalMs: Long) {
        stopVibration()
        vibrationJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                try {
                    val duration = when {
                        intervalMs < 200 -> 500L
                        intervalMs < 500 -> 200L
                        intervalMs < 1000 -> 100L
                        else -> 50L
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(
                            VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(duration)
                    }

                    delay(intervalMs)
                } catch (e: Exception) {
                    Log.e(TAG, "Proximity vibration error: ${e.message}")
                    delay(intervalMs)
                }
            }
        }
    }

    fun stop() { stopVibration() }

    fun release() {
        stopVibration()
        scope.cancel()
        vibrator = null
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
