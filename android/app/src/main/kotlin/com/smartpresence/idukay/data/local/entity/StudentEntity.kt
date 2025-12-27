package com.smartpresence.idukay.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "students")
data class StudentEntity(
    @PrimaryKey
    val id: String,
    val documentId: String,
    val firstName: String,
    val lastName: String,
    val email: String?,
    val courseId: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
