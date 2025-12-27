package com.smartpresence.idukay.di

import android.os.Build
import com.smartpresence.idukay.BuildConfig

/**
 * Network configuration provider for SmartPresence.
 * Detects emulator vs real device and provides the appropriate base URL.
 * 
 * Configuration via BuildConfig:
 * - API_HOST: LAN IP address for real devices (default: 192.168.1.24)
 * - USE_ADB_REVERSE: Set to true to use 127.0.0.1 with adb reverse
 * 
 * For emulator: Always uses 10.0.2.2 (Android emulator's alias to host's localhost)
 * For real device: Uses API_HOST or 127.0.0.1 if USE_ADB_REVERSE is true
 * 
 * To use adb reverse:
 *   1. Run: adb reverse tcp:3000 tcp:3000
 *   2. Set USE_ADB_REVERSE = true in build.gradle.kts
 */
object NetworkConfig {
    
    private const val PORT = "3000"
    private const val API_PATH = "/api/v1/"
    
    // Host addresses
    private const val EMULATOR_HOST = "10.0.2.2"
    private const val ADB_REVERSE_HOST = "127.0.0.1"
    
    /**
     * Returns true if running on an Android emulator.
     * Uses multiple Build properties for reliable detection.
     */
    val isEmulator: Boolean by lazy {
        // Check Build.FINGERPRINT for emulator indicators
        Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
        Build.FINGERPRINT.contains("emulator", ignoreCase = true) ||
        Build.FINGERPRINT.startsWith("google/sdk_gphone") ||
        // Check Build.MODEL for emulator patterns
        Build.MODEL.contains("Emulator", ignoreCase = true) ||
        Build.MODEL.contains("Android SDK", ignoreCase = true) ||
        Build.MODEL.contains("sdk_gphone", ignoreCase = true) ||
        // Check Build.HARDWARE for known emulator values
        Build.HARDWARE.contains("ranchu", ignoreCase = true) ||
        Build.HARDWARE.contains("goldfish", ignoreCase = true) ||
        // Check Build.PRODUCT
        Build.PRODUCT.contains("sdk", ignoreCase = true) ||
        Build.PRODUCT.contains("emulator", ignoreCase = true) ||
        // Additional manufacturer checks
        Build.MANUFACTURER.contains("Genymotion", ignoreCase = true) ||
        Build.BRAND.startsWith("generic", ignoreCase = true)
    }
    
    /**
     * Determines the appropriate host based on environment and configuration.
     * Priority:
     * 1. Emulator -> 10.0.2.2
     * 2. USE_ADB_REVERSE enabled -> 127.0.0.1
     * 3. Real device -> API_HOST from BuildConfig
     */
    private fun getHost(): String {
        return when {
            isEmulator -> EMULATOR_HOST
            BuildConfig.USE_ADB_REVERSE -> ADB_REVERSE_HOST
            else -> BuildConfig.API_HOST
        }
    }
    
    /**
     * Base URL for the SmartPresence API.
     * Automatically selects the correct host based on runtime environment.
     * Always ends with a trailing slash as required by Retrofit.
     */
    val baseUrl: String by lazy {
        "http://${getHost()}:$PORT$API_PATH"
    }
    
    /**
     * Returns the current host being used (for logging/debugging).
     */
    val currentHost: String
        get() = getHost()
    
    /**
     * Debug information about current network configuration.
     */
    fun getDebugInfo(): String = buildString {
        appendLine("=== NetworkConfig Debug ===")
        appendLine("isEmulator: $isEmulator")
        appendLine("USE_ADB_REVERSE: ${BuildConfig.USE_ADB_REVERSE}")
        appendLine("API_HOST: ${BuildConfig.API_HOST}")
        appendLine("Resolved Host: ${getHost()}")
        appendLine("Base URL: $baseUrl")
        appendLine("===========================")
    }
}
