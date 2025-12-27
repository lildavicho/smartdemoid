package com.smartpresence.idukay.data.local.dao

import androidx.room.*
import com.smartpresence.idukay.data.local.entity.StudentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {
    
    @Query("SELECT * FROM students")
    fun getAllStudents(): Flow<List<StudentEntity>>
    
    @Query("SELECT * FROM students WHERE courseId = :courseId")
    fun getStudentsByCourse(courseId: String): Flow<List<StudentEntity>>
    
    @Query("SELECT * FROM students WHERE id = :studentId")
    suspend fun getStudentById(studentId: String): StudentEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudent(student: StudentEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudents(students: List<StudentEntity>)
    
    @Delete
    suspend fun deleteStudent(student: StudentEntity)
    
    @Query("DELETE FROM students")
    suspend fun deleteAllStudents()
}
