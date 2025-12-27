package com.smartpresence.idukay.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.smartpresence.idukay.data.local.util.PinHashUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

val Context.adminPinDataStore: DataStore<Preferences> by preferencesDataStore(name = "admin_pin_prefs")

object AdminPinPreferences {
    val ADMIN_PIN_HASH_KEY = stringPreferencesKey("admin_pin_hash")
    val ADMIN_PIN_SALT_KEY = stringPreferencesKey("admin_pin_salt")
    val FAILED_ATTEMPTS_KEY = intPreferencesKey("failed_attempts")
    val LOCKED_UNTIL_KEY = longPreferencesKey("locked_until")
}

@Singleton
class AdminPinDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.adminPinDataStore
    
    companion object {
        private const val MAX_ATTEMPTS = 5
        private const val LOCKOUT_DURATION_MS = 5 * 60 * 1000L // 5 minutes
    }
    
    val isPinSet: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[AdminPinPreferences.ADMIN_PIN_HASH_KEY] != null
    }
    
    val failedAttempts: Flow<Int> = dataStore.data.map { prefs ->
        prefs[AdminPinPreferences.FAILED_ATTEMPTS_KEY] ?: 0
    }
    
    val lockedUntil: Flow<Long> = dataStore.data.map { prefs ->
        prefs[AdminPinPreferences.LOCKED_UNTIL_KEY] ?: 0L
    }
    
    /**
     * Check if PIN verification is currently locked
     */
    suspend fun isLocked(): Boolean {
        val lockTime = lockedUntil.first()
        return System.currentTimeMillis() < lockTime
    }
    
    /**
     * Get remaining lockout time in seconds
     */
    suspend fun getRemainingLockoutSeconds(): Int {
        val lockTime = lockedUntil.first()
        val remaining = lockTime - System.currentTimeMillis()
        return if (remaining > 0) (remaining / 1000).toInt() else 0
    }
    
    /**
     * Set a new admin PIN
     */
    suspend fun setPin(pin: String) {
        if (!PinHashUtil.isValidPinFormat(pin)) {
            throw IllegalArgumentException("PIN must be 4-6 digits")
        }
        
        val salt = PinHashUtil.generateSalt()
        val hash = PinHashUtil.hashPin(pin, salt)
        
        dataStore.edit { prefs ->
            prefs[AdminPinPreferences.ADMIN_PIN_HASH_KEY] = hash
            prefs[AdminPinPreferences.ADMIN_PIN_SALT_KEY] = salt
            prefs[AdminPinPreferences.FAILED_ATTEMPTS_KEY] = 0
            prefs[AdminPinPreferences.LOCKED_UNTIL_KEY] = 0L
        }
        
        Timber.d("Admin PIN set successfully")
    }
    
    /**
     * Verify a PIN attempt
     * Returns true if correct, false if incorrect
     * Throws exception if locked
     */
    suspend fun verifyPin(pin: String): Boolean {
        if (isLocked()) {
            val remainingSeconds = getRemainingLockoutSeconds()
            throw SecurityException("PIN verification locked for $remainingSeconds seconds")
        }
        
        val prefs = dataStore.data.first()
        val storedHash = prefs[AdminPinPreferences.ADMIN_PIN_HASH_KEY]
        val salt = prefs[AdminPinPreferences.ADMIN_PIN_SALT_KEY]
        
        if (storedHash == null || salt == null) {
            Timber.w("No PIN set, verification failed")
            return false
        }
        
        val isCorrect = PinHashUtil.verifyPin(pin, salt, storedHash)
        
        if (isCorrect) {
            resetFailedAttempts()
            Timber.d("PIN verified successfully")
        } else {
            recordFailedAttempt()
            Timber.w("PIN verification failed")
        }
        
        return isCorrect
    }
    
    /**
     * Record a failed PIN attempt
     */
    private suspend fun recordFailedAttempt() {
        dataStore.edit { prefs ->
            val currentAttempts = prefs[AdminPinPreferences.FAILED_ATTEMPTS_KEY] ?: 0
            val newAttempts = currentAttempts + 1
            prefs[AdminPinPreferences.FAILED_ATTEMPTS_KEY] = newAttempts
            
            if (newAttempts >= MAX_ATTEMPTS) {
                val lockUntil = System.currentTimeMillis() + LOCKOUT_DURATION_MS
                prefs[AdminPinPreferences.LOCKED_UNTIL_KEY] = lockUntil
                Timber.w("PIN locked until ${lockUntil}")
            }
        }
    }
    
    /**
     * Reset failed attempts counter
     */
    suspend fun resetFailedAttempts() {
        dataStore.edit { prefs ->
            prefs[AdminPinPreferences.FAILED_ATTEMPTS_KEY] = 0
            prefs[AdminPinPreferences.LOCKED_UNTIL_KEY] = 0L
        }
    }
    
    /**
     * Clear all PIN data (for testing/reset)
     */
    suspend fun clearPin() {
        dataStore.edit { prefs ->
            prefs.remove(AdminPinPreferences.ADMIN_PIN_HASH_KEY)
            prefs.remove(AdminPinPreferences.ADMIN_PIN_SALT_KEY)
            prefs.remove(AdminPinPreferences.FAILED_ATTEMPTS_KEY)
            prefs.remove(AdminPinPreferences.LOCKED_UNTIL_KEY)
        }
        Timber.d("Admin PIN cleared")
    }
}
