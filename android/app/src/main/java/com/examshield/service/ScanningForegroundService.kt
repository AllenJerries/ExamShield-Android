package com.examshield.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.examshield.MainActivity
import com.examshield.R
import com.examshield.data.local.AppDatabase
import com.examshield.data.models.Device
import com.examshield.data.repository.DeviceRepository
import com.examshield.data.repository.ExamRepository
import com.examshield.scanner.BluetoothScanner
import com.examshield.scanner.DeviceClassifier
import com.examshield.scanner.UnifiedScanner
import com.examshield.scanner.WifiScanner
import com.examshield.utils.Constants
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class ScanningForegroundService : Service() {

    private var unifiedMonitorJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var unifiedScanner: UnifiedScanner
    private lateinit var deviceRepository: DeviceRepository
    private lateinit var examRepository: ExamRepository
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        unifiedScanner = UnifiedScanner(this)
        val db = AppDatabase.getDatabase(this)
        deviceRepository = DeviceRepository(db)
        examRepository = ExamRepository(db)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Constants.ACTION_START_SCAN -> {
                val examId = intent.getLongExtra("exam_id", -1)
                if (examId != -1L) {
                    startForeground(Constants.NOTIFICATION_ID, createNotification("Scanning active"))
                    startScanning(examId)
                }
            }
            Constants.ACTION_STOP_SCAN -> {
                stopScanning()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startScanning(examId: Long) {
        acquireWakeLock()
        unifiedScanner.startScanning()

        unifiedMonitorJob = scope.launch {
            unifiedScanner.devices.collect { deviceList ->
                val unauthorized = deviceList.filter { device ->
                    !deviceRepository.isDeviceWhitelisted(device.macAddress, examId)
                }
                if (unauthorized.isNotEmpty()) {
                    val closest = unauthorized.first()
                    updateNotification("ALERT: ${closest.riskLevel.name} - ${closest.name.ifEmpty { "Unknown" }} (${closest.rssi} dBm)")
                } else {
                    updateNotification("Scanning... ${deviceList.size} devices found")
                }
            }
        }
    }

    private fun stopScanning() {
        unifiedMonitorJob?.cancel()
        unifiedScanner.stopScanning()
        releaseWakeLock()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ExamShield::ScanningWakeLock"
        ).apply {
            acquire(60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    private fun createNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("ExamShield")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(Constants.NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScanning()
        scope.cancel()
    }
}
