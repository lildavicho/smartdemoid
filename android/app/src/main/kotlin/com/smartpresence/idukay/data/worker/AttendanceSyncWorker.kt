package com.smartpresence.idukay.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.smartpresence.idukay.data.local.SyncHealthDataStore
import com.smartpresence.idukay.data.local.dao.PendingAttendanceDao
import com.smartpresence.idukay.data.local.dao.PendingSessionUpdateDao
import com.smartpresence.idukay.data.local.util.JsonSerializationHelper
import com.smartpresence.idukay.data.remote.api.SmartPresenceApi
import com.smartpresence.idukay.data.remote.dto.AttendanceEventDto
import com.smartpresence.idukay.data.remote.dto.AttendanceRecordDto
import com.smartpresence.idukay.data.remote.dto.BatchEventsRequest
import com.smartpresence.idukay.data.remote.dto.FinalizeSessionRequest
import com.smartpresence.idukay.data.repository.AuthRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import retrofit2.HttpException
import timber.log.Timber
import java.time.Instant

@HiltWorker
class AttendanceSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val pendingDao: PendingAttendanceDao,
    private val pendingSessionUpdateDao: PendingSessionUpdateDao,
    private val syncHealthDataStore: SyncHealthDataStore,
    private val authRepository: AuthRepository,
    private val api: SmartPresenceApi
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        return try {
            var totalSuccess = 0
            var totalFailed = 0
            
            // Sync pending attendance events with idempotency
            val attendanceResult = syncPendingAttendance()
            totalSuccess += attendanceResult.first
            totalFailed += attendanceResult.second
            
            // Sync pending session finalizations with idempotency
            val sessionResult = syncPendingSessionUpdates()
            totalSuccess += sessionResult.first
            totalFailed += sessionResult.second
            
            // Cleanup old sent records (older than 7 days)
            val oneWeekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
            val deletedAttendance = pendingDao.deleteSent(oneWeekAgo)
            val deletedSessionUpdates = pendingSessionUpdateDao.deleteSent(oneWeekAgo)
            
            if (deletedAttendance > 0 || deletedSessionUpdates > 0) {
                Timber.d("Cleanup: deleted $deletedAttendance old attendance records and $deletedSessionUpdates old session updates")
            }
            
            Timber.d("Sync completed: $totalSuccess success, $totalFailed failed")
            
            if (totalFailed > 0 && totalSuccess == 0) {
                val errorMsg = "Sync failed: $totalFailed items failed"
                syncHealthDataStore.recordFailure(errorMsg)
                Timber.w(errorMsg)
                Result.retry()
            } else {
                if (totalSuccess > 0) {
                    syncHealthDataStore.recordSuccess()
                }
                Result.success()
            }
        } catch (e: Exception) {
            Timber.e(e, "Sync worker failed")
            Result.retry()
        }
    }
    
    /**
     * Sync pending attendance events using idempotent batch endpoint
     */
    private suspend fun syncPendingAttendance(): Pair<Int, Int> {
        val pending = pendingDao.getPending(limit = 50)
        
        if (pending.isEmpty()) {
            Timber.d("No pending attendance to sync")
            return Pair(0, 0)
        }
        
        Timber.d("Syncing ${pending.size} pending attendance records")
        
        // Group by sessionId
        val groupedBySession = pending.groupBy { it.sessionId }
        var successCount = 0
        var failCount = 0
        
        for ((sessionId, records) in groupedBySession) {
            try {
                // Map to AttendanceEventDto with idempotencyKey = localId
                val events = records.map { record ->
                    AttendanceEventDto(
                        studentId = record.studentId,
                        occurredAt = Instant.ofEpochMilli(record.timestamp).toString(),
                        confidence = record.confidence,
                        idempotencyKey = record.localId, // Use localId as idempotency key
                        source = "edge"
                    )
                }
                
                val request = BatchEventsRequest(
                    sessionId = sessionId,
                    events = events
                )
                
                // Call idempotent batch endpoint
                val response = api.batchInsertEvents(request)
                
                if (response.success) {
                    val data = response.data
                    Timber.d("Batch events for session $sessionId: ${data?.inserted} inserted, ${data?.ignored} ignored (duplicates)")
                    
                    // Mark all as SENT (even if ignored by server due to idempotency)
                    records.forEach { record ->
                        pendingDao.markSent(record.id)
                        successCount++
                    }
                } else {
                    Timber.w("Batch events failed for session $sessionId: ${response.message}")
                    records.forEach { record ->
                        if (record.retryCount < 3) {
                            pendingDao.markFailed(record.id)
                        }
                        failCount++
                    }
                }
            } catch (e: HttpException) {
                if (e.code() == 409) {
                    // Device mismatch - log and mark as failed
                    Timber.w("Device mismatch for session $sessionId")
                    syncHealthDataStore.recordFailure("Device mismatch - rebind required")
                    records.forEach { record ->
                        pendingDao.markFailed(record.id)
                        failCount++
                    }
                } else {
                    Timber.e(e, "HTTP error syncing session $sessionId")
                    records.forEach { record ->
                        if (record.retryCount < 3) {
                            pendingDao.markFailed(record.id)
                        }
                        failCount++
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync session $sessionId")
                records.forEach { record ->
                    if (record.retryCount < 3) {
                        pendingDao.markFailed(record.id)
                    }
                    failCount++
                }
            }
        }
        
        return Pair(successCount, failCount)
    }
    
    /**
     * Sync pending session finalizations using idempotent finalize endpoint
     */
    private suspend fun syncPendingSessionUpdates(): Pair<Int, Int> {
        val pending = pendingSessionUpdateDao.getPending(limit = 20)
        
        if (pending.isEmpty()) {
            Timber.d("No pending session updates to sync")
            return Pair(0, 0)
        }
        
        Timber.d("Syncing ${pending.size} pending session updates")
        
        var successCount = 0
        var failCount = 0
        
        for (update in pending) {
            try {
                val teacherId = authRepository.getTeacherId() ?: ""
                
                // Get courseId from update, fallback to empty if old data
                val courseId = update.courseId ?: ""
                
                if (courseId.isBlank()) {
                    Timber.w("Session ${update.sessionId} has no courseId (old data), marking as FAILED")
                    syncHealthDataStore.recordFailure("Session finalization missing courseId")
                    pendingSessionUpdateDao.markFailed(update.id)
                    failCount++
                    continue
                }
                
                // Decode records from JSON
                val records = JsonSerializationHelper.decodeAttendanceRecordsFromJson(update.recordsJson)
                
                // Map to AttendanceRecordDto
                val recordDtos = records.map { record ->
                    AttendanceRecordDto(
                        studentId = record.studentId,
                        status = "present",
                        confidence = record.confidence,
                        confirmedBy = "system",
                        detectedAt = record.detectedAt
                    )
                }
                
                val request = FinalizeSessionRequest(
                    sessionId = update.sessionId,
                    teacherId = teacherId,
                    courseId = courseId,
                    recordsJson = recordDtos,
                    idempotencyKey = update.localId // Use localId as idempotency key
                )
                
                // Call idempotent finalize endpoint
                val response = api.finalizeSession(request)
                
                if (response.success) {
                    val status = response.data?.status ?: "unknown"
                    Timber.d("Session ${update.sessionId} finalized: $status")
                    
                    pendingSessionUpdateDao.markSent(update.id)
                    successCount++
                } else {
                    Timber.w("Session finalization failed for ${update.sessionId}: ${response.message}")
                    if (update.retryCount < 3) {
                        pendingSessionUpdateDao.markFailed(update.id)
                    }
                    failCount++
                }
            } catch (e: HttpException) {
                if (e.code() == 409) {
                    // Device mismatch
                    Timber.w("Device mismatch for session ${update.sessionId}")
                    syncHealthDataStore.recordFailure("Device mismatch - rebind required")
                    pendingSessionUpdateDao.markFailed(update.id)
                    failCount++
                } else {
                    Timber.e(e, "HTTP error syncing session update ${update.sessionId}")
                    if (update.retryCount < 3) {
                        pendingSessionUpdateDao.markFailed(update.id)
                    }
                    failCount++
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync session update ${update.sessionId}")
                if (update.retryCount < 3) {
                    pendingSessionUpdateDao.markFailed(update.id)
                }
                failCount++
            }
        }
        
        return Pair(successCount, failCount)
    }
}
