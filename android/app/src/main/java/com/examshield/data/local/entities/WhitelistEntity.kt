package com.examshield.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "whitelist_v2")
data class WhitelistEntity(
    @PrimaryKey
    val macAddress: String,
    val deviceName: String,
    val deviceType: String,
    val addedTime: Long,
    val notes: String = ""
)
