package com.example.util

import android.util.Base64
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {
    private const val ALGORITHM = "AES"
    // Symmetric 16-byte key for seamless cross-device DB compatibility
    private val KEY_BYTES = "CorpRemindSecKey".toByteArray(StandardCharsets.UTF_8)

    /**
     * Encrypts the input value using symmetric AES-128 if not null or empty.
     */
    fun encrypt(value: String?): String? {
        if (value.isNullOrEmpty()) return value
        return try {
            val keySpec = SecretKeySpec(KEY_BYTES, ALGORITHM)
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec)
            val encryptedBytes = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
            Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            value
        }
    }

    /**
     * Decrypts the input AES base64 value. Fallbacks gracefully to plaintext if the input
     * is not encrypted (backward-compatible with older backups).
     */
    fun decrypt(value: String?): String? {
        if (value.isNullOrEmpty()) return value
        return try {
            val keySpec = SecretKeySpec(KEY_BYTES, ALGORITHM)
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, keySpec)
            val decodedBytes = Base64.decode(value, Base64.NO_WRAP)
            val decryptedBytes = cipher.doFinal(decodedBytes)
            String(decryptedBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            // Graceful fallback for legacy raw texts (prior to encryption version)
            value
        }
    }
}
