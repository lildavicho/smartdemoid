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
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.syncHealthDataStore: DataStore<Preferences> by preferencesDataStore(name = "sync_health_prefs")

object SyncHealthPreferences {
    val LAST_SYNC_ERROR_KEY = stringPreferencesKey("last_sync_error")
    val CONSECUTIVE_FAILURES_KEY = intPreferencesKey("consecutive_failures")
    val LAST_SUCCESSFUL_SYNC_AT_KEY = longPreferencesKey("last_successful_sync_at")
}

@Singleton
class SyncHealthDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.syncHealthDataStore
    
    val lastSyncError: Flow<String?> = dataStore.data.map { prefs ->
        prefs[SyncHealthPreferences.LAST_SYNC_ERROR_KEY]
    }
    
    val consecutiveFailures: Flow<Int> = dataStore.data.map { prefs ->
        prefs[SyncHealthPreferences.CONSECUTIVE_FAILURES_KEY] ?: 0
    }
    
    val lastSuccessfulSyncAt: Flow<Long> = dataStore.data.map { prefs ->
        prefs[SyncHealthPreferences.LAST_SUCCESSFUL_SYNC_AT_KEY] ?: 0L
    }
    
    suspend fun recordSuccess() {
        dataStore.edit { prefs ->
            prefs[SyncHealthPreferences.LAST_SYNC_ERROR_KEY] = ""
            prefs[SyncHealthPreferences.CONSECUTIVE_FAILURES_KEY] = 0
            prefs[SyncHealthPreferences.LAST_SUCCESSFUL_SYNC_AT_KEY] = System.currentTimeMillis()
        }
    }
    
    suspend fun recordFailure(errorMessage: String) {
        dataStore.edit { prefs ->
            val currentFailures = prefs[SyncHealthPreferences.CONSECUTIVE_FAILURES_KEY] ?: 0
            prefs[SyncHealthPreferences.CONSECUTIVE_FAILURES_KEY] = currentFailures + 1
            
            if (currentFailures + 1 >= 3) {
                prefs[SyncHealthPreferences.LAST_SYNC_ERROR_KEY] = errorMessage
            }
        }
    }
    
    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(SyncHealthPreferences.LAST_SYNC_ERROR_KEY)
            prefs.remove(SyncHealthPreferences.CONSECUTIVE_FAILURES_KEY)
            prefs.remove(SyncHealthPreferences.LAST_SUCCESSFUL_SYNC_AT_KEY)
        }
    }
}
