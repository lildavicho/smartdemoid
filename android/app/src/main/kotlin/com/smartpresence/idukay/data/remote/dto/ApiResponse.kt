package com.smartpresence.idukay.data.remote.dto

/**
 * Generic API response wrapper for backend responses
 */
data class ApiResponse<T>(
    val success: Boolean,
    val message: String? = null,
    val data: T? = null,
    val error: String? = null
)
