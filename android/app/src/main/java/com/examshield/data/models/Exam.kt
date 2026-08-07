package com.examshield.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exams")
data class Exam(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hallName: String = "",
    val roomNumber: String = "",
    val examName: String = "",
    val examDate: Long = 0,
    val invigilatorName: String = "",
    val durationMinutes: Int = 0,
    val isActive: Boolean = false,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val startedAt: Long = 0L,
    val endedAt: Long = 0L,
    val devicesDetected: Int = 0,
    val incidentsCount: Int = 0
)
