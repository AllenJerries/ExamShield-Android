package com.examshield.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "devices")
data class Device(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val examId: Long = 0,
    val macAddress: String = "",
    val deviceName: String = "",
    val deviceType: String = "",
    val rssi: Int = 0,
    val manufacturer: String = "",
    val isWhitelisted: Boolean = false,
    val isAuthorized: Boolean = false,
    val firstSeen: Long = System.currentTimeMillis(),
    val lastSeen: Long = System.currentTimeMillis(),
    val riskLevel: String = "LOW"
)
