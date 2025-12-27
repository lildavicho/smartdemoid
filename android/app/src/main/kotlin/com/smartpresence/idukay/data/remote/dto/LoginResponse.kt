package com.smartpresence.idukay.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LoginResponse(
    val accessToken: String,
    val device: DeviceDto,
    val teacher: TeacherDto
)

@JsonClass(generateAdapter = true)
data class DeviceDto(
    val id: String,
    val serialNumber: String,
    val location: String?
)

@JsonClass(generateAdapter = true)
data class TeacherDto(
    val id: String,
    val firstName: String,
    val lastName: String
)
