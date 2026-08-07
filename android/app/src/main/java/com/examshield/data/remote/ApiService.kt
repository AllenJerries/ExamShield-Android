package com.examshield.data.remote

import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @POST("rooms")
    suspend fun createRoom(@Body room: RoomRequest): Response<RoomResponse>

    @POST("exams")
    suspend fun startExam(@Body exam: ExamRequest): Response<ExamResponse>

    @PUT("exams/{id}/end")
    suspend fun endExam(
        @Path("id") examId: Long,
        @Body request: EndExamRequest
    ): Response<ApiResponse>

    @POST("whitelist")
    suspend fun addWhitelist(@Body whitelist: WhitelistRequest): Response<ApiResponse>

    @POST("detections")
    suspend fun uploadDetection(@Body detection: DetectionRequest): Response<ApiResponse>

    @POST("incidents")
    suspend fun reportIncident(@Body incident: IncidentRequest): Response<ApiResponse>
}
