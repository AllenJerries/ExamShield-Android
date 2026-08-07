package com.examshield.data.repository

import com.examshield.data.local.AppDatabase
import com.examshield.data.models.Incident
import kotlinx.coroutines.flow.Flow

class IncidentRepository(private val db: AppDatabase) {

    private val incidentDao = db.incidentDao()

    suspend fun insertIncident(incident: Incident): Long {
        return incidentDao.insertIncident(incident)
    }

    suspend fun updateIncident(incident: Incident) {
        incidentDao.updateIncident(incident)
    }

    fun getAllIncidents(): Flow<List<Incident>> {
        return incidentDao.getAllIncidents()
    }

    fun getIncidentsForExam(examId: Long): Flow<List<Incident>> {
        return incidentDao.getIncidentsForExam(examId)
    }

    fun getIncidentsByRiskLevel(riskLevel: String): Flow<List<Incident>> {
        return incidentDao.getIncidentsByRiskLevel(riskLevel)
    }

    fun getIncidentsByDateRange(startTime: Long, endTime: Long): Flow<List<Incident>> {
        return incidentDao.getIncidentsByDateRange(startTime, endTime)
    }

    fun getIncidentsByDeviceType(deviceType: String): Flow<List<Incident>> {
        return incidentDao.getIncidentsByDeviceType(deviceType)
    }

    suspend fun getUnsyncedIncidents(): List<Incident> {
        return incidentDao.getUnsyncedIncidents()
    }

    suspend fun markAsSynced(id: Long) {
        incidentDao.markAsSynced(id)
    }

    suspend fun updateAction(id: Long, action: String) {
        incidentDao.updateAction(id, action)
    }

    fun getTotalIncidentsCount(): Flow<Int> {
        return incidentDao.getTotalIncidentsCount()
    }

    suspend fun deleteIncident(incident: Incident) {
        incidentDao.deleteIncident(incident)
    }
}
