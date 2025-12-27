package com.smartpresence.idukay.data.remote.dto

data class CourseRosterResponse(
    val course: CourseDto,
    val students: List<StudentDto>,
    val totalStudents: Int
)

data class CourseDto(
    val id: String,
    val name: String,
    val code: String,
    val academicPeriod: String
)

data class StudentDto(
    val id: String,
    val documentId: String,
    val firstName: String,
    val lastName: String,
    val email: String?
)

data class FaceTemplateDto(
    val id: String,
    val studentId: String,
    val embedding: FloatArray,
    val qualityScore: Float
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FaceTemplateDto
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
