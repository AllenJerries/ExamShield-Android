package com.examshield.data.remote

import com.google.gson.annotations.SerializedName

data class RoomRequest(
    @SerializedName("hall_name") val hallName: String,
    @SerializedName("room_number") val roomNumber: String
)

data class RoomResponse(
    @SerializedName("id") val id: Long,
    @SerializedName("hall_name") val hallName: String,
    @SerializedName("room_number") val roomNumber: String
)

data class ExamRequest(
    @SerializedName("room_id") val roomId: Long,
    @SerializedName("exam_name") val examName: String,
    @SerializedName("exam_date") val examDate: String,
    @SerializedName("invigilator") val invigilator: String,
    @SerializedName("duration_minutes") val durationMinutes: Int
)

data class ExamResponse(
    @SerializedName("id") val id: Long,
    @SerializedName("exam_name") val examName: String,
    @SerializedName("status") val status: String
)

data class WhitelistRequest(
    @SerializedName("exam_id") val examId: Long,
    @SerializedName("devices") val devices: List<WhitelistDeviceRequest>
)

data class WhitelistDeviceRequest(
    @SerializedName("mac_address") val macAddress: String,
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("device_type") val deviceType: String
)

data class DetectionRequest(
    @SerializedName("exam_id") val examId: Long,
    @SerializedName("mac_address") val macAddress: String,
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("rssi") val rssi: Int,
    @SerializedName("risk_level") val riskLevel: String,
    @SerializedName("device_type") val deviceType: String,
    @SerializedName("timestamp") val timestamp: Long
)

data class IncidentRequest(
    @SerializedName("exam_id") val examId: Long,
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("mac_address") val macAddress: String,
    @SerializedName("device_type") val deviceType: String,
    @SerializedName("risk_level") val riskLevel: String,
    @SerializedName("rssi") val rssi: Int,
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("action_taken") val actionTaken: String
)

data class ApiResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("id") val id: Long? = null
)

data class EndExamRequest(
    @SerializedName("devices_detected") val devicesDetected: Int,
    @SerializedName("incidents_count") val incidentsCount: Int
)
