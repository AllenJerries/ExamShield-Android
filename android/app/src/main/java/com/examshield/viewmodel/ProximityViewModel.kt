package com.examshield.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.examshield.data.models.DeviceSource
import com.examshield.data.models.UnifiedDevice
import com.examshield.scanner.Direction
import com.examshield.scanner.DirectionDetector
import com.examshield.scanner.ScannerProvider
import com.examshield.scanner.UnifiedScanner
import com.examshield.utils.AlarmManager
import com.examshield.utils.DistanceCalculator
import com.examshield.utils.ProximityLevel
import com.examshield.utils.VibrationHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class ProximityViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ProximityViewModel"
        private const val STALE_TIMEOUT_MS = 3000L
        private const val POLL_INTERVAL_MS = 300L
    }

    private val _rssi = MutableStateFlow(-100)
    val rssi: StateFlow<Int> = _rssi.asStateFlow()

    private val _distance = MutableStateFlow(999.0)
    val distance: StateFlow<Double> = _distance.asStateFlow()

    private val _smoothedDistance = MutableStateFlow(999.0)
    val smoothedDistance: StateFlow<Double> = _smoothedDistance.asStateFlow()

    private val _proximityLevel = MutableStateFlow(ProximityLevel.SEARCHING)
    val proximityLevel: StateFlow<ProximityLevel> = _proximityLevel.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isFound = MutableStateFlow(false)
    val isFound: StateFlow<Boolean> = _isFound.asStateFlow()

    private val _lastUpdate = MutableStateFlow(0L)
    val lastUpdate: StateFlow<Long> = _lastUpdate.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _direction = MutableStateFlow(Direction.SEARCHING)
    val direction: StateFlow<Direction> = _direction.asStateFlow()

    private val _directionConfidence = MutableStateFlow(0)
    val directionConfidence: StateFlow<Int> = _directionConfidence.asStateFlow()

    private val _huntSource = MutableStateFlow(DeviceSource.BLUETOOTH)
    val huntSource: StateFlow<DeviceSource> = _huntSource.asStateFlow()

    private val _huntDevice = MutableStateFlow<UnifiedDevice?>(null)
    val huntDevice: StateFlow<UnifiedDevice?> = _huntDevice.asStateFlow()

    private val _accuracy = MutableStateFlow(0.0)
    val accuracy: StateFlow<Double> = _accuracy.asStateFlow()

    private val distanceSmoother = DistanceCalculator.DistanceSmoother()
    private val rssiSmoother = DistanceCalculator.RSSISmoother(bufferSize = 5)

    private val directionDetector = DirectionDetector()
    private val vibrationHelper = VibrationHelper(getApplication())

    private var unifiedScanner: UnifiedScanner? = ScannerProvider.get(getApplication())

    private var targetMac: String = ""
    private var isWifiTarget = false
    private var huntJob: Job? = null
    private var beepJob: Job? = null

    private val isHunting = AtomicBoolean(false)
    private val lastRssiUpdate = AtomicLong(0)

    fun setScanner(scanner: UnifiedScanner) {
        unifiedScanner = scanner
    }

    fun startHunting(macAddress: String, source: DeviceSource) {
        Log.d(TAG, "START HUNT: $macAddress | $source")

        if (!isHunting.compareAndSet(false, true)) {
            Log.w(TAG, "Already hunting")
            return
        }

        AlarmManager.stopAll()

        val scanner = unifiedScanner
        if (scanner == null) {
            _errorMessage.value = "Scanner not available. Close and reopen the hunt screen."
            isHunting.set(false)
            return
        }

        _huntSource.value = source
        targetMac = macAddress.uppercase()
        isWifiTarget = source == DeviceSource.WIFI_HOTSPOT || source == DeviceSource.WIFI_NETWORK
        _huntDevice.value = null

        resetHuntState()

        ensureScannerRunning(scanner)

        startHuntLoop()
        startBeepUpdater()
    }

    fun startHunting(target: UnifiedDevice) {
        Log.d(TAG, "Start hunting: ${target.name} | ${target.macAddress} | ${target.source}")

        if (!isHunting.compareAndSet(false, true)) {
            Log.w(TAG, "Already hunting")
            return
        }

        AlarmManager.stopAll()

        val scanner = unifiedScanner
        if (scanner == null) {
            _errorMessage.value = "Scanner not available. Close and reopen the hunt screen."
            isHunting.set(false)
            return
        }

        _huntDevice.value = target
        _huntSource.value = target.source
        targetMac = target.macAddress.uppercase()
        isWifiTarget = target.source == DeviceSource.WIFI_HOTSPOT || target.source == DeviceSource.WIFI_NETWORK

        resetHuntState()

        ensureScannerRunning(scanner)

        startHuntLoop()
        startBeepUpdater()
    }

    private fun resetHuntState() {
        _isScanning.value = true
        _isFound.value = false
        _errorMessage.value = null
        _rssi.value = -100
        _distance.value = 999.0
        _smoothedDistance.value = 999.0
        _proximityLevel.value = ProximityLevel.SEARCHING
        _accuracy.value = 0.0
        _direction.value = Direction.SEARCHING
        _directionConfidence.value = 0

        distanceSmoother.reset()
        rssiSmoother.reset()
        directionDetector.stop()
        lastRssiUpdate.set(0L)
    }

    private fun ensureScannerRunning(scanner: UnifiedScanner) {
        if (scanner.isScanning.value != true) {
            Log.d(TAG, "Starting shared unified scan for hunt")
            try {
                scanner.startScanning()
            } catch (e: Exception) {
                Log.e(TAG, "Scanner start error", e)
            }
        }
    }

    private fun startHuntLoop() {
        huntJob?.cancel()
        huntJob = viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Hunt loop started for $targetMac (wifi=$isWifiTarget)")
            while (isActive && isHunting.get()) {
                val liveRssi = unifiedScanner?.getLiveRssi(targetMac, isWifiTarget)
                if (liveRssi != null) {
                    processReading(liveRssi)
                } else {
                    val now = System.currentTimeMillis()
                    val last = lastRssiUpdate.get()
                    if (last > 0 && now - last > STALE_TIMEOUT_MS) {
                        viewModelScope.launch(Dispatchers.Main) {
                            _rssi.value = -100
                            _distance.value = 999.0
                            _smoothedDistance.value = 999.0
                            _proximityLevel.value = ProximityLevel.OUT_OF_RANGE
                            _isFound.value = false
                            _accuracy.value = 0.0
                        }
                    }
                }
                delay(POLL_INTERVAL_MS)
            }
            Log.d(TAG, "Hunt loop stopped")
        }
    }

    private fun processReading(rawRssi: Int) {
        val smoothedRssi = rssiSmoother.addReading(rawRssi)
        val rawDistance = DistanceCalculator.calculateDistance(smoothedRssi, isWifi = isWifiTarget)
        val smoothed = distanceSmoother.update(rawDistance)

        lastRssiUpdate.set(System.currentTimeMillis())

        directionDetector.addSignalReading(smoothedRssi)

        viewModelScope.launch(Dispatchers.Main) {
            _rssi.value = smoothedRssi
            _distance.value = rawDistance
            _smoothedDistance.value = smoothed
            _proximityLevel.value = getProximityLevel(smoothed)
            _lastUpdate.value = System.currentTimeMillis()
            _accuracy.value = DistanceCalculator.getAccuracyEstimate(smoothedRssi)
            _isFound.value = smoothed < 0.5
            _direction.value = directionDetector.directionToDevice
            _directionConfidence.value = directionDetector.confidence

            Log.d(TAG, "RSSI: $smoothedRssi | Distance: ${String.format("%.2f", smoothed)}m")
        }
    }

    private fun startBeepUpdater() {
        beepJob?.cancel()
        beepJob = viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Beep updater started")

            while (isActive && isHunting.get()) {
                try {
                    val currentDist = _smoothedDistance.value

                    if (currentDist < 999) {
                        Log.d(TAG, "Playing beep at ${currentDist}m")

                        AlarmManager.playProximityBeep(
                            context = getApplication(),
                            distance = currentDist
                        )

                        VibrationHelper.triggerHuntPulse(
                            getApplication(),
                            duration = when {
                                currentDist < 0.5 -> 300L
                                currentDist < 1.0 -> 200L
                                currentDist < 3.0 -> 150L
                                else -> 100L
                            }
                        )
                    }

                    val waitTime = when {
                        currentDist < 0.5 -> 250L
                        currentDist < 1.0 -> 400L
                        currentDist < 2.0 -> 600L
                        currentDist < 5.0 -> 900L
                        currentDist < 10.0 -> 1300L
                        else -> 2000L
                    }

                    delay(waitTime)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Beep updater error", e)
                    delay(1000)
                }
            }

            Log.d(TAG, "Beep updater stopped")
        }
    }

    private fun getProximityLevel(distance: Double): ProximityLevel {
        return when {
            distance < 0.3 -> ProximityLevel.FOUND
            distance < 1.0 -> ProximityLevel.VERY_CLOSE
            distance < 2.5 -> ProximityLevel.CLOSE
            distance < 5.0 -> ProximityLevel.MEDIUM
            distance < 10.0 -> ProximityLevel.FAR
            distance < 30.0 -> ProximityLevel.VERY_FAR
            else -> ProximityLevel.OUT_OF_RANGE
        }
    }

    fun stopHunting() {
        Log.d(TAG, "Stop hunting")
        isHunting.set(false)
        _isScanning.value = false
        _huntDevice.value = null

        huntJob?.cancel()
        beepJob?.cancel()

        AlarmManager.stopAll()
        directionDetector.stop()
    }

    fun markAsFound() {
        stopHunting()
    }

    override fun onCleared() {
        stopHunting()
        AlarmManager.stopAll()
        vibrationHelper.stop()
        super.onCleared()
    }
}