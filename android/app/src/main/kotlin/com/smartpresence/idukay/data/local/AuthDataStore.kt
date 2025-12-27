package com.smartpresence.idukay.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

/**
 * Singleton DataStore for authentication preferences
 * This prevents multiple DataStore instances error
 */
val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")

object AuthPreferences {
    val TOKEN_KEY = stringPreferencesKey("access_token")
    val DEVICE_ID_KEY = stringPreferencesKey("device_id")
    val TEACHER_ID_KEY = stringPreferencesKey("teacher_id")
}
