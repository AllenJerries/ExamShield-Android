package com.examshield.scanner

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
        // Active-only eviction: any device whose last signal update is older
        // than 3s is a stale/offline presence and is purged from the live list.
        private const val STALE_DEVICE_TIMEOUT = 3000L
        private const val CLEANUP_INTERVAL = 3000L
        private const val CLASSIC_BONDED_REFRESH_MS = 3000L
        private const val CLASSIC_NOMINAL_RSSI = -90
    }

    private val bluetoothManager = context.getSystemService(
        Context.BLUETOOTH_SERVICE
    ) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var bleScanner: BluetoothLeScanner? = null

    private val isScanning = AtomicBoolean(false)
    private val deviceMap = ConcurrentHashMap<String, AppScanResult>()
    private var currentCallback: ScanCallback? = null

    // Per-MAC last emitted RSSI. Lets callbacks that repeat the same reading
    // (cached advertisements) refresh the internal map/timestamps without
    // rebuilding + re-emitting the whole StateFlow list on every single hit,
    // which is what keeps discovery zero-lag.
    private val lastEmittedRssi = ConcurrentHashMap<String, Int>()

    private val _discoveredDevices = MutableStateFlow<List<AppScanResult>>(emptyList())
    val discoveredDevices: StateFlow<List<AppScanResult>> = _discoveredDevices.asStateFlow()

    private val _scanErrors = MutableStateFlow<String?>(null)
    val scanErrors: StateFlow<String?> = _scanErrors.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var restartJob: Job? = null
    private var cleanupJob: Job? = null

    // Classic (BR/EDR) integration: paired/bonded devices plus classic
    // discovery are merged into the same device map so connected TWS earbuds,
    // AirPods, smartwatches and other paired devices surface even when they are
    // not broadcasting BLE advertisements.
    private val classicMacs = ConcurrentHashMap.newKeySet<String>()
    private var classicReceiver: BroadcastReceiver? = null
    private var bondedRefreshJob: Job? = null

    // MACs with a live RF link right now, fed by ACTION_ACL_CONNECTED /
    // ACTION_ACL_DISCONNECTED broadcasts. This is what refreshBondedDevices
    // uses for "device.isConnected": a bonded profile that is actually linked.
    private val activeClassicMacs = ConcurrentHashMap.newKeySet<String>()

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

    fun isClassicDevice(mac: String): Boolean = classicMacs.contains(mac.uppercase())

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
            startClassicBluetoothIntegration()
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
                .ifBlank { "Hidden BLE Device (${mac.takeLast(5)})" }
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
                lastEmittedRssi[mac] = rssi
                emitDevices()
            } else {
                // Always keep the map + timestamp fresh for stale-eviction;
                // only rebuild/emit the list when the RSSI actually changed.
                deviceMap[mac] = scanResult
                if (lastEmittedRssi[mac] != rssi) {
                    lastEmittedRssi[mac] = rssi
                    emitDevices()
                }
            }
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
        // Paired/connected classic devices (TWS earbuds, AirPods, smartwatches
        // and other bonded profiles) are NEVER evicted as stale — they stay in
        // memory for the whole hunt so a brief advertisement gap never drops a
        // live threat off the board.
        val staleKeys = deviceMap.filter {
            val mac = it.key
            mac in classicMacs || mac in activeClassicMacs ||
                now - it.value.timestamp <= STALE_DEVICE_TIMEOUT
        }.keys
        if (staleKeys.isNotEmpty()) {
            staleKeys.forEach {
                deviceMap.remove(it)
                lastEmittedRssi.remove(it)
            }
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

        stopClassicBluetoothIntegration()

        val duration = System.currentTimeMillis() - scanStartTime.get()
        Log.d(TAG, "Scan stats: ${duration / 1000}s | ${totalScansPerformed.get()} scans | ${totalDevicesFound.get()} devices | ${deviceMap.size} tracked")
    }

    fun clearDevices() {
        deviceMap.clear()
        lastEmittedRssi.clear()
        classicMacs.clear()
        activeClassicMacs.clear()
        emitDevices()
    }

    @SuppressLint("MissingPermission")
    private fun startClassicBluetoothIntegration() {
        safeExecute("Register classic receiver") {
            classicReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    when (intent.action) {
                        BluetoothDevice.ACTION_FOUND -> {
                            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(
                                    BluetoothDevice.EXTRA_DEVICE,
                                    BluetoothDevice::class.java
                                )
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                            }
                            if (device != null) {
                                val rssi = intent.getIntExtra(
                                    BluetoothDevice.EXTRA_RSSI,
                                    CLASSIC_NOMINAL_RSSI
                                )
                                upsertClassicDevice(device, rssi)
                            }
                        }

                        BluetoothDevice.ACTION_ACL_CONNECTED -> {
                            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(
                                    BluetoothDevice.EXTRA_DEVICE,
                                    BluetoothDevice::class.java
                                )
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                            }
                            if (device != null) {
                                val mac = device.address?.uppercase() ?: return@onReceive
                                activeClassicMacs.add(mac)
                                upsertClassicDevice(device, CLASSIC_NOMINAL_RSSI)
                            }
                        }

                        BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(
                                    BluetoothDevice.EXTRA_DEVICE,
                                    BluetoothDevice::class.java
                                )
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                            }
                            if (device != null) {
                                val mac = device.address?.uppercase() ?: return@onReceive
                                activeClassicMacs.remove(mac)
                                val existing = deviceMap[mac]
                                val fresh = existing != null &&
                                    System.currentTimeMillis() - existing.timestamp <= STALE_DEVICE_TIMEOUT
                                if (!fresh) {
                                    deviceMap.remove(mac)
                                    lastEmittedRssi.remove(mac)
                                    emitDevices()
                                }
                            }
                        }

                        BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                            if (isScanning.get()) {
                                handler.postDelayed({ restartClassicDiscovery() }, 1000)
                            }
                        }
                    }
                }
            }
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(classicReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(classicReceiver, filter)
            }
        }

        restartClassicDiscovery()

        bondedRefreshJob = scope.launch {
            while (isScanning.get()) {
                refreshBondedDevices()
                delay(CLASSIC_BONDED_REFRESH_MS)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopClassicBluetoothIntegration() {
        bondedRefreshJob?.cancel()
        bondedRefreshJob = null
        safeExecute("Unregister classic receiver") {
            classicReceiver?.let { context.unregisterReceiver(it) }
            classicReceiver = null
        }
        safeExecute("Cancel classic discovery") {
            bluetoothAdapter?.cancelDiscovery()
        }
    }

    @SuppressLint("MissingPermission")
    private fun restartClassicDiscovery() {
        safeExecute("Restart classic discovery") {
            bluetoothAdapter?.cancelDiscovery()
            bluetoothAdapter?.startDiscovery()
        }
    }

    @SuppressLint("MissingPermission")
    private fun refreshBondedDevices() {
        try {
            val now = System.currentTimeMillis()
            bluetoothAdapter?.bondedDevices?.forEach { device ->
                val mac = device.address?.uppercase() ?: return@forEach

                val existing = deviceMap[mac]
                val existingFresh = existing != null &&
                    now - existing.timestamp <= STALE_DEVICE_TIMEOUT

                val isConnected = mac in activeClassicMacs

                if (isConnected || existingFresh) {
                    // Active presence: either the radio is connected right now
                    // or a live BLE/Classic scan result refreshed this MAC
                    // within the 3s window. Keep it in the live list.
                    upsertClassicDevice(device, CLASSIC_NOMINAL_RSSI)
                } else {
                    // Offline previously-paired device with no fresh scan or
                    // live connection: purge it from the active list.
                    if (deviceMap.remove(mac) != null || classicMacs.remove(mac)) {
                        lastEmittedRssi.remove(mac)
                    }
                }
            }
            emitDevices()
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot read bonded devices: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun upsertClassicDevice(device: BluetoothDevice, rssi: Int) {
        try {
            val mac = device.address?.uppercase() ?: return
            val deviceName = try {
                device.name
            } catch (e: SecurityException) {
                null
            } ?: ""
            val displayName = deviceName.ifBlank {
                "Bluetooth Device (${mac.takeLast(5)})"
            }

            classicMacs.add(mac)

            val existing = deviceMap[mac]
            val existingFresh = existing != null &&
                System.currentTimeMillis() - existing.timestamp <= STALE_DEVICE_TIMEOUT
            val hasNominalOnly = rssi == CLASSIC_NOMINAL_RSSI
            val finalRssi = when {
                existingFresh && (hasNominalOnly || rssi == 0) -> existing!!.rssi
                rssi != 0 && rssi in -100..0 -> rssi
                existingFresh -> existing!!.rssi
                else -> CLASSIC_NOMINAL_RSSI
            }

            deviceMap[mac] = AppScanResult(
                macAddress = mac,
                deviceName = displayName,
                rssi = finalRssi,
                isBluetooth = true,
                manufacturer = ManufacturerResolver.getManufacturer(mac),
                scanRecord = existing?.scanRecord,
                timestamp = System.currentTimeMillis()
            )
            if (lastEmittedRssi[mac] != finalRssi) {
                lastEmittedRssi[mac] = finalRssi
                emitDevices()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Upsert classic device error", e)
        }
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
                    val name = (result.scanRecord?.deviceName
                        ?: device.name ?: "")
                        .ifBlank { "Hidden BLE Device (${mac.takeLast(5)})" }
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
                                deviceName = (result.device.name
                                    ?: result.scanRecord?.deviceName ?: "")
                                    .ifBlank {
                                        "Hidden BLE Device (${result.device.address.takeLast(5)})"
                                    },
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
        stopClassicBluetoothIntegration()
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
