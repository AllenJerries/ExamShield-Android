package com.examshield.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "incidents")
data class Incident(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val examId: Long = 0,
    val examName: String = "",
    val roomName: String = "",
    val deviceName: String = "",
    val macAddress: String = "",
    val deviceType: String = "",
    val riskLevel: String = "",
    val rssi: Int = 0,
    val manufacturer: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val actionTaken: String = "DETECTED",
    val notes: String = "",
    val synced: Boolean = false
)
