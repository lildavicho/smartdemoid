package com.smartpresence.idukay.data.local.dao

import androidx.room.*
import com.smartpresence.idukay.data.local.entity.PendingSessionUpdateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingSessionUpdateDao {
    
    @Query("SELECT * FROM pending_session_updates WHERE syncStatus IN ('PENDING', 'FAILED') ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getPending(limit: Int = 20): List<PendingSessionUpdateEntity>
    
    @Query("SELECT COUNT(*) FROM pending_session_updates WHERE syncStatus IN ('PENDING', 'FAILED')")
    fun getPendingCount(): Flow<Int>
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(update: PendingSessionUpdateEntity): Long
    
    @Query("UPDATE pending_session_updates SET syncStatus = 'SENT' WHERE id = :id")
    suspend fun markSent(id: Long)
    
    @Query("UPDATE pending_session_updates SET syncStatus = 'FAILED', retryCount = retryCount + 1 WHERE id = :id")
    suspend fun markFailed(id: Long)
    
    @Query("DELETE FROM pending_session_updates WHERE syncStatus = 'SENT' AND timestamp < :olderThan")
    suspend fun deleteSent(olderThan: Long): Int
    
    @Query("DELETE FROM pending_session_updates")
    suspend fun deleteAll()
}
