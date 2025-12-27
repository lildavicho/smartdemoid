package com.smartpresence.idukay.data.local.dao

import androidx.room.*
import com.smartpresence.idukay.data.local.entity.CourseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {
    
    @Query("SELECT * FROM courses")
    fun getAllCourses(): Flow<List<CourseEntity>>
    
    @Query("SELECT * FROM courses WHERE id = :courseId")
    suspend fun getCourseById(courseId: String): CourseEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourse(course: CourseEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourses(courses: List<CourseEntity>)
    
    @Delete
    suspend fun deleteCourse(course: CourseEntity)
    
    @Query("DELETE FROM courses")
    suspend fun deleteAllCourses()
}
