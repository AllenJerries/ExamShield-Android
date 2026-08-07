package com.examshield.scanner

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import com.examshield.data.models.ScanResult as AppScanResult
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

class BluetoothScanner(private val context: Context) {

    companion object {
        private const val TAG = "BluetoothScanner"
        private const val SCAN_RESTART_INTERVAL = 20000L
        private const val STALE_DEVICE_TIMEOUT = 10000L
        private const val CLEANUP_INTERVAL = 3000L
    }

    private val bluetoothManager = context.getSystemService(
        Context.BLUETOOTH_SERVICE
    ) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var bleScanner: BluetoothLeScanner? = null

    private val isScanning = AtomicBoolean(false)
    private val deviceMap = ConcurrentHashMap<String, AppScanResult>()
    private var currentCallback: ScanCallback? = null

    private val _discoveredDevices = MutableStateFlow<List<AppScanResult>>(emptyList())
    val discoveredDevices: StateFlow<List<AppScanResult>> = _discoveredDevices.asStateFlow()

    private val _scanErrors = MutableStateFlow<String?>(null)
    val scanErrors: StateFlow<String?> = _scanErrors.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var restartJob: Job? = null
    private var cleanupJob: Job? = null

    private val scanStartTime = AtomicLong(0)
    private val totalScansPerformed = AtomicLong(0)
    private val totalDevicesFound = AtomicLong(0)

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true
    fun isBluetoothAvailable(): Boolean = bluetoothAdapter != null

    fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun getDiscoveredDevices(): Map<String, AppScanResult> {
        return deviceMap.toMap()
    }

    private fun validateEnvironment(): Boolean {
        if (bluetoothAdapter == null) {
            Log.e(TAG, "No Bluetooth adapter")
            _scanErrors.value = "Bluetooth not supported"
            return false
        }
        if (!bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth disabled")
            _scanErrors.value = "Bluetooth is disabled"
            return false
        }
        bleScanner = bluetoothAdapter.bluetoothLeScanner
        if (bleScanner == null) {
            Log.e(TAG, "BLE scanner unavailable")
            _scanErrors.value = "BLE scanner unavailable"
            return false
        }
        if (!hasBluetoothPermission()) {
            Log.e(TAG, "No permission")
            _scanErrors.value = "Permission required"
            return false
        }
        return true
    }

    private fun createOptimalSettings(): ScanSettings {
        return ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setReportDelay(0)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)
                    setLegacy(false)
                }
            }
            .build()
    }

    @SuppressLint("MissingPermission")
    fun startAggressiveScan() {
        if (!isScanning.compareAndSet(false, true)) {
            Log.w(TAG, "Already scanning")
            return
        }
        if (!validateEnvironment()) {
            isScanning.set(false)
            return
        }

        Log.d(TAG, "Starting ultra-fast BLE scan")
        scanStartTime.set(System.currentTimeMillis())
        _scanErrors.value = null

        currentCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                safeProcessResult(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { safeProcessResult(it) }
            }

            override fun onScanFailed(errorCode: Int) {
                val error = when (errorCode) {
                    SCAN_FAILED_ALREADY_STARTED -> "Already started"
                    SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "Registration failed"
                    SCAN_FAILED_FEATURE_UNSUPPORTED -> "Feature unsupported"
                    SCAN_FAILED_INTERNAL_ERROR -> "Internal error"
                    5 -> "Out of resources"
                    6 -> "Scanning too frequently"
                    else -> "Unknown error $errorCode"
                }
                Log.e(TAG, "Scan failed: $error")
                _scanErrors.value = error
                handler.postDelayed({
                    if (isScanning.get()) restartScan()
                }, 3000)
            }
        }

        safeExecute("Start BLE scan") {
            bleScanner?.startScan(null, createOptimalSettings(), currentCallback)
            totalScansPerformed.incrementAndGet()
            Log.d(TAG, "BLE scan #${totalScansPerformed.get()} started")
            startRestartCycle()
            startCleanupCycle()
        }
    }

    @SuppressLint("MissingPermission")
    private fun safeProcessResult(result: ScanResult) {
        try {
            val device = result.device
            val mac = device.address?.uppercase() ?: return
            val rssi = result.rssi
            if (rssi == 0 || rssi < -100) return

            val scanRecord = result.scanRecord
            val name = extractDeviceName(device, scanRecord)
            val scanRecordBytes = scanRecord?.bytes

            val macMfr = ManufacturerResolver.getManufacturer(mac)
            val bleMfr = DeviceClassifier.getManufacturerFromScanRecord(scanRecordBytes)
            val effectiveMfr = if (macMfr != "Unknown") macMfr else bleMfr

            val scanResult = AppScanResult(
                macAddress = mac,
                deviceName = name,
                rssi = rssi,
                isBluetooth = true,
                manufacturer = effectiveMfr,
                scanRecord = scanRecordBytes
            )

            val isNew = deviceMap.putIfAbsent(mac, scanResult) == null
            if (isNew) {
                totalDevicesFound.incrementAndGet()
            } else {
                deviceMap[mac] = scanResult
            }
            emitDevices()
        } catch (e: Exception) {
            Log.e(TAG, "Process error", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun extractDeviceName(
        device: android.bluetooth.BluetoothDevice,
        scanRecord: android.bluetooth.le.ScanRecord?
    ): String {
        return try {
            device.name?.takeIf { it.isNotBlank() }
                ?: scanRecord?.deviceName?.takeIf { it.isNotBlank() }
                ?: ""
        } catch (e: SecurityException) {
            scanRecord?.deviceName ?: ""
        }
    }

    private fun emitDevices() {
        val list = deviceMap.values
            .sortedByDescending { it.rssi }
            .toList()
        _discoveredDevices.value = list
    }

    private fun startRestartCycle() {
        restartJob?.cancel()
        restartJob = scope.launch {
            while (isScanning.get()) {
                delay(SCAN_RESTART_INTERVAL)
                if (isScanning.get()) {
                    Log.d(TAG, "Auto-restart scan")
                    restartScan()
                }
            }
        }
    }

    private fun startCleanupCycle() {
        cleanupJob?.cancel()
        cleanupJob = scope.launch {
            while (isScanning.get()) {
                delay(CLEANUP_INTERVAL)
                cleanupStaleDevices()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun restartScan() {
        safeExecute("Restart scan") {
            currentCallback?.let { bleScanner?.stopScan(it) }
            Thread.sleep(50)
            bleScanner?.startScan(null, createOptimalSettings(), currentCallback)
            totalScansPerformed.incrementAndGet()
        }
    }

    private fun cleanupStaleDevices() {
        val now = System.currentTimeMillis()
        val staleKeys = deviceMap.filter {
            now - it.value.timestamp > STALE_DEVICE_TIMEOUT
        }.keys
        if (staleKeys.isNotEmpty()) {
            staleKeys.forEach { deviceMap.remove(it) }
            emitDevices()
            Log.d(TAG, "Cleaned ${staleKeys.size} stale devices")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopAggressiveScan() {
        if (!isScanning.compareAndSet(true, false)) return

        Log.d(TAG, "Stopping BLE aggressive scan")
        _scanErrors.value = null

        restartJob?.cancel()
        cleanupJob?.cancel()

        safeExecute("Stop scan") {
            currentCallback?.let { bleScanner?.stopScan(it) }
            currentCallback = null
        }

        val duration = System.currentTimeMillis() - scanStartTime.get()
        Log.d(TAG, "Scan stats: ${duration / 1000}s | ${totalScansPerformed.get()} scans | ${totalDevicesFound.get()} devices | ${deviceMap.size} tracked")
    }

    fun clearDevices() {
        deviceMap.clear()
        emitDevices()
    }

    @SuppressLint("MissingPermission")
    fun startScan(durationSeconds: Int = 20): Flow<AppScanResult> = callbackFlow {
        if (!isBluetoothEnabled()) {
            Log.w(TAG, "Bluetooth not enabled")
            close()
            return@callbackFlow
        }

        bleScanner = bluetoothAdapter?.bluetoothLeScanner
        val scanning = AtomicBoolean(true)
        Log.d(TAG, "Starting BLE scan for ${durationSeconds}s")

        val discoveredLocal = ConcurrentHashMap<String, AppScanResult>()

        val leScanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (!scanning.get()) return
                val device = result.device
                val mac = device.address?.uppercase() ?: return
                if (!discoveredLocal.containsKey(mac)) {
                    val name = result.scanRecord?.deviceName ?: device.name ?: ""
                    val rssi = result.rssi
                    val scanRecordBytes = result.scanRecord?.bytes
                    val macMfr = ManufacturerResolver.getManufacturer(mac)
                    val bleMfr = DeviceClassifier.getManufacturerFromScanRecord(scanRecordBytes)
                    val effectiveMfr = if (macMfr != "Unknown") macMfr else bleMfr
                    val scanResult = AppScanResult(
                        macAddress = mac,
                        deviceName = name,
                        rssi = rssi,
                        isBluetooth = true,
                        manufacturer = effectiveMfr,
                        scanRecord = scanRecordBytes
                    )
                    discoveredLocal[mac] = scanResult
                    trySend(scanResult)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed: $errorCode")
                close()
            }
        }

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        safeExecute("Start scan flow") {
            bleScanner?.startScan(null, scanSettings, leScanCallback)
        }

        val executor = Executors.newSingleThreadScheduledExecutor()
        executor.schedule({
            scanning.set(false)
            safeExecute("Stop scan flow") {
                bleScanner?.stopScan(leScanCallback)
            }
            close()
        }, durationSeconds.toLong(), TimeUnit.SECONDS)

        awaitClose {
            scanning.set(false)
            safeExecute("Close scan flow") {
                bleScanner?.stopScan(leScanCallback)
            }
            executor.shutdownNow()
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun scanForSpecificDevice(
        targetMac: String,
        timeoutMs: Long
    ): AppScanResult? {
        if (!isBluetoothEnabled()) return null
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return null

        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                var hasResumed = false

                val scanCallback = object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult) {
                        if (!hasResumed && result.device.address.equals(targetMac, true)) {
                            hasResumed = true
                            safeExecute("Stop specific scan") { scanner.stopScan(this) }
                            val scanResult = AppScanResult(
                                macAddress = result.device.address,
                                deviceName = result.device.name ?: result.scanRecord?.deviceName ?: "",
                                rssi = result.rssi,
                                isBluetooth = true,
                                manufacturer = ManufacturerResolver.getManufacturer(result.device.address),
                                scanRecord = result.scanRecord?.bytes
                            )
                            continuation.resume(scanResult)
                        }
                    }

                    override fun onScanFailed(errorCode: Int) {
                        if (!hasResumed) {
                            hasResumed = true
                            continuation.resume(null)
                        }
                    }
                }

                val filter = ScanFilter.Builder()
                    .setDeviceAddress(targetMac)
                    .build()

                val settings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                    .build()

                safeExecute("Start specific scan") {
                    scanner.startScan(listOf(filter), settings, scanCallback)
                }

                continuation.invokeOnCancellation {
                    hasResumed = true
                    safeExecute("Cancel specific scan") { scanner.stopScan(scanCallback) }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        isScanning.set(false)
        _scanErrors.value = null
        restartJob?.cancel()
        cleanupJob?.cancel()
        safeExecute("Stop scan") {
            currentCallback?.let { bleScanner?.stopScan(it) }
            currentCallback = null
        }
        safeExecute("Stop scan null") {
            bleScanner?.stopScan(null as ScanCallback?)
        }
    }

    private fun safeExecute(operation: String, block: () -> Unit) {
        try {
            block()
        } catch (e: SecurityException) {
            Log.e(TAG, "Security: $operation", e)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "State: $operation", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed: $operation", e)
        }
    }
}
