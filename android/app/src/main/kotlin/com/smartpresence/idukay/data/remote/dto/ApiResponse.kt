package com.smartpresence.idukay.data.remote.dto

import com.squareup.moshi.JsonClass

/**
 * Generic API response wrapper for backend responses
 */
@JsonClass(generateAdapter = true)
data class ApiResponse<T>(
    val success: Boolean,
    val message: String? = null,
    val data: T? = null,
    val error: String? = null
)
