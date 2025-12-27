package com.smartpresence.idukay.presentation.attendance

data class ConfirmedRecord(
    val localId: String,
    val studentId: String,
    val studentName: String?,
    val confidence: Float,
    val detectedAt: Long,
    val confirmedAt: Long,
    val status: String,
    val trackId: Int? = null
) {
    val displayName: String
        get() = studentName?.takeIf { it.isNotBlank() } ?: "ID: $studentId"
}
