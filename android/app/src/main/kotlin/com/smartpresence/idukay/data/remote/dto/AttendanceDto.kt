package com.smartpresence.idukay.data.remote.dto

data class CreateSessionRequest(
    val courseId: String,
    val teacherId: String? = null,
    val deviceId: String? = null
)

/**
 * Response for createAttendanceSession - the backend returns the session object directly at root level.
 * Fields match the JSON response from the backend.
 */
data class CreateSessionResponse(
    val id: String,
    val courseId: String,
    val teacherId: String,
    val deviceId: String,
    val startedAt: String,
    val endedAt: String? = null,
    val status: String,
    val metadata: Map<String, Any>? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class AttendanceSessionDto(
    val id: String,
    val courseId: String,
    val teacherId: String,
    val deviceId: String,
    val startedAt: String,
    val endedAt: String?,
    val status: String
)

data class UpdateSessionRequest(
    val status: String,
    val records: List<AttendanceRecordDto>
)

data class AttendanceRecordDto(
    val studentId: String,
    val status: String,
    val confidence: Float,
    val confirmedBy: String,
    val detectedAt: String?
)
