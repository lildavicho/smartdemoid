package com.smartpresence.idukay.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "face_templates")
data class FaceTemplateEntity(
    @PrimaryKey
    val id: String,
    val studentId: String,
    val embedding: ByteArray,
    val qualityScore: Float,
    val modelVersion: String = "w600k_r50_int8",
    val createdAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FaceTemplateEntity
        if (id != other.id) return false
        if (!embedding.contentEquals(other.embedding)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}
