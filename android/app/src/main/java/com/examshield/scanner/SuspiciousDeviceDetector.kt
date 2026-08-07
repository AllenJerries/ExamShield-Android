package com.examshield.scanner

import android.util.Log
import com.examshield.data.models.DeviceType
import com.examshield.data.models.UnifiedDevice
import kotlin.math.abs

object SuspiciousDeviceDetector {

    private const val TAG = "SuspiciousDetector"

    data class SuspicionAnalysis(
        val suspicionScore: Int,
        val reasons: List<String>,
        val recommendation: String
    )

    fun analyzeDevice(
        device: UnifiedDevice,
        allDevices: List<UnifiedDevice>
    ): SuspicionAnalysis {
        var score = 0
        val reasons = mutableListOf<String>()

        if (device.deviceType == DeviceType.EARPHONE) {
            score += 40
            reasons.add("Wireless earphone detected")

            if (device.rssi > -55) {
                score += 20
                reasons.add("Very close - active use likely")
            }
        }

        if (device.deviceType == DeviceType.SMARTWATCH) {
            val hasPhoneNearby = allDevices.any {
                (it.deviceType == DeviceType.PHONE_ANDROID ||
                 it.deviceType == DeviceType.PHONE_IOS) &&
                abs(it.rssi - device.rssi) < 20
            }

            if (hasPhoneNearby) {
                score += 60
                reasons.add("Smartwatch paired with nearby phone")
                reasons.add("Watch can receive data notifications")
            } else {
                score += 30
                reasons.add("Smartwatch detected")
            }
        }

        if (device.deviceType == DeviceType.MOBILE_HOTSPOT) {
            score += 70
            reasons.add("Mobile hotspot broadcasting")
            reasons.add("Student sharing mobile data")

            val nearbyEarphone = allDevices.any {
                it.deviceType == DeviceType.EARPHONE &&
                abs(it.rssi - device.rssi) < 15
            }

            if (nearbyEarphone) {
                score += 20
                reasons.add("Earphone near hotspot - audio cheating likely")
            }
        }

        if (device.deviceType == DeviceType.PHONE_ANDROID ||
            device.deviceType == DeviceType.PHONE_IOS) {

            val nearbyPhones = allDevices.count {
                (it.deviceType == DeviceType.PHONE_ANDROID ||
                 it.deviceType == DeviceType.PHONE_IOS) &&
                it.macAddress != device.macAddress &&
                abs(it.rssi - device.rssi) < 15
            }

            if (nearbyPhones >= 1) {
                score += 40
                reasons.add("Multiple phones in close proximity")
                reasons.add("Possible phone-to-phone cheating")
            }
        }

        if (device.deviceType == DeviceType.HIDDEN_EARPIECE) {
            score += 90
            reasons.add("Hidden earpiece detected")
            reasons.add("Very close signal - active use")
            reasons.add("Likely receiving audio from data-connected device")
        }

        score = score.coerceAtMost(100)

        val recommendation = when {
            score >= 80 -> "CRITICAL - Check student immediately"
            score >= 60 -> "HIGH - Investigate closely"
            score >= 40 -> "MEDIUM - Monitor situation"
            else -> "LOW - Standard monitoring"
        }

        Log.d(TAG, "Suspicion for ${device.name}: $score")

        return SuspicionAnalysis(
            suspicionScore = score,
            reasons = reasons,
            recommendation = recommendation
        )
    }
}
