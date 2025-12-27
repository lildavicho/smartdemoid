package com.smartpresence.idukay.ai

sealed class ModelLoadingState {
    object Success : ModelLoadingState()
    data class Error(val message: String, val cause: Throwable?) : ModelLoadingState()
    data class AssetNotFound(val path: String) : ModelLoadingState()
    
    fun isSuccess(): Boolean = this is Success
    fun getErrorMessage(): String? = when (this) {
        is Error -> message
        is AssetNotFound -> "Model not found: $path"
        is Success -> null
    }
}
