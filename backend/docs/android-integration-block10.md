# Block 10: Backend Support - Android Integration Guide

## Overview

This guide explains how to integrate the new backend endpoints for device binding, idempotent attendance events, and session finalization into the Android application.

---

## 1. Device Binding Integration

### AuthRepository.kt

Add device binding call after successful login:

```kotlin
suspend fun bindDevice(deviceId: String): Result<Unit> {
    return try {
        val response = apiService.bindDevice(
            BindDeviceRequest(
                teacherId = getTeacherId() ?: "",
                deviceId = deviceId,
                metadata = mapOf(
                    "model" to Build.MODEL,
                    "manufacturer" to Build.MANUFACTURER,
                    "osVersion" to Build.VERSION.RELEASE
                )
            )
        )
        
        if (response.success) {
            Result.success(Unit)
        } else {
            Result.failure(Exception(response.message))
        }
    } catch (e: HttpException) {
        if (e.code() == 409) {
            // Device mismatch
            val errorBody = e.response()?.errorBody()?.string()
            Result.failure(DeviceMismatchException(errorBody))
        } else {
            Result.failure(e)
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

### LoginViewModel.kt

Call bindDevice after login:

```kotlin
private suspend fun checkDeviceBinding() {
    val currentDeviceId = authRepository.getDeviceId() ?: ""
    
    val bindResult = authRepository.bindDevice(currentDeviceId)
    
    bindResult.fold(
        onSuccess = {
            // Binding successful, proceed
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                loginSuccess = true
            )
        },
        onFailure = { error ->
            if (error is DeviceMismatchException) {
                // Show DeviceNotAuthorizedScreen
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    deviceNotAuthorized = true,
                    currentDeviceId = currentDeviceId
                )
            } else {
                // Other error
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = error.message
                )
            }
        }
    )
}
```

---

## 2. Idempotent Attendance Events

### AttendanceSyncWorker.kt

Update to use batch events endpoint with idempotency keys:

```kotlin
private suspend fun syncPendingAttendance(): Result<Unit> {
    return try {
        val pending = pendingAttendanceDao.getPending().first()
        
        if (pending.isEmpty()) {
            return Result.success(Unit)
        }
        
        // Group by sessionId
        val bySession = pending.groupBy { it.sessionId }
        
        for ((sessionId, records) in bySession) {
            val events = records.map { record ->
                AttendanceEventDto(
                    studentId = record.studentId,
                    occurredAt = Instant.ofEpochMilli(record.timestamp).toString(),
                    confidence = record.confidence,
                    idempotencyKey = record.localId, // Use localId as idempotency key
                    source = "edge"
                )
            }
            
            val response = apiService.batchInsertEvents(
                BatchEventsRequest(
                    sessionId = sessionId,
                    events = events
                )
            )
            
            if (response.success) {
                // Mark as sent
                val localIds = records.map { it.localId }
                pendingAttendanceDao.markAsSent(localIds)
                
                Timber.d("Synced ${response.data.inserted} events, ${response.data.ignored} duplicates")
            }
        }
        
        Result.success(Unit)
    } catch (e: Exception) {
        Timber.e(e, "Failed to sync attendance events")
        Result.failure(e)
    }
}
```

---

## 3. Idempotent Session Finalization

### AttendanceSyncWorker.kt

Update session finalization to use idempotency:

```kotlin
private suspend fun syncPendingSessionUpdates(): Result<Unit> {
    return try {
        val pending = pendingSessionUpdateDao.getPending().first()
        
        if (pending.isEmpty()) {
            return Result.success(Unit)
        }
        
        for (update in pending) {
            val records = JsonSerializationHelper.decodeAttendanceRecordsFromJson(update.recordsJson)
            
            val response = apiService.finalizeSession(
                FinalizeSessionRequest(
                    sessionId = update.sessionId,
                    teacherId = authRepository.getTeacherId() ?: "",
                    courseId = getCourseIdForSession(update.sessionId),
                    recordsJson = records,
                    idempotencyKey = update.localId // Use localId as idempotency key
                )
            )
            
            if (response.success) {
                // Mark as sent
                pendingSessionUpdateDao.markAsSent(update.localId)
                
                Timber.d("Finalized session ${update.sessionId}: ${response.data.status}")
            }
        }
        
        Result.success(Unit)
    } catch (e: Exception) {
        Timber.e(e, "Failed to sync session updates")
        Result.failure(e)
    }
}
```

---

## 4. API Service Updates

### ApiService.kt

Add new endpoints:

```kotlin
@POST("api/v1/devices/bind")
suspend fun bindDevice(@Body request: BindDeviceRequest): ApiResponse<BindDeviceResponse>

@POST("api/v1/devices/rebind")
suspend fun rebindDevice(@Body request: RebindDeviceRequest): ApiResponse<RebindDeviceResponse>

@POST("api/v1/attendance/events/batch")
suspend fun batchInsertEvents(@Body request: BatchEventsRequest): ApiResponse<BatchEventsResponse>

@POST("api/v1/attendance/sessions/finalize")
suspend fun finalizeSession(@Body request: FinalizeSessionRequest): ApiResponse<FinalizeSessionResponse>
```

---

## 5. DTOs

### BindDeviceRequest.kt

```kotlin
data class BindDeviceRequest(
    val teacherId: String,
    val deviceId: String,
    val metadata: Map<String, String>? = null
)
```

### BatchEventsRequest.kt

```kotlin
data class BatchEventsRequest(
    val sessionId: String,
    val events: List<AttendanceEventDto>
)

data class AttendanceEventDto(
    val studentId: String,
    val occurredAt: String, // ISO 8601
    val confidence: Float?,
    val idempotencyKey: String,
    val source: String = "edge"
)
```

### FinalizeSessionRequest.kt

```kotlin
data class FinalizeSessionRequest(
    val sessionId: String,
    val teacherId: String,
    val courseId: String,
    val recordsJson: List<AttendanceRecordDto>,
    val idempotencyKey: String
)
```

---

## 6. Error Handling

### DeviceMismatchException.kt

```kotlin
class DeviceMismatchException(message: String?) : Exception(message)
```

Handle 409 errors in interceptor or repository:

```kotlin
if (response.code() == 409) {
    val errorBody = response.errorBody()?.string()
    val error = Json.decodeFromString<ErrorResponse>(errorBody ?: "")
    
    if (error.code == "DEVICE_MISMATCH") {
        throw DeviceMismatchException(error.message)
    }
}
```

---

## Testing

### Test 1: Device Binding
1. Login on device A → bind success
2. Login on device B → 409 DEVICE_MISMATCH
3. Rebind with admin PIN → success

### Test 2: Idempotent Events
1. Confirm students offline
2. Sync → events inserted
3. Sync again → events ignored (duplicates)

### Test 3: Idempotent Finalization
1. End session offline
2. Sync → session finalized
3. Sync again → already_applied

---

## Environment Variables

No new environment variables needed on Android side.

Backend requires:
```env
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_SERVICE_ROLE_KEY=eyJ...
```
