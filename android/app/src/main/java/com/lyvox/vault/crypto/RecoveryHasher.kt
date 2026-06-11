package com.lyvox.vault.crypto

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * SHA-256 hashing for recovery question answers.
 *
 * Matches the desktop implementation:
 * - SHA-256(salt + normalized_answer)
 * - Normalization: trim + lowercase (accents preserved)
 * - Salt: 16 random bytes per answer
 */
object RecoveryHasher {

    private const val SALT_LENGTH = 16
    private val secureRandom = SecureRandom()

    /**
     * Generates a random salt for a recovery answer.
     */
    fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        secureRandom.nextBytes(salt)
        return salt
    }

    /**
     * Normalizes an answer: trims whitespace and converts to lowercase.
     * Accents are NOT removed (intentional — preserves information).
     */
    fun normalizeAnswer(answer: String): String {
        return answer.trim().lowercase()
    }

    /**
     * Computes SHA-256(salt + normalized_answer) and returns hex string.
     *
     * @param salt hex-encoded salt
     * @param answer raw answer from user
     * @return hex-encoded SHA-256 hash
     */
    fun hashAnswer(salt: String, answer: String): String {
        val normalized = normalizeAnswer(answer)
        val saltBytes = hexToBytes(salt)
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(saltBytes)
        digest.update(normalized.toByteArray(Charsets.UTF_8))
        return bytesToHex(digest.digest())
    }

    /**
     * Computes SHA-256(salt_bytes + normalized_answer).
     */
    fun hashAnswer(salt: ByteArray, answer: String): String {
        val normalized = normalizeAnswer(answer)
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        digest.update(normalized.toByteArray(Charsets.UTF_8))
        return bytesToHex(digest.digest())
    }

    /**
     * 10 predefined security questions in Portuguese.
     */
    val QUESTIONS: List<String> = listOf(
        "Qual o nome do seu primeiro animal de estimação?",
        "Qual o nome da sua cidade natal?",
        "Qual o nome do seu melhor amigo de infância?",
        "Qual o nome do seu professor favorito?",
        "Qual o seu prato favorito?",
        "Qual o nome da sua primeira escola?",
        "Qual o sobrenome de solteira da sua mãe?",
        "Qual o nome do seu livro favorito?",
        "Em que ano você se formou no ensino médio?",
        "Qual o modelo do seu primeiro carro?"
    )

    // ─── Utility ───────────────────────────────────

    private val HEX_CHARS = "0123456789abcdef"

    fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(HEX_CHARS[(b.toInt() shr 4) and 0x0F])
            sb.append(HEX_CHARS[b.toInt() and 0x0F])
        }
        return sb.toString()
    }

    fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        require(len % 2 == 0) { "Invalid hex string length" }
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
        }
        return data
    }
}
