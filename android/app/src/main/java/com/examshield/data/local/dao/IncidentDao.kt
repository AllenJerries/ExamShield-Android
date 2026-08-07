package com.examshield.data.local.dao

import androidx.room.*
import com.examshield.data.local.entities.IncidentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IncidentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIncident(incident: IncidentEntity): Long

    @Update
    suspend fun updateIncident(incident: IncidentEntity)

    @Delete
    suspend fun deleteIncident(incident: IncidentEntity)

    @Query("SELECT * FROM incidents_v2 ORDER BY timestamp DESC")
    fun getAllIncidents(): Flow<List<IncidentEntity>>

    @Query("SELECT * FROM incidents_v2 WHERE examId = :examId ORDER BY timestamp DESC")
    fun getIncidentsForExam(examId: Long): Flow<List<IncidentEntity>>

    @Query("""
        SELECT * FROM incidents_v2 
        WHERE riskLevel = :riskLevel 
        ORDER BY timestamp DESC
    """)
    fun getIncidentsByRisk(riskLevel: String): Flow<List<IncidentEntity>>

    @Query("""
        SELECT * FROM incidents_v2 
        WHERE deviceType = :deviceType 
        ORDER BY timestamp DESC
    """)
    fun getIncidentsByType(deviceType: String): Flow<List<IncidentEntity>>

    @Query("""
        SELECT * FROM incidents_v2 
        WHERE timestamp BETWEEN :startDate AND :endDate
        ORDER BY timestamp DESC
    """)
    fun getIncidentsBetweenDates(startDate: Long, endDate: Long): Flow<List<IncidentEntity>>

    @Query("""
        SELECT * FROM incidents_v2 
        WHERE deviceName LIKE '%' || :query || '%' 
        OR macAddress LIKE '%' || :query || '%'
        ORDER BY timestamp DESC
    """)
    fun searchIncidents(query: String): Flow<List<IncidentEntity>>

    @Query("SELECT COUNT(*) FROM incidents_v2")
    suspend fun getTotalIncidentsCount(): Int

    @Query("""
        SELECT COUNT(*) FROM incidents_v2 
        WHERE riskLevel = 'HIGH' OR riskLevel = 'CRITICAL'
    """)
    suspend fun getCriticalIncidentsCount(): Int

    @Query("""
        SELECT deviceType, COUNT(*) as count 
        FROM incidents_v2 
        GROUP BY deviceType 
        ORDER BY count DESC
    """)
    suspend fun getDeviceTypeDistribution(): List<TypeCount>

    @Query("""
        SELECT COUNT(DISTINCT macAddress) FROM incidents_v2
    """)
    suspend fun getUniqueDevicesCount(): Int

    @Query("DELETE FROM incidents_v2 WHERE timestamp < :beforeDate")
    suspend fun deleteOldIncidents(beforeDate: Long)

    @Query("DELETE FROM incidents_v2")
    suspend fun deleteAllIncidents()
}

data class TypeCount(
    val deviceType: String,
    val count: Int
)
