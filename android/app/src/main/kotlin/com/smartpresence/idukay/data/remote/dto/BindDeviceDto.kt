package com.smartpresence.idukay.data.remote.dto

data class BindDeviceRequest(
    val teacherId: String,
    val deviceId: String,
    val metadata: Map<String, String>
)

data class BindDeviceResponse(
    val id: String,
    val teacherId: String,
    val deviceId: String,
    val boundAt: String,
    val lastSeenAt: String
)
