package com.examshield.scanner

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import com.examshield.data.models.DeviceSource
import com.examshield.data.models.DeviceType
import com.examshield.data.models.RiskLevel
import com.examshield.data.models.UnifiedDevice
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class ExamWiFiScanner(private val context: Context) {

    private val TAG = "ExamWiFiScanner"

    private val _wifiDevices = MutableStateFlow<List<ExamWiFiDevice>>(emptyList())
    val wifiDevices: StateFlow<List<ExamWiFiDevice>> = _wifiDevices

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _scanStatus = MutableStateFlow("Idle")
    val scanStatus: StateFlow<String> = _scanStatus

    private val _scanCount = MutableStateFlow(0)
    val scanCount: StateFlow<Int> = _scanCount

    private val wifiManager = context.applicationContext.getSystemService(
        Context.WIFI_SERVICE
    ) as? WifiManager

    private var wifiScanReceiver: BroadcastReceiver? = null
    private var scanJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val deviceMap = ConcurrentHashMap<String, ExamWiFiDevice>()
    private val isActive = AtomicBoolean(false)

    fun startScanning() {
        if (!isActive.compareAndSet(false, true)) return
        Log.d(TAG, "Starting WiFi scanning")
        _isScanning.value = true
        _scanStatus.value = "Starting..."

        if (!hasRequiredPermissions()) {
            Log.e(TAG, "Missing required permissions for WiFi scan")
            _scanStatus.value = "Missing permissions"
            isActive.set(false)
            _isScanning.value = false
            return
        }

        if (wifiManager?.isWifiEnabled != true) {
            Log.e(TAG, "WiFi is disabled on device")
            _scanStatus.value = "WiFi OFF"
            isActive.set(false)
            _isScanning.value = false
            return
        }

        registerReceiver()
        startContinuousScanning()
    }

    fun stopScanning() {
        Log.d(TAG, "Stopping WiFi scanning")
        isActive.set(false)
        _isScanning.value = false
        _scanStatus.value = "Stopped"

        safeExecute("Unregister receiver") {
            wifiScanReceiver?.let { context.unregisterReceiver(it) }
            wifiScanReceiver = null
        }

        scanJob?.cancel()
    }

    @SuppressLint("MissingPermission")
    fun manualScan() {
        scope.launch { triggerScan() }
    }

    fun isWifiEnabled(): Boolean = try {
        wifiManager?.isWifiEnabled == true
    } catch (e: Exception) {
        false
    }

    fun getDeviceCount(): Int = deviceMap.size

    fun clearDevices() {
        deviceMap.clear()
        _wifiDevices.value = emptyList()
    }

    @SuppressLint("MissingPermission")
    private fun registerReceiver() {
        wifiScanReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                    val updated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                    } else {
                        true
                    }
                    Log.d(TAG, "Scan broadcast received (updated=$updated)")
                    handleScanResults()
                    _scanStatus.value = if (updated) "Fresh scan" else "Cached results"
                }
            }
        }

        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        safeExecute("Register receiver") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(wifiScanReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(wifiScanReceiver, filter)
            }
            Log.d(TAG, "WiFi scan receiver registered")
        }
    }

    private fun startContinuousScanning() {
        scanJob = scope.launch {
            Log.d(TAG, "Starting continuous scan loop")
            while (this@ExamWiFiScanner.isActive.get()) {
                try {
                    triggerScan()
                    delay(30_000L)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e(TAG, "Failed: Scan loop", e)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun triggerScan() {
        if (!isWifiEnabled()) {
            _scanStatus.value = "WiFi OFF"
            return
        }
        if (!hasRequiredPermissions()) {
            _scanStatus.value = "Missing permissions"
            return
        }
        safeExecute("Trigger scan") {
            @Suppress("DEPRECATION")
            val started = wifiManager?.startScan()
            if (started == true) {
                _scanStatus.value = "Scan triggered"
            } else {
                _scanStatus.value = "Throttled - using cached"
                handleScanResults()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleScanResults() {
        safeExecute("Handle scan results") {
            val results = wifiManager?.scanResults ?: return@safeExecute
            Log.d(TAG, "Processing ${results.size} WiFi scan results")

            var hotspotCount = 0
            for (result in results) {
                processWiFiResult(result)?.let {
                    if (it.isHotspot) hotspotCount++
                }
            }

            _scanCount.value++
            emitDevices()
            Log.d(TAG, "Batch: ${results.size} results -> ${deviceMap.size} total ($hotspotCount hotspots)")
        }
    }

    @SuppressLint("MissingPermission")
    private fun processWiFiResult(result: ScanResult): ExamWiFiDevice? {
        return try {
            val ssid = result.SSID?.trim()
            val displaySsid = ssid?.ifEmpty { "Hidden Network" } ?: "Hidden Network"
            val bssid = result.BSSID?.uppercase() ?: return null
            if (bssid.isEmpty()) return null

            val rssi = result.level
            if (rssi == 0) return null

            val frequency = result.frequency
            val capabilities = result.capabilities ?: ""
            val isHotspot = detectMobileHotspot(displaySsid, bssid, frequency, capabilities)

            val deviceType = if (isHotspot) ExamWiFiDeviceType.MOBILE_HOTSPOT else ExamWiFiDeviceType.WIFI_NETWORK

            val device = ExamWiFiDevice(
                bssid = bssid,
                ssid = displaySsid,
                rssi = rssi,
                frequency = frequency,
                capabilities = capabilities,
                isHotspot = isHotspot,
                deviceType = deviceType,
                firstSeen = System.currentTimeMillis(),
                lastSeen = System.currentTimeMillis()
            )

            val existing = deviceMap[bssid]
            deviceMap[bssid] = existing?.copy(
                rssi = rssi,
                lastSeen = System.currentTimeMillis()
            ) ?: device

            Log.d(TAG, "${if (isHotspot) "HOTSPOT" else "WiFi"}: $displaySsid | RSSI: $rssi | Freq: $frequency")
            device
        } catch (e: Exception) {
            Log.e(TAG, "Process result error: ${e.message}")
            null
        }
    }

    private fun detectMobileHotspot(
        ssid: String,
        bssid: String,
        frequency: Int,
        capabilities: String
    ): Boolean {
        return HotspotDetector.isMobileHotspot(ssid, bssid, frequency, capabilities)
    }

    private fun emitDevices() {
        val list = deviceMap.values
            .sortedBy { it.rssi * -1 }
            .toList()
        _wifiDevices.value = list
    }

    private fun hasRequiredPermissions(): Boolean {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        return permissions.all {
            ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun toUnifiedDevice(wifi: ExamWiFiDevice): UnifiedDevice {
        val deviceType = if (wifi.isHotspot) DeviceType.MOBILE_HOTSPOT else DeviceType.WIFI_DEVICE
        val riskLevel = if (wifi.isHotspot) RiskLevel.HIGH else RiskLevel.MEDIUM
        val source = if (wifi.isHotspot) DeviceSource.WIFI_HOTSPOT else DeviceSource.WIFI_NETWORK
        val description = if (wifi.isHotspot) "Mobile hotspot detected" else "WiFi network"

        return UnifiedDevice(
            macAddress = wifi.bssid,
            name = wifi.ssid,
            rssi = wifi.rssi,
            source = source,
            deviceType = deviceType,
            riskLevel = riskLevel,
            manufacturer = ManufacturerResolver.getManufacturer(wifi.bssid),
            isHotspot = wifi.isHotspot,
            frequency = wifi.frequency,
            firstSeen = wifi.firstSeen,
            lastSeen = wifi.lastSeen,
            description = description
        )
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

data class ExamWiFiDevice(
    val bssid: String,
    val ssid: String,
    val rssi: Int,
    val frequency: Int,
    val capabilities: String,
    val isHotspot: Boolean,
    val deviceType: ExamWiFiDeviceType,
    val firstSeen: Long,
    val lastSeen: Long
) {
    val estimatedDistance: Double
        get() {
            if (rssi == 0 || rssi <= -100) return 999.0
            val ratio = (-59 - rssi).toDouble() / 20.0
            return Math.pow(10.0, ratio)
        }

    val proximityScore: Double
        get() = estimatedDistance
}

enum class ExamWiFiDeviceType {
    MOBILE_HOTSPOT,
    WIFI_NETWORK
}
