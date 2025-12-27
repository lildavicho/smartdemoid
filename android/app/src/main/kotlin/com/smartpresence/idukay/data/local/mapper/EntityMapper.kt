package com.smartpresence.idukay.data.local.mapper

import com.smartpresence.idukay.data.local.entity.FaceTemplateEntity
import com.smartpresence.idukay.data.local.entity.StudentEntity
import com.smartpresence.idukay.data.remote.dto.FaceTemplateDto
import com.smartpresence.idukay.data.remote.dto.StudentDto
import java.nio.ByteBuffer

object EntityMapper {
    
    fun StudentDto.toEntity(courseId: String? = null): StudentEntity {
        return StudentEntity(
            id = this.id,
            documentId = this.documentId,
            firstName = this.firstName,
            lastName = this.lastName,
            email = this.email,
            courseId = courseId,
            updatedAt = System.currentTimeMillis()
        )
    }
    
    fun FaceTemplateDto.toEntity(): FaceTemplateEntity {
        return FaceTemplateEntity(
            id = this.id,
            studentId = this.studentId,
            embedding = floatArrayToByteArray(this.embedding),
            qualityScore = this.qualityScore
        )
    }
    
    fun FaceTemplateEntity.toFloatArray(): FloatArray {
        return byteArrayToFloatArray(this.embedding)
    }
    
    private fun floatArrayToByteArray(floats: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(floats.size * 4)
        buffer.asFloatBuffer().put(floats)
        return buffer.array()
    }
    
    private fun byteArrayToFloatArray(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes)
        val floats = FloatArray(bytes.size / 4)
        buffer.asFloatBuffer().get(floats)
        return floats
    }
}
