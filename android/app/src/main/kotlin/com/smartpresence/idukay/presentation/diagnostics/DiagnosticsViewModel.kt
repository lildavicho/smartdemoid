package com.smartpresence.idukay.presentation.diagnostics

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartpresence.idukay.BuildConfig
import com.smartpresence.idukay.ai.RecognitionConfig
import com.smartpresence.idukay.ai.onnx.OnnxSessionOptionsFactory
import com.smartpresence.idukay.data.local.SettingsDataStore
import com.smartpresence.idukay.data.local.SyncHealthDataStore
import com.smartpresence.idukay.data.local.dao.PendingAttendanceDao
import com.smartpresence.idukay.data.local.dao.PendingSessionUpdateDao
import com.smartpresence.idukay.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject

data class DiagnosticsUiState(
    val deviceId: String = "",
    val teacherId: String = "",
    val appVersion: String = BuildConfig.VERSION_NAME,
    val onnxProvider: String = "",
    val powerMode: String = "",
    val thresholdMode: String = "",
    val qualityMin: Float = 0f,
    val pendingAttendanceCount: Int = 0,
    val pendingSessionUpdateCount: Int = 0,
    val lastSyncError: String? = null,
    val consecutiveFailures: Int = 0,
    val lastSuccessfulSyncAt: Long = 0L,
    val modelLoadingErrors: List<String> = emptyList()
)

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    val settingsDataStore: SettingsDataStore,
    private val syncHealthDataStore: SyncHealthDataStore,
    private val pendingAttendanceDao: PendingAttendanceDao,
    private val pendingSessionUpdateDao: PendingSessionUpdateDao,
    val adminPinDataStore: com.smartpresence.idukay.data.local.AdminPinDataStore
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(DiagnosticsUiState())
    val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()
    
    init {
        loadDiagnostics()
    }
    
    private fun loadDiagnostics() {
        viewModelScope.launch {
            try {
                val deviceId = authRepository.getDeviceId() ?: "N/A"
                val teacherId = authRepository.getTeacherId() ?: "N/A"
                
                val powerMode = settingsDataStore.powerMode.first()
                val thresholdMode = settingsDataStore.thresholdMode.first()
                val qualityMin = RecognitionConfig.effectiveQualityMin(powerMode)
                
                val pendingAttendance = pendingAttendanceDao.getPendingCount().first()
                val pendingSessionUpdates = pendingSessionUpdateDao.getPendingCount().first()
                
                val lastSyncError = syncHealthDataStore.lastSyncError.first()
                val consecutiveFailures = syncHealthDataStore.consecutiveFailures.first()
                val lastSuccessfulSync = syncHealthDataStore.lastSuccessfulSyncAt.first()
                
                val onnxProvider = OnnxSessionOptionsFactory.getProviderName()
                
                _uiState.value = DiagnosticsUiState(
                    deviceId = deviceId,
                    teacherId = teacherId,
                    onnxProvider = onnxProvider,
                    powerMode = powerMode.name,
                    thresholdMode = thresholdMode.name,
                    qualityMin = qualityMin,
                    pendingAttendanceCount = pendingAttendance,
                    pendingSessionUpdateCount = pendingSessionUpdates,
                    lastSyncError = lastSyncError?.takeIf { it.isNotBlank() },
                    consecutiveFailures = consecutiveFailures,
                    lastSuccessfulSyncAt = lastSuccessfulSync
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to load diagnostics")
            }
        }
    }
    
    fun setModelLoadingErrors(errors: List<String>) {
        _uiState.value = _uiState.value.copy(modelLoadingErrors = errors)
    }
    
    fun exportDiagnostics(anonymize: Boolean = false): String {
        val state = _uiState.value
        
        return try {
            JSONObject().apply {
                put("timestamp", System.currentTimeMillis())
                put("appVersion", state.appVersion)
                
                if (anonymize) {
                    put("deviceId", "****${state.deviceId.takeLast(4)}")
                    put("teacherId", "****${state.teacherId.takeLast(4)}")
                } else {
                    put("deviceId", state.deviceId)
                    put("teacherId", state.teacherId)
                }
                
                put("onnxProvider", state.onnxProvider)
                put("powerMode", state.powerMode)
                put("thresholdMode", state.thresholdMode)
                put("qualityMin", state.qualityMin)
                put("pendingAttendanceCount", state.pendingAttendanceCount)
                put("pendingSessionUpdateCount", state.pendingSessionUpdateCount)
                put("lastSyncError", state.lastSyncError ?: "")
                put("consecutiveFailures", state.consecutiveFailures)
                put("lastSuccessfulSyncAt", state.lastSuccessfulSyncAt)
                put("modelLoadingErrors", state.modelLoadingErrors.joinToString("; "))
            }.toString(2)
        } catch (e: Exception) {
            Timber.e(e, "Failed to export diagnostics")
            "Error generating diagnostics: ${e.message}"
        }
    }
    
    fun shareDiagnostics(anonymize: Boolean = false) {
        val diagnosticsText = exportDiagnostics(anonymize)
        
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "SmartPresence Diagnostics")
            putExtra(Intent.EXTRA_TEXT, diagnosticsText)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        try {
            val chooser = Intent.createChooser(shareIntent, "Share Diagnostics")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Timber.e(e, "Failed to share diagnostics")
        }
    }
}
