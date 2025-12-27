package com.smartpresence.idukay.data.local.dao

import androidx.room.*
import com.smartpresence.idukay.data.local.entity.PendingAttendanceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingAttendanceDao {
    
    @Query("SELECT * FROM pending_attendance WHERE status IN ('PENDING', 'FAILED') ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getPending(limit: Int = 20): List<PendingAttendanceEntity>
    
    @Query("SELECT COUNT(*) FROM pending_attendance WHERE status IN ('PENDING', 'FAILED')")
    fun getPendingCount(): Flow<Int>

    @Query("SELECT * FROM pending_attendance WHERE sessionId = :sessionId AND status != 'CANCELED' ORDER BY confirmedAt DESC")
    fun observeBySession(sessionId: String): Flow<List<PendingAttendanceEntity>>

    @Query("SELECT * FROM pending_attendance WHERE sessionId = :sessionId AND studentId = :studentId ORDER BY confirmedAt DESC LIMIT 1")
    suspend fun getLatestBySessionAndStudent(sessionId: String, studentId: String): PendingAttendanceEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attendance: PendingAttendanceEntity): Long
    
    @Query("UPDATE pending_attendance SET status = 'SENT' WHERE id = :id AND status != 'CANCELED'")
    suspend fun markSent(id: Long)
    
    @Query("UPDATE pending_attendance SET status = 'FAILED', retryCount = retryCount + 1 WHERE id = :id AND status != 'CANCELED'")
    suspend fun markFailed(id: Long)

    @Query("UPDATE pending_attendance SET status = 'CANCELED' WHERE id = :id")
    suspend fun markCanceled(id: Long)
    
    @Query("DELETE FROM pending_attendance WHERE status = 'SENT' AND timestamp < :olderThan")
    suspend fun deleteSent(olderThan: Long): Int
    
    @Query("DELETE FROM pending_attendance")
    suspend fun deleteAll()
}
