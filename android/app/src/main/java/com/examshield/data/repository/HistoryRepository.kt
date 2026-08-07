package com.examshield.data.repository

import android.content.Context
import com.examshield.data.local.AppDatabase
import com.examshield.data.local.dao.TypeCount
import com.examshield.data.local.entities.ExamEntity
import com.examshield.data.local.entities.IncidentEntity
import com.examshield.data.local.entities.WhitelistEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class HistoryRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val examDao = db.examDaoV2()
    private val incidentDao = db.incidentDaoV2()
    private val whitelistDao = db.whitelistDaoV2()

    suspend fun startExam(
        name: String,
        hallName: String,
        roomNumber: String,
        invigilatorName: String,
        duration: String
    ): Long {
        val exam = ExamEntity(
            name = name,
            hallName = hallName,
            roomNumber = roomNumber,
            invigilatorName = invigilatorName,
            startTime = System.currentTimeMillis(),
            duration = duration,
            status = "ACTIVE"
        )
        return examDao.insertExam(exam)
    }

    suspend fun endExam(examId: Long) {
        val exam = examDao.getExamById(examId) ?: return
        val incidents = getIncidentsForExamSync(examId)

        val updated = exam.copy(
            endTime = System.currentTimeMillis(),
            status = "COMPLETED",
            totalDevicesDetected = incidents.size,
            criticalIncidents = incidents.count { it.riskLevel == "CRITICAL" },
            highRiskIncidents = incidents.count { it.riskLevel == "HIGH" },
            mediumRiskIncidents = incidents.count { it.riskLevel == "MEDIUM" }
        )
        examDao.updateExam(updated)
    }

    fun getAllExams(): Flow<List<ExamEntity>> = examDao.getAllExams()

    suspend fun getExamById(id: Long) = examDao.getExamById(id)

    suspend fun getActiveExam() = examDao.getActiveExam()

    suspend fun recordIncident(
        examId: Long,
        macAddress: String,
        deviceName: String,
        deviceType: String,
        source: String,
        riskLevel: String,
        rssi: Int,
        distance: Double
    ): Long {
        val incident = IncidentEntity(
            examId = examId,
            macAddress = macAddress,
            deviceName = deviceName,
            deviceType = deviceType,
            source = source,
            riskLevel = riskLevel,
            rssi = rssi,
            estimatedDistance = distance,
            timestamp = System.currentTimeMillis()
        )
        return incidentDao.insertIncident(incident)
    }

    fun getAllIncidents(): Flow<List<IncidentEntity>> = incidentDao.getAllIncidents()

    fun getIncidentsForExam(examId: Long): Flow<List<IncidentEntity>> =
        incidentDao.getIncidentsForExam(examId)

    private suspend fun getIncidentsForExamSync(examId: Long): List<IncidentEntity> {
        return incidentDao.getIncidentsForExam(examId).first()
    }

    fun getIncidentsByRisk(risk: String): Flow<List<IncidentEntity>> =
        incidentDao.getIncidentsByRisk(risk)

    fun searchIncidents(query: String): Flow<List<IncidentEntity>> =
        incidentDao.searchIncidents(query)

    suspend fun getStatistics(): HistoryStats {
        return HistoryStats(
            totalExams = examDao.getTotalExamsCount(),
            completedExams = examDao.getCompletedExamsCount(),
            totalIncidents = incidentDao.getTotalIncidentsCount(),
            criticalIncidents = incidentDao.getCriticalIncidentsCount(),
            uniqueDevices = incidentDao.getUniqueDevicesCount(),
            deviceDistribution = incidentDao.getDeviceTypeDistribution()
        )
    }

    suspend fun addToWhitelist(mac: String, name: String, type: String) {
        val device = WhitelistEntity(
            macAddress = mac,
            deviceName = name,
            deviceType = type,
            addedTime = System.currentTimeMillis()
        )
        whitelistDao.insertToWhitelist(device)
    }

    suspend fun getAllWhitelistedMacs(): List<String> = whitelistDao.getAllWhitelistedMacs()

    fun getAllWhitelisted(): Flow<List<WhitelistEntity>> = whitelistDao.getAllWhitelisted()

    suspend fun deleteOldRecords(daysToKeep: Int = 30) {
        val cutoff = System.currentTimeMillis() - (daysToKeep * 86400000L)
        examDao.deleteOldExams(cutoff)
        incidentDao.deleteOldIncidents(cutoff)
    }

    suspend fun deleteIncident(incident: IncidentEntity) {
        incidentDao.deleteIncident(incident)
    }

    suspend fun deleteExam(exam: ExamEntity) {
        examDao.deleteExam(exam)
    }

    suspend fun clearAllHistory() {
        examDao.deleteAllExams()
        incidentDao.deleteAllIncidents()
    }
}

data class HistoryStats(
    val totalExams: Int,
    val completedExams: Int,
    val totalIncidents: Int,
    val criticalIncidents: Int,
    val uniqueDevices: Int,
    val deviceDistribution: List<TypeCount>
)
