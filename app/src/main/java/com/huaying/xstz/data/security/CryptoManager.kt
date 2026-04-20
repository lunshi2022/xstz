package com.huaying.xstz.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import android.util.Base64

/**
 * Modern CryptoManager using Android Keystore for secure key management.
 * Provides AES-256 GCM encryption/decryption.
 */
class CryptoManager {

    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    private val encryptCipher = Cipher.getInstance(TRANSFORMATION)
    private val decryptCipher = Cipher.getInstance(TRANSFORMATION)

    private fun getKey(): SecretKey {
        val existingKey = keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry
        return existingKey?.secretKey ?: createKey()
    }

    private fun createKey(): SecretKey {
        return KeyGenerator.getInstance(ALGORITHM).apply {
            init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(BLOCK_MODE)
                    .setEncryptionPaddings(PADDING)
                    .setUserAuthenticationRequired(false) // Set true for biometric prompt
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
        }.generateKey()
    }

    fun encrypt(bytes: ByteArray, outputStream: OutputStream): ByteArray {
        val cipher = encryptCipher
        cipher.init(Cipher.ENCRYPT_MODE, getKey())
        val iv = cipher.iv
        outputStream.write(iv) // Store IV at the beginning
        val encryptedBytes = cipher.doFinal(bytes)
        outputStream.write(encryptedBytes)
        return encryptedBytes
    }
    
    // Helper for simple String encryption
    fun encrypt(plainText: String): String {
        val cipher = encryptCipher
        cipher.init(Cipher.ENCRYPT_MODE, getKey())
        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        // Combine IV and encrypted data: IV(12 bytes) + EncryptedData
        val combined = ByteArray(iv.size + encryptedBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(inputStream: InputStream): ByteArray {
        return inputStream.use { input ->
            val iv = ByteArray(12) // GCM IV is usually 12 bytes
            input.read(iv)
            val cipher = decryptCipher
            cipher.init(Cipher.DECRYPT_MODE, getKey(), IvParameterSpec(iv))
            val encryptedBytes = input.readBytes()
            cipher.doFinal(encryptedBytes)
        }
    }
    
    // Helper for simple String decryption
    fun decrypt(encryptedBase64: String): String {
        if (encryptedBase64.isEmpty()) return ""
        try {
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            
            // Extract IV (first 12 bytes)
            val iv = ByteArray(12)
            System.arraycopy(combined, 0, iv, 0, 12)
            
            // Extract Encrypted Data
            val encryptedSize = combined.size - 12
            val encryptedBytes = ByteArray(encryptedSize)
            System.arraycopy(combined, 12, encryptedBytes, 0, encryptedSize)

            val cipher = decryptCipher
            cipher.init(Cipher.DECRYPT_MODE, getKey(), IvParameterSpec(iv))
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            return "" // Or throw exception / return null
        }
    }

    companion object {
        private const val ALIAS = "investment_manager_secret_key"
        private const val ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
        private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
        private const val PADDING = KeyProperties.ENCRYPTION_PADDING_NONE
        private const val TRANSFORMATION = "$ALGORITHM/$BLOCK_MODE/$PADDING"
    }
}