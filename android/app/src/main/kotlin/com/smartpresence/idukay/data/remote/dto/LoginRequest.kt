package com.smartpresence.idukay.data.remote.dto

data class LoginRequest(
    val serial_number: String,
    val pin_code: String
)
