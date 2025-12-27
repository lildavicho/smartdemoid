package com.smartpresence.idukay.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartpresence.idukay.data.remote.DeviceMismatchException
import com.smartpresence.idukay.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onSerialNumberChange(serialNumber: String) {
        _uiState.value = _uiState.value.copy(serialNumber = serialNumber, error = null, status = null)
    }

    fun onPinCodeChange(pinCode: String) {
        _uiState.value = _uiState.value.copy(pinCode = pinCode, error = null, status = null)
    }

    fun login() {
        val snapshot = _uiState.value

        val serial = snapshot.serialNumber.trim()
        val pin = snapshot.pinCode.trim()

        if (serial.isBlank()) {
            _uiState.value = snapshot.copy(error = "El numero de serie es requerido")
            return
        }

        if (pin.isBlank()) {
            _uiState.value = snapshot.copy(error = "El PIN es requerido")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, status = "Conectando...", error = null)

            val firstAttempt = authRepository.login(serial, pin)
            val finalResult = if (firstAttempt.isFailure && firstAttempt.exceptionOrNull().isRetryable()) {
                _uiState.value = _uiState.value.copy(status = "Reintentando...")
                delay(1200)
                authRepository.login(serial, pin)
            } else {
                firstAttempt
            }

            finalResult.fold(
                onSuccess = { _ ->
                    _uiState.value = _uiState.value.copy(status = "Verificando dispositivo...")
                    checkDeviceBinding()
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        status = null,
                        error = error.toUserMessage()
                    )
                }
            )
        }
    }

    private suspend fun checkDeviceBinding() {
        val currentDeviceId = authRepository.getDeviceId().orEmpty()

        if (currentDeviceId.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                status = null,
                error = "No se pudo obtener el ID del dispositivo"
            )
            return
        }

        val bindResult = authRepository.bindDevice(currentDeviceId)

        bindResult.fold(
            onSuccess = {
                Timber.d("Device bound successfully")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    status = null,
                    loginSuccess = true
                )
            },
            onFailure = { error ->
                if (error is DeviceMismatchException) {
                    Timber.w("Device mismatch: ${error.message}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        status = null,
                        deviceNotAuthorized = true,
                        currentDeviceId = currentDeviceId
                    )
                } else {
                    Timber.e(error, "Failed to bind device")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        status = null,
                        error = error.toUserMessage()
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
            _uiState.value = LoginUiState(error = "Inicio de sesion cancelado")
        }
    }
}

data class LoginUiState(
    val serialNumber: String = "",
    val pinCode: String = "",
    val isLoading: Boolean = false,
    val status: String? = null,
    val error: String? = null,
    val loginSuccess: Boolean = false,
    val deviceNotAuthorized: Boolean = false,
    val currentDeviceId: String = ""
)

private fun Throwable?.isRetryable(): Boolean {
    return this is SocketTimeoutException || this is IOException
}

private fun Throwable.toUserMessage(): String {
    return when (this) {
        is HttpException -> when (code()) {
            401 -> "Credenciales invalidas"
            404 -> "Ruta no encontrada"
            408 -> "Tiempo de espera agotado"
            429 -> "Demasiadas solicitudes. Intenta de nuevo en un momento"
            in 500..599 -> "Error del servidor. Intenta nuevamente"
            else -> "Error HTTP ${code()}"
        }
        is SocketTimeoutException -> "Tiempo de espera agotado. Intenta nuevamente"
        is IOException -> "No se pudo conectar al servidor. Revisa tu conexion"
        else -> message?.takeIf { it.isNotBlank() } ?: "Ocurrio un error"
    }
}
