package com.smartpresence.idukay.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "pending_session_updates",
    indices = [Index(value = ["localId"], unique = true)]
)
data class PendingSessionUpdateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val localId: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val courseId: String? = null, // Added for Block 11
    val status: String,
    val recordsJson: String,
    val timestamp: Long = System.currentTimeMillis(),
    val syncStatus: String = "PENDING",
    val retryCount: Int = 0
)
