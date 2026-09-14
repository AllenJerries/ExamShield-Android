package com.examshield.scanner

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.examshield.data.models.DeviceSource
import com.examshield.data.models.RiskLevel
import com.examshield.data.models.UnifiedDevice
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicBoolean

class UnifiedScanner(private val context: Context) {

    companion object {
        private const val TAG = "UnifiedScanner"
        private const val CLEANUP_INTERVAL_MS = 5_000L
        private const val STATS_INTERVAL_MS = 2_000L
    }

    private val _devices = MutableStateFlow<List<UnifiedDevice>>(emptyList())
    val devices: StateFlow<List<UnifiedDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanStatus = MutableStateFlow<ScanStatus>(ScanStatus.Idle)
    val scanStatus: StateFlow<ScanStatus> = _scanStatus.asStateFlow()

    private val _scanStats = MutableStateFlow(ScanStats())
    val scanStats: StateFlow<ScanStats> = _scanStats.asStateFlow()

    private val _bleActive = MutableStateFlow(false)
    val bleActive: StateFlow<Boolean> = _bleActive.asStateFlow()

    private val _wifiActive = MutableStateFlow(false)
    val wifiActive: StateFlow<Boolean> = _wifiActive.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isScanningActive = AtomicBoolean(false)

    val bluetoothScanner = BluetoothScanner(context)
    val wifiScanner = ExamWiFiScanner(context)

    private var combineJob: Job? = null
    private var cleanupJob: Job? = null
    private var statsJob: Job? = null

    @SuppressLint("MissingPermission")
    fun startScanning() {
        if (!isScanningActive.compareAndSet(false, true)) return

        Log.d(TAG, "Starting unified scanning (BLE + WiFi)")
        _isScanning.value = true
        _scanStatus.value = ScanStatus.Starting

        bluetoothScanner.startScanning()
        wifiScanner.startScanning()

        _bleActive.value = true
        _wifiActive.value = true
        _scanStatus.value = ScanStatus.Active

        startCombineLoop()
        startCleanup()
        startStatsUpdate()
    }

    fun stopScanning() {
        Log.d(TAG, "Stopping unified scanning")
        isScanningActive.set(false)
        _isScanning.value = false
        _scanStatus.value = ScanStatus.Idle

        bluetoothScanner.stopScanning()
        wifiScanner.stopScanning()

        _bleActive.value = false
        _wifiActive.value = false

        combineJob?.cancel()
        cleanupJob?.cancel()
        statsJob?.cancel()
    }

    fun manualRefresh() {
        Log.d(TAG, "Manual refresh triggered")
        _scanStatus.value = ScanStatus.Refreshing
        wifiScanner.manualScan()
        scope.launch {
            delay(1000)
            if (_isScanning.value) {
                _scanStatus.value = ScanStatus.Active
            }
        }
    }

    fun isBluetoothEnabled(): Boolean = bluetoothScanner.isBluetoothEnabled()
    fun isWifiEnabled(): Boolean = wifiScanner.isWifiEnabled()

    fun getDeviceCount(): Int = _devices.value.size

    fun getDevice(mac: String): UnifiedDevice? {
        return _devices.value.find { it.macAddress.equals(mac, true) }
    }

    fun clearDevices() {
        bluetoothScanner.clearDevices()
        wifiScanner.clearDevices()
        _devices.value = emptyList()
    }

    private fun startCombineLoop() {
        combineJob?.cancel()
        combineJob = scope.launch {
            combine(
                bluetoothScanner.devices,
                wifiScanner.wifiDevices
            ) { bleDevices, wifiDevices ->
                combineLists(bleDevices, wifiDevices)
            }.collect { combined ->
                _devices.value = combined
            }
        }
    }

    private fun combineLists(
        bleDevices: List<UnifiedDevice>,
        wifiDevices: List<ExamWiFiDevice>
    ): List<UnifiedDevice> {
        val wifiUnified = wifiDevices.map { wifiScanner.toUnifiedDevice(it) }
        return (bleDevices + wifiUnified)
            .sortedWith(
                compareBy<UnifiedDevice> {
                    when (it.riskLevel) {
                        RiskLevel.CRITICAL -> 0
                        RiskLevel.HIGH -> 1
                        RiskLevel.MEDIUM -> 2
                        RiskLevel.LOW -> 3
                    }
                }.thenBy { it.proximityScore }
            )
            .distinctBy { it.macAddress.uppercase() }
    }

    private fun startCleanup() {
        cleanupJob?.cancel()
        cleanupJob = scope.launch {
            while (isScanningActive.get()) {
                delay(CLEANUP_INTERVAL_MS)
                bluetoothScanner.cleanupStaleDevices()
                wifiScanner.cleanupStaleDevices()
            }
        }
    }

    private fun startStatsUpdate() {
        statsJob?.cancel()
        statsJob = scope.launch {
            while (isScanningActive.get()) {
                delay(STATS_INTERVAL_MS)
                val current = _devices.value
                _scanStats.value = ScanStats(
                    totalDevices = current.size,
                    bluetoothCount = current.count { it.source == DeviceSource.BLUETOOTH },
                    wifiCount = current.count { it.source == DeviceSource.WIFI_NETWORK },
                    hotspotCount = current.count { it.source == DeviceSource.WIFI_HOTSPOT },
                    unauthorizedCount = current.size
                )
            }
        }
    }
}

data class ScanStats(
    val totalDevices: Int = 0,
    val bluetoothCount: Int = 0,
    val wifiCount: Int = 0,
    val hotspotCount: Int = 0,
    val unauthorizedCount: Int = 0
) {
    fun isEmpty() = totalDevices == 0
}

sealed class ScanStatus {
    data object Idle : ScanStatus()
    data object Starting : ScanStatus()
    data object Active : ScanStatus()
    data object Refreshing : ScanStatus()
}