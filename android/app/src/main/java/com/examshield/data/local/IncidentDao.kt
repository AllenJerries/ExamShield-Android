package com.examshield.data.local

import androidx.room.*
import com.examshield.data.models.Incident
import kotlinx.coroutines.flow.Flow

@Dao
interface IncidentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIncident(incident: Incident): Long

    @Update
    suspend fun updateIncident(incident: Incident)

    @Query("SELECT * FROM incidents ORDER BY timestamp DESC")
    fun getAllIncidents(): Flow<List<Incident>>

    @Query("SELECT * FROM incidents WHERE examId = :examId ORDER BY timestamp DESC")
    fun getIncidentsForExam(examId: Long): Flow<List<Incident>>

    @Query("SELECT * FROM incidents WHERE riskLevel = :riskLevel ORDER BY timestamp DESC")
    fun getIncidentsByRiskLevel(riskLevel: String): Flow<List<Incident>>

    @Query("SELECT * FROM incidents WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    fun getIncidentsByDateRange(startTime: Long, endTime: Long): Flow<List<Incident>>

    @Query("SELECT * FROM incidents WHERE deviceType = :deviceType ORDER BY timestamp DESC")
    fun getIncidentsByDeviceType(deviceType: String): Flow<List<Incident>>

    @Query("SELECT * FROM incidents WHERE synced = 0")
    suspend fun getUnsyncedIncidents(): List<Incident>

    @Query("UPDATE incidents SET synced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: Long)

    @Query("UPDATE incidents SET actionTaken = :action WHERE id = :id")
    suspend fun updateAction(id: Long, action: String)

    @Query("SELECT COUNT(*) FROM incidents")
    fun getTotalIncidentsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM incidents")
    suspend fun getTotalIncidentsCountSync(): Int

    @Delete
    suspend fun deleteIncident(incident: Incident)

    @Query("DELETE FROM incidents WHERE examId = :examId")
    suspend fun deleteIncidentsForExam(examId: Long)
}
