package com.smartpresence.idukay.data.repository

import com.smartpresence.idukay.data.local.dao.FaceTemplateDao
import com.smartpresence.idukay.data.local.dao.StudentDao
import com.smartpresence.idukay.data.local.mapper.EntityMapper.toEntity
import com.smartpresence.idukay.data.local.mapper.EntityMapper.toFloatArray
import com.smartpresence.idukay.data.local.entity.StudentEntity
import com.smartpresence.idukay.data.local.entity.FaceTemplateEntity
import com.smartpresence.idukay.data.remote.api.SmartPresenceApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StudentsRepository @Inject constructor(
    private val api: SmartPresenceApi,
    private val studentDao: StudentDao,
    private val faceTemplateDao: FaceTemplateDao
) {
    
    private val cacheValidityMs = 24 * 60 * 60 * 1000L
    
    suspend fun getRoster(courseId: String, forceRefresh: Boolean = false): Result<Map<String, FloatArray>> {
        return try {
            val cachedStudents = studentDao.getStudentsByCourse(courseId).first()
            val lastUpdatedAt = cachedStudents.maxOfOrNull { it.updatedAt } ?: 0L
            val shouldFetch = forceRefresh || 
                             cachedStudents.isEmpty() || 
                             System.currentTimeMillis() - lastUpdatedAt > cacheValidityMs
            
            if (shouldFetch) {
                Timber.d("Fetching roster from API for course: $courseId")
                val roster = api.getCourseRoster(courseId)
                
                val students = roster.students.map { it.toEntity(courseId) }
                studentDao.insertStudents(students)
                
                val embeddings = mutableMapOf<String, FloatArray>()
                for (student in roster.students) {
                    val templates = api.getStudentFaceTemplates(student.id)
                    if (templates.isNotEmpty()) {
                        val templateEntities = templates.map { it.toEntity() }
                        faceTemplateDao.insertTemplates(templateEntities)
                        embeddings[student.id] = templates.first().embedding
                    }
                }
                
                Timber.d("Cached ${students.size} students and ${embeddings.size} templates")
                Result.success(embeddings)
            } else {
                Timber.d("Using cached roster for course: $courseId")
                val embeddings = getTemplatesFromCache(courseId)
                Result.success(embeddings)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get roster, trying cache")
            try {
                val embeddings = getTemplatesFromCache(courseId)
                if (embeddings.isNotEmpty()) {
                    Result.success(embeddings)
                } else {
                    Result.failure(e)
                }
            } catch (cacheError: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Get templates from local cache for a course
     */
    suspend fun getTemplatesFromCache(courseId: String): Map<String, FloatArray> {
        val students = studentDao.getStudentsByCourse(courseId).first()
        if (students.isEmpty()) {
            return emptyMap()
        }
        
        val studentIds = students.map { it.id }
        val templates = faceTemplateDao.getTemplatesByStudentIds(studentIds)
        
        val embeddings = mutableMapOf<String, FloatArray>()
        for (template in templates) {
            embeddings[template.studentId] = template.toFloatArray()
        }
        
        return embeddings
    }
    
    /**
     * Get course info by ID (returns null if not cached)
     */
    suspend fun getCourseById(courseId: String): CourseInfo? {
        val students = studentDao.getStudentsByCourse(courseId).first()
        return if (students.isNotEmpty()) {
            CourseInfo(
                id = courseId,
                name = "Curso $courseId"
            )
        } else {
            null
        }
    }
    
    suspend fun forceRefresh(courseId: String): Result<Map<String, FloatArray>> {
        return getRoster(courseId, forceRefresh = true)
    }

    fun observeStudentsByCourse(courseId: String): Flow<List<StudentEntity>> {
        return studentDao.getStudentsByCourse(courseId)
    }

    suspend fun getStudentDisplayName(studentId: String): String? {
        val student = studentDao.getStudentById(studentId) ?: return null
        val name = "${student.firstName} ${student.lastName}".trim()
        return name.ifBlank { null }
    }
}

/**
 * Simple course info data class
 */
data class CourseInfo(
    val id: String,
    val name: String
)
