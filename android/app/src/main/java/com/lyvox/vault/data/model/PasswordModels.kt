package com.lyvox.vault.data.model

import kotlin.math.log2
import kotlin.math.min

/**
 * Password generation and strength evaluation.
 * Matches the desktop implementation logic.
 */

/** Password generation configuration. */
data class PasswordGenConfig(
    val length: Int = 24,
    val useUppercase: Boolean = true,
    val useLowercase: Boolean = true,
    val useNumbers: Boolean = true,
    val useSpecial: Boolean = true,
    val specialChars: String = "!@#\$%^&*()-_=+[]{}|;:,.<>?",
    val excludeAmbiguous: Boolean = false
)

/** Password generation result. */
data class PasswordGenResult(
    val password: String,
    val entropyBits: Double,
    val strengthLabel: String
)

/** Password generator using SecureRandom. */
object PasswordGenerator {

    private const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
    private const val NUMBERS = "0123456789"
    private const val AMBIGUOUS = "il1Lo0O"

    private val random = java.security.SecureRandom()

    /**
     * Generates a cryptographically secure random password.
     */
    fun generate(config: PasswordGenConfig): PasswordGenResult {
        // Build character pool
        val pool = mutableListOf<Char>()

        if (config.useUppercase) pool.addAll(UPPERCASE.toList())
        if (config.useLowercase) pool.addAll(LOWERCASE.toList())
        if (config.useNumbers) pool.addAll(NUMBERS.toList())
        if (config.useSpecial) pool.addAll(config.specialChars.toList())

        if (pool.isEmpty()) {
            throw IllegalArgumentException("Select at least one character type")
        }

        // Remove ambiguous characters if requested
        val filteredPool = if (config.excludeAmbiguous) {
            pool.filter { it !in AMBIGUOUS }
        } else {
            pool
        }.toMutableList()

        if (filteredPool.isEmpty()) {
            // Fallback: use lowercase only
            filteredPool.addAll(LOWERCASE.toList())
        }

        // Generate password
        val actualLength = config.length.coerceIn(4, 128)
        val password = CharArray(actualLength) {
            filteredPool[random.nextInt(filteredPool.size)]
        }

        val pwd = String(password)
        val entropy = calculateEntropy(pwd, filteredPool.size)
        val strength = evaluateStrength(entropy)

        return PasswordGenResult(pwd, entropy, strength)
    }

    /**
     * Calculates entropy of a password in bits.
     */
    private fun calculateEntropy(password: String, poolSize: Int): Double {
        if (poolSize <= 0) return 0.0
        return password.length * log2(poolSize.toDouble())
    }

    /**
     * Evaluates password strength label based on entropy bits.
     */
    fun evaluateStrength(entropyBits: Double): String {
        return when {
            entropyBits < 40 -> "Fraca"
            entropyBits < 60 -> "Razoável"
            entropyBits < 80 -> "Boa"
            entropyBits < 100 -> "Forte"
            else -> "Muito Forte"
        }
    }

    /**
     * Evaluates a user-provided password.
     */
    fun evaluatePassword(password: String): PasswordGenResult {
        // Determine effective pool size from password characters
        var poolSize = 0
        if (password.any { it.isUpperCase() }) poolSize += 26
        if (password.any { it.isLowerCase() }) poolSize += 26
        if (password.any { it.isDigit() }) poolSize += 10
        if (password.any { !it.isLetterOrDigit() }) poolSize += 32 // approximate

        if (poolSize == 0) poolSize = 26 // lowercase fallback

        val entropy = calculateEntropy(password, poolSize)
        val strength = evaluateStrength(entropy)

        return PasswordGenResult(password, entropy, strength)
    }
}
