package com.lyvox.vault.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.lyvox.vault.crypto.CryptoManager
import com.lyvox.vault.data.database.DatabaseHelper
import com.lyvox.vault.data.model.MediaItem
import com.lyvox.vault.data.model.MediaItemDecrypted
import com.lyvox.vault.security.SessionManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.SecureRandom
import java.util.UUID

class MediaVaultManager(
    private val context: Context,
    private val dbHelper: DatabaseHelper,
    private val sessionManager: SessionManager
) {

    private val secureRandom = SecureRandom()

    private val mediaDir: File
        get() = File(context.filesDir, "media_vault").apply {
            if (!exists()) {
                mkdirs()
            }
            val nomedia = File(this, ".nomedia")
            if (!nomedia.exists()) {
                nomedia.createNewFile()
            }
        }

    fun createMedia(
        filename: String,
        mimeType: String,
        fileBytes: ByteArray,
        thumbnailBytes: ByteArray? = null
    ): String {
        val sessionKey = sessionManager.getKey()
            ?: throw IllegalStateException("Cofre bloqueado. Desbloqueie primeiro.")

        val mediaId = UUID.randomUUID().toString()

        val fileKey = ByteArray(CryptoManager.KEY_LENGTH)
        secureRandom.nextBytes(fileKey)

        val (encryptedFileBytes, fileNonce) = CryptoManager.encrypt(fileKey, fileBytes)
        val (encryptedKeyBytes, keyNonce) = CryptoManager.encrypt(sessionKey, fileKey)

        val encryptedKeyB64 = Base64.encodeToString(encryptedKeyBytes, Base64.NO_WRAP)
        val keyNonceB64 = Base64.encodeToString(keyNonce, Base64.NO_WRAP)
        val fileNonceB64 = Base64.encodeToString(fileNonce, Base64.NO_WRAP)

        var thumbnailData: String? = null
        var thumbnailNonce: String? = null
        if (thumbnailBytes != null) {
            val (encryptedThumbBytes, thumbNonceBytes) = CryptoManager.encrypt(sessionKey, thumbnailBytes)
            thumbnailData = Base64.encodeToString(encryptedThumbBytes, Base64.NO_WRAP)
            thumbnailNonce = Base64.encodeToString(thumbNonceBytes, Base64.NO_WRAP)
        }

        val destFile = File(mediaDir, mediaId)
        destFile.writeBytes(encryptedFileBytes)

        val now = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())

        dbHelper.createMedia(
            MediaItem(
                id = mediaId,
                filename = filename,
                mimeType = mimeType,
                fileSize = fileBytes.size.toLong(),
                encryptedKey = encryptedKeyB64,
                keyNonce = keyNonceB64,
                fileNonce = fileNonceB64,
                thumbnailData = thumbnailData,
                thumbnailNonce = thumbnailNonce,
                isFavorite = false,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
                deviceId = null,
                lastModifiedDeviceId = null
            )
        )

        return mediaId
    }

    fun getMediaData(id: String): ByteArray {
        val sessionKey = sessionManager.getKey()
            ?: throw IllegalStateException("Cofre bloqueado. Desbloqueie primeiro.")

        val media = dbHelper.getMedia(id)
            ?: throw IllegalArgumentException("Mídia não encontrada ou excluída.")

        val encryptedKeyBytes = Base64.decode(media.encryptedKey, Base64.NO_WRAP)
        val keyNonce = Base64.decode(media.keyNonce, Base64.NO_WRAP)
        val fileNonce = Base64.decode(media.fileNonce, Base64.NO_WRAP)

        val decryptedKeyBytes = CryptoManager.decrypt(sessionKey, encryptedKeyBytes, keyNonce)

        val file = File(mediaDir, id)
        if (!file.exists()) {
            throw java.io.FileNotFoundException("Arquivo físico não encontrado em disco.")
        }
        val encryptedFileBytes = file.readBytes()

        return CryptoManager.decrypt(decryptedKeyBytes, encryptedFileBytes, fileNonce)
    }

    fun getThumbnailData(media: MediaItem): ByteArray? {
        if (media.thumbnailData == null || media.thumbnailNonce == null) return null

        val sessionKey = sessionManager.getKey()
            ?: throw IllegalStateException("Cofre bloqueado. Desbloqueie primeiro.")

        val encryptedThumbBytes = Base64.decode(media.thumbnailData, Base64.NO_WRAP)
        val thumbNonce = Base64.decode(media.thumbnailNonce, Base64.NO_WRAP)

        return try {
            CryptoManager.decrypt(sessionKey, encryptedThumbBytes, thumbNonce)
        } catch (e: Exception) {
            // Tenta fallback com a fileKey caso tenha sido gerada antes do patch
            try {
                val encryptedKeyBytes = Base64.decode(media.encryptedKey, Base64.NO_WRAP)
                val keyNonce = Base64.decode(media.keyNonce, Base64.NO_WRAP)
                val decryptedKeyBytes = CryptoManager.decrypt(sessionKey, encryptedKeyBytes, keyNonce)
                CryptoManager.decrypt(decryptedKeyBytes, encryptedThumbBytes, thumbNonce)
            } catch (e2: Exception) {
                null
            }
        }
    }

    fun listMedia(): List<MediaItemDecrypted> {
        if (!sessionManager.isUnlocked) {
            throw IllegalStateException("Cofre bloqueado. Desbloqueie primeiro.")
        }

        return dbHelper.listMedia().map {
            MediaItemDecrypted(
                id = it.id,
                filename = it.filename,
                mimeType = it.mimeType,
                fileSize = it.fileSize,
                isFavorite = it.isFavorite,
                createdAt = it.createdAt,
                updatedAt = it.updatedAt,
                thumbnailBytes = getThumbnailData(it)
            )
        }
    }

    fun deleteMedia(id: String) {
        if (!sessionManager.isUnlocked) {
            throw IllegalStateException("Cofre bloqueado. Desbloqueie primeiro.")
        }

        dbHelper.deleteMedia(id)

        val file = File(mediaDir, id)
        if (file.exists()) {
            file.delete()
        }
    }
}
