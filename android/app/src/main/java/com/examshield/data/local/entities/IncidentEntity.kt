package com.examshield.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "incidents_v2",
    foreignKeys = [
        ForeignKey(
            entity = ExamEntity::class,
            parentColumns = ["id"],
            childColumns = ["examId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class IncidentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val examId: Long,
    val macAddress: String,
    val deviceName: String,
    val deviceType: String,
    val source: String,
    val riskLevel: String,
    val rssi: Int,
    val estimatedDistance: Double,
    val timestamp: Long,
    val actionTaken: String = "DETECTED",
    val notes: String = "",
    val huntStartTime: Long? = null,
    val huntEndTime: Long? = null,
    val markedFound: Boolean = false
)
