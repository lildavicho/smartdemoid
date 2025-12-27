package com.smartpresence.idukay.data.local.util

import java.security.MessageDigest
import java.security.SecureRandom

object PinHashUtil {
    
    /**
     * Generate a random salt for PIN hashing
     */
    fun generateSalt(): String {
        val random = SecureRandom()
        val salt = ByteArray(16)
        random.nextBytes(salt)
        return salt.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Hash a PIN with the given salt using SHA-256
     */
    fun hashPin(pin: String, salt: String): String {
        val combined = "$pin$salt"
        return MessageDigest.getInstance("SHA-256")
            .digest(combined.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Verify a PIN against a stored hash
     */
    fun verifyPin(pin: String, salt: String, storedHash: String): Boolean {
        val computedHash = hashPin(pin, salt)
        return computedHash == storedHash
    }
    
    /**
     * Validate PIN format (4-6 digits)
     */
    fun isValidPinFormat(pin: String): Boolean {
        return pin.length in 4..6 && pin.all { it.isDigit() }
    }
}
