package com.examshield.scanner

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.net.wifi.ScanResult as WifiScanResult
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import com.examshield.data.models.ScanResult
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class WifiScanner(private val context: Context) {

    companion object {
        private const val TAG = "WifiScanner"
        private const val WIFI_SCAN_INTERVAL_MS = 30_000L
        private const val WIFI_STALE_TIMEOUT_MS = 120_000L
    }

    private val wifiManager = context.applicationContext.getSystemService(
        Context.WIFI_SERVICE
    ) as? WifiManager

    private val _discoveredDevices = MutableStateFlow<List<ScanResult>>(emptyList())
    val discoveredDevices: StateFlow<List<ScanResult>> = _discoveredDevices.asStateFlow()

    private val deviceMap = ConcurrentHashMap<String, ScanResult>()
    private var scanReceiver: BroadcastReceiver? = null
    private var scanJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isScanning = AtomicBoolean(false)

    private val scanAttempts = AtomicInteger(0)

    fun isWifiEnabled(): Boolean {
        return try {
            wifiManager?.isWifiEnabled == true
        } catch (e: Exception) {
            Log.w(TAG, "Cannot check WiFi state: ${e.message}")
            false
        }
    }

    fun hasPermissions(): Boolean {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        return perms.all {
            ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    fun startContinuousScanning() {
        if (!isScanning.compareAndSet(false, true)) return

        Log.d(TAG, "Starting continuous WiFi scanning")

        if (!hasPermissions()) {
            Log.e(TAG, "Missing permissions for WiFi scan")
            isScanning.set(false)
            return
        }

        if (wifiManager?.isWifiEnabled != true) {
            Log.e(TAG, "WiFi is disabled")
            isScanning.set(false)
            return
        }

        registerReceiver()

        scanJob = scope.launch {
            while (isActive && isScanning.get()) {
                var interval = WIFI_SCAN_INTERVAL_MS
                safeExecute("WiFi scan loop") {
                    val started = wifiManager?.startScan() ?: false
                    val attempt = scanAttempts.incrementAndGet()
                    Log.d(TAG, "WiFi scan attempt #$attempt: ${if (started) "OK" else "THROTTLED"}")

                    processScanResults()

                    interval = if (started) WIFI_SCAN_INTERVAL_MS else 45_000L
                }
                delay(interval)
            }
        }
    }

    private fun registerReceiver() {
        scanReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                    val updated = intent.getBooleanExtra(
                        WifiManager.EXTRA_RESULTS_UPDATED, false
                    )
                    if (updated) {
                        processScanResults()
                    }
                }
            }
        }

        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(scanReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(scanReceiver, filter)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Register receiver error: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun processScanResults() {
        safeExecute("Process scan results") {
            val results = wifiManager?.scanResults ?: return@safeExecute
            Log.d(TAG, "Processing ${results.size} WiFi scan results")

            for (result in results) {
                processSingleResult(result)
            }
            emitDevices()
        }
    }

    private fun processSingleResult(result: WifiScanResult) {
        try {
            val ssid = result.SSID?.trim()
            val displaySsid = ssid?.ifEmpty { "Hidden Network" } ?: "Hidden Network"
            val bssid = result.BSSID?.uppercase() ?: return
            if (bssid.isEmpty()) return

            val rssi = result.level
            if (rssi == 0) return

            val manufacturer = ManufacturerResolver.getManufacturer(bssid)

            val isHotspot = HotspotDetector.isMobileHotspot(
                ssid = displaySsid,
                bssid = bssid,
                frequency = result.frequency,
                capabilities = result.capabilities
            )
            Log.d(TAG, "Classified ${if (isHotspot) "HOTSPOT" else "WiFi"}: $displaySsid")

            val scanResult = ScanResult(
                macAddress = bssid,
                deviceName = displaySsid,
                rssi = rssi,
                isBluetooth = false,
                isWifi = true,
                manufacturer = manufacturer
            )

            deviceMap[bssid] = scanResult
        } catch (e: Exception) {
            Log.e(TAG, "Process single WiFi result error: ${e.message}")
        }
    }

    fun cleanupStaleDevices() {
        val now = System.currentTimeMillis()
        val stale = deviceMap.filter {
            now - it.value.timestamp > WIFI_STALE_TIMEOUT_MS
        }
        if (stale.isNotEmpty()) {
            stale.keys.forEach { deviceMap.remove(it) }
            emitDevices()
            Log.d(TAG, "Removed ${stale.size} stale WiFi devices")
        }
    }

    private fun emitDevices() {
        val list = deviceMap.values
            .sortedBy { it.rssi * -1 }
            .toList()
        _discoveredDevices.value = list
    }

    fun stopContinuousScanning() {
        Log.d(TAG, "Stopping continuous WiFi scan")
        isScanning.set(false)
        scanJob?.cancel()
        scanJob = null
        safeExecute("Unregister receiver") {
            scanReceiver?.let { context.unregisterReceiver(it) }
            scanReceiver = null
        }
    }

    fun getDiscoveredDevices(): Map<String, ScanResult> = deviceMap.toMap()

    fun clearDevices() {
        deviceMap.clear()
        _discoveredDevices.value = emptyList()
    }

    @SuppressLint("MissingPermission")
    fun startScan(durationSeconds: Int = 20): Flow<ScanResult> = callbackFlow {
        if (!isWifiEnabled()) {
            Log.w(TAG, "WiFi not enabled, skipping WiFi scan")
            close()
            return@callbackFlow
        }

        val discoveredNetworks = ConcurrentHashMap<String, ScanResult>()
        val scanning = AtomicBoolean(true)

        val scanReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                    safeExecute("WiFi scan flow receiver") {
                        val results = wifiManager?.scanResults
                        for (result in results ?: emptyList()) {
                            val ssid = result.SSID
                            val bssid = result.BSSID
                            val rssi = result.level
                            if (bssid.isNotEmpty() && !discoveredNetworks.containsKey(bssid)) {
                                val deviceName = if (ssid.isNotEmpty()) ssid else "Hidden Network"
                                val scanResult = ScanResult(
                                    macAddress = bssid,
                                    deviceName = deviceName,
                                    rssi = rssi,
                                    isBluetooth = false,
                                    isWifi = true,
                                    manufacturer = ManufacturerResolver.getManufacturer(bssid)
                                )
                                discoveredNetworks[bssid] = scanResult
                                trySend(scanResult)
                            }
                        }
                    }
                }
            }
        }

        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(scanReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(scanReceiver, filter)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cannot register WiFi scan receiver: ${e.message}")
            close()
            return@callbackFlow
        }

        var started = false
        safeExecute("Start WiFi scan flow") {
            @Suppress("DEPRECATION")
            started = wifiManager?.startScan() == true
        }

        if (!started) {
            Log.w(TAG, "WiFi startScan returned false (throttled)")
            safeExecute("Unregister throttled") { context.unregisterReceiver(scanReceiver) }
            close()
            return@callbackFlow
        }

        try {
            delay(durationSeconds * 1000L)
        } catch (_: Exception) {}

        scanning.set(false)
        safeExecute("Unregister wifi flow") { context.unregisterReceiver(scanReceiver) }
        Log.d(TAG, "WiFi scan completed. Found ${discoveredNetworks.size} networks")
        close()

        awaitClose {
            safeExecute("Unregister wifi flow close") { context.unregisterReceiver(scanReceiver) }
        }
    }

    @SuppressLint("MissingPermission")
    fun triggerSingleScan(): List<ScanResult> {
        if (!isWifiEnabled()) {
            Log.w(TAG, "WiFi not enabled, skipping single scan")
            return emptyList()
        }

        return safeExecuteWithDefault(emptyList()) {
            @Suppress("DEPRECATION")
            val started = wifiManager?.startScan()
            if (started != true) {
                Log.w(TAG, "WiFi single scan startScan returned false")
                return@safeExecuteWithDefault emptyList()
            }

            Thread.sleep(3000)

            val results = mutableListOf<ScanResult>()
            for (scanResult in wifiManager?.scanResults ?: emptyList()) {
                val ssid = scanResult.SSID
                val bssid = scanResult.BSSID
                val rssi = scanResult.level
                if (bssid.isNotEmpty()) {
                    results.add(
                        ScanResult(
                            macAddress = bssid,
                            deviceName = if (ssid.isNotEmpty()) ssid else "Hidden Network",
                            rssi = rssi,
                            isBluetooth = false,
                            isWifi = true,
                            manufacturer = ManufacturerResolver.getManufacturer(bssid)
                        )
                    )
                }
            }
            Log.d(TAG, "WiFi single scan: found ${results.size} networks")
            results
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

    private fun <T> safeExecuteWithDefault(default: T, block: () -> T): T {
        return try {
            block()
        } catch (e: Exception) {
            Log.w(TAG, "Failed with default: ${e.message}")
            default
        }
    }
}
