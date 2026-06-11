package com.lyvox.vault.data.model

/**
 * Data models matching the desktop Rust implementation in storage/models.rs.
 */

/** Vault entry as stored in DB (encrypted fields). */
data class VaultEntry(
    val id: Long = 0,
    val serviceName: String,
    val login: String,
    val encryptedPassword: String,   // base64
    val passwordNonce: String,       // base64
    val encryptedNotes: String,      // base64
    val notesNonce: String?,         // base64
    val url: String,
    val categoryId: Long?,
    val isFavorite: Boolean = false,
    val createdAt: String,
    val updatedAt: String
)

/** Decrypted vault entry for UI display. */
data class VaultEntryDecrypted(
    val id: Long,
    val serviceName: String,
    val login: String,
    val password: String,
    val notes: String,
    val url: String,
    val categoryId: Long?,
    val categoryName: String?,
    val isFavorite: Boolean = false,
    val createdAt: String,
    val updatedAt: String
)

/** Payload for creating a new entry. */
data class CreateEntryPayload(
    val serviceName: String,
    val login: String,
    val url: String,
    val categoryId: Long?,
    val isFavorite: Boolean = false
)

/** Payload for updating an entry. */
data class UpdateEntryPayload(
    val id: Long,
    val serviceName: String,
    val login: String,
    val url: String,
    val categoryId: Long?,
    val isFavorite: Boolean = false
)

/** Category for organizing entries. */
data class Category(
    val id: Long,
    val name: String,
    val color: String,
    val icon: String,
    val sortOrder: Int
)

/** Tag for organizing entries. */
data class Tag(
    val id: Long = 0,
    val name: String,
    val color: String,
    val createdAt: String
)

/** Password history record. */
data class PasswordHistory(
    val id: Long = 0,
    val entryId: Long,
    val encryptedPassword: String,
    val passwordNonce: String,
    val createdAt: String
)

/** Secure note as stored in DB. */
data class SecureNote(
    val id: Long = 0,
    val title: String,
    val encryptedContent: String,   // base64
    val contentNonce: String,       // base64
    val category: String,
    val createdAt: String,
    val updatedAt: String
)

/** Decrypted secure note for UI display. */
data class SecureNoteDecrypted(
    val id: Long,
    val title: String,
    val content: String,
    val category: String,
    val createdAt: String,
    val updatedAt: String
)

/** Payload for creating/updating a note. */
data class NotePayload(
    val title: String,
    val content: String,
    val category: String
)

/** Recovery question as stored in config. */
data class RecoveryQuestion(
    val questionIndex: Int,
    val answerSalt: String,     // hex
    val answerHash: String,     // hex
    val options: List<String>   // 5 alternatives (correct + 4 distractors)
)

/** Recovery config. */
data class RecoveryConfig(
    val questions: List<RecoveryQuestion>,
    val wrappedMasterKey: String?,  // base64
    val wrapNonce: String?,         // base64
    val attempts: Int = 0,
    val blockedUntil: Long? = null,  // epoch seconds
    val recoverySalt: String? = null // hex — random salt for Argon2id recovery key derivation
)

/** App configuration (stored as JSON, NOT encrypted). */
data class AppConfig(
    val salt: String? = null,               // hex
    val autoLockMinutes: Int = 5,
    val clipboardClearSeconds: Int = 30,
    val theme: String = "dark",      // "dark", "light", "system"
    val recovery: RecoveryConfig? = null,

    // Password verification token — used to validate master password on unlock
    // Stores AES-256-GCM encrypted known plaintext to verify key correctness
    val verifyToken: String? = null,        // base64 ciphertext
    val verifyNonce: String? = null,        // base64 nonce

    val passwordGenLength: Int = 24,
    val passwordGenUppercase: Boolean = true,
    val passwordGenLowercase: Boolean = true,
    val passwordGenNumbers: Boolean = true,
    val passwordGenSpecial: Boolean = true,
    val passwordGenSpecialChars: String = "!@#\$%^&*()-_=+[]{}|;:,.<>?",
    val passwordGenExcludeAmbiguous: Boolean = false
)

/** Question with options for the recovery UI. */
data class QuestionWithOptions(
    val index: Int,
    val question: String,
    val options: List<String>
)

/** Recovery status for UI. */
data class RecoveryStatus(
    val configured: Boolean,
    val blocked: Boolean,
    val blockedRemainingSecs: Long?,
    val attempts: Int
)
