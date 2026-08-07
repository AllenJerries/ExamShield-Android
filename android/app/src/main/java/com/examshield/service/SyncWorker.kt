package com.examshield.service

import android.content.Context
import androidx.work.*
import com.examshield.data.local.AppDatabase
import com.examshield.data.remote.IncidentRequest
import com.examshield.data.remote.RetrofitClient
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getDatabase(applicationContext)
        val incidentDao = db.incidentDao()

        return try {
            val unsynced = incidentDao.getUnsyncedIncidents()
            val api = RetrofitClient.apiService

            for (incident in unsynced) {
                try {
                    val request = IncidentRequest(
                        examId = incident.examId,
                        deviceName = incident.deviceName,
                        macAddress = incident.macAddress,
                        deviceType = incident.deviceType,
                        riskLevel = incident.riskLevel,
                        rssi = incident.rssi,
                        timestamp = incident.timestamp,
                        actionTaken = incident.actionTaken
                    )
                    val response = api.reportIncident(request)
                    if (response.isSuccessful) {
                        incidentDao.markAsSynced(incident.id)
                    }
                } catch (_: Exception) {
                    // Skip this one, will retry later
                }
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                5, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "examshield_sync",
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork("examshield_sync")
        }
    }
}
