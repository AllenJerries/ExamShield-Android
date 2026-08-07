package com.examshield.scanner

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CellularInfo(
    val networkType: String,
    val signalStrength: Int,
    val cellId: Long,
    val timestamp: Long
)

class CellularScanner(private val context: Context) {

    private val TAG = "CellularScanner"

    private val telephonyManager = context.getSystemService(
        Context.TELEPHONY_SERVICE
    ) as? TelephonyManager

    private val _cellInfo = MutableStateFlow<List<CellularInfo>>(emptyList())
    val cellInfo: StateFlow<List<CellularInfo>> = _cellInfo.asStateFlow()

    private val _isMobileDataActive = MutableStateFlow(false)
    val isMobileDataActive: StateFlow<Boolean> = _isMobileDataActive.asStateFlow()

    private val _nearbyPhonesEstimate = MutableStateFlow(0)
    val nearbyPhonesEstimate: StateFlow<Int> = _nearbyPhonesEstimate.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var scanJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @SuppressLint("MissingPermission")
    fun startScanning() {
        Log.d(TAG, "Starting cellular scan")

        if (!hasPermission()) {
            Log.e(TAG, "No phone state permission")
            return
        }

        _isScanning.value = true

        scanJob = scope.launch {
            while (isActive) {
                try {
                    scanCellularNetworks()
                    checkMobileDataStatus()
                    delay(5000)
                } catch (e: Exception) {
                    Log.e(TAG, "Scan error", e)
                    delay(10000)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun scanCellularNetworks() {
        try {
            val cells = telephonyManager?.allCellInfo ?: return

            val infoList = mutableListOf<CellularInfo>()

            for (cell in cells) {
                when (cell) {
                    is CellInfoNr -> {
                        val signal = cell.cellSignalStrength
                        infoList.add(CellularInfo(
                            networkType = "5G",
                            signalStrength = signal.dbm,
                            cellId = 0L,
                            timestamp = System.currentTimeMillis()
                        ))
                    }
                    is CellInfoLte -> {
                        val signal = cell.cellSignalStrength
                        val identity = cell.cellIdentity
                        infoList.add(CellularInfo(
                            networkType = "4G LTE",
                            signalStrength = signal.dbm,
                            cellId = identity.ci.toLong(),
                            timestamp = System.currentTimeMillis()
                        ))
                    }
                    is CellInfoWcdma -> {
                        val signal = cell.cellSignalStrength
                        infoList.add(CellularInfo(
                            networkType = "3G",
                            signalStrength = signal.dbm,
                            cellId = 0L,
                            timestamp = System.currentTimeMillis()
                        ))
                    }
                    is CellInfoGsm -> {
                        val signal = cell.cellSignalStrength
                        infoList.add(CellularInfo(
                            networkType = "2G",
                            signalStrength = signal.dbm,
                            cellId = 0L,
                            timestamp = System.currentTimeMillis()
                        ))
                    }
                }
            }

            _cellInfo.value = infoList

            val uniqueSignals = infoList
                .filter { it.signalStrength > -100 }
                .distinctBy { it.cellId }
                .size
            _nearbyPhonesEstimate.value = uniqueSignals

            Log.d(TAG, "Detected ${infoList.size} cellular signals, ~$uniqueSignals nearby phones")
        } catch (e: SecurityException) {
            Log.e(TAG, "Security error", e)
        } catch (e: Exception) {
            Log.e(TAG, "Scan error", e)
        }
    }

    private fun checkMobileDataStatus() {
        try {
            val dataState = telephonyManager?.dataState
            _isMobileDataActive.value =
                dataState == TelephonyManager.DATA_CONNECTED
        } catch (e: Exception) {
            Log.e(TAG, "Data state error", e)
        }
    }

    fun stopScanning() {
        _isScanning.value = false
        scanJob?.cancel()
        scanJob = null
    }

    private fun hasPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }
}
