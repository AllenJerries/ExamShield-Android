package com.examshield.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log

class VibrationHelper(private val context: Context) {

    companion object {
        private const val TAG = "VibrationHelper"
        private val CRITICAL_PATTERN = longArrayOf(0, 500, 100, 500, 100, 500, 100, 500)
        private val HIGH_PATTERN = longArrayOf(0, 300, 100, 300, 100, 300)
        private val MEDIUM_PATTERN = longArrayOf(0, 200, 100, 200)
        private val LOW_PATTERN = longArrayOf(0, 150)
        private val ALERT_PATTERN = longArrayOf(0, 300, 200, 300, 200, 500)

        fun triggerAlert(context: Context, urgency: AlertUrgency = AlertUrgency.HIGH) {
            VibrationHelper(context).triggerAlert(urgency)
        }

        fun triggerHuntPulse(context: Context, duration: Long) {
            VibrationHelper(context).triggerHuntPulse(duration)
        }

        fun vibrateAlert(context: Context, isCritical: Boolean = false) {
            VibrationHelper(context).vibrateAlert(isCritical)
        }

        fun stopVibration(context: Context) {
            VibrationHelper(context).stop()
        }
    }

    private val vibrator: Vibrator? = getVibrator()

    fun vibrateAlert(isCritical: Boolean = false) {
        safeExecute("Vibrate alert") {
            val pattern = if (isCritical) CRITICAL_PATTERN else ALERT_PATTERN
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, -1)
            }
        }
    }

    fun triggerAlert(urgency: AlertUrgency = AlertUrgency.HIGH) {
        safeExecute("Trigger alert") {
            val pattern = when (urgency) {
                AlertUrgency.CRITICAL -> CRITICAL_PATTERN
                AlertUrgency.HIGH -> HIGH_PATTERN
                AlertUrgency.MEDIUM -> MEDIUM_PATTERN
                AlertUrgency.LOW -> LOW_PATTERN
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, -1)
            }
        }
    }

    fun triggerHuntPulse(duration: Long) {
        safeExecute("Hunt pulse") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(
                        duration, VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(duration)
            }
        }
    }

    fun stop() {
        safeExecute("Stop vibration") {
            vibrator?.cancel()
        }
    }

    private fun getVibrator(): Vibrator? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(
                    Context.VIBRATOR_MANAGER_SERVICE
                ) as? android.os.VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get vibrator error", e)
            null
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
