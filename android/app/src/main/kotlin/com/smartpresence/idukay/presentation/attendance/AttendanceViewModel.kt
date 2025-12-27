package com.smartpresence.idukay.presentation.attendance

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.smartpresence.idukay.ai.RecognitionConfig
import com.smartpresence.idukay.ai.pipeline.TrackRecognitionStatus
import com.smartpresence.idukay.ai.pipeline.RecognitionPipeline
import com.smartpresence.idukay.ai.pipeline.RecognitionResult
import com.smartpresence.idukay.data.local.SettingsDataStore
import com.smartpresence.idukay.data.local.dao.PendingAttendanceDao
import com.smartpresence.idukay.data.local.dao.PendingSessionUpdateDao
import com.smartpresence.idukay.data.local.entity.PendingAttendanceEntity
import com.smartpresence.idukay.data.local.entity.PendingSessionUpdateEntity
import com.smartpresence.idukay.data.local.util.JsonSerializationHelper
import com.smartpresence.idukay.data.remote.api.SmartPresenceApi
import com.smartpresence.idukay.data.remote.dto.AttendanceRecordDto
import com.smartpresence.idukay.data.remote.dto.CreateSessionRequest
import com.smartpresence.idukay.data.remote.dto.UpdateSessionRequest
import com.smartpresence.idukay.data.repository.AuthRepository
import com.smartpresence.idukay.data.repository.StudentsRepository
import com.smartpresence.idukay.data.worker.AttendanceSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt
import javax.inject.Inject

data class OverlayFaceUi(
    val trackId: Int,
    val bbox: FloatArray,
    val label: String,
    val isConfirmed: Boolean,
    val votes: Int,
    val requiredVotes: Int
)

data class AttendanceUiState(
    val isLoading: Boolean = false,
    val isSessionActive: Boolean = false,
    val sessionId: String? = null,
    val courseId: String = "",
    val courseName: String = "",
    val recognizedStudents: Map<String, RecognitionResult> = emptyMap(),
    val confirmedStudents: Set<String> = emptySet(),
    val confirmedRecords: Map<String, ConfirmedRecord> = emptyMap(),
    val error: String? = null,
    val modelLoadingError: String? = null,
    val cameraPermissionGranted: Boolean = false,
    val pendingCount: Int = 0,
    val pendingSessionUpdateCount: Int = 0,
    val isOffline: Boolean = false,
    val thresholdMode: RecognitionConfig.ThresholdMode = RecognitionConfig.ThresholdMode.NORMAL,
    val debugOverlay: Boolean = true,
    val powerMode: RecognitionConfig.PowerMode = RecognitionConfig.PowerMode.BALANCED,
    val autoConfirmEnabled: Boolean = false,
    val autoConfirmMinConfidence: Float = 0.75f,
    val overlayFaces: List<OverlayFaceUi> = emptyList(),
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val fps: Int = 0,
    val msPerFrame: Long = 0L,
    val kioskEnabled: Boolean = false
)

sealed interface AttendanceUiEvent {
    data class Snackbar(val message: String) : AttendanceUiEvent
}

enum class ConfirmSource {
    MANUAL,
    AUTO
}

@HiltViewModel
class AttendanceViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recognitionPipeline: RecognitionPipeline,
    private val api: SmartPresenceApi,
    private val authRepository: AuthRepository,
    private val studentsRepository: StudentsRepository,
    private val pendingDao: PendingAttendanceDao,
    private val pendingSessionUpdateDao: PendingSessionUpdateDao,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(AttendanceUiState())
    val uiState: StateFlow<AttendanceUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AttendanceUiEvent>(extraBufferCapacity = 16)
    val events = _events.asSharedFlow()

    private val confirmMutex = Mutex()
    private val inFlightConfirmations = mutableSetOf<String>()

    private var studentNamesJob: Job? = null
    private var confirmedRecordsJob: Job? = null
    private var studentNameById: Map<String, String> = emptyMap()

    private var studentEmbeddings: Map<String, FloatArray> = emptyMap()
    private var frameQueue: ConflatedFrameQueue<CameraFrame>? = null
    private var frameProcessorJob: Job? = null
    private var lastFrameProcessedAtMs: Long = 0L
    private val studentCooldowns = mutableMapOf<String, Long>()
    private val cooldownMs = RecognitionConfig.COOLDOWN_SECONDS * 1000L
    private val autoConfirmCooldownMs = 60_000L

    private class ConflatedFrameQueue<T>(
        private val onDrop: (T) -> Unit
    ) {
        private val latest = AtomicReference<T?>(null)
        private val signal = Channel<Unit>(capacity = Channel.CONFLATED)
        private val closed = AtomicBoolean(false)

        fun offer(item: T) {
            if (closed.get()) {
                onDrop(item)
                return
            }

            val previous = latest.getAndSet(item)
            if (previous != null) onDrop(previous)

            val result = signal.trySend(Unit)
            if (result.isFailure) {
                val dropped = latest.getAndSet(null)
                if (dropped != null) onDrop(dropped)
            }
        }

        suspend fun take(): T? {
            while (true) {
                signal.receiveCatching().getOrNull() ?: return null
                val item = latest.getAndSet(null)
                if (item != null) return item
            }
        }

        fun clear() {
            val item = latest.getAndSet(null)
            if (item != null) onDrop(item)
        }

        fun close() {
            closed.set(true)
            signal.close()
            clear()
        }
    }

    private data class TrackLock(
        var studentId: String,
        var distance: Float,
        var lockedAtMs: Long,
        var lastSeenMs: Long
    )

    private val trackLocks = mutableMapOf<Int, TrackLock>()
    private val stabilityLockMs = 2_500L
    private val switchDistanceDelta = 0.06f
    private val lockExpiryMs = 2_000L

    private var lastFrameTimestampMs: Long = 0L
    private var fpsSmoothed: Float = 0f
    
    init {
        observePendingCount()
        observePendingSessionUpdateCount()
        loadSettings()
        checkModelLoadingErrors()
    }
    
    private fun checkModelLoadingErrors() {
        val errors = recognitionPipeline.modelLoadingErrors
        if (errors.isNotEmpty()) {
            val errorMessage = "AI Models failed to load: ${errors.joinToString(", ")}"
            _uiState.value = _uiState.value.copy(modelLoadingError = errorMessage)
            Timber.e(errorMessage)
        }
    }
    
    private fun loadSettings() {
        viewModelScope.launch {
            settingsDataStore.thresholdMode.collect { mode ->
                recognitionPipeline.setThresholdMode(mode)
                _uiState.value = _uiState.value.copy(thresholdMode = mode)
            }
        }
        viewModelScope.launch {
            settingsDataStore.debugOverlay.collect { enabled ->
                _uiState.value = _uiState.value.copy(debugOverlay = enabled)
            }
        }
        viewModelScope.launch {
            settingsDataStore.powerMode.collect { mode ->
                recognitionPipeline.setQualityMin(RecognitionConfig.effectiveQualityMin(mode))
                _uiState.value = _uiState.value.copy(powerMode = mode)
            }
        }
        viewModelScope.launch {
            settingsDataStore.autoConfirmEnabled.collect { enabled ->
                _uiState.value = _uiState.value.copy(autoConfirmEnabled = enabled)
            }
        }
        viewModelScope.launch {
            settingsDataStore.autoConfirmMinConfidence.collect { minConfidence ->
                _uiState.value = _uiState.value.copy(autoConfirmMinConfidence = minConfidence)
            }
        }
        viewModelScope.launch {
            settingsDataStore.kioskEnabled.collect { enabled ->
                _uiState.value = _uiState.value.copy(kioskEnabled = enabled)
            }
        }
    }
    
    private fun observePendingCount() {
        viewModelScope.launch {
            pendingDao.getPendingCount().collect { count ->
                _uiState.value = _uiState.value.copy(pendingCount = count)
            }
        }
    }

    private fun observePendingSessionUpdateCount() {
        viewModelScope.launch {
            pendingSessionUpdateDao.getPendingCount().collect { count ->
                _uiState.value = _uiState.value.copy(pendingSessionUpdateCount = count)
            }
        }
    }
    
    fun setCourseId(courseId: String) {
        _uiState.value = _uiState.value.copy(courseId = courseId)
        observeStudentNames(courseId)
        loadCourseRoster(courseId)
    }
    
    fun setCameraPermission(granted: Boolean) {
        _uiState.value = _uiState.value.copy(cameraPermissionGranted = granted)
    }

    fun setThresholdMode(mode: RecognitionConfig.ThresholdMode) {
        recognitionPipeline.setThresholdMode(mode)
        _uiState.value = _uiState.value.copy(thresholdMode = mode)
        viewModelScope.launch {
            settingsDataStore.setThresholdMode(mode)
        }
    }
    
    fun setDebugOverlay(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(debugOverlay = enabled)
        viewModelScope.launch {
            settingsDataStore.setDebugOverlay(enabled)
        }
    }

    fun setPowerMode(mode: RecognitionConfig.PowerMode) {
        recognitionPipeline.setQualityMin(RecognitionConfig.effectiveQualityMin(mode))
        _uiState.value = _uiState.value.copy(powerMode = mode)
        viewModelScope.launch {
            settingsDataStore.setPowerMode(mode)
        }
    }

    fun setAutoConfirmEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(autoConfirmEnabled = enabled)
        viewModelScope.launch {
            settingsDataStore.setAutoConfirmEnabled(enabled)
        }
    }

    fun setAutoConfirmMinConfidence(value: Float) {
        _uiState.value = _uiState.value.copy(autoConfirmMinConfidence = value)
        viewModelScope.launch {
            settingsDataStore.setAutoConfirmMinConfidence(value)
        }
    }
    
    private fun loadCourseRoster(courseId: String) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                
                val result = studentsRepository.getRoster(courseId)
                
                result.onSuccess { embeddings ->
                    studentEmbeddings = embeddings
                    recognitionPipeline.setStudentRoster(embeddings)
                    
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        courseName = "Curso $courseId",
                        isOffline = false
                    )
                    
                    Timber.d("Loaded ${embeddings.size} student embeddings from cache/API")
                }.onFailure { e ->
                    Timber.e(e, "Failed to load roster")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Error al cargar roster: ${e.message}",
                        isOffline = true
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load course roster")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error: ${e.message}",
                    isOffline = true
                )
            }
        }
    }
    
    fun forceRefresh() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                
                val result = studentsRepository.forceRefresh(_uiState.value.courseId)
                
                result.onSuccess { embeddings ->
                    studentEmbeddings = embeddings
                    recognitionPipeline.setStudentRoster(embeddings)
                    
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isOffline = false
                    )
                    
                    Timber.d("Force refreshed ${embeddings.size} embeddings")
                }.onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Error al sincronizar: ${e.message}"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Force refresh failed")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error: ${e.message}"
                )
            }
        }
    }
    
    fun startSession() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                
                val teacherId = authRepository.getTeacherId()
                val deviceId = authRepository.getDeviceId()
                val courseId = _uiState.value.courseId
                
                Timber.d("Starting session - courseId: $courseId, teacherId: $teacherId, deviceId: $deviceId")
                
                // Validate all required fields
                if (teacherId.isNullOrBlank()) {
                    throw IllegalStateException("Teacher ID not found in session")
                }
                if (deviceId.isNullOrBlank()) {
                    throw IllegalStateException("Device ID not found in session")
                }
                if (courseId.isBlank()) {
                    throw IllegalStateException("Course ID is required. Please select a course first.")
                }
                
                val request = CreateSessionRequest(
                    courseId = courseId,
                    teacherId = teacherId,
                    deviceId = deviceId
                )
                
                val response = api.createAttendanceSession(request)
                
                // Response is the session object directly (not wrapped in "session" field)
                val sessionId = response.id
                Timber.d("Session created successfully: sessionId=$sessionId, status=${response.status}")
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isSessionActive = true,
                    sessionId = sessionId,
                    recognizedStudents = emptyMap(),
                    confirmedStudents = emptySet(),
                    confirmedRecords = emptyMap(),
                    error = null
                )

                observeConfirmedRecords(sessionId)
                
                studentCooldowns.clear()
                recognitionPipeline.reset()
                trackLocks.clear()
                stopFrameProcessing()
                startFrameProcessing()
                lastFrameProcessedAtMs = 0L
                lastFrameTimestampMs = 0L
                fpsSmoothed = 0f
                
                Timber.d("Started attendance session: $sessionId")
            } catch (e: Exception) {
                Timber.e(e, "Failed to start session: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isSessionActive = false,
                    error = "Error al iniciar sesión: ${e.message}"
                )
            }
        }
    }
    
    fun submitFrame(frame: CameraFrame) {
        val queue = frameQueue
        if (!_uiState.value.isSessionActive || queue == null) {
            frame.release()
            return
        }

        queue.offer(frame)
    }

    private fun startFrameProcessing() {
        val queue = ConflatedFrameQueue<CameraFrame>(onDrop = { it.release() })
        frameQueue = queue

        frameProcessorJob?.cancel()
        frameProcessorJob = viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                val frame = queue.take() ?: break
                try {
                    if (_uiState.value.isSessionActive) {
                        val intervalMs = RecognitionConfig.frameIntervalMs(_uiState.value.powerMode)
                        val nowMs = System.currentTimeMillis()
                        if (intervalMs > 0 && nowMs - lastFrameProcessedAtMs < intervalMs) {
                            continue
                        }
                        lastFrameProcessedAtMs = nowMs
                        processFrameInternal(frame.bitmap, frame.timestampMs)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Frame processing loop failed")
                } finally {
                    frame.release()
                }
            }
        }
    }

    private fun stopFrameProcessing() {
        frameProcessorJob?.cancel()
        frameProcessorJob = null
        frameQueue?.close()
        frameQueue = null
    }

    private suspend fun processFrameInternal(bitmap: android.graphics.Bitmap, timestampMs: Long) {
        val startTime = System.currentTimeMillis()
        val frame = recognitionPipeline.processFrame(bitmap)
        val processingTime = System.currentTimeMillis() - startTime
        val nowMs = System.currentTimeMillis()

        val currentState = _uiState.value
        val recognized = currentState.recognizedStudents.toMutableMap()

        val seenTrackIds = frame.tracks.map { it.trackId }.toSet()
        trackLocks.entries.removeAll { (trackId, lock) ->
            trackId !in seenTrackIds && nowMs - lock.lastSeenMs > lockExpiryMs
        }

        val overlay = mutableListOf<OverlayFaceUi>()

        for (track in frame.tracks) {
            val trackId = track.trackId
            val lock = trackLocks[trackId]
            if (lock != null) {
                lock.lastSeenMs = nowMs
            }

            if (track.status == TrackRecognitionStatus.CONFIRMED && track.studentId != null) {
                val candidateId = track.studentId
                val candidateDistance = track.distance

                if (lock == null) {
                    trackLocks[trackId] = TrackLock(
                        studentId = candidateId,
                        distance = candidateDistance,
                        lockedAtMs = nowMs,
                        lastSeenMs = nowMs
                    )
                    emitRecognized(recognized, candidateId, track.toRecognitionResult())
                } else if (lock.studentId == candidateId) {
                    lock.distance = candidateDistance
                    emitRecognized(recognized, candidateId, track.toRecognitionResult())
                } else {
                    val lockedRecently = nowMs - lock.lockedAtMs < stabilityLockMs
                    val significantlyBetter = candidateDistance <= lock.distance - switchDistanceDelta

                    if (!lockedRecently || significantlyBetter) {
                        val oldId = lock.studentId
                        lock.studentId = candidateId
                        lock.distance = candidateDistance
                        lock.lockedAtMs = nowMs
                        lock.lastSeenMs = nowMs

                        if (recognized[oldId]?.trackId == trackId && oldId !in currentState.confirmedStudents) {
                            recognized.remove(oldId)
                        }

                        emitRecognized(recognized, candidateId, track.toRecognitionResult())
                    }
                }
            }

            val effectiveId = trackLocks[trackId]?.studentId
            val isConfirmed = effectiveId != null

            val label: String = if (isConfirmed && effectiveId != null) {
                effectiveId
            } else {
                val progress = if (track.requiredVotes > 0) "${track.votesForCandidate}/${track.requiredVotes}" else ""
                if (progress.isNotEmpty()) "analizando... $progress" else "analizando..."
            }

            overlay.add(
                OverlayFaceUi(
                    trackId = trackId,
                    bbox = track.bbox,
                    label = label,
                    isConfirmed = isConfirmed,
                    votes = track.votesForCandidate,
                    requiredVotes = track.requiredVotes
                )
            )
        }

        val fps = computeFps(startTime)

        _uiState.value = _uiState.value.copy(
            recognizedStudents = recognized,
            overlayFaces = overlay,
            frameWidth = bitmap.width,
            frameHeight = bitmap.height,
            fps = fps,
            msPerFrame = processingTime
        )

        if (currentState.autoConfirmEnabled) {
            for ((studentId, result) in recognized) {
                if (shouldAutoConfirm(studentId, result, nowMs, currentState)) {
                    confirmStudent(studentId, source = ConfirmSource.AUTO)
                }
            }
        }
    }

    private fun emitRecognized(
        recognized: MutableMap<String, RecognitionResult>,
        studentId: String,
        result: RecognitionResult
    ) {
        if (studentId in _uiState.value.confirmedStudents) return
        recognized[studentId] = result
    }

    private fun computeFps(frameStartMs: Long): Int {
        if (lastFrameTimestampMs != 0L) {
            val delta = (frameStartMs - lastFrameTimestampMs).coerceAtLeast(1L)
            val instantFps = 1000f / delta.toFloat()
            fpsSmoothed = if (fpsSmoothed == 0f) instantFps else fpsSmoothed * 0.8f + instantFps * 0.2f
        }
        lastFrameTimestampMs = frameStartMs
        return fpsSmoothed.roundToInt().coerceAtLeast(0)
    }

    private fun shouldAutoConfirm(
        studentId: String,
        result: RecognitionResult,
        nowMs: Long,
        state: AttendanceUiState
    ): Boolean {
        if (studentId in state.confirmedStudents) return false
        if (result.confidence < state.autoConfirmMinConfidence) return false
        if (result.qualityScore < RecognitionConfig.effectiveQualityMin(state.powerMode)) return false

        val lastMarked = studentCooldowns[studentId] ?: 0L
        if (nowMs - lastMarked < autoConfirmCooldownMs) return false

        return true
    }
    
    fun confirmStudent(studentId: String, source: ConfirmSource = ConfirmSource.MANUAL) {
        viewModelScope.launch {
            confirmStudentInternal(studentId, source)
        }
    }

    private suspend fun confirmStudentInternal(studentId: String, source: ConfirmSource) {
        val canStart = confirmMutex.withLock {
            if (studentId in inFlightConfirmations) return@withLock false
            inFlightConfirmations.add(studentId)
            true
        }

        if (!canStart) return

        try {
            if (!_uiState.value.isSessionActive) return

            val currentTime = System.currentTimeMillis()
            val lastMarked = studentCooldowns[studentId] ?: 0L
            
            if (currentTime - lastMarked < cooldownMs) {
                Timber.d("Student $studentId in cooldown, skipping")
                return
            }
            
            if (studentId in _uiState.value.confirmedStudents) {
                Timber.d("Student $studentId already confirmed, skipping")
                return
            }
            
            val sessionId = _uiState.value.sessionId ?: return
            val result = _uiState.value.recognizedStudents[studentId]
            
            val detectedAt = result?.let { currentTime } ?: currentTime
            val confirmedAt = currentTime

            val pending = PendingAttendanceEntity(
                sessionId = sessionId,
                studentId = studentId,
                confidence = result?.confidence ?: 1.0f,
                detectedAt = detectedAt,
                confirmedAt = confirmedAt
            )
            
            try {
                pendingDao.insert(pending)
                studentCooldowns[studentId] = currentTime

                val confirmedRecord = ConfirmedRecord(
                    localId = pending.localId,
                    studentId = studentId,
                    studentName = studentNameById[studentId],
                    confidence = pending.confidence,
                    detectedAt = detectedAt,
                    confirmedAt = confirmedAt,
                    status = pending.status,
                    trackId = result?.trackId
                )
                
                val confirmed = _uiState.value.confirmedStudents + studentId
                val confirmedRecords = _uiState.value.confirmedRecords + (studentId to confirmedRecord)
                val recognized = _uiState.value.recognizedStudents.toMutableMap()
                recognized.remove(studentId)
                
                _uiState.value = _uiState.value.copy(
                    confirmedStudents = confirmed,
                    confirmedRecords = confirmedRecords,
                    recognizedStudents = recognized
                )

                if (source == ConfirmSource.AUTO) {
                    _events.tryEmit(AttendanceUiEvent.Snackbar("Confirmado: ${confirmedRecord.displayName}"))
                }
                
                Timber.d("Confirmed student: $studentId (queued for sync)")
            } catch (e: Exception) {
                Timber.e(e, "Failed to insert pending attendance for $studentId")
            }
        } finally {
            confirmMutex.withLock { inFlightConfirmations.remove(studentId) }
        }
    }

    fun undoConfirmation(studentId: String) {
        viewModelScope.launch {
            val sessionId = _uiState.value.sessionId ?: return@launch
            val record = pendingDao.getLatestBySessionAndStudent(sessionId, studentId) ?: return@launch

            if (record.status == "SENT") {
                Timber.d("Cannot undo student $studentId, already sent")
                _events.tryEmit(AttendanceUiEvent.Snackbar("No se puede deshacer: ya fue sincronizado"))
                return@launch
            }

            try {
                pendingDao.markCanceled(record.id)

                val confirmedRecords = _uiState.value.confirmedRecords.toMutableMap()
                confirmedRecords.remove(studentId)

                val confirmedStudents = _uiState.value.confirmedStudents - studentId

                _uiState.value = _uiState.value.copy(
                    confirmedRecords = confirmedRecords,
                    confirmedStudents = confirmedStudents
                )

                val name = studentNameById[studentId]
                val label = name?.takeIf { it.isNotBlank() } ?: "ID $studentId"
                _events.tryEmit(AttendanceUiEvent.Snackbar("Deshecho: $label"))

                Timber.d("Undo confirmation for student $studentId (local canceled)")
            } catch (e: Exception) {
                Timber.e(e, "Failed to undo student $studentId")
                _events.tryEmit(AttendanceUiEvent.Snackbar("Error al deshacer: ${e.message}"))
            }
        }
    }
    
    fun endSession() {
        viewModelScope.launch {
            try {
                stopFrameProcessing()
                trackLocks.clear()
                recognitionPipeline.reset()

                val sessionId = _uiState.value.sessionId ?: return@launch
                val records = _uiState.value.confirmedRecords.values.map { record ->
                    AttendanceRecordDto(
                        studentId = record.studentId,
                        status = "present",
                        confidence = record.confidence,
                        confirmedBy = "system",
                        detectedAt = null
                    )
                }

                confirmedRecordsJob?.cancel()
                confirmedRecordsJob = null

                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    isSessionActive = false,
                    recognizedStudents = emptyMap(),
                    overlayFaces = emptyList()
                )
                
                val request = UpdateSessionRequest(
                    status = "completed",
                    records = records
                )
                
                try {
                    api.updateAttendanceSession(sessionId, request)
                    
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isSessionActive = false,
                        sessionId = null,
                        recognizedStudents = emptyMap(),
                        confirmedStudents = emptySet(),
                        confirmedRecords = emptyMap()
                    )
                    
                    Timber.d("Ended session online with ${records.size} records")
                } catch (apiError: Exception) {
                    Timber.w(apiError, "Failed to end session online, queuing for offline sync")
                    
                    val recordsJson = JsonSerializationHelper.encodeAttendanceRecordsToJson(records)
                    val currentCourseId = _uiState.value.courseId
                    
                    if (currentCourseId.isBlank()) {
                        Timber.e("Cannot queue session finalization: courseId is empty")
                        throw IllegalStateException("Course ID is required for session finalization")
                    }
                    
                    val pendingUpdate = PendingSessionUpdateEntity(
                        sessionId = sessionId,
                        courseId = currentCourseId,
                        status = "completed",
                        recordsJson = recordsJson
                    )
                    
                    pendingSessionUpdateDao.insert(pendingUpdate)
                    
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isSessionActive = false,
                        sessionId = null,
                        recognizedStudents = emptyMap(),
                        confirmedStudents = emptySet(),
                        confirmedRecords = emptyMap(),
                        isOffline = true
                    )
                    
                    Timber.d("Queued session finalization for offline sync")
                }
                
                triggerSync()
            } catch (e: Exception) {
                Timber.e(e, "Failed to end session")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error al finalizar sesión: ${e.message}"
                )
                
                triggerSync()
            }
        }
    }
    
    private fun triggerSync() {
        val syncRequest = OneTimeWorkRequestBuilder<AttendanceSyncWorker>().build()
        WorkManager.getInstance(context).enqueue(syncRequest)
        Timber.d("Triggered one-time sync")
    }

    fun syncNow() {
        triggerSync()
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    /**
     * Switch to a different course (multi-course support)
     * Clears all recognition state to prevent mixing data between courses
     */
    fun switchCourse(newCourseId: String) {
        if (newCourseId == _uiState.value.courseId) return
        
        viewModelScope.launch {
            try {
                Timber.d("Switching course: ${_uiState.value.courseId} -> $newCourseId")
                
                // Stop frame processing and clear all state
                stopFrameProcessing()
                recognitionPipeline.clearAll()
                
                // Clear all recognition and confirmation state via uiState
                studentCooldowns.clear()
                trackLocks.clear()
                
                // Load new course data
                val course = studentsRepository.getCourseById(newCourseId)
                if (course != null) {
                    _uiState.value = _uiState.value.copy(
                        courseId = newCourseId,
                        courseName = course.name,
                        recognizedStudents = emptyMap(),
                        confirmedStudents = emptySet(),
                        confirmedRecords = emptyMap(),
                        overlayFaces = emptyList()
                    )
                    
                    // Load templates only for new course
                    val embeddings = studentsRepository.getTemplatesFromCache(newCourseId)
                    recognitionPipeline.setStudentRoster(embeddings)
                    
                    // Observe student names for new course
                    observeStudentNames(newCourseId)
                    
                    Timber.d("Course switched successfully: ${embeddings.size} templates loaded for course $newCourseId")
                } else {
                    Timber.w("Course $newCourseId not found")
                    _uiState.value = _uiState.value.copy(
                        error = "Course not found: $newCourseId"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to switch course")
                _uiState.value = _uiState.value.copy(
                    error = "Failed to switch course: ${e.message}"
                )
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        stopFrameProcessing()
        recognitionPipeline.close()
    }

    private fun com.smartpresence.idukay.ai.pipeline.TrackRecognition.toRecognitionResult(): RecognitionResult {
        return RecognitionResult(
            trackId = trackId,
            bbox = bbox,
            studentId = studentId,
            confidence = confidence,
            qualityScore = qualityScore
        )
    }

    private fun observeStudentNames(courseId: String) {
        studentNamesJob?.cancel()
        studentNamesJob = viewModelScope.launch {
            studentsRepository.observeStudentsByCourse(courseId).collect { students ->
                studentNameById = students.associate { student ->
                    val fullName = "${student.firstName} ${student.lastName}".trim()
                    student.id to fullName
                }

                val updatedRecords = _uiState.value.confirmedRecords.mapValues { (studentId, record) ->
                    record.copy(studentName = studentNameById[studentId])
                }

                _uiState.value = _uiState.value.copy(confirmedRecords = updatedRecords)
            }
        }
    }

    private fun observeConfirmedRecords(sessionId: String) {
        confirmedRecordsJob?.cancel()
        confirmedRecordsJob = viewModelScope.launch {
            pendingDao.observeBySession(sessionId).collect { entities ->
                val records = entities
                    .filter { it.status != "CANCELED" }
                    .groupBy { it.studentId }
                    .mapValues { (_, list) -> list.maxBy { it.confirmedAt } }
                    .mapValues { (_, entity) ->
                        ConfirmedRecord(
                            localId = entity.localId,
                            studentId = entity.studentId,
                            studentName = studentNameById[entity.studentId],
                            confidence = entity.confidence,
                            detectedAt = entity.detectedAt,
                            confirmedAt = entity.confirmedAt,
                            status = entity.status,
                            trackId = null
                        )
                    }

                _uiState.value = _uiState.value.copy(
                    confirmedRecords = records,
                    confirmedStudents = records.keys
                )
            }
        }
    }
}
