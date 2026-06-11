package com.lyvox.vault.crypto

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM encryption/decryption manager.
 *
 * Matches the desktop implementation exactly:
 * - AES-256-GCM with 12-byte nonce
 * - 128-bit authentication tag (GCM default)
 * - Base64 encoding for transport/storage
 */
object CryptoManager {

    private const val ALGORITHM = "AES/GCM/NoPadding"
    const val NONCE_LENGTH = 12 // bytes (96 bits)
    const val KEY_LENGTH = 32   // bytes (256 bits)

    private val secureRandom = SecureRandom()

    /**
     * Generates a cryptographically secure random nonce.
     */
    fun generateNonce(): ByteArray {
        val nonce = ByteArray(NONCE_LENGTH)
        secureRandom.nextBytes(nonce)
        return nonce
    }

    /**
     * Encrypts plaintext using AES-256-GCM.
     *
     * @param key 256-bit (32-byte) encryption key
     * @param plaintext data to encrypt
     * @return Pair of (ciphertext_with_tag, nonce)
     */
    fun encrypt(key: ByteArray, plaintext: ByteArray): Pair<ByteArray, ByteArray> {
        val nonce = generateNonce()
        val keySpec = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance(ALGORITHM)
        val gcmSpec = GCMParameterSpec(128, nonce) // 128-bit auth tag
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        val ciphertext = cipher.doFinal(plaintext)
        return Pair(ciphertext, nonce)
    }

    /**
     * Decrypts ciphertext using AES-256-GCM.
     * Authentication tag is embedded in ciphertext (last 16 bytes).
     *
     * @param key 256-bit (32-byte) encryption key
     * @param ciphertext encrypted data with embedded tag
     * @param nonce nonce used during encryption (12 bytes)
     * @return decrypted plaintext
     * @throws Exception if decryption fails (wrong key, tampered data)
     */
    fun decrypt(key: ByteArray, ciphertext: ByteArray, nonce: ByteArray): ByteArray {
        val keySpec = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance(ALGORITHM)
        val gcmSpec = GCMParameterSpec(128, nonce)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        return cipher.doFinal(ciphertext)
    }

    /**
     * Encrypts a string field and returns (ciphertext_base64, nonce_base64).
     */
    fun encryptField(key: ByteArray, plaintext: String): Pair<String, String> {
        val (ciphertext, nonce) = encrypt(key, plaintext.toByteArray(Charsets.UTF_8))
        val ctB64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        val nonceB64 = Base64.encodeToString(nonce, Base64.NO_WRAP)
        return Pair(ctB64, nonceB64)
    }

    /**
     * Decrypts a base64-encoded ciphertext field.
     */
    fun decryptField(key: ByteArray, ciphertextB64: String, nonceB64: String): String {
        val ciphertext = Base64.decode(ciphertextB64, Base64.NO_WRAP)
        val nonce = Base64.decode(nonceB64, Base64.NO_WRAP)
        require(nonce.size == NONCE_LENGTH) { "Invalid nonce length" }
        val plaintext = decrypt(key, ciphertext, nonce)
        return String(plaintext, Charsets.UTF_8)
    }
}
