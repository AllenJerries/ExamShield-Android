package com.examshield.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.examshield.data.local.AppDatabase
import com.examshield.data.models.Device
import com.examshield.data.repository.DeviceRepository
import com.examshield.data.remote.RetrofitClient
import com.examshield.utils.Constants
import com.examshield.utils.DistanceCalculator
import com.examshield.utils.SettingsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = SettingsRepository(application)
    private val db = AppDatabase.getDatabase(application)
    private val deviceRepository = DeviceRepository(db)

    private val _alertDistanceMeters = MutableStateFlow(settings.alertDistanceMeters)
    val alertDistanceMeters: StateFlow<Float> = _alertDistanceMeters.asStateFlow()

    val alertRssiThreshold: Int
        get() = settings.alertRssiThreshold

    private val _scanIntervalSeconds = MutableStateFlow(settings.scanIntervalSeconds)
    val scanIntervalSeconds: StateFlow<Float> = _scanIntervalSeconds.asStateFlow()

    private val _soundEnabled = MutableStateFlow(settings.soundEnabled)
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    private val _vibrationEnabled = MutableStateFlow(settings.vibrationEnabled)
    val vibrationEnabled: StateFlow<Boolean> = _vibrationEnabled.asStateFlow()

    private val _beepVolume = MutableStateFlow(settings.beepVolume.toFloat())
    val beepVolume: StateFlow<Float> = _beepVolume.asStateFlow()

    private val _voiceAlerts = MutableStateFlow(settings.voiceAlerts)
    val voiceAlerts: StateFlow<Boolean> = _voiceAlerts.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(settings.notificationsEnabled)
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _backendUrl = MutableStateFlow(settings.backendUrl)
    val backendUrl: StateFlow<String> = _backendUrl.asStateFlow()

    private val _whitelistedDevices = MutableStateFlow<List<Device>>(emptyList())
    val whitelistedDevices: StateFlow<List<Device>> = _whitelistedDevices.asStateFlow()

    private val _activeExamId = MutableStateFlow<Long?>(null)
    val activeExamId: StateFlow<Long?> = _activeExamId.asStateFlow()

    fun updateAlertDistance(meters: Float) {
        _alertDistanceMeters.value = meters
        settings.alertDistanceMeters = meters
    }

    fun updateScanInterval(seconds: Float) {
        _scanIntervalSeconds.value = seconds
        settings.scanIntervalSeconds = seconds
    }

    fun updateSoundEnabled(enabled: Boolean) {
        _soundEnabled.value = enabled
        settings.soundEnabled = enabled
    }

    fun updateVibrationEnabled(enabled: Boolean) {
        _vibrationEnabled.value = enabled
        settings.vibrationEnabled = enabled
    }

    fun updateBeepVolume(volume: Float) {
        _beepVolume.value = volume
        settings.beepVolume = volume.toInt()
    }

    fun updateVoiceAlerts(enabled: Boolean) {
        _voiceAlerts.value = enabled
        settings.voiceAlerts = enabled
    }

    fun updateNotificationsEnabled(enabled: Boolean) {
        _notificationsEnabled.value = enabled
        settings.notificationsEnabled = enabled
    }

    fun updateBackendUrl(url: String) {
        _backendUrl.value = url
        settings.backendUrl = url
        RetrofitClient.updateBaseUrl(getApplication(), url)
    }

    fun loadWhitelist(examId: Long) {
        _activeExamId.value = examId
        viewModelScope.launch {
            deviceRepository.getWhitelistedDevices(examId).collect {
                _whitelistedDevices.value = it
            }
        }
    }

    fun removeDevice(device: Device) {
        val examId = _activeExamId.value ?: return
        viewModelScope.launch {
            deviceRepository.removeFromWhitelist(device.id, examId)
            loadWhitelist(examId)
        }
    }

    fun addDeviceManually(macAddress: String, examId: Long) {
        viewModelScope.launch {
            val device = Device(
                examId = examId,
                macAddress = macAddress.uppercase(),
                deviceName = "Manual Entry",
                deviceType = "OTHER",
                rssi = 0,
                manufacturer = "Unknown",
                isWhitelisted = true,
                isAuthorized = true,
                riskLevel = "LOW"
            )
            deviceRepository.addToWhitelist(device)
            loadWhitelist(examId)
        }
    }
}
