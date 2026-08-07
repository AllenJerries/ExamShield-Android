package com.examshield.data.local

import androidx.room.*
import com.examshield.data.models.Device
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: Device): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevices(devices: List<Device>)

    @Update
    suspend fun updateDevice(device: Device)

    @Query("UPDATE devices SET isWhitelisted = :whitelisted WHERE macAddress = :macAddress AND examId = :examId")
    suspend fun updateWhitelistStatus(macAddress: String, examId: Long, whitelisted: Boolean)

    @Query("SELECT * FROM devices WHERE examId = :examId AND isWhitelisted = 1")
    fun getWhitelistedDevices(examId: Long): Flow<List<Device>>

    @Query("SELECT * FROM devices WHERE examId = :examId AND isWhitelisted = 0")
    fun getUnauthorizedDevices(examId: Long): Flow<List<Device>>

    @Query("SELECT * FROM devices WHERE examId = :examId")
    fun getAllDevicesForExam(examId: Long): Flow<List<Device>>

    @Query("SELECT * FROM devices WHERE examId = :examId AND macAddress = :macAddress LIMIT 1")
    suspend fun findDeviceByMac(examId: Long, macAddress: String): Device?

    @Query("SELECT EXISTS(SELECT 1 FROM devices WHERE macAddress = :macAddress AND examId = :examId AND isWhitelisted = 1)")
    suspend fun isDeviceWhitelisted(macAddress: String, examId: Long): Boolean

    @Query("DELETE FROM devices WHERE examId = :examId")
    suspend fun deleteDevicesForExam(examId: Long)

    @Query("SELECT COUNT(*) FROM devices WHERE examId = :examId")
    suspend fun getDeviceCountForExam(examId: Long): Int

    @Query("SELECT COUNT(*) FROM devices WHERE examId = :examId AND isWhitelisted = 1")
    suspend fun getWhitelistedCount(examId: Long): Int
}
