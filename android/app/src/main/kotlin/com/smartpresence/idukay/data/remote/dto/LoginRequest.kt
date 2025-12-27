package com.smartpresence.idukay.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LoginRequest(
    val serial_number: String,
    val pin_code: String
)
