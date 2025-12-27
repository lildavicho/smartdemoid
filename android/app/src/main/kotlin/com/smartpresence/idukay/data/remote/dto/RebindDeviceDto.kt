package com.smartpresence.idukay.data.remote.dto

data class RebindDeviceRequest(
    val teacherId: String,
    val deviceId: String,
    val adminPinProof: String,
    val metadata: Map<String, String>
)

data class RebindDeviceResponse(
    val id: String,
    val teacherId: String,
    val deviceId: String,
    val boundAt: String
)
