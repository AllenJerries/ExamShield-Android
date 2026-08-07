package com.examshield.utils

import android.content.Context

class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("examshield_settings_v2", Context.MODE_PRIVATE)

    var alertDistanceMeters: Float
        get() = prefs.getFloat("alert_distance_meters", 3f)
        set(value) = prefs.edit().putFloat("alert_distance_meters", value).apply()

    val alertRssiThreshold: Int
        get() = DistanceCalculator.metersToApproxDbm(alertDistanceMeters.toDouble())

    var scanIntervalSeconds: Float
        get() = prefs.getFloat("scan_interval_seconds", 3f)
        set(value) = prefs.edit().putFloat("scan_interval_seconds", value).apply()

    var soundEnabled: Boolean
        get() = prefs.getBoolean("sound_enabled", true)
        set(value) = prefs.edit().putBoolean("sound_enabled", value).apply()

    var vibrationEnabled: Boolean
        get() = prefs.getBoolean("vibration_enabled", true)
        set(value) = prefs.edit().putBoolean("vibration_enabled", value).apply()

    var beepVolume: Int
        get() = prefs.getInt("beep_volume", 100)
        set(value) = prefs.edit().putInt("beep_volume", value).apply()

    var voiceAlerts: Boolean
        get() = prefs.getBoolean("voice_alerts", false)
        set(value) = prefs.edit().putBoolean("voice_alerts", value).apply()

    var notificationsEnabled: Boolean
        get() = prefs.getBoolean("notifications_enabled", true)
        set(value) = prefs.edit().putBoolean("notifications_enabled", value).apply()

    var backendUrl: String
        get() = prefs.getString("backend_url", "http://10.137.105.26:8000/api/") ?: ""
        set(value) = prefs.edit().putString("backend_url", value).apply()
}
