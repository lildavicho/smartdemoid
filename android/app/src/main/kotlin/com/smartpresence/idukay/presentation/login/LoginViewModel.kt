package com.smartpresence.idukay.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartpresence.idukay.data.remote.DeviceMismatchException
import com.smartpresence.idukay.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()
    
    fun onSerialNumberChange(serialNumber: String) {
        _uiState.value = _uiState.value.copy(serialNumber = serialNumber, error = null)
    }
    
    fun onPinCodeChange(pinCode: String) {
        _uiState.value = _uiState.value.copy(pinCode = pinCode, error = null)
    }
    
    fun login() {
        val currentState = _uiState.value
        
        if (currentState.serialNumber.isBlank()) {
            _uiState.value = currentState.copy(error = "El número de serie es requerido")
            return
        }
        
        if (currentState.pinCode.isBlank()) {
            _uiState.value = currentState.copy(error = "El PIN es requerido")
            return
        }
        
        viewModelScope.launch {
            _uiState.value = currentState.copy(isLoading = true, error = null)
            
            val result = authRepository.login(
                currentState.serialNumber,
                currentState.pinCode
            )
            
            result.fold(
                onSuccess = { response ->
                    // Login successful, now bind device
                    checkDeviceBinding()
                },
                onFailure = { error ->
                    _uiState.value = currentState.copy(
                        isLoading = false,
                        error = error.message ?: "Error de autenticación"
                    )
                }
            )
        }
    }
    
    private suspend fun checkDeviceBinding() {
        val currentDeviceId = authRepository.getDeviceId() ?: ""
        
        if (currentDeviceId.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = "Device ID not found"
            )
            return
        }
        
        // Call backend to bind device
        val bindResult = authRepository.bindDevice(currentDeviceId)
        
        bindResult.fold(
            onSuccess = {
                // Binding successful
                Timber.d("Device bound successfully")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    loginSuccess = true
                )
            },
            onFailure = { error ->
                if (error is DeviceMismatchException) {
                    // Device mismatch - show not authorized screen
                    Timber.w("Device mismatch: ${error.message}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        deviceNotAuthorized = true,
                        currentDeviceId = currentDeviceId
                    )
                } else {
                    // Other error
                    Timber.e(error, "Failed to bind device")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to bind device"
                    )
                }
            }
        )
    }
    
    fun onDeviceRebindSuccess() {
        _uiState.value = _uiState.value.copy(
            deviceNotAuthorized = false,
            loginSuccess = true
        )
    }
    
    fun onDeviceRebindCancel() {
        viewModelScope.launch {
            authRepository.logout()
            _uiState.value = LoginUiState(
                error = "Login cancelled - device not authorized"
            )
        }
    }
}

data class LoginUiState(
    val serialNumber: String = "",
    val pinCode: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val loginSuccess: Boolean = false,
    val deviceNotAuthorized: Boolean = false,
    val currentDeviceId: String = ""
)
