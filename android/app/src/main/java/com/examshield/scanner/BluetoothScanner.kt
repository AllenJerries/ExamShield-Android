package com.examshield.scanner

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import com.examshield.data.models.DeviceSource
import com.examshield.data.models.UnifiedDevice
import com.examshield.data.models.ScanResult as AppScanResult
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class BluetoothScanner(private val context: Context) {
    private val TAG = "BluetoothScanner"

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bleScanner = bluetoothManager?.adapter?.bluetoothLeScanner

    private val discoveredDevices = ConcurrentHashMap<String, UnifiedDevice>()
    private val _devices = MutableStateFlow<List<UnifiedDevice>>(emptyList())
    val devices: StateFlow<List<UnifiedDevice>> = _devices

    private var scanCallback: ScanCallback? = null
    var isScanning = false
        private set

    fun isBluetoothEnabled(): Boolean = bluetoothManager?.adapter?.isEnabled == true

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

    @SuppressLint("MissingPermission")
    fun startScanning() {
        if (isScanning || bleScanner == null) return

        Log.d(TAG, "Starting FAST BLE Scan")

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                processResult(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { processResult(it) }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "BLE Scan Failed: $errorCode")
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // FASTEST MODE
            .setReportDelay(0) // INSTANT RESULTS
            .build()

        try {
            bleScanner.startScan(null, settings, scanCallback)
            isScanning = true
        } catch (e: Exception) {
            Log.e(TAG, "Scan start error", e)
        }
    }

    private fun processResult(result: ScanResult) {
        val mac = result.device.address?.uppercase() ?: return
        val rssi = result.rssi
        if (rssi == 0 || rssi < -100) return

        val name = try {
            result.device.name ?: result.scanRecord?.deviceName ?: ""
        } catch (e: Exception) {
            ""
        }.trim().ifEmpty { "Unknown Device" }

        val classification = DeviceClassifier.classifyDeviceStrict(
            deviceName = name,
            macAddress = mac,
            rssi = rssi,
            scanRecord = result.scanRecord?.bytes,
            isFromWifi = false
        )
        if (!classification.shouldShow) return

        val now = System.currentTimeMillis()
        val existing = discoveredDevices[mac]
        val device = existing?.copy(
            rssi = rssi,
            lastSeen = now
        ) ?: UnifiedDevice(
            macAddress = mac,
            name = name,
            rssi = rssi,
            source = DeviceSource.BLUETOOTH,
            deviceType = classification.deviceType,
            riskLevel = classification.riskLevel,
            firstSeen = now,
            lastSeen = now
        )

        discoveredDevices[mac] = device
        emitDevices()
    }

    private fun emitDevices() {
        _devices.value = discoveredDevices.values
            .sortedBy { it.proximityScore }
            .toList()
    }

    fun cleanupStaleDevices() {
        val now = System.currentTimeMillis()
        val stale = discoveredDevices.filter { now - it.value.lastSeen > 15000 }.keys
        if (stale.isNotEmpty()) {
            stale.forEach { discoveredDevices.remove(it) }
            emitDevices()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        isScanning = false
        try {
            scanCallback?.let { bleScanner?.stopScan(it) }
            scanCallback = null
        } catch (e: Exception) {
        }
    }

    fun clearDevices() {
        discoveredDevices.clear()
        emitDevices()
    }

    @SuppressLint("MissingPermission")
    fun startScan(durationSeconds: Int = 20): Flow<AppScanResult> = callbackFlow {
        if (!isBluetoothEnabled()) {
            Log.w(TAG, "Bluetooth not enabled")
            close()
            return@callbackFlow
        }
        val scanner = bleScanner ?: run {
            close()
            return@callbackFlow
        }

        val scanning = AtomicBoolean(true)
        val discoveredLocal = ConcurrentHashMap<String, AppScanResult>()

        val leScanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (!scanning.get()) return
                val mac = result.device.address?.uppercase() ?: return
                if (!discoveredLocal.containsKey(mac)) {
                    val name = try {
                        result.device.name ?: result.scanRecord?.deviceName ?: ""
                    } catch (e: SecurityException) {
                        result.scanRecord?.deviceName ?: ""
                    }
                    val scanResult = AppScanResult(
                        macAddress = mac,
                        deviceName = name,
                        rssi = result.rssi,
                        isBluetooth = true,
                        manufacturer = ManufacturerResolver.getManufacturer(mac),
                        scanRecord = result.scanRecord?.bytes
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

        try {
            scanner.startScan(null, scanSettings, leScanCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Scan start error", e)
            close()
            return@callbackFlow
        }

        val executor = Executors.newSingleThreadScheduledExecutor()
        executor.schedule({
            scanning.set(false)
            try {
                scanner.stopScan(leScanCallback)
            } catch (e: Exception) {
            }
            close()
        }, durationSeconds.toLong(), TimeUnit.SECONDS)

        awaitClose {
            scanning.set(false)
            try {
                scanner.stopScan(leScanCallback)
            } catch (e: Exception) {
            }
            executor.shutdownNow()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        stopScanning()
        try {
            bleScanner?.stopScan(null as ScanCallback?)
        } catch (e: Exception) {
        }
    }
}