package com.smartpresence.idukay.data.local.dao

import androidx.room.*
import com.smartpresence.idukay.data.local.entity.AttendanceSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceSessionDao {
    
    @Query("SELECT * FROM attendance_sessions ORDER BY startedAt DESC")
    fun getAllSessions(): Flow<List<AttendanceSessionEntity>>
    
    @Query("SELECT * FROM attendance_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: String): AttendanceSessionEntity?
    
    @Query("SELECT * FROM attendance_sessions WHERE courseId = :courseId ORDER BY startedAt DESC")
    fun getSessionsByCourse(courseId: String): Flow<List<AttendanceSessionEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: AttendanceSessionEntity)
    
    @Update
    suspend fun updateSession(session: AttendanceSessionEntity)
    
    @Delete
    suspend fun deleteSession(session: AttendanceSessionEntity)
    
    @Query("DELETE FROM attendance_sessions")
    suspend fun deleteAllSessions()
}
