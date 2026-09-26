package com.examshield.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.examshield.data.local.AppDatabase
import com.examshield.data.models.*
import com.examshield.data.repository.DeviceRepository
import com.examshield.data.repository.ExamRepository
import com.examshield.data.repository.HistoryRepository
import com.examshield.scanner.BluetoothScanner
import com.examshield.scanner.CellularInfo
import com.examshield.scanner.CellularScanner
import com.examshield.scanner.DeviceClassifier
import com.examshield.scanner.HotspotDetector
import com.examshield.scanner.NetworkMonitor
import com.examshield.scanner.NetworkStatus
import com.examshield.scanner.ScanStatus
import com.examshield.scanner.ScannerProvider
import com.examshield.scanner.UnifiedScanner
import com.examshield.scanner.WifiScanner
import com.examshield.utils.AlarmManager
import com.examshield.utils.AlertUrgency
import com.examshield.utils.Constants
import com.examshield.utils.SettingsRepository
import com.examshield.utils.SoundManager
import com.examshield.utils.VibrationHelper
import com.examshield.utils.VibrationManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

enum class DeviceCategoryFilter { ALL, BLUETOOTH, WIFI, HOTSPOT }

class ScanViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ScanViewModel"

        // How long a threat that vanished from the RF stream stays retained. Kept at
        // 3s to mirror the scanner's live-eviction window so every displayed
        // device is a currently-present threat (100% live list, no ghosts).
        private const val ACTIVE_THREAT_RETENTION_MS = 3_000L

        private val PRIORITY_ORDER = mapOf(
            DeviceType.HIDDEN_EARPIECE to 5,
            DeviceType.TRACKING_BEACON to 5,
            DeviceType.MOBILE_HOTSPOT to 4,
            DeviceType.EARPHONE to 3,
            DeviceType.SMARTWATCH to 2,
            DeviceType.PHONE_IOS to 1,
            DeviceType.PHONE_ANDROID to 1,
        )
    }

    /**
     * Numeric risk priority: risk level dominates, then explicit type priority
     * (hidden earpieces / tracking beacons first), so CRITICAL hidden cheating
     * tools always outrank a far-but-loud unrelated device.
     */
    private fun riskPriority(device: UnifiedDevice): Int {
        val typePriority = PRIORITY_ORDER[device.deviceType] ?: 0
        val riskBonus = when (device.riskLevel) {
            RiskLevel.CRITICAL -> 1000
            RiskLevel.HIGH -> 600
            RiskLevel.MEDIUM -> 300
            RiskLevel.LOW -> 0
        }
        return riskBonus + typePriority * 10
    }

    /**
     * Returns true for devices classified as an active cheating threat
     * (CRITICAL / HIGH risk) — these always sort above low-risk noise.
     */
    private fun isHighRiskDevice(device: UnifiedDevice): Boolean {
        return device.riskLevel == RiskLevel.CRITICAL ||
            device.riskLevel == RiskLevel.HIGH
    }

    /**
     * Dynamic output ordering: risk priority FIRST, then ascending estimated
     * distance, then stronger RSSI — the closest, highest-priority cheating
     * device is always rendered at the very top of the list.
     */
    private fun sortForDisplay(devices: List<UnifiedDevice>): List<UnifiedDevice> {
        return devices.sortedWith(
            compareByDescending<UnifiedDevice> { riskPriority(it) }
                .thenBy { it.estimatedDistance }
                .thenByDescending { it.rssi }
        )
    }

    private val db = AppDatabase.getDatabase(application)
    private val deviceRepository = DeviceRepository(db)
    private val examRepository = ExamRepository(db)
    private val unifiedScanner = ScannerProvider.get(application)
    private val bluetoothScanner = BluetoothScanner(application)
    private val wifiScanner = WifiScanner(application)
    private val cellularScanner = CellularScanner(application)
    private val networkMonitor = NetworkMonitor(application)
    private val soundManager = SoundManager(application)
    private val vibrationManager = VibrationManager(application)
    private val vibrationHelper = VibrationHelper(application)
    private val prefs: SharedPreferences = application.getSharedPreferences(
        Constants.PREF_NAME, Context.MODE_PRIVATE
    )
    private val settingsRepo = SettingsRepository(application)
    private val historyRepository = HistoryRepository(application)

    val unifiedDevices: StateFlow<List<UnifiedDevice>> = unifiedScanner.devices
        .map { sortForDisplay(it) }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            sortForDisplay(unifiedScanner.devices.value)
        )
    val isUnifiedScanning: StateFlow<Boolean> = unifiedScanner.isScanning
    val scanStats: StateFlow<com.examshield.scanner.ScanStats> = unifiedScanner.scanStats
    val scanStatus: StateFlow<ScanStatus> = unifiedScanner.scanStatus
    val bleActive: StateFlow<Boolean> = unifiedScanner.bleActive
    val wifiActive: StateFlow<Boolean> = unifiedScanner.wifiActive

    val cellularInfo: StateFlow<List<CellularInfo>> = cellularScanner.cellInfo
    val isMobileDataActive: StateFlow<Boolean> = cellularScanner.isMobileDataActive
    val nearbyPhonesEstimate: StateFlow<Int> = cellularScanner.nearbyPhonesEstimate
    val cellularScanning: StateFlow<Boolean> = cellularScanner.isScanning
    val networkStatus: StateFlow<NetworkStatus> = networkMonitor.networkStatus

    private val _scannedDevices = MutableStateFlow<List<com.examshield.data.models.ScanResult>>(emptyList())
    val scannedDevices: StateFlow<List<com.examshield.data.models.ScanResult>> = _scannedDevices.asStateFlow()

    private val _isBaselineScanning = MutableStateFlow(false)
    val isBaselineScanning: StateFlow<Boolean> = _isBaselineScanning.asStateFlow()

    private val _baselineProgress = MutableStateFlow(0f)
    val baselineProgress: StateFlow<Float> = _baselineProgress.asStateFlow()

    private val _baselineTimeRemaining = MutableStateFlow(Constants.BASELINE_SCAN_DURATION)
    val baselineTimeRemaining: StateFlow<Int> = _baselineTimeRemaining.asStateFlow()

    private val _whitelistedCount = MutableStateFlow(0)
    val whitelistedCount: StateFlow<Int> = _whitelistedCount.asStateFlow()

    private val _isExamActive = MutableStateFlow(false)
    val isExamActive: StateFlow<Boolean> = _isExamActive.asStateFlow()

    private val _activeExam = MutableStateFlow<Exam?>(null)
    val activeExam: StateFlow<Exam?> = _activeExam.asStateFlow()

    private val _detectedDevices = MutableStateFlow<List<Device>>(emptyList())
    val detectedDevices: StateFlow<List<Device>> = _detectedDevices.asStateFlow()

    private val _sortedUnauthorizedDevices = MutableStateFlow<List<UnifiedDevice>>(emptyList())
    val sortedUnauthorizedDevices: StateFlow<List<UnifiedDevice>> = _sortedUnauthorizedDevices.asStateFlow()

    // In-memory retention of every active threat (BLE advertisements, paired /
    // connected classic Bluetooth, TWS earphones, AirPods, smartwatches and
    // Wi-Fi hotspots). Feeders only refresh the flow when the RF layer changes,
    // so this map keeps a live threat on the board even if a scanner emission
    // skips it for one poll cycle.
    private val _activeThreats = MutableStateFlow<List<UnifiedDevice>>(emptyList())
    val activeThreats: StateFlow<List<UnifiedDevice>> = _activeThreats.asStateFlow()
    private val activeThreatsMap = ConcurrentHashMap<String, UnifiedDevice>()

    private val _criticalAlertDevice = MutableStateFlow<UnifiedDevice?>(null)
    val criticalAlertDevice: StateFlow<UnifiedDevice?> = _criticalAlertDevice.asStateFlow()

    private val _newUnauthorizedDevice = MutableStateFlow<Device?>(null)
    val newUnauthorizedDevice: StateFlow<Device?> = _newUnauthorizedDevice.asStateFlow()

    private val _newUnauthorizedUnifiedDevice = MutableStateFlow<UnifiedDevice?>(null)
    val newUnauthorizedUnifiedDevice: StateFlow<UnifiedDevice?> = _newUnauthorizedUnifiedDevice.asStateFlow()

    private val _lastScanTime = MutableStateFlow(0L)
    val lastScanTime: StateFlow<Long> = _lastScanTime.asStateFlow()

    private val allDetectedDevices = ConcurrentHashMap<String, Device>()
    private val whitelistMacs = ConcurrentHashMap<String, Boolean>()

    private var baselineJob: Job? = null
    private var unifiedMonitorJob: Job? = null
    private var currentExamId = AtomicLong(-1L)
    private var historyExamId = AtomicLong(-1L)

    val currentHuntTarget: UnifiedDevice? get() = _currentHuntTarget.value
    private val _currentHuntTarget = MutableStateFlow<UnifiedDevice?>(null)

    val wifiScanStatus: StateFlow<String> = unifiedScanner.wifiScanner.scanStatus
    val wifiDeviceCount: StateFlow<Int> = run {
        val flow = MutableStateFlow(0)
        viewModelScope.launch {
            unifiedScanner.wifiScanner.wifiDevices.collect { flow.value = it.size }
        }
        flow.asStateFlow()
    }

    private val _deviceCategoryFilter = MutableStateFlow(DeviceCategoryFilter.ALL)
    val deviceCategoryFilter: StateFlow<DeviceCategoryFilter> = _deviceCategoryFilter.asStateFlow()

    fun setDeviceCategoryFilter(filter: DeviceCategoryFilter) {
        _deviceCategoryFilter.value = filter
    }

    fun matchesCategory(device: UnifiedDevice, filter: DeviceCategoryFilter): Boolean {
        return when (filter) {
            DeviceCategoryFilter.ALL -> true
            DeviceCategoryFilter.BLUETOOTH -> device.source == DeviceSource.BLUETOOTH
            DeviceCategoryFilter.WIFI -> device.source == DeviceSource.WIFI_NETWORK
            DeviceCategoryFilter.HOTSPOT -> device.source == DeviceSource.WIFI_HOTSPOT
        }
    }

    private val isInitialized = AtomicBoolean(false)

    init {
        soundManager.initialize()
        loadActiveExam()
    }

    private fun loadActiveExam() {
        viewModelScope.launch {
            examRepository.getActiveExam()?.let { exam ->
                _activeExam.value = exam
                _isExamActive.value = true
            }
        }
    }

    fun startBaselineScan(examId: Long) {
        _scannedDevices.value = emptyList()
        _isBaselineScanning.value = true
        _baselineProgress.value = 0f
        _baselineTimeRemaining.value = Constants.BASELINE_SCAN_DURATION

        baselineJob = viewModelScope.launch(Dispatchers.IO) {
            val allDevices = mutableListOf<com.examshield.data.models.ScanResult>()
            val seenMacs = mutableSetOf<String>()

            val duration = Constants.BASELINE_SCAN_DURATION

            launch {
                bluetoothScanner.startScan(duration).collect { result ->
                    if (result.macAddress !in seenMacs) {
                        seenMacs.add(result.macAddress)
                        allDevices.add(result)
                        _scannedDevices.value = allDevices.toList()
                    }
                }
            }

            launch {
                wifiScanner.startScan(duration).collect { result ->
                    if (result.macAddress !in seenMacs) {
                        seenMacs.add(result.macAddress)
                        allDevices.add(result)
                        _scannedDevices.value = allDevices.toList()
                    }
                }
            }

            for (i in 0..duration) {
                _baselineTimeRemaining.value = duration - i
                _baselineProgress.value = i.toFloat() / duration
                delay(1000)
            }

            val devices = allDevices.map { scanResult ->
                val (deviceType, risk) = DeviceClassifier.classifyDevice(
                    scanResult.deviceName,
                    scanResult.macAddress,
                    scanResult.rssi,
                    scanResult.scanRecord,
                    isFromWifi = scanResult.isWifi
                )
                val type = when {
                    scanResult.isWifi -> {
                        if (isWifiHotspot(scanResult.deviceName, scanResult.macAddress)) {
                            DeviceType.MOBILE_HOTSPOT
                        } else {
                            DeviceType.WIFI_DEVICE
                        }
                    }
                    else -> deviceType
                }
                Device(
                    examId = examId,
                    macAddress = scanResult.macAddress,
                    deviceName = scanResult.deviceName,
                    deviceType = type.name,
                    rssi = scanResult.rssi,
                    manufacturer = scanResult.manufacturer,
                    isWhitelisted = true,
                    isAuthorized = true,
                    riskLevel = risk.level.name
                )
            }

            deviceRepository.addAllToWhitelist(devices)
            _whitelistedCount.value = devices.size
            _isBaselineScanning.value = false
            Log.d(TAG, "Baseline scan complete: ${devices.size} devices whitelisted")
        }
    }

    fun createAndStartExam(
        name: String,
        hallName: String,
        roomNumber: String,
        invigilatorName: String,
        duration: String,
        examName: String = name
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val exam = com.examshield.data.models.Exam(
                examName = examName,
                hallName = hallName,
                roomNumber = roomNumber,
                invigilatorName = invigilatorName,
                isActive = true,
                startedAt = System.currentTimeMillis()
            )
            val examId = examRepository.insertExam(exam)
            Log.d(TAG, "Exam created: id=$examId")

            val hId = historyRepository.startExam(
                name = name,
                hallName = hallName,
                roomNumber = roomNumber,
                invigilatorName = invigilatorName,
                duration = duration
            )
            historyExamId.set(hId)
            Log.d(TAG, "History exam created: id=$hId")

            withContext(Dispatchers.Main) {
                startExamMonitoring(
                    examId, name, hallName, roomNumber, invigilatorName, duration,
                    createHistoryExam = false
                )
            }
        }
    }

    fun startScanning() {
        Log.d(TAG, "startScanning() requested")
        unifiedScanner.startScanning()
        cellularScanner.startScanning()
        networkMonitor.startMonitoring()
    }

    fun startExamMonitoring(
        examId: Long,
        examName: String = "Exam",
        hallName: String = "",
        roomNumber: String = "",
        invigilatorName: String = "",
        duration: String = "",
        createHistoryExam: Boolean = true
    ) {
        _isExamActive.value = true
        currentExamId.set(examId)
        Log.d(TAG, "Starting exam monitoring: examId=$examId")

        if (createHistoryExam && historyExamId.get() < 0) {
            viewModelScope.launch(Dispatchers.IO) {
                val hId = historyRepository.startExam(
                    name = examName,
                    hallName = hallName,
                    roomNumber = roomNumber,
                    invigilatorName = invigilatorName,
                    duration = duration
                )
                historyExamId.set(hId)
                Log.d(TAG, "History exam created: id=$hId")
            }
        }

        unifiedScanner.startScanning()
        cellularScanner.startScanning()
        networkMonitor.startMonitoring()

        unifiedMonitorJob = viewModelScope.launch {
            unifiedScanner.devices.collect { deviceList ->
                processUnifiedDevices(deviceList, examId)
            }
        }

        viewModelScope.launch {
            examRepository.getActiveExam()?.let { exam ->
                _activeExam.value = exam
            }
        }
    }

    private fun processUnifiedDevices(deviceList: List<UnifiedDevice>, examId: Long) {
        val unauthorized = deviceList.filter { device ->
            !whitelistMacs.containsKey(device.macAddress.uppercase())
        }.sortedWith(
            compareByDescending<UnifiedDevice> { isHighRiskDevice(it) }
                .thenBy { it.estimatedDistance }
        )

        val existingWhitelisted = allDetectedDevices.values
            .filter { it.isWhitelisted }
            .map { it.macAddress.uppercase() }
            .toSet()

        val trulyUnauthorized = unauthorized.filter { device ->
            device.macAddress.uppercase() !in existingWhitelisted
        }

        _sortedUnauthorizedDevices.value = sortForDisplay(trulyUnauthorized)
        _lastScanTime.value = System.currentTimeMillis()

        // Retain every active threat in memory keyed by MAC. Threats seen on
        // this emission refresh their lastSeen; anything that has gone silent
        // beyond the retention window is pruned so the set stays hot but bounded.
        val now = System.currentTimeMillis()
        trulyUnauthorized.forEach { device ->
            activeThreatsMap[device.macAddress.uppercase()] = device.copy(
                lastSeen = now
            )
        }
        val staleThreatKeys = activeThreatsMap.filter {
            now - it.value.lastSeen > ACTIVE_THREAT_RETENTION_MS
        }.keys
        staleThreatKeys.forEach(activeThreatsMap::remove)
        _activeThreats.value = sortForDisplay(activeThreatsMap.values.toList())

        checkForAlerts(trulyUnauthorized)
        autoSaveIncidents(trulyUnauthorized)
    }

    private fun autoSaveIncidents(devices: List<UnifiedDevice>) {
        if (devices.isEmpty() || historyExamId.get() < 0) return

        viewModelScope.launch(Dispatchers.IO) {
            for (device in devices) {
                historyRepository.recordIncident(
                    examId = historyExamId.get(),
                    macAddress = device.macAddress,
                    deviceName = device.name,
                    deviceType = device.deviceType.name,
                    source = device.source.name,
                    riskLevel = device.riskLevel.name,
                    rssi = device.rssi,
                    distance = device.estimatedDistance
                )
            }
        }
    }

    private fun checkForAlerts(devices: List<UnifiedDevice>) {
        if (devices.isEmpty()) return

        val currentAlert = _criticalAlertDevice.value
        val rssiThreshold = settingsRepo.alertRssiThreshold

        val highestPriorityDevice = devices.maxByOrNull { device ->
            val typePriority = PRIORITY_ORDER[device.deviceType] ?: 0
            val rssiBonus = if (device.rssi > rssiThreshold) 10 else 0
            typePriority * 100 + rssiBonus
        } ?: return

        if (currentAlert == null || getPriority(highestPriorityDevice) > getPriority(currentAlert)) {
            _criticalAlertDevice.value = highestPriorityDevice
            _newUnauthorizedUnifiedDevice.value = highestPriorityDevice

            var urgency = when (highestPriorityDevice.deviceType) {
                DeviceType.HIDDEN_EARPIECE, DeviceType.MOBILE_HOTSPOT -> AlertUrgency.CRITICAL
                DeviceType.EARPHONE, DeviceType.SMARTWATCH -> AlertUrgency.HIGH
                DeviceType.PHONE_IOS, DeviceType.PHONE_ANDROID -> AlertUrgency.MEDIUM
                else -> AlertUrgency.LOW
            }

            // Escalate urgency when device is very close (strong RSSI)
            when {
                highestPriorityDevice.rssi > -50 &&
                    urgency.ordinal < AlertUrgency.CRITICAL.ordinal ->
                    urgency = AlertUrgency.CRITICAL
                highestPriorityDevice.rssi > -70 &&
                    urgency.ordinal < AlertUrgency.HIGH.ordinal ->
                    urgency = AlertUrgency.HIGH
            }

            viewModelScope.launch(Dispatchers.Main) {
                Log.d(TAG, "Triggering alarm for ${highestPriorityDevice.name} (${highestPriorityDevice.deviceType})")
                AlarmManager.playAlert(getApplication(), urgency)
                vibrationHelper.triggerAlert(urgency)
            }

            Log.d(TAG, "Alert: ${highestPriorityDevice.deviceType} | ${highestPriorityDevice.name} | RSSI: ${highestPriorityDevice.rssi} (threshold: $rssiThreshold)")
        }
    }

    private fun getPriority(device: UnifiedDevice): Int {
        val typePriority = PRIORITY_ORDER[device.deviceType] ?: 0
        val rssiBonus = if (device.rssi > settingsRepo.alertRssiThreshold) 10 else 0
        return typePriority * 100 + rssiBonus
    }

    private fun isWifiHotspot(ssid: String, bssid: String): Boolean {
        return HotspotDetector.isMobileHotspot(ssid = ssid, bssid = bssid)
    }

    fun dismissAlert() {
        _criticalAlertDevice.value = null
        _newUnauthorizedDevice.value = null
        _newUnauthorizedUnifiedDevice.value = null
        AlarmManager.stopAll()
        vibrationHelper.stop()
        soundManager.stopBeeping()
        vibrationManager.stopVibration()
    }

    fun addToWhitelist(device: UnifiedDevice) {
        viewModelScope.launch(Dispatchers.IO) {
            historyRepository.addToWhitelist(
                mac = device.macAddress,
                name = device.name,
                type = device.deviceType.name
            )
        }
        if (_activeExam.value != null || currentExamId.get() > 0) {
            viewModelScope.launch(Dispatchers.IO) {
                val dvc = Device(
                    examId = if (currentExamId.get() > 0) currentExamId.get() else 0,
                    macAddress = device.macAddress,
                    deviceName = device.name,
                    deviceType = device.deviceType.name,
                    rssi = device.rssi,
                    riskLevel = device.riskLevel.name,
                    isWhitelisted = true,
                    isAuthorized = true
                )
                deviceRepository.addToWhitelist(dvc)
            }
        }
    }

    fun setHuntTarget(device: UnifiedDevice) {
        _currentHuntTarget.value = device
    }

    fun manualRefreshWiFi() {
        unifiedScanner.manualRefresh()
    }

    fun getHuntDeviceRssi(mac: String): Int? {
        return unifiedDevices.value.find { it.macAddress.equals(mac, ignoreCase = true) }?.rssi
    }

    fun stopScanning() {
        unifiedMonitorJob?.cancel()
        bluetoothScanner.stopScan()
        unifiedScanner.stopScanning()
        cellularScanner.stopScanning()
        networkMonitor.stopMonitoring()
        _isExamActive.value = false
        AlarmManager.stopAll()
        vibrationHelper.stop()
        soundManager.stopBeeping()
        vibrationManager.stopVibration()
        allDetectedDevices.clear()
        whitelistMacs.clear()
        activeThreatsMap.clear()
        _activeThreats.value = emptyList()
        currentExamId.set(-1L)
        historyExamId.set(-1L)
    }

    fun endExam() {
        stopScanning()
        viewModelScope.launch(Dispatchers.IO) {
            val hId = historyExamId.get()
            if (hId > 0) {
                historyRepository.endExam(hId)
                historyExamId.set(-1L)
            }
            _activeExam.value?.let { exam ->
                val deviceCount = deviceRepository.getWhitelistCount(exam.id)
                examRepository.endExam(exam.id, deviceCount, 0)
                examRepository.updateExam(
                    exam.copy(
                        isActive = false,
                        isCompleted = true,
                        endedAt = System.currentTimeMillis()
                    )
                )
                _isExamActive.value = false
                _activeExam.value = null
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        baselineJob?.cancel()
        unifiedMonitorJob?.cancel()
        bluetoothScanner.stopScan()
        unifiedScanner.stopScanning()
        cellularScanner.stopScanning()
        networkMonitor.stopMonitoring()
        AlarmManager.stopAll()
        soundManager.release()
        vibrationManager.release()
    }
}
