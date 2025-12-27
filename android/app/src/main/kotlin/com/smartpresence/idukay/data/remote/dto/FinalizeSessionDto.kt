package com.smartpresence.idukay.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class FinalizeSessionRequest(
    val sessionId: String,
    val teacherId: String,
    val courseId: String,
    val recordsJson: List<AttendanceRecordDto>,
    val idempotencyKey: String
)

@JsonClass(generateAdapter = true)
data class FinalizeSessionResponse(
    val success: Boolean,
    val status: String, // "applied", "already_applied", "rejected"
    val finalizationId: String
)
