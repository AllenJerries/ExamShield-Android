package com.examshield.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exams_v2")
data class ExamEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val hallName: String,
    val roomNumber: String,
    val invigilatorName: String,
    val startTime: Long,
    val endTime: Long? = null,
    val duration: String,
    val totalDevicesDetected: Int = 0,
    val criticalIncidents: Int = 0,
    val highRiskIncidents: Int = 0,
    val mediumRiskIncidents: Int = 0,
    val status: String = "ACTIVE",
    val notes: String = ""
)
