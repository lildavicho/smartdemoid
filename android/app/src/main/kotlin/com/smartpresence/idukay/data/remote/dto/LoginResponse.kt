package com.smartpresence.idukay.data.remote.dto

data class LoginResponse(
    val accessToken: String,
    val device: DeviceDto,
    val teacher: TeacherDto
)

data class DeviceDto(
    val id: String,
    val serialNumber: String,
    val location: String?
)

data class TeacherDto(
    val id: String,
    val firstName: String,
    val lastName: String
)
