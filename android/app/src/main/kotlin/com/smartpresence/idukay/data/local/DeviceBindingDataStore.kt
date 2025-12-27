package com.smartpresence.idukay.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

val Context.deviceBindingDataStore: DataStore<Preferences> by preferencesDataStore(name = "device_binding_prefs")

object DeviceBindingPreferences {
    val BOUND_DEVICE_ID_KEY = stringPreferencesKey("bound_device_id")
    val BOUND_AT_KEY = longPreferencesKey("bound_at")
    val REBIND_COUNT_KEY = intPreferencesKey("rebind_count")
}

@Singleton
class DeviceBindingDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.deviceBindingDataStore
    
    val boundDeviceId: Flow<String?> = dataStore.data.map { prefs ->
        prefs[DeviceBindingPreferences.BOUND_DEVICE_ID_KEY]
    }
    
    val boundAt: Flow<Long> = dataStore.data.map { prefs ->
        prefs[DeviceBindingPreferences.BOUND_AT_KEY] ?: 0L
    }
    
    val rebindCount: Flow<Int> = dataStore.data.map { prefs ->
        prefs[DeviceBindingPreferences.REBIND_COUNT_KEY] ?: 0
    }
    
    /**
     * Check if a device is bound
     */
    suspend fun isDeviceBound(): Boolean {
        return boundDeviceId.first() != null
    }
    
    /**
     * Check if the current device matches the bound device
     */
    suspend fun isBoundToCurrentDevice(currentDeviceId: String): Boolean {
        val bound = boundDeviceId.first()
        return bound == null || bound == currentDeviceId
    }
    
    /**
     * Bind the device on first successful login
     */
    suspend fun bindDevice(deviceId: String) {
        dataStore.edit { prefs ->
            prefs[DeviceBindingPreferences.BOUND_DEVICE_ID_KEY] = deviceId
            prefs[DeviceBindingPreferences.BOUND_AT_KEY] = System.currentTimeMillis()
        }
        Timber.d("Device bound: ${deviceId.takeLast(4)}")
    }
    
    /**
     * Rebind to a new device (requires admin PIN)
     */
    suspend fun rebindDevice(deviceId: String) {
        dataStore.edit { prefs ->
            val currentCount = prefs[DeviceBindingPreferences.REBIND_COUNT_KEY] ?: 0
            prefs[DeviceBindingPreferences.BOUND_DEVICE_ID_KEY] = deviceId
            prefs[DeviceBindingPreferences.BOUND_AT_KEY] = System.currentTimeMillis()
            prefs[DeviceBindingPreferences.REBIND_COUNT_KEY] = currentCount + 1
        }
        Timber.w("Device rebound: ${deviceId.takeLast(4)}")
    }
    
    /**
     * Get masked device ID for display (show last 4 chars)
     */
    suspend fun getMaskedBoundDeviceId(): String {
        val deviceId = boundDeviceId.first() ?: return "None"
        return if (deviceId.length > 4) {
            "****${deviceId.takeLast(4)}"
        } else {
            deviceId
        }
    }
    
    /**
     * Clear device binding (for testing/reset)
     */
    suspend fun clearBinding() {
        dataStore.edit { prefs ->
            prefs.remove(DeviceBindingPreferences.BOUND_DEVICE_ID_KEY)
            prefs.remove(DeviceBindingPreferences.BOUND_AT_KEY)
            prefs.remove(DeviceBindingPreferences.REBIND_COUNT_KEY)
        }
        Timber.d("Device binding cleared")
    }
}
