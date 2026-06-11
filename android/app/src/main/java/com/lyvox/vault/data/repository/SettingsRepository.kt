package com.lyvox.vault.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.lyvox.vault.data.model.AppConfig
import com.lyvox.vault.data.model.RecoveryConfig
import com.lyvox.vault.data.model.RecoveryQuestion
import com.lyvox.vault.data.model.RecoveryStatus
import java.io.File

/**
 * Repository for app configuration (config.json).
 *
 * The config file is NOT encrypted and mirrors the desktop config.json format.
 * It contains non-sensitive metadata: salt, theme, auto-lock settings,
 * and recovery config (wrapped master key in base64).
 */
class SettingsRepository(private val context: Context) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    private val configFile: File
        get() = File(context.filesDir.parentFile, "config.json")

    /**
     * Loads the current config, or returns defaults if not found.
     */
    fun loadConfig(): AppConfig {
        return try {
            if (configFile.exists()) {
                val content = configFile.readText()
                gson.fromJson(content, AppConfig::class.java)
            } else {
                AppConfig()
            }
        } catch (e: Exception) {
            AppConfig()
        }
    }

    /**
     * Saves the config to disk.
     */
    fun saveConfig(config: AppConfig) {
        val content = gson.toJson(config)
        configFile.writeText(content)
    }

    /**
     * Returns whether this is a first run (no salt configured).
     */
    fun isFirstRun(): Boolean {
        return loadConfig().salt == null
    }

    /**
     * Gets the salt (hex) for key derivation.
     */
    fun getSalt(): String? = loadConfig().salt

    /**
     * Sets the salt and saves.
     */
    fun setSalt(salt: String) {
        val config = loadConfig().copy(salt = salt)
        saveConfig(config)
    }

    /**
     * Gets the theme setting.
     */
    fun getTheme(): String = loadConfig().theme

    /**
     * Sets the theme.
     */
    fun setTheme(theme: String) {
        val config = loadConfig().copy(theme = theme)
        saveConfig(config)
    }

    /**
     * Gets auto-lock minutes.
     */
    fun getAutoLockMinutes(): Int = loadConfig().autoLockMinutes

    /**
     * Sets auto-lock minutes.
     */
    fun setAutoLockMinutes(minutes: Int) {
        val config = loadConfig().copy(autoLockMinutes = minutes)
        saveConfig(config)
    }

    /**
     * Gets clipboard clear seconds.
     */
    fun getClipboardClearSeconds(): Int = loadConfig().clipboardClearSeconds

    /**
     * Sets clipboard clear seconds.
     */
    fun setClipboardClearSeconds(seconds: Int) {
        val config = loadConfig().copy(clipboardClearSeconds = seconds)
        saveConfig(config)
    }

    /**
     * Gets the recovery config.
     */
    fun getRecoveryConfig(): RecoveryConfig? = loadConfig().recovery

    /**
     * Sets the recovery config.
     */
    fun setRecoveryConfig(recovery: RecoveryConfig?) {
        val config = loadConfig().copy(recovery = recovery)
        saveConfig(config)
    }

    /**
     * Returns recovery status for UI.
     */
    fun getRecoveryStatus(): RecoveryStatus {
        val recovery = loadConfig().recovery
        if (recovery == null || recovery.questions.size != 3) {
            return RecoveryStatus(false, false, null, 0)
        }
        val now = System.currentTimeMillis() / 1000
        val blocked = recovery.blockedUntil != null && now < recovery.blockedUntil!!
        val remaining = if (blocked) (recovery.blockedUntil!! - now) else null
        return RecoveryStatus(true, blocked, remaining, recovery.attempts)
    }

    /**
     * Updates recovery attempts and block time.
     *
     * Progressive rate limiting (more restrictive than documented minimum):
     *   1-3 failed attempts → 60s block
     *   4-5 failed attempts → 300s (5 min) block
     *   6+  failed attempts → 1800s (30 min) block
     */
    fun updateRecoveryAttempts() {
        val recovery = loadConfig().recovery ?: return
        val newAttempts = recovery.attempts + 1
        val blockSecs = when {
            newAttempts <= 3 -> 60L
            newAttempts <= 5 -> 300L
            else -> 1800L
        }
        val blockedUntil = (System.currentTimeMillis() / 1000) + blockSecs
        setRecoveryConfig(recovery.copy(attempts = newAttempts, blockedUntil = blockedUntil))
    }

    /**
     * Resets recovery attempts.
     */
    fun resetRecoveryAttempts() {
        val recovery = loadConfig().recovery ?: return
        setRecoveryConfig(recovery.copy(attempts = 0, blockedUntil = null))
    }

    // ─── Master Password Verification ─────────────────────

    /**
     * Stores the verification token used to validate the master password on unlock.
     * The token is an AES-256-GCM encrypted known plaintext.
     */
    fun setVerifyToken(token: String, nonce: String) {
        val config = loadConfig().copy(verifyToken = token, verifyNonce = nonce)
        saveConfig(config)
    }

    fun getVerifyToken(): String? = loadConfig().verifyToken

    fun getVerifyNonce(): String? = loadConfig().verifyNonce
}
