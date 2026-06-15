package com.lyvox.vault.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.spec.AlgorithmParameterSpec
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages biometric unlock via Android BiometricPrompt + Android Keystore.
 *
 * The master key is encrypted with a device-bound key stored in Android Keystore.
 * This encrypted blob is stored in SharedPreferences. Biometric authentication
 * is required to access the Keystore key, which then decrypts the master key.
 *
 * Security model:
 * - Biometric only: biometric authentication required to use the Keystore key
 * - Key invalidation: key is invalidated if biometric enrollment changes
 * - Master password is still required for: first setup, recovery, changing password
 */
class BiometricAuthManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "biometric_prefs", Context.MODE_PRIVATE
    )

    private val KEYSTORE_ALIAS = "lyvox_vault_biometric_key"
    private val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private val PREFS_KEY_ENCRYPTED_MASTER = "encrypted_master_key"
    private val PREFS_KEY_NONCE = "biometric_nonce"
    private val PREFS_KEY_ENABLED = "biometric_enabled"

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "lyvox_vault_biometric_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }

    /**
     * Checks if biometric hardware is available and enrolled.
     */
    fun isBiometricAvailable(): Boolean {
        val biometricManager = androidx.biometric.BiometricManager.from(context)
        return biometricManager.canAuthenticate(BIOMETRIC_STRONG) ==
                androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Checks if biometric unlock is enabled for this app.
     */
    fun isBiometricEnabled(): Boolean {
        return prefs.getBoolean(PREFS_KEY_ENABLED, false)
    }

    /**
     * Enables biometric unlock by encrypting the master key with a Keystore-bound key.
     */
    fun enableBiometric(masterKey: ByteArray): Boolean {
        return try {
            // Generate or get the Keystore key
            val secretKey = getOrCreateSecretKey()

            // Encrypt the master key
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv // 12-byte nonce for GCM
            val encrypted = cipher.doFinal(masterKey)

            // Store encrypted master key and IV
            prefs.edit()
                .putString(PREFS_KEY_ENCRYPTED_MASTER, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(PREFS_KEY_NONCE, Base64.encodeToString(iv, Base64.NO_WRAP))
                .putBoolean(PREFS_KEY_ENABLED, true)
                .apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    private val PREFS_KEY_CONSECUTIVE_USAGE = "consecutive_biometric_usage"
    private val MAX_CONSECUTIVE_USAGE = 5

    fun getConsecutiveBiometricUsages(): Int {
        return prefs.getInt(PREFS_KEY_CONSECUTIVE_USAGE, 0)
    }

    fun incrementConsecutiveBiometricUsages() {
        val current = getConsecutiveBiometricUsages()
        prefs.edit().putInt(PREFS_KEY_CONSECUTIVE_USAGE, current + 1).apply()
    }

    fun resetConsecutiveBiometricUsages() {
        prefs.edit().putInt(PREFS_KEY_CONSECUTIVE_USAGE, 0).apply()
    }

    fun isBiometricSuspended(): Boolean {
        return getConsecutiveBiometricUsages() >= MAX_CONSECUTIVE_USAGE
    }

    /**
     * Disables biometric unlock and removes the stored encrypted key.
     * Does NOT remove fingerprints from the device — only the app's local binding.
     */
    fun disableBiometric() {
        prefs.edit()
            .remove(PREFS_KEY_ENCRYPTED_MASTER)
            .remove(PREFS_KEY_NONCE)
            .putBoolean(PREFS_KEY_ENABLED, false)
            .putInt(PREFS_KEY_CONSECUTIVE_USAGE, 0)
            .apply()

        // Remove the Keystore key
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            keyStore.deleteEntry(KEY_ALIAS)
        } catch (_: Exception) {}
    }

    /**
     * Creates a BiometricPrompt for authentication and starts the prompt.
     * On success, decrypts the master key and returns it via the callback.
     *
     * IMPORTANT: The caller must NOT call authenticate() again on the returned prompt —
     * authentication is started automatically inside this method.
     */
    fun createBiometricPrompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: (ByteArray) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)

            // Read stored IV (required for GCM decryption)
            val nonceB64 = prefs.getString(PREFS_KEY_NONCE, null)
            if (nonceB64 != null) {
                val iv = Base64.decode(nonceB64, Base64.NO_WRAP)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            } else {
                onError("Configuração biométrica não encontrada. Reconfigure a biometria.")
                return
            }

            val cryptoObject = BiometricPrompt.CryptoObject(cipher)

            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    try {
                        val cryptoCipher = result.cryptoObject?.cipher ?: run {
                            onError("Erro ao obter cipher da biometria")
                            return
                        }
                        val encryptedB64 = prefs.getString(PREFS_KEY_ENCRYPTED_MASTER, null)
                        val storedNonce = prefs.getString(PREFS_KEY_NONCE, null)

                        if (encryptedB64 == null || storedNonce == null) {
                            onError("Chave biométrica não encontrada")
                            return
                        }

                        val encrypted = Base64.decode(encryptedB64, Base64.NO_WRAP)

                        // Cipher is already initialized with key + IV and authorized by
                        // biometric authentication — just decrypt directly
                        val masterKey = cryptoCipher.doFinal(encrypted)
                        onSuccess(masterKey)
                    } catch (e: Exception) {
                        onError("Erro na descriptografia biométrica: ${e.message}")
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                        onError(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onError("Biometria não reconhecida. Tente novamente.")
                }
            }

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(BIOMETRIC_STRONG)
                .setNegativeButtonText("Cancelar")
                .build()

            BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
                .authenticate(promptInfo, cryptoObject)
        } catch (e: Exception) {
            onError("Erro ao iniciar biometria: ${e.message}")
        }
    }

    /**
     * Enables biometric unlock by authenticating the user via BiometricPrompt first,
     * then encrypting the master key using the authorized Keystore key.
     */
    fun enableBiometricPrompt(
        activity: FragmentActivity,
        masterKey: ByteArray,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val cryptoObject = BiometricPrompt.CryptoObject(cipher)

            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    try {
                        val cryptoCipher = result.cryptoObject?.cipher ?: run {
                            onError("Erro ao obter cipher da biometria")
                            return
                        }
                        val encrypted = cryptoCipher.doFinal(masterKey)
                        val iv = cryptoCipher.iv // 12-byte nonce for GCM

                        // Store encrypted master key and IV
                        prefs.edit()
                            .putString(PREFS_KEY_ENCRYPTED_MASTER, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                            .putString(PREFS_KEY_NONCE, Base64.encodeToString(iv, Base64.NO_WRAP))
                            .putBoolean(PREFS_KEY_ENABLED, true)
                            .apply()

                        onSuccess()
                    } catch (e: Exception) {
                        onError("Erro ao criptografar chave: ${e.message}")
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                        onError(errString.toString())
                    } else {
                        onError("Cancelado pelo usuário")
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onError("Biometria não reconhecida. Tente novamente.")
                }
            }

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Ativar digital")
                .setSubtitle("Confirme sua digital para ativar o acesso biométrico")
                .setAllowedAuthenticators(BIOMETRIC_STRONG)
                .setNegativeButtonText("Cancelar")
                .build()

            BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
                .authenticate(promptInfo, cryptoObject)
        } catch (e: Exception) {
            onError("Erro ao iniciar biometria: ${e.message}")
        }
    }


    @Throws(Exception::class)
    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        // Check if key already exists
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        }

        // Generate new key
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true) // Invalidate if fingerprints change
            .build()

        keyGenerator.init(spec as AlgorithmParameterSpec)
        return keyGenerator.generateKey()
    }
}
