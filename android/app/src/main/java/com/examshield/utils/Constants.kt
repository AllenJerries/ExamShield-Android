package com.examshield.utils

object Constants {
    const val DATABASE_NAME = "examshield_database"
    const val BASE_URL = "http://10.137.105.26:8000/api/"

    const val DEFAULT_SCAN_INTERVAL = 5000L
    const val FAST_SCAN_INTERVAL = 3000L
    const val BASELINE_SCAN_DURATION = 20
    const val PROXIMITY_SCAN_INTERVAL = 500L
    const val WIFI_SCAN_INTERVAL = 5000L
    const val STALE_DEVICE_TIMEOUT = 30000L
    const val UI_UPDATE_INTERVAL = 500L

    const val DEFAULT_RSSI_THRESHOLD = -80
    const val DEFAULT_ALERT_DISTANCE_METERS = 3.0f
    const val FOUND_RSSI_THRESHOLD = -35

    const val NOTIFICATION_CHANNEL_ID = "examshield_scanning"
    const val NOTIFICATION_CHANNEL_NAME = "Exam Scanning"
    const val NOTIFICATION_ID = 1001

    const val ACTION_START_SCAN = "com.examshield.START_SCAN"
    const val ACTION_STOP_SCAN = "com.examshield.STOP_SCAN"

    const val PREF_NAME = "examshield_prefs"
    const val PREF_NAME_V2 = "examshield_settings_v2"
    const val PREF_BEEP_VOLUME = "beep_volume"
    const val PREF_SCAN_INTERVAL = "scan_interval"
    const val PREF_RSSI_THRESHOLD = "rssi_threshold"
    const val PREF_VIBRATION_ON = "vibration_on"
    const val PREF_SOUND_ON = "sound_on"
    const val PREF_BACKEND_URL = "backend_url"
    const val PREF_VOICE_ALERTS = "voice_alerts"
    const val PREF_NOTIFICATIONS = "notifications_enabled"

    const val PERMISSION_REQUEST_CODE = 100
}
