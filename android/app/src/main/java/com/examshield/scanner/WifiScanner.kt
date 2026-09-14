package com.examshield.scanner

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.examshield.data.models.DeviceSource
import com.examshield.data.models.DeviceType
import com.examshield.data.models.RiskLevel
import com.examshield.data.models.ScanResult as AppScanResult
import com.examshield.data.models.UnifiedDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class WifiScanner(private val context: Context) {

    companion object {
        private const val TAG = "WifiScanner"
        private const val SCAN_INTERVAL_MS = 12_000L
        private const val STALE_TIMEOUT_MS = 45_000L
    }

    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val deviceMap = ConcurrentHashMap<String, UnifiedDevice>()
    private val _devices = MutableStateFlow<List<UnifiedDevice>>(emptyList())
    val devices: StateFlow<List<UnifiedDevice>> = _devices.asStateFlow()

    private val _scanStatus = MutableStateFlow("Idle")
    val scanStatus: StateFlow<String> = _scanStatus.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val scanActive = AtomicBoolean(false)
    private var scanLoopJob: Job? = null
    private var receiverRegistered = false

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                processScanResults()
            }
        }
    }

    fun isWifiEnabled(): Boolean = wifiManager?.isWifiEnabled == true

    @SuppressLint("MissingPermission")
    fun startScanning() {
        if (!scanActive.compareAndSet(false, true)) return
        _isScanning.value = true
        _scanStatus.value = "Active"
        registerReceiver()
        scanLoopJob = scope.launch {
            while (isActive && scanActive.get()) {
                try {
                    wifiManager?.startScan()
                } catch (e: Exception) {
                    Log.e(TAG, "startScan error", e)
                }
                delay(SCAN_INTERVAL_MS)
            }
        }
    }

    private fun registerReceiver() {
        if (receiverRegistered) return
        try {
            val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(scanReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(scanReceiver, filter)
            }
            receiverRegistered = true
        } catch (e: Exception) {
            Log.e(TAG, "Receiver error", e)
        }
    }

    private fun unregisterReceiverIfNeeded() {
        if (!receiverRegistered) return
        try {
            context.unregisterReceiver(scanReceiver)
        } catch (e: Exception) {
            // ignore
        }
        receiverRegistered = false
    }

    @SuppressLint("MissingPermission")
    fun manualScan() {
        if (wifiManager == null) return
        _scanStatus.value = "Refreshing"
        try {
            wifiManager.startScan()
        } catch (e: Exception) {
            Log.e(TAG, "manualScan error", e)
        }
        scope.launch {
            delay(1000)
            if (scanActive.get()) _scanStatus.value = "Active"
        }
    }

    @SuppressLint("MissingPermission")
    private fun processScanResults() {
        if (wifiManager == null) return
        val results = try {
            wifiManager.scanResults ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "scanResults error", e)
            emptyList()
        }
        if (results.isEmpty()) return

        val now = System.currentTimeMillis()
        results.forEach { result ->
            val bssid = result.BSSID?.uppercase() ?: return@forEach
            val rssi = result.level
            if (rssi == 0 || rssi < -100) return@forEach

            val ssid = (result.SSID ?: "").trim()
            val isHotspot = isMobileHotspot(ssid, bssid)

            val deviceType = if (isHotspot) DeviceType.MOBILE_HOTSPOT else DeviceType.WIFI_DEVICE
            val riskLevel = if (isHotspot) RiskLevel.HIGH else RiskLevel.MEDIUM
            val source = if (isHotspot) DeviceSource.WIFI_HOTSPOT else DeviceSource.WIFI_NETWORK
            val wifiName = ssid.ifEmpty {
                if (isHotspot) "Mobile Hotspot ($bssid)" else "WiFi Network ($bssid)"
            }

            val existing = deviceMap[bssid]
            val device = existing?.copy(rssi = rssi, lastSeen = now)
                ?: UnifiedDevice(
                    macAddress = bssid,
                    name = wifiName,
                    rssi = rssi,
                    source = source,
                    deviceType = deviceType,
                    riskLevel = riskLevel,
                    isHotspot = isHotspot,
                    frequency = result.frequency,
                    firstSeen = now,
                    lastSeen = now
                )
            deviceMap[bssid] = device
        }

        _devices.value = deviceMap.values
            .sortedBy { it.proximityScore }
            .toList()
    }

    fun cleanupStaleDevices() {
        val now = System.currentTimeMillis()
        val stale = deviceMap.filter { now - it.value.lastSeen > STALE_TIMEOUT_MS }.keys
        if (stale.isNotEmpty()) {
            stale.forEach { deviceMap.remove(it) }
            _devices.value = deviceMap.values
                .sortedBy { it.proximityScore }
                .toList()
        }
    }

    fun clearDevices() {
        deviceMap.clear()
        _devices.value = emptyList()
    }

    fun getDeviceRssi(bssid: String): Int? = deviceMap[bssid.uppercase()]?.rssi

    fun getDevice(bssid: String): UnifiedDevice? = deviceMap[bssid.uppercase()]

    fun stopScanning() {
        scanActive.set(false)
        _isScanning.value = false
        _scanStatus.value = "Idle"
        scanLoopJob?.cancel()
        scanLoopJob = null
        unregisterReceiverIfNeeded()
    }

    @SuppressLint("MissingPermission")
    fun startScan(durationSeconds: Int = 15): Flow<AppScanResult> = callbackFlow {
        val manager = wifiManager ?: run {
            close()
            return@callbackFlow
        }
        if (!manager.isWifiEnabled) {
            Log.w(TAG, "WiFi not enabled")
            close()
            return@callbackFlow
        }

        val scanning = AtomicBoolean(true)
        val seen = ConcurrentHashMap.newKeySet<String>()

        val baselineReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) return
                if (!scanning.get()) return
                val results = try {
                    manager.scanResults ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
                results.forEach { r ->
                    val mac = r.BSSID?.uppercase() ?: return@forEach
                    if (!seen.add(mac)) return@forEach
                    val ssid = (r.SSID ?: "").trim()
                    trySend(
                        AppScanResult(
                            macAddress = mac,
                            deviceName = ssid,
                            rssi = r.level,
                            isBluetooth = false,
                            isWifi = true,
                            manufacturer = ManufacturerResolver.getManufacturer(mac),
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
            }
        }

        try {
            val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(baselineReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(baselineReceiver, filter)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Baseline receiver error", e)
            close()
            return@callbackFlow
        }

        try {
            manager.startScan()
        } catch (e: Exception) {
            Log.e(TAG, "Baseline startScan error", e)
            try {
                context.unregisterReceiver(baselineReceiver)
            } catch (e2: Exception) {
                // ignore
            }
            close()
            return@callbackFlow
        }

        val executor = Executors.newSingleThreadScheduledExecutor()
        executor.schedule({
            scanning.set(false)
            try {
                context.unregisterReceiver(baselineReceiver)
            } catch (e: Exception) {
                // ignore
            }
            close()
        }, durationSeconds.toLong(), TimeUnit.SECONDS)

        awaitClose {
            scanning.set(false)
            try {
                context.unregisterReceiver(baselineReceiver)
            } catch (e: Exception) {
                // ignore
            }
            executor.shutdownNow()
        }
    }

    private fun isMobileHotspot(ssid: String, bssid: String): Boolean {
        val ssidLower = ssid.lowercase()

        val hotspotKeywords = listOf(
            "androidap", "android_hotspot", "mywifi",
            "iphone", "galaxy", "redmi",
            "vivo", "oppo", "realme", "oneplus",
            "poco", "samsung", "moto",
            "pixel", "nothing", "iqoo", "infinix",
            "tecno", "hotspot", "phone", "mobile",
            "personal hotspot", "portable", "tethering", "mifi"
        )

        if (hotspotKeywords.any { ssidLower.contains(it) }) return true

        if (bssid.length >= 17) {
            val oui = bssid.replace(":", "").take(6).uppercase()
            val mobileOUIs = listOf(
                "F0C77F", "00265C", "D0176A", "8425DB", "34145F", "3413E8", "5C0A5B",
                "00259C", "D89B3B", "F0DBE2", "68967B", "F41BA1", "5CF7E6",
                "A0999B", "8C1D96", "0C1105", "50EC50", "68DFDD", "742344",
                "94E979", "38A28C", "68D247", "A81B18", "8CBFA6"
            )
            if (mobileOUIs.any { oui.startsWith(it) }) return true
        }

        return false
    }
}