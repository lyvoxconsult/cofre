package com.lyvox.vault.data.model

/**
 * Data models matching the desktop Rust implementation in storage/models.rs.
 */

/** Vault entry as stored in DB (encrypted fields). */
data class VaultEntry(
    val id: String = "",
    val serviceName: String,
    val login: String,
    val encryptedPassword: String,   // base64
    val passwordNonce: String,       // base64
    val encryptedNotes: String,      // base64
    val notesNonce: String?,         // base64
    val url: String,
    val categoryId: String?,
    val isFavorite: Boolean = false,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String? = null,
    val deviceId: String? = null,
    val lastModifiedDeviceId: String? = null
)

/** Decrypted vault entry for UI display. */
data class VaultEntryDecrypted(
    val id: String,
    val serviceName: String,
    val login: String,
    val password: String,
    val notes: String,
    val url: String,
    val categoryId: String?,
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
    val categoryId: String?,
    val isFavorite: Boolean = false
)

/** Payload for updating an entry. */
data class UpdateEntryPayload(
    val id: String,
    val serviceName: String,
    val login: String,
    val url: String,
    val categoryId: String?,
    val isFavorite: Boolean = false
)

/** Category for organizing entries. */
data class Category(
    val id: String,
    val name: String,
    val color: String,
    val icon: String,
    val sortOrder: Int,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val deletedAt: String? = null
)

/** Tag for organizing entries. */
data class Tag(
    val id: String = "",
    val name: String,
    val color: String,
    val createdAt: String
)

/** Password history record. */
data class PasswordHistory(
    val id: String = "",
    val entryId: String,
    val encryptedPassword: String,
    val passwordNonce: String,
    val createdAt: String
)

/** Secure note as stored in DB. */
data class SecureNote(
    val id: String = "",
    val title: String,
    val encryptedContent: String,   // base64
    val contentNonce: String,       // base64
    val category: String,
    val isFavorite: Boolean = false,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String? = null,
    val deviceId: String? = null,
    val lastModifiedDeviceId: String? = null
)

/** Decrypted secure note for UI display. */
data class SecureNoteDecrypted(
    val id: String,
    val title: String,
    val content: String,
    val category: String,
    val isFavorite: Boolean = false,
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

data class Attachment(
    val id: String = "",
    val entryId: String,
    val filename: String,
    val mimeType: String,
    val fileSize: Long,
    val encryptedKey: String,
    val keyNonce: String,
    val fileNonce: String,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String? = null,
    val deviceId: String? = null,
    val lastModifiedDeviceId: String? = null
)

data class AttachmentDecrypted(
    val id: String,
    val entryId: String,
    val filename: String,
    val mimeType: String,
    val fileSize: Long,
    val createdAt: String,
    val updatedAt: String
)

data class MediaItem(
    val id: String = "",
    val filename: String,
    val mimeType: String,
    val fileSize: Long,
    val encryptedKey: String,
    val keyNonce: String,
    val fileNonce: String,
    val thumbnailData: String?,
    val thumbnailNonce: String?,
    val isFavorite: Boolean = false,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String? = null,
    val deviceId: String? = null,
    val lastModifiedDeviceId: String? = null
)

data class MediaItemDecrypted(
    val id: String,
    val filename: String,
    val mimeType: String,
    val fileSize: Long,
    val isFavorite: Boolean,
    val thumbnailBytes: ByteArray?,
    val createdAt: String,
    val updatedAt: String
)
