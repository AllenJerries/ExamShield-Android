package com.examshield.scanner

import android.content.Context
import com.examshield.data.models.Device
import com.examshield.data.models.ScanResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.abs

class ProximityHunter(private val context: Context) {

    private val bluetoothScanner = BluetoothScanner(context)
    private val wifiScanner = WifiScanner(context)

    private var huntJob: Job? = null
    private var _isHunting = MutableStateFlow(false)
    val isHunting: StateFlow<Boolean> = _isHunting.asStateFlow()

    private var _currentRssi = MutableStateFlow(0)
    val currentRssi: StateFlow<Int> = _currentRssi.asStateFlow()

    private var _rssiHistory = MutableStateFlow<List<Int>>(emptyList())
    val rssiHistory: StateFlow<List<Int>> = _rssiHistory.asStateFlow()

    private val scanResults = MutableStateFlow<Map<String, ScanResult>>(emptyMap())

    fun startHunt(targetMac: String, scope: CoroutineScope) {
        _isHunting.value = true
        _rssiHistory.value = emptyList()

        huntJob = scope.launch(Dispatchers.Default) {
            while (isActive && _isHunting.value) {
                try {
                    val allResults = mutableListOf<ScanResult>()

                    if (bluetoothScanner.isBluetoothEnabled()) {
                        bluetoothScanner.startScan(5)
                            .take(500)
                            .toList()
                            .let { allResults.addAll(it) }
                    }

                    val targetResult = allResults.find {
                        it.macAddress.equals(targetMac, ignoreCase = true)
                    }

                    if (targetResult != null) {
                        _currentRssi.value = targetResult.rssi
                        val history = _rssiHistory.value.toMutableList()
                        history.add(targetResult.rssi)
                        if (history.size > 50) history.removeFirst()
                        _rssiHistory.value = history
                    }

                    delay(500)
                } catch (_: Exception) {
                    delay(1000)
                }
            }
        }
    }

    fun stopHunt() {
        _isHunting.value = false
        huntJob?.cancel()
        bluetoothScanner.stopScan()
    }

    fun markFound(): String {
        val rssi = _currentRssi.value
        return "Device found at RSSI: $rssi dBm"
    }

    fun markFalseAlarm(): String {
        return "False alarm - device not found"
    }

    companion object {
        fun getBeepInterval(rssi: Int): Long {
            return when {
                rssi < -85 -> 2000L
                rssi < -75 -> 1500L
                rssi < -65 -> 1000L
                rssi < -55 -> 600L
                rssi < -45 -> 300L
                rssi < -35 -> 100L
                else -> 0L
            }
        }

        fun getSignalBars(rssi: Int): Int {
            return when {
                rssi >= -30 -> 10
                rssi >= -40 -> 9
                rssi >= -50 -> 8
                rssi >= -55 -> 7
                rssi >= -60 -> 6
                rssi >= -65 -> 5
                rssi >= -70 -> 4
                rssi >= -75 -> 3
                rssi >= -80 -> 2
                rssi >= -85 -> 1
                else -> 0
            }
        }

        fun getStatusText(rssi: Int): String {
            return when {
                rssi >= -35 -> "DEVICE FOUND!"
                rssi >= -55 -> "VERY CLOSE"
                rssi >= -75 -> "GETTING CLOSER"
                rssi > -85 -> "FAR AWAY"
                else -> "NO SIGNAL"
            }
        }

        fun getStatusColor(rssi: Int): Long {
            return when {
                rssi >= -35 -> 0xFF00E676
                rssi >= -45 -> 0xFF76FF03
                rssi >= -55 -> 0xFFFFEB3B
                rssi >= -65 -> 0xFFFF9800
                rssi >= -75 -> 0xFFFF5722
                else -> 0xFFF44336
            }
        }

        fun isDeviceFound(rssi: Int): Boolean {
            return rssi >= -35
        }
    }
}
