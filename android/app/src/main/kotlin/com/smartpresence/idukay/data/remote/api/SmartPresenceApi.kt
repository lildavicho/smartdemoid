package com.smartpresence.idukay.data.remote.api

import com.smartpresence.idukay.data.remote.dto.*
import retrofit2.http.*

interface SmartPresenceApi {
    
    @POST("auth/device/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse
    
    @GET("courses/{id}/roster")
    suspend fun getCourseRoster(@Path("id") courseId: String): CourseRosterResponse
    
    @GET("students/{id}/face-templates")
    suspend fun getStudentFaceTemplates(@Path("id") studentId: String): List<FaceTemplateDto>
    
    @POST("attendance/sessions")
    suspend fun createAttendanceSession(@Body request: CreateSessionRequest): CreateSessionResponse
    
    @PUT("attendance/sessions/{id}")
    suspend fun updateAttendanceSession(
        @Path("id") sessionId: String,
        @Body request: UpdateSessionRequest
    ): AttendanceSessionDto
    
    @GET("attendance/sessions/{id}")
    suspend fun getAttendanceSession(@Path("id") sessionId: String): AttendanceSessionDto
    
    // Block 11: Device Binding
    @POST("devices/bind")
    suspend fun bindDevice(@Body request: BindDeviceRequest): ApiResponse<BindDeviceResponse>
    
    @POST("devices/rebind")
    suspend fun rebindDevice(@Body request: RebindDeviceRequest): ApiResponse<RebindDeviceResponse>
    
    // Block 11: Idempotent Attendance Events
    @POST("attendance/events/batch")
    suspend fun batchInsertEvents(@Body request: BatchEventsRequest): ApiResponse<BatchEventsResponse>
    
    // Block 11: Idempotent Session Finalization
    @POST("attendance/sessions/finalize")
    suspend fun finalizeSession(@Body request: FinalizeSessionRequest): ApiResponse<FinalizeSessionResponse>
}
