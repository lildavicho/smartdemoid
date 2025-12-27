package com.smartpresence.idukay.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.smartpresence.idukay.ai.RecognitionConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings_prefs")

object SettingsPreferences {
    val THRESHOLD_MODE_KEY = stringPreferencesKey("threshold_mode")
    val DEBUG_OVERLAY_KEY = booleanPreferencesKey("debug_overlay")
    val AUTO_CONFIRM_ENABLED_KEY = booleanPreferencesKey("auto_confirm_enabled")
    val AUTO_CONFIRM_MIN_CONFIDENCE_KEY = floatPreferencesKey("auto_confirm_min_confidence")
    val POWER_MODE_KEY = stringPreferencesKey("power_mode")
    val KIOSK_ENABLED_KEY = booleanPreferencesKey("kiosk_enabled")
}

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.settingsDataStore
    
    val thresholdMode: Flow<RecognitionConfig.ThresholdMode> = dataStore.data.map { prefs ->
        val modeString = prefs[SettingsPreferences.THRESHOLD_MODE_KEY] ?: "NORMAL"
        try {
            RecognitionConfig.ThresholdMode.valueOf(modeString)
        } catch (e: IllegalArgumentException) {
            RecognitionConfig.ThresholdMode.NORMAL
        }
    }
    
    val debugOverlay: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[SettingsPreferences.DEBUG_OVERLAY_KEY] ?: true
    }

    val powerMode: Flow<RecognitionConfig.PowerMode> = dataStore.data.map { prefs ->
        val modeString = prefs[SettingsPreferences.POWER_MODE_KEY] ?: "BALANCED"
        try {
            RecognitionConfig.PowerMode.valueOf(modeString)
        } catch (e: IllegalArgumentException) {
            RecognitionConfig.PowerMode.BALANCED
        }
    }

    val autoConfirmEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[SettingsPreferences.AUTO_CONFIRM_ENABLED_KEY] ?: false
    }

    val autoConfirmMinConfidence: Flow<Float> = dataStore.data.map { prefs ->
        prefs[SettingsPreferences.AUTO_CONFIRM_MIN_CONFIDENCE_KEY] ?: 0.75f
    }
    
    suspend fun setThresholdMode(mode: RecognitionConfig.ThresholdMode) {
        dataStore.edit { prefs ->
            prefs[SettingsPreferences.THRESHOLD_MODE_KEY] = mode.name
        }
    }
    
    suspend fun setDebugOverlay(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[SettingsPreferences.DEBUG_OVERLAY_KEY] = enabled
        }
    }

    suspend fun setPowerMode(mode: RecognitionConfig.PowerMode) {
        dataStore.edit { prefs ->
            prefs[SettingsPreferences.POWER_MODE_KEY] = mode.name
        }
    }

    suspend fun setAutoConfirmEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[SettingsPreferences.AUTO_CONFIRM_ENABLED_KEY] = enabled
        }
    }

    suspend fun setAutoConfirmMinConfidence(value: Float) {
        dataStore.edit { prefs ->
            prefs[SettingsPreferences.AUTO_CONFIRM_MIN_CONFIDENCE_KEY] = value.coerceIn(0f, 1f)
        }
    }
    
    val kioskEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[SettingsPreferences.KIOSK_ENABLED_KEY] ?: false
    }
    
    suspend fun setKioskEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[SettingsPreferences.KIOSK_ENABLED_KEY] = enabled
        }
    }
}
