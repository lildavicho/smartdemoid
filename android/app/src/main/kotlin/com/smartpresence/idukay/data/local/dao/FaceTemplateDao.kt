package com.smartpresence.idukay.data.local.dao

import androidx.room.*
import com.smartpresence.idukay.data.local.entity.FaceTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FaceTemplateDao {
    
    @Query("SELECT * FROM face_templates WHERE studentId = :studentId")
    suspend fun getTemplatesByStudent(studentId: String): List<FaceTemplateEntity>
    
    @Query("SELECT * FROM face_templates WHERE studentId IN (:studentIds)")
    suspend fun getTemplatesByStudentIds(studentIds: List<String>): List<FaceTemplateEntity>
    
    @Query("SELECT * FROM face_templates")
    fun getAllTemplates(): Flow<List<FaceTemplateEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: FaceTemplateEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplates(templates: List<FaceTemplateEntity>)
    
    @Query("DELETE FROM face_templates WHERE studentId = :studentId")
    suspend fun deleteTemplatesByStudent(studentId: String)
    
    @Query("DELETE FROM face_templates")
    suspend fun deleteAllTemplates()
}
