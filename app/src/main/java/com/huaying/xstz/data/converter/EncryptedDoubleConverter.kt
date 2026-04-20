package com.huaying.xstz.data.converter

import androidx.room.TypeConverter
import com.huaying.xstz.data.security.CryptoManager
import java.util.Locale

/**
 * Automatically encrypts Double values when saving to DB,
 * and decrypts them when reading from DB.
 * Uses CryptoManager for AES-256 GCM encryption.
 */
class EncryptedDoubleConverter {

    private val cryptoManager = CryptoManager()

    @TypeConverter
    fun fromDouble(value: Double?): String? {
        if (value == null) return null
        return try {
            // Convert Double to String, then Encrypt
            val stringValue = String.format(Locale.US, "%.10f", value)
            cryptoManager.encrypt(stringValue)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    @TypeConverter
    fun toDouble(encryptedValue: String?): Double? {
        if (encryptedValue.isNullOrEmpty()) return null
        return try {
            // Check if value looks like a plain double first (migration support)
            // If it parses directly as a number, return it (it's not encrypted yet)
            if (isPlainDouble(encryptedValue)) {
                return encryptedValue.toDoubleOrNull()
            }
            
            // Decrypt String, then parse to Double
            val decryptedString = cryptoManager.decrypt(encryptedValue)
            decryptedString.toDoubleOrNull()
        } catch (e: Exception) {
            e.printStackTrace()
            0.0 // Fail safe default
        }
    }

    private fun isPlainDouble(str: String): Boolean {
        // Simple regex check: optional minus, digits, optional dot, optional digits
        return str.matches(Regex("^-?\\d*(\\.\\d+)?$"))
    }
}