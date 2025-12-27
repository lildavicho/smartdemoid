package com.smartpresence.idukay.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "pending_attendance",
    indices = [Index(value = ["localId"], unique = true)]
)
data class PendingAttendanceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val localId: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val studentId: String,
    val confidence: Float = 1.0f,
    val detectedAt: Long,
    val confirmedAt: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "PENDING",
    val retryCount: Int = 0
)
