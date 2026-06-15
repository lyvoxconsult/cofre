package com.lyvox.vault.service

import android.content.Context
import android.util.Base64
import com.lyvox.vault.crypto.CryptoManager
import com.lyvox.vault.data.database.DatabaseHelper
import com.lyvox.vault.data.model.AttachmentDecrypted
import com.lyvox.vault.security.SessionManager
import java.io.File
import java.security.SecureRandom
import java.util.UUID

/**
 * Manager for encrypted attachments and media vault on Android.
 * Compatible with the desktop implementation.
 */
class AttachmentManager(
    private val context: Context,
    private val dbHelper: DatabaseHelper,
    private val sessionManager: SessionManager
) {

    private val secureRandom = SecureRandom()

    private val attachmentsDir: File
        get() = File(context.filesDir, "attachments").apply {
            if (!exists()) {
                mkdirs()
            }
        }

    /**
     * Creates an encrypted attachment in the database and saves the encrypted file on disk.
     */
    fun createAttachment(
        entryId: String,
        filename: String,
        mimeType: String,
        fileBytes: ByteArray
    ): String {
        val sessionKey = sessionManager.getKey()
            ?: throw IllegalStateException("Cofre bloqueado. Desbloqueie primeiro.")

        // 1. Generate unique attachment ID
        val attachmentId = UUID.randomUUID().toString()

        // 2. Generate random 256-bit symmetric key for this specific file
        val fileKey = ByteArray(CryptoManager.KEY_LENGTH)
        secureRandom.nextBytes(fileKey)

        // 3. Encrypt file bytes with the file key
        val (encryptedFileBytes, fileNonce) = CryptoManager.encrypt(fileKey, fileBytes)

        // 4. Encrypt the file key with the session master key
        val (encryptedKeyBytes, keyNonce) = CryptoManager.encrypt(sessionKey, fileKey)

        // 5. Convert keys/nonces to Base64 for database storage
        val encryptedKeyB64 = Base64.encodeToString(encryptedKeyBytes, Base64.NO_WRAP)
        val keyNonceB64 = Base64.encodeToString(keyNonce, Base64.NO_WRAP)
        val fileNonceB64 = Base64.encodeToString(fileNonce, Base64.NO_WRAP)

        // 6. Save encrypted file to private app storage
        val destFile = File(attachmentsDir, attachmentId)
        destFile.writeBytes(encryptedFileBytes)

        // 7. Save metadata in SQLite
        dbHelper.createAttachment(
            id = attachmentId,
            entryId = entryId,
            filename = filename,
            mimeType = mimeType,
            fileSize = fileBytes.size.toLong(),
            encryptedKey = encryptedKeyB64,
            keyNonce = keyNonceB64,
            fileNonce = fileNonceB64
        )

        return attachmentId
    }

    /**
     * Decrypts and retrieves the attachment content bytes from disk.
     */
    fun getAttachmentData(id: String): ByteArray {
        val sessionKey = sessionManager.getKey()
            ?: throw IllegalStateException("Cofre bloqueado. Desbloqueie primeiro.")

        // 1. Fetch metadata from database
        val att = dbHelper.getAttachment(id)
            ?: throw IllegalArgumentException("Anexo não encontrado ou excluído.")

        // 2. Decode keys/nonces from Base64
        val encryptedKeyBytes = Base64.decode(att.encryptedKey, Base64.NO_WRAP)
        val keyNonce = Base64.decode(att.keyNonce, Base64.NO_WRAP)
        val fileNonce = Base64.decode(att.fileNonce, Base64.NO_WRAP)

        // 3. Decrypt the file symmetric key using the session key
        val decryptedKeyBytes = CryptoManager.decrypt(sessionKey, encryptedKeyBytes, keyNonce)
        require(decryptedKeyBytes.size == CryptoManager.KEY_LENGTH) { "Chave decodificada com tamanho inválido." }

        // 4. Read the encrypted file from storage
        val file = File(attachmentsDir, id)
        if (!file.exists()) {
            throw java.io.FileNotFoundException("Arquivo físico do anexo não encontrado em disco.")
        }
        val encryptedFileBytes = file.readBytes()

        // 5. Decrypt the file bytes using the file key
        return CryptoManager.decrypt(decryptedKeyBytes, encryptedFileBytes, fileNonce)
    }

    /**
     * Lists all non-deleted attachments metadata for a given entry.
     */
    fun listAttachments(entryId: String): List<AttachmentDecrypted> {
        if (!sessionManager.isUnlocked) {
            throw IllegalStateException("Cofre bloqueado. Desbloqueie primeiro.")
        }

        return dbHelper.listAttachments(entryId).map {
            AttachmentDecrypted(
                id = it.id,
                entryId = it.entryId,
                filename = it.filename,
                mimeType = it.mimeType,
                fileSize = it.fileSize,
                createdAt = it.createdAt,
                updatedAt = it.updatedAt
            )
        }
    }

    /**
     * Soft-deletes the attachment from the database and removes the physical file from disk.
     */
    fun deleteAttachment(id: String) {
        if (!sessionManager.isUnlocked) {
            throw IllegalStateException("Cofre bloqueado. Desbloqueie primeiro.")
        }

        // 1. Soft delete in DB
        dbHelper.deleteAttachment(id)

        // 2. Delete the physical file to free up space
        val file = File(attachmentsDir, id)
        if (file.exists()) {
            file.delete()
        }
    }
}
