package com.smartpresence.idukay.data.remote.dto

data class BatchEventsRequest(
    val sessionId: String,
    val events: List<AttendanceEventDto>
)

data class AttendanceEventDto(
    val studentId: String,
    val occurredAt: String, // ISO 8601
    val confidence: Float?,
    val idempotencyKey: String,
    val source: String = "edge"
)

data class BatchEventsResponse(
    val inserted: Int,
    val ignored: Int,
    val total: Int
)
