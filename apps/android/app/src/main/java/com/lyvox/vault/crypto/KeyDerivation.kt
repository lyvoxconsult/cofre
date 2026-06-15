package com.lyvox.vault.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.SecureRandom

/**
 * Argon2id key derivation — identical parameters to the desktop Rust implementation.
 *
 * Desktop (Rust argon2 crate):
 *   - Algorithm: Argon2id
 *   - Version: 0x13
 *   - Memory: 64 MB (64 * 1024 KiB)
 *   - Iterations: 3
 *   - Parallelism: 4
 *   - Output: 32 bytes (256 bits)
 */
object KeyDerivation {

    private const val KEY_LENGTH = 32      // 256 bits
    private const val SALT_LENGTH = 16     // 128 bits
    private const val MEMORY_COST = 64 * 1024  // 64 MB in KiB
    private const val ITERATIONS = 3
    private const val PARALLELISM = 4

    private val secureRandom = SecureRandom()

    /**
     * Generates a cryptographically secure random salt.
     */
    fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        secureRandom.nextBytes(salt)
        return salt
    }

    /**
     * Derives a 256-bit key from a password using Argon2id.
     *
     * @param password the master password
     * @param salt 16-byte salt
     * @return 32-byte derived key
     */
    fun deriveKey(password: String, salt: ByteArray): ByteArray {
        val builder = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withMemoryAsKB(MEMORY_COST)
            .withIterations(ITERATIONS)
            .withParallelism(PARALLELISM)
            .withSalt(salt)
            .build()

        val generator = Argon2BytesGenerator()
        generator.init(builder)

        val result = ByteArray(KEY_LENGTH)
        generator.generateBytes(password.toByteArray(Charsets.UTF_8), result)
        return result
    }
}
