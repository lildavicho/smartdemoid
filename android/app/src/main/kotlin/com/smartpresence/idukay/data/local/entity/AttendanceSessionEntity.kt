package com.smartpresence.idukay.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "attendance_sessions")
data class AttendanceSessionEntity(
    @PrimaryKey
    val id: String,
    val courseId: String,
    val teacherId: String,
    val deviceId: String,
    val startedAt: String,
    val endedAt: String?,
    val status: String
)
