package com.huaying.xstz.data.converter

import android.util.Log
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
            Log.e("EncryptedDoubleConverter", "encrypt failed", e)
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
            Log.e("EncryptedDoubleConverter", "decrypt failed", e)
            null
        }
    }

    private fun isPlainDouble(str: String): Boolean {
        // Must have at least one digit, optional minus, optional dot with digits
        return str.matches(Regex("^-?\\d+(\\.\\d+)?$"))
    }
}