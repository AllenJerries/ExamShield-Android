package com.examshield.viewmodel

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.examshield.data.models.DeviceSource
import com.examshield.data.models.UnifiedDevice
import com.examshield.scanner.Direction
import com.examshield.scanner.DirectionDetector
import com.examshield.scanner.ScannerProvider
import com.examshield.utils.AlarmManager
import com.examshield.utils.BeepManager
import com.examshield.utils.BeepManager.BeepProfile
import com.examshield.utils.DistanceCalculator
import com.examshield.utils.PerMacRssiMedian
import com.examshield.utils.ProximityLevel
import com.examshield.utils.AdaptiveRssiSmoother
import com.examshield.utils.SettingsRepository
import com.examshield.utils.VibrationHelper
import com.examshield.utils.calculateDistanceFromRssi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class ProximityViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ProximityViewModel"
        private const val STALE_TIMEOUT_MS = 3000L

        // Live-RSSI poll cadences
        // 80ms keeps distance/audio feedback <=100ms behind a raw RSSI change.
        private const val POLL_INTERVAL_MS = 80L
        private const val WIFI_SCAN_THROTTLE_MS = 15_000L

        // Skip double-feeding the same raw reading into the smoothers
        // when the direct scan callback and the fallback poll observe the
        // exact same value within the same window.
        private const val DEDUPE_WINDOW_MS = 80L
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

    private val _directionConfidence = MutableStateFlow(0f)
    val directionConfidence: StateFlow<Float> = _directionConfidence.asStateFlow()

    // Dynamic audio feedback parameters (volume / pitch / interval) resolved
    // from the current smoothed RSSI and streamed to the hunter screen.
    private val _audioFeedback = MutableStateFlow<BeepProfile?>(null)
    val audioFeedback: StateFlow<BeepProfile?> = _audioFeedback.asStateFlow()

    // Current compass azimuth (deg) from the sensor-fusion direction detector.
    private val _azimuth = MutableStateFlow(0f)
    val azimuth: StateFlow<Float> = _azimuth.asStateFlow()

    private val _huntSource = MutableStateFlow(DeviceSource.BLUETOOTH)
    val huntSource: StateFlow<DeviceSource> = _huntSource.asStateFlow()

    private val _huntDevice = MutableStateFlow<UnifiedDevice?>(null)
    val huntDevice: StateFlow<UnifiedDevice?> = _huntDevice.asStateFlow()

    private val _accuracy = MutableStateFlow(0.0)
    val accuracy: StateFlow<Double> = _accuracy.asStateFlow()

    private val unifiedScanner = ScannerProvider.get(application)

    private val distanceSmoother = DistanceCalculator.DistanceSmoother()
    // Adaptive Moving Average: alpha 0.95 on >2dBm motion (instant response),
    // alpha 0.35 when static (strips multipath noise).
    private val rssiSmoother = AdaptiveRssiSmoother()

    // Per-MAC 5-sample median filter. Only the hunted MAC is ever fed into it,
    // so crosstalk from every other nearby device never reaches the direction
    // engine or the audio engine.
    private val perMacMedian = PerMacRssiMedian(windowSize = 5)

    private val directionDetector = DirectionDetector(application)

    private var targetMac: String = ""
    private var isWifiTarget = false
    private var scanCallback: ScanCallback? = null
    private var wifiHuntJob: Job? = null
    private var bleFallbackJob: Job? = null
    private var timeoutJob: Job? = null
    private var beepJob: Job? = null

    private val bluetoothManager = getApplication<Application>()
        .getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter = bluetoothManager?.adapter
    private val bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner

    private val wifiManager = getApplication<Application>()
        .applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val vibrationHelper = VibrationHelper(getApplication())
    private val settingsRepo = SettingsRepository(application)

    private val isHunting = AtomicBoolean(false)
    private val lastRssiUpdate = AtomicLong(0)
    private val lastDirectRssiTime = AtomicLong(0)
    private val lastWifiScanRequested = AtomicLong(0)

    // Change-guard against double-feeding the smoothers
    private val lastAppliedKey = AtomicLong(-1)
    private val lastAppliedTime = AtomicLong(0)

    fun startHunting(macAddress: String, source: DeviceSource) {
        Log.d(TAG, "START HUNT: $macAddress | $source")

        if (!isHunting.compareAndSet(false, true)) {
            Log.w(TAG, "Already hunting")
            return
        }

        AlarmManager.stopAll()

        _huntSource.value = source
        targetMac = macAddress.uppercase()
        isWifiTarget = source == DeviceSource.WIFI_HOTSPOT || source == DeviceSource.WIFI_NETWORK

        resetHuntState()
        perMacMedian.clear()

        distanceSmoother.reset()
        rssiSmoother.reset()
        directionDetector.start()
        lastAppliedTime.set(0)

        if (isWifiTarget) {
            startWiFiHunt()
        } else {
            startBluetoothHunt()
            startBleFallbackPoll()
        }

        startStaleMonitor()
        startBeepUpdater()
    }

    fun startHunting(target: UnifiedDevice) {
        Log.d(TAG, "Start hunting: ${target.name} | ${target.macAddress} | ${target.source}")

        if (!isHunting.compareAndSet(false, true)) {
            Log.w(TAG, "Already hunting")
            return
        }

        AlarmManager.stopAll()

        _huntDevice.value = target
        _huntSource.value = target.source
        targetMac = target.macAddress.uppercase()
        isWifiTarget = target.source == DeviceSource.WIFI_HOTSPOT || target.source == DeviceSource.WIFI_NETWORK

        resetHuntState()
        perMacMedian.clear()

        distanceSmoother.reset()
        rssiSmoother.reset()
        directionDetector.start()
        lastAppliedTime.set(0)

        if (isWifiTarget) {
            startWiFiHunt()
        } else {
            startBluetoothHunt()
            startBleFallbackPoll()
        }

        startStaleMonitor()
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
        _directionConfidence.value = 0f
        _audioFeedback.value = null
        _azimuth.value = 0f
    }

    @SuppressLint("MissingPermission")
    private fun startBluetoothHunt() {
        try {
            if (!hasBlePermission()) {
                _errorMessage.value = "BLE permission required"
                isHunting.set(false)
                return
            }

            scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    if (result.device.address.equals(targetMac, true)) {
                        lastDirectRssiTime.set(System.currentTimeMillis())
                        processReading(result.rssi, isWifi = false)
                    }
                }

                override fun onBatchScanResults(results: MutableList<ScanResult>) {
                    results.forEach {
                        if (it.device.address.equals(targetMac, true)) {
                            lastDirectRssiTime.set(System.currentTimeMillis())
                            processReading(it.rssi, isWifi = false)
                        }
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.e(TAG, "Scan failed: $errorCode")
                    _errorMessage.value = "Scan error: $errorCode"
                }
            }

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setReportDelay(0)
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
                .build()

            bluetoothLeScanner?.startScan(null, settings, scanCallback)
            Log.d(TAG, "BLE hunt started for $targetMac")
        } catch (e: SecurityException) {
            _errorMessage.value = "Permission denied"
            isHunting.set(false)
            Log.e(TAG, "Security", e)
        } catch (e: Exception) {
            _errorMessage.value = "Error: ${e.message}"
            isHunting.set(false)
            Log.e(TAG, "Error", e)
        }
    }

    /**
     * 300ms fallback poll against the shared UnifiedScanner's live maps.
     * Used when the direct LOW_LATENCY callback has gone quiet (device is
     * far / filtered out of the direct feed) but the aggregating scan still
     * sees the target. The change-guard in [processReading] prevents the
     * same raw reading from being double-fed into the smoothers.
     */
    private fun startBleFallbackPoll() {
        bleFallbackJob?.cancel()
        bleFallbackJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive && isHunting.get() && !isWifiTarget) {
                try {
                    val now = System.currentTimeMillis()
                    val freshDirect = now - lastDirectRssiTime.get() < 1000
                    if (!freshDirect && unifiedScanner.isScanning.value) {
                        unifiedScanner.getLiveRssi(targetMac, isWifi = false)
                            ?.takeIf { it != -100 || unifiedScanner.getDevice(targetMac) != null }
                            ?.let { processReading(it, isWifi = false) }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "BLE fallback poll error: ${e.message}")
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startWiFiHunt() {
        wifiHuntJob?.cancel()
        wifiHuntJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive && isHunting.get() && isWifiTarget) {
                try {
                    refreshWifiCacheIfNeeded()

                    val liveRssi = unifiedScanner.getLiveRssi(targetMac, isWifi = true)
                    if (liveRssi != null) {
                        processReading(liveRssi, isWifi = true)
                    } else {
                        // Fallback to the OS scan-results cache
                        wifiManager?.scanResults
                            ?.find { it.BSSID.equals(targetMac, true) }
                            ?.level
                            ?.takeIf { it != 0 }
                            ?.let { processReading(it, isWifi = true) }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "WiFi hunt error", e)
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun refreshWifiCacheIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastWifiScanRequested.get() >= WIFI_SCAN_THROTTLE_MS) {
            lastWifiScanRequested.set(now)
            try {
                wifiManager?.startScan()
            } catch (e: Exception) {
                Log.w(TAG, "wifi startScan error: ${e.message}")
            }
        }
    }

    fun processReading(rawRssi: Int, isWifi: Boolean = false) {
        if (rawRssi == 0) return

        // STRICT TARGET ISOLATION: only the hunted MAC's scan updates may ever
        // reach the direction engine, distance pipeline and audio engine. Every
        // caller upstream already matches the address against [targetMac]; this
        // guard makes the hunt airtight even if a future caller forgets.
        if (targetMac.isEmpty()) return

        // Change-guard: identical raw reading observed twice within the same
        // window (direct callback vs fallback poll) is treated as one.
        val now = System.currentTimeMillis()
        val fingerprint = (if (isWifi) 0L else 1L) * 1_000_000L + rawRssi
        if (fingerprint == lastAppliedKey.get() &&
            now - lastAppliedTime.get() < DEDUPE_WINDOW_MS
        ) {
            return
        }
        lastAppliedKey.set(fingerprint)
        lastAppliedTime.set(now)

        // Per-MAC 5-sample median strips interference noise spikes BEFORE any
        // smoothing / distance / direction / audio math (isolated per device).
        val medianRssi = perMacMedian.medianFor(targetMac, rawRssi)
        val smoothedRssi = rssiSmoother.addReading(medianRssi)
        val rawDistance = calculateDistanceFromRssi(
            smoothedRssi,
            source = _huntSource.value
        )
        val smoothed = distanceSmoother.update(rawDistance)

        lastRssiUpdate.set(System.currentTimeMillis())

        directionDetector.addSignalReading(smoothedRssi)

        // All RSSI / distance / proximity state is emitted through
        // Dispatchers.Main.immediate: on the main thread the write happens
        // synchronously in-place (zero hops), off-main threads it posts to the
        // main queue — every StateFlow consumer (gauge, arrow, audio engine,
        // haptics) receives the fresh values as fast as the frame that produced
        // them. Target RSSI, dynamic audio volume AND haptic pulse rate fire
        // together here (BeepManager.updateProximity is fully synchronous), so
        // the audio + haptic engine reacts in the same frame as the gauge.
        // Using the immediate dispatcher guarantees each high-frequency scan
        // frame lands as its own state emission — never conflated/coalesced
        // away by a default Main dispatcher queue while the scanner races ahead.
        viewModelScope.launch(Dispatchers.Main.immediate) {
            _rssi.value = smoothedRssi
            _distance.value = rawDistance
            _smoothedDistance.value = smoothed
            _proximityLevel.value = getProximityLevel(smoothed)
            _lastUpdate.value = System.currentTimeMillis()
            _accuracy.value = DistanceCalculator.getAccuracyEstimate(smoothedRssi)
            _isFound.value = smoothed < 0.5
            _direction.value = directionDetector.directionToDevice
            _directionConfidence.value = directionDetector.confidence

            // Synchronous haptic + volume scaling from the same frame's RSSI.
            BeepManager.updateProximity(smoothedRssi)
            _audioFeedback.value = BeepManager.getBeepProfile(smoothedRssi)

            _azimuth.value = directionDetector.currentAzimuth
        }

        Log.d(TAG, "RSSI: $smoothedRssi | Distance: ${String.format("%.2f", smoothed)}m")
    }

    private fun startBeepUpdater() {
        beepJob?.cancel()

        // Dynamic audio + haptic beeping — tier-scaled to the target's live smoothed
        // RSSI (100% very close / 65% medium / 15% far; beep cadence
        // 45/250/600 ms; heavy haptic pulses < 50 cm, light pulses every 250 ms
        // at medium, sound-only when far). Driven strictly by the TARGET's live
        // smoothed RSSI (median-filtered, per-MAC isolated) — non-target
        // devices are invisible to the engine.
        BeepManager.startProximityBeeping(
            context = getApplication(),
            getRssi = { _rssi.value },
            volume = settingsRepo.beepVolume
        )

        beepJob = viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Hunt pulse updater started")

            while (isActive && isHunting.get()) {
                try {
                    val currentDist = _smoothedDistance.value

                    if (currentDist < 999) {
                        val closeness = ((3.0 - currentDist) / 2.95)
                            .coerceIn(0.0, 1.0)
                        Log.d(TAG, "Hunt pulse at ${currentDist}m (closeness ${String.format("%.2f", closeness)})")

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

                    // Pulse rate strictly tied to the target's filtered distance:
                    // 500ms idle -> 40ms near-contact, mirroring the audio engine.
                    val waitTime = if (currentDist >= 999) {
                        2000L
                    } else {
                        val closeness = ((3.0 - currentDist) / 2.95)
                            .coerceIn(0.0, 1.0)
                        500L - (460L * closeness).toLong()
                    }

                    delay(waitTime)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Hunt pulse updater error", e)
                    delay(1000)
                }
            }

            Log.d(TAG, "Hunt pulse updater stopped")
        }
    }

    private fun startStaleMonitor() {
        timeoutJob?.cancel()
        timeoutJob = viewModelScope.launch {
            while (isActive && isHunting.get()) {
                delay(1000)
                val elapsed = System.currentTimeMillis() - lastRssiUpdate.get()
                if (elapsed > STALE_TIMEOUT_MS && lastRssiUpdate.get() > 0) {
                    _rssi.value = -100
                    _distance.value = 999.0
                    _smoothedDistance.value = 999.0
                    _proximityLevel.value = ProximityLevel.OUT_OF_RANGE
                    _isFound.value = false
                    _accuracy.value = 0.0
                    _audioFeedback.value = BeepManager.getBeepProfile(-100)
                    // Stale target: force far-tier audio + zero haptics.
                    BeepManager.updateProximity(-100)
                    _azimuth.value = 0f
                }
            }
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

    @SuppressLint("MissingPermission")
    fun stopHunting() {
        Log.d(TAG, "Stop hunting")
        isHunting.set(false)
        _isScanning.value = false
        _huntDevice.value = null

        wifiHuntJob?.cancel()
        bleFallbackJob?.cancel()
        timeoutJob?.cancel()
        beepJob?.cancel()

        BeepManager.stopBeeping()
        AlarmManager.stopAll()

        try {
            scanCallback?.let { bluetoothLeScanner?.stopScan(it) }
            scanCallback = null
        } catch (e: Exception) {
            Log.e(TAG, "Stop error", e)
        }

        directionDetector.stop()
        perMacMedian.removeMac(targetMac)
        targetMac = ""
    }

    fun markAsFound() {
        stopHunting()
    }

    private fun hasBlePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(
                getApplication(), Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(
                getApplication(), Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onCleared() {
        stopHunting()
        BeepManager.release()
        AlarmManager.stopAll()
        vibrationHelper.stop()
        super.onCleared()
    }
}