package com.examshield.data.local

import androidx.room.*
import com.examshield.data.models.Device
import kotlinx.coroutines.flow.Flow

@Dao
interface WhitelistDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToWhitelist(device: Device): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addAllToWhitelist(devices: List<Device>)

    @Query("SELECT * FROM devices WHERE examId = :examId AND isWhitelisted = 1")
    fun getWhitelist(examId: Long): Flow<List<Device>>

    @Query("SELECT * FROM devices WHERE examId = :examId AND isWhitelisted = 1")
    suspend fun getWhitelistSync(examId: Long): List<Device>

    @Query("SELECT * FROM devices WHERE examId = :examId AND isWhitelisted = 1 AND macAddress = :macAddress LIMIT 1")
    suspend fun findInWhitelist(examId: Long, macAddress: String): Device?

    @Query("DELETE FROM devices WHERE id = :id AND examId = :examId")
    suspend fun removeFromWhitelist(id: Long, examId: Long)

    @Query("DELETE FROM devices WHERE examId = :examId AND isWhitelisted = 1")
    suspend fun clearWhitelist(examId: Long)

    @Query("SELECT COUNT(*) FROM devices WHERE examId = :examId AND isWhitelisted = 1")
    suspend fun getWhitelistCount(examId: Long): Int
}
