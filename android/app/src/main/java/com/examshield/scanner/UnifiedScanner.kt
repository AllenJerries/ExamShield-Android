package com.examshield.scanner

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.examshield.data.models.DeviceSource
import com.examshield.data.models.DeviceType
import com.examshield.data.models.RiskLevel
import com.examshield.data.models.UnifiedDevice
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class UnifiedScanner(private val context: Context) {

    companion object {
        private const val TAG = "UnifiedScanner"
        private const val BLE_CLEANUP_INTERVAL_MS = 8_000L
        private const val COMBINE_INTERVAL_MS = 1_000L
        private const val BLE_STALE_TIMEOUT_MS = 20_000L
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

    private val bleDeviceMap = ConcurrentHashMap<String, UnifiedDevice>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isScanningActive = AtomicBoolean(false)

    val bluetoothScanner = BluetoothScanner(context)
    val wifiScanner = ExamWiFiScanner(context)

    private var cleanupJob: Job? = null
    private var statsJob: Job? = null
    private var combineJob: Job? = null

    @SuppressLint("MissingPermission")
    fun startScanning() {
        if (!isScanningActive.compareAndSet(false, true)) return

        Log.d(TAG, "Starting unified scanning (BLE + WiFi)")
        _isScanning.value = true
        _scanStatus.value = ScanStatus.Starting

        bluetoothScanner.startAggressiveScan()
        wifiScanner.startScanning()

        _bleActive.value = true
        _wifiActive.value = true
        _scanStatus.value = ScanStatus.Active

        startCleanupJob()
        startStatsUpdate()
        startCombineLoop()
    }

    fun stopScanning() {
        Log.d(TAG, "Stopping unified scanning")
        isScanningActive.set(false)
        _isScanning.value = false
        _scanStatus.value = ScanStatus.Idle

        bluetoothScanner.stopAggressiveScan()
        wifiScanner.stopScanning()

        _bleActive.value = false
        _wifiActive.value = false

        cleanupJob?.cancel()
        statsJob?.cancel()
        combineJob?.cancel()
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

    fun getDeviceCount(): Int {
        return bleDeviceMap.size + wifiScanner.getDeviceCount()
    }

    fun getDevice(mac: String): UnifiedDevice? {
        val upper = mac.uppercase()
        return bleDeviceMap[upper]
            ?: wifiScanner.wifiDevices.value.find { it.bssid == upper }
                ?.let { wifiScanner.toUnifiedDevice(it) }
    }

    fun clearDevices() {
        bleDeviceMap.clear()
        wifiScanner.clearDevices()
        _devices.value = emptyList()
    }

    private fun convertToUnifiedDevice(scanResult: com.examshield.data.models.ScanResult): UnifiedDevice? {
        return try {
            val mac = scanResult.macAddress
            val rssi = scanResult.rssi
            if (rssi == 0 || mac.isEmpty()) return null

            val name = scanResult.deviceName.ifEmpty { "" }
            val scanRecordBytes = scanResult.scanRecord
            val macAddress = mac.uppercase()

            val classification = DeviceClassifier.classifyDeviceStrict(
                deviceName = name,
                macAddress = macAddress,
                rssi = rssi,
                scanRecord = scanRecordBytes,
                isFromWifi = false
            )

            if (!classification.shouldShow) return null

            UnifiedDevice(
                macAddress = macAddress,
                name = name,
                rssi = rssi,
                source = DeviceSource.BLUETOOTH,
                deviceType = classification.deviceType,
                riskLevel = classification.riskLevel,
                manufacturer = scanResult.manufacturer,
                scanRecord = scanRecordBytes,
                description = classification.description
            )
        } catch (e: Exception) {
            Log.e(TAG, "Convert BLE error", e)
            null
        }
    }

    private fun updateBleDeviceMap() {
        val bleResults = bluetoothScanner.getDiscoveredDevices()
        for ((mac, scanResult) in bleResults) {
            val upperMac = mac.uppercase()
            val existing = bleDeviceMap[upperMac]
            if (existing != null) {
                bleDeviceMap[upperMac] = existing.copy(
                    rssi = scanResult.rssi,
                    lastSeen = System.currentTimeMillis()
                )
            } else {
                val device = convertToUnifiedDevice(scanResult)
                if (device != null) {
                    bleDeviceMap[upperMac] = device
                }
            }
        }
    }

    private fun startCombineLoop() {
        combineJob = scope.launch {
            while (isScanningActive.get()) {
                try {
                    updateBleDeviceMap()
                    combineDevices()
                } catch (e: Exception) {
                    Log.e(TAG, "Combine error: ${e.message}")
                }
                delay(COMBINE_INTERVAL_MS)
            }
        }
    }

    private fun combineDevices() {
        val allDevices = mutableListOf<UnifiedDevice>()

        allDevices.addAll(bleDeviceMap.values)

        val wifiUnified = wifiScanner.wifiDevices.value.map { wifiScanner.toUnifiedDevice(it) }
        allDevices.addAll(wifiUnified)

        val sorted = allDevices.sortedWith(
            compareBy<UnifiedDevice> {
                when (it.riskLevel) {
                    RiskLevel.CRITICAL -> 0
                    RiskLevel.HIGH -> 1
                    RiskLevel.MEDIUM -> 2
                    RiskLevel.LOW -> 3
                }
            }.thenBy { it.proximityScore }
        )

        _devices.value = sorted
    }

    private fun startCleanupJob() {
        cleanupJob = scope.launch {
            while (isScanningActive.get()) {
                delay(BLE_CLEANUP_INTERVAL_MS)
                cleanupBleDevices()
            }
        }
    }

    private fun cleanupBleDevices() {
        val now = System.currentTimeMillis()
        val stale = bleDeviceMap.filter {
            now - it.value.lastSeen > BLE_STALE_TIMEOUT_MS
        }
        stale.keys.forEach { bleDeviceMap.remove(it) }
        if (stale.isNotEmpty()) {
            Log.d(TAG, "Removed ${stale.size} stale BLE devices")
        }
    }

    private fun startStatsUpdate() {
        statsJob = scope.launch {
            while (isScanningActive.get()) {
                delay(2000)
                val bleCount = bleDeviceMap.size
                val wifiDevices = wifiScanner.wifiDevices.value
                _scanStats.value = ScanStats(
                    totalDevices = bleCount + wifiDevices.size,
                    bluetoothCount = bleCount,
                    wifiCount = wifiDevices.count { it.deviceType == ExamWiFiDeviceType.WIFI_NETWORK },
                    hotspotCount = wifiDevices.count { it.deviceType == ExamWiFiDeviceType.MOBILE_HOTSPOT },
                    unauthorizedCount = bleCount + wifiDevices.size
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
