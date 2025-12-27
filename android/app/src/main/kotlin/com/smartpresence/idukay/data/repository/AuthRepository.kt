package com.smartpresence.idukay.data.repository

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.edit
import com.smartpresence.idukay.data.local.AuthPreferences
import com.smartpresence.idukay.data.local.authDataStore
import com.smartpresence.idukay.data.remote.DeviceMismatchException
import com.smartpresence.idukay.data.remote.api.SmartPresenceApi
import com.smartpresence.idukay.data.remote.dto.BindDeviceRequest
import com.smartpresence.idukay.data.remote.dto.LoginRequest
import com.smartpresence.idukay.data.remote.dto.LoginResponse
import com.smartpresence.idukay.data.remote.dto.RebindDeviceRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: SmartPresenceApi
) {
    
    suspend fun login(serialNumber: String, pinCode: String): Result<LoginResponse> {
        return try {
            val response = api.login(LoginRequest(serialNumber, pinCode))
            
            // Save token to DataStore
            context.authDataStore.edit { preferences ->
                preferences[AuthPreferences.TOKEN_KEY] = response.accessToken
                preferences[AuthPreferences.DEVICE_ID_KEY] = response.device.id
                preferences[AuthPreferences.TEACHER_ID_KEY] = response.teacher.id
            }
            
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Bind device to teacher
     * Throws DeviceMismatchException if 409 DEVICE_MISMATCH
     */
    suspend fun bindDevice(deviceId: String): Result<Unit> {
        return try {
            val teacherId = getTeacherId() ?: return Result.failure(Exception("Teacher ID not found"))
            
            val metadata = mapOf(
                "model" to Build.MODEL,
                "manufacturer" to Build.MANUFACTURER,
                "osVersion" to Build.VERSION.RELEASE,
                "sdkInt" to Build.VERSION.SDK_INT.toString()
            )
            
            val response = api.bindDevice(
                BindDeviceRequest(
                    teacherId = teacherId,
                    deviceId = deviceId,
                    metadata = metadata
                )
            )
            
            if (response.success) {
                Timber.d("Device bound successfully: ${response.data?.deviceId}")
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message ?: "Device binding failed"))
            }
        } catch (e: HttpException) {
            if (e.code() == 409) {
                // Parse error body for DEVICE_MISMATCH
                val errorBody = e.response()?.errorBody()?.string()
                Timber.w("Device mismatch: $errorBody")
                Result.failure(DeviceMismatchException(errorBody))
            } else {
                Result.failure(e)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to bind device")
            Result.failure(e)
        }
    }
    
    /**
     * Rebind device with admin PIN proof
     */
    suspend fun rebindDevice(deviceId: String, adminPinProof: String): Result<Unit> {
        return try {
            val teacherId = getTeacherId() ?: return Result.failure(Exception("Teacher ID not found"))
            
            val metadata = mapOf(
                "model" to Build.MODEL,
                "manufacturer" to Build.MANUFACTURER,
                "osVersion" to Build.VERSION.RELEASE,
                "sdkInt" to Build.VERSION.SDK_INT.toString()
            )
            
            val response = api.rebindDevice(
                RebindDeviceRequest(
                    teacherId = teacherId,
                    deviceId = deviceId,
                    adminPinProof = adminPinProof,
                    metadata = metadata
                )
            )
            
            if (response.success) {
                Timber.d("Device rebound successfully: ${response.data?.deviceId}")
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message ?: "Device rebinding failed"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to rebind device")
            Result.failure(e)
        }
    }
    
    suspend fun logout() {
        context.authDataStore.edit { preferences ->
            preferences.clear()
        }
    }
    
    suspend fun getToken(): String? {
        return context.authDataStore.data.map { preferences ->
            preferences[AuthPreferences.TOKEN_KEY]
        }.first()
    }
    
    suspend fun getDeviceId(): String? {
        return context.authDataStore.data.map { preferences ->
            preferences[AuthPreferences.DEVICE_ID_KEY]
        }.first()
    }
    
    suspend fun getTeacherId(): String? {
        return context.authDataStore.data.map { preferences ->
            preferences[AuthPreferences.TEACHER_ID_KEY]
        }.first()
    }
}
