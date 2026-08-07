package com.examshield.data.repository

import com.examshield.data.local.AppDatabase
import com.examshield.data.models.Device
import kotlinx.coroutines.flow.Flow

class DeviceRepository(private val db: AppDatabase) {

    private val deviceDao = db.deviceDao()
    private val whitelistDao = db.whitelistDao()

    suspend fun insertDevice(device: Device): Long {
        return deviceDao.insertDevice(device)
    }

    suspend fun insertDevices(devices: List<Device>) {
        deviceDao.insertDevices(devices)
    }

    suspend fun findDeviceByMac(examId: Long, macAddress: String): Device? {
        return deviceDao.findDeviceByMac(examId, macAddress)
    }

    suspend fun isDeviceWhitelisted(macAddress: String, examId: Long): Boolean {
        return whitelistDao.findInWhitelist(examId, macAddress) != null
    }

    fun getWhitelistedDevices(examId: Long): Flow<List<Device>> {
        return whitelistDao.getWhitelist(examId)
    }

    fun getUnauthorizedDevices(examId: Long): Flow<List<Device>> {
        return deviceDao.getUnauthorizedDevices(examId)
    }

    fun getAllDevicesForExam(examId: Long): Flow<List<Device>> {
        return deviceDao.getAllDevicesForExam(examId)
    }

    suspend fun getWhitelistSync(examId: Long): List<Device> {
        return whitelistDao.getWhitelistSync(examId)
    }

    suspend fun addToWhitelist(device: Device): Long {
        return whitelistDao.addToWhitelist(device.copy(isWhitelisted = true))
    }

    suspend fun addAllToWhitelist(devices: List<Device>) {
        whitelistDao.addAllToWhitelist(devices.map { it.copy(isWhitelisted = true) })
    }

    suspend fun removeFromWhitelist(id: Long, examId: Long) {
        whitelistDao.removeFromWhitelist(id, examId)
    }

    suspend fun clearWhitelist(examId: Long) {
        whitelistDao.clearWhitelist(examId)
    }

    suspend fun getWhitelistCount(examId: Long): Int {
        return whitelistDao.getWhitelistCount(examId)
    }

    suspend fun deleteDevicesForExam(examId: Long) {
        deviceDao.deleteDevicesForExam(examId)
    }
}
