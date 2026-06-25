package com.lyvox.vault.service

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.lyvox.vault.crypto.CryptoManager
import com.lyvox.vault.crypto.KeyDerivation
import com.lyvox.vault.data.database.DatabaseHelper
import com.lyvox.vault.data.model.*

data class SyncCategory(
    val id: String,
    val name: String,
    val color: String,
    val icon: String,
    val sort_order: Int,
    val created_at: String,
    val updated_at: String,
    val deleted_at: String?
)

data class SyncEntry(
    val id: String,
    val service_name: String,
    val login: String,
    val password: String,
    val notes: String,
    val url: String,
    val category_id: String?,
    val is_favorite: Boolean,
    val created_at: String,
    val updated_at: String,
    val deleted_at: String?
)

data class SyncNote(
    val id: String,
    val title: String,
    val content: String,
    val category: String,
    val is_favorite: Boolean,
    val created_at: String,
    val updated_at: String,
    val deleted_at: String?
)

data class SyncAttachment(
    val id: String,
    val entry_id: String,
    val filename: String,
    val mime_type: String,
    val file_size: Long,
    val encrypted_key: String,
    val key_nonce: String,
    val file_nonce: String,
    val created_at: String,
    val updated_at: String,
    val deleted_at: String?,
    val encrypted_file_bytes_base64: String?,
    val raw_file_key_base64: String? = null
)

data class SyncMediaItem(
    val id: String,
    val filename: String,
    val mime_type: String,
    val file_size: Long,
    val encrypted_key: String,
    val key_nonce: String,
    val file_nonce: String,
    val thumbnail_data: String?,
    val thumbnail_nonce: String?,
    val is_favorite: Boolean,
    val created_at: String,
    val updated_at: String,
    val deleted_at: String?,
    val encrypted_file_bytes_base64: String?,
    val raw_file_key_base64: String? = null
)

data class SyncPayload(
    val app: String,
    val version: String,
    val created_at: String,
    val categories: List<SyncCategory>,
    val entries: List<SyncEntry>,
    val notes: List<SyncNote>,
    val attachments: List<SyncAttachment>,
    val media: List<SyncMediaItem>? = emptyList()
)

data class SyncEnvelope(
    val app: String,
    val version: String,
    val created_at: String,
    val encrypted_data: String,   // base64
    val nonce: String,            // base64
    val salt: String              // hex
)

class SyncManager(private val context: Context) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    companion object {
        private const val APP_NAME = "lyvox-vault"
        private const val SYNC_VERSION = "1"
        private val ID_REGEX = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    }

    fun exportSyncPackage(
        dbHelper: DatabaseHelper,
        sessionKey: ByteArray,
        syncPassword: String
    ): String {
        // Obter todas as categorias (incluindo deletadas)
        val categories = dbHelper.listAllCategoriesForSync().map { cat ->
            SyncCategory(
                id = cat.id,
                name = cat.name,
                color = cat.color,
                icon = cat.icon,
                sort_order = cat.sortOrder,
                created_at = cat.createdAt ?: "",
                updated_at = cat.updatedAt ?: "",
                deleted_at = cat.deletedAt
            )
        }

        // Obter todas as entradas (incluindo deletadas)
        val entries = dbHelper.listAllEntriesForSync().map { entry ->
            val password = if (entry.encryptedPassword.isEmpty()) ""
                else CryptoManager.decryptField(sessionKey, entry.encryptedPassword, entry.passwordNonce)
            val notes = if (entry.encryptedNotes.isEmpty()) ""
                else try { CryptoManager.decryptField(sessionKey, entry.encryptedNotes, entry.notesNonce ?: "") }
                    catch (_: Exception) { "" }

            SyncEntry(
                id = entry.id,
                service_name = entry.serviceName,
                login = entry.login,
                password = password,
                notes = notes,
                url = entry.url,
                category_id = entry.categoryId,
                is_favorite = entry.isFavorite,
                created_at = entry.createdAt,
                updated_at = entry.updatedAt,
                deleted_at = entry.deletedAt
            )
        }

        // Obter todas as notas (incluindo deletadas)
        val notes = dbHelper.listAllNotesForSync().map { note ->
            val content = if (note.encryptedContent.isEmpty()) ""
                else CryptoManager.decryptField(sessionKey, note.encryptedContent, note.contentNonce)

            SyncNote(
                id = note.id,
                title = note.title,
                content = content,
                category = note.category,
                is_favorite = note.isFavorite,
                created_at = note.createdAt,
                updated_at = note.updatedAt,
                deleted_at = note.deletedAt
            )
        }

        val attachments = dbHelper.listAllAttachmentsForSync().map { att ->
            validateStorageId(att.id)
            val fileBytes = if (att.deletedAt == null) readAttachmentFile(att.id) else null
            val fileBytesB64 = fileBytes?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) }
            
            val rawKey = try {
                decryptFileKeyBase64(sessionKey, att.encryptedKey, att.keyNonce)
            } catch (e: Exception) { "" }

            SyncAttachment(
                id = att.id,
                entry_id = att.entryId,
                filename = att.filename,
                mime_type = att.mimeType,
                file_size = att.fileSize,
                encrypted_key = att.encryptedKey,
                key_nonce = att.keyNonce,
                file_nonce = att.fileNonce,
                created_at = att.createdAt,
                updated_at = att.updatedAt,
                deleted_at = att.deletedAt,
                encrypted_file_bytes_base64 = fileBytesB64,
                raw_file_key_base64 = if (rawKey.isNotEmpty()) rawKey else null
            )
        }

        val media = dbHelper.listAllMediaForSync().map { m ->
            validateStorageId(m.id)
            val fileBytes = if (m.deletedAt == null) readMediaFile(m.id) else null
            val fileBytesB64 = fileBytes?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) }
            
            val rawKey = try {
                decryptFileKeyBase64(sessionKey, m.encryptedKey, m.keyNonce)
            } catch (e: Exception) { "" }

            SyncMediaItem(
                id = m.id,
                filename = m.filename,
                mime_type = m.mimeType,
                file_size = m.fileSize,
                encrypted_key = m.encryptedKey,
                key_nonce = m.keyNonce,
                file_nonce = m.fileNonce,
                thumbnail_data = m.thumbnailData,
                thumbnail_nonce = m.thumbnailNonce,
                is_favorite = m.isFavorite,
                created_at = m.createdAt,
                updated_at = m.updatedAt,
                deleted_at = m.deletedAt,
                encrypted_file_bytes_base64 = fileBytesB64,
                raw_file_key_base64 = if (rawKey.isNotEmpty()) rawKey else null
            )
        }

        val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date())

        val payload = SyncPayload(
            app = APP_NAME,
            version = SYNC_VERSION,
            created_at = now,
            categories = categories,
            entries = entries,
            notes = notes,
            attachments = attachments,
            media = media
        )

        val payloadJson = gson.toJson(payload)

        // Derivar chave de sincronização
        val syncSalt = KeyDerivation.generateSalt()
        val syncKey = KeyDerivation.deriveKey(syncPassword, syncSalt)

        // Criptografar
        val (ciphertext, nonce) = CryptoManager.encrypt(syncKey, payloadJson.toByteArray(Charsets.UTF_8))

        val encryptedB64 = android.util.Base64.encodeToString(ciphertext, android.util.Base64.NO_WRAP)
        val nonceB64 = android.util.Base64.encodeToString(nonce, android.util.Base64.NO_WRAP)
        val saltHex = bytesToHex(syncSalt)

        val envelope = SyncEnvelope(
            app = APP_NAME,
            version = SYNC_VERSION,
            created_at = now,
            encrypted_data = encryptedB64,
            nonce = nonceB64,
            salt = saltHex
        )

        return gson.toJson(envelope)
    }

    fun importSyncPackage(
        syncJson: String,
        syncPassword: String,
        sessionKey: ByteArray,
        dbHelper: DatabaseHelper
    ): String {
        val envelope = try {
            gson.fromJson(syncJson, SyncEnvelope::class.java)
        } catch (e: Exception) {
            throw IllegalArgumentException("Arquivo de sync inválido")
        }

        if (envelope.app != APP_NAME) {
            throw IllegalArgumentException("Arquivo de sincronização não reconhecido")
        }

        val encryptedBytes = try {
            android.util.Base64.decode(envelope.encrypted_data, android.util.Base64.NO_WRAP)
        } catch (_: Exception) {
            throw IllegalArgumentException("Pacote corrompido (base64 inválido)")
        }

        val nonceBytes = try {
            android.util.Base64.decode(envelope.nonce, android.util.Base64.NO_WRAP)
        } catch (_: Exception) {
            throw IllegalArgumentException("Pacote corrompido (nonce inválido)")
        }

        require(nonceBytes.size == CryptoManager.NONCE_LENGTH) {
            throw IllegalArgumentException("Pacote corrompido (nonce com tamanho inválido)")
        }

        val saltBytes = try {
            hexToBytes(envelope.salt)
        } catch (_: Exception) {
            throw IllegalArgumentException("Pacote corrompido (salt inválido)")
        }

        require(saltBytes.size == 16) {
            throw IllegalArgumentException("Pacote corrompido (salt com tamanho inválido)")
        }

        val syncKey = KeyDerivation.deriveKey(syncPassword, saltBytes)

        val decryptedBytes = try {
            CryptoManager.decrypt(syncKey, encryptedBytes, nonceBytes)
        } catch (e: Exception) {
            throw IllegalArgumentException("Senha de sincronização incorreta ou arquivo corrompido")
        }

        val payloadJson = String(decryptedBytes, Charsets.UTF_8)
        val payload = try {
            gson.fromJson(payloadJson, SyncPayload::class.java)
        } catch (_: Exception) {
            throw IllegalArgumentException("Pacote corrompido (dados inválidos)")
        }

        var catAdded = 0
        var catUpdated = 0
        var entryAdded = 0
        var entryUpdated = 0
        var entryIgnored = 0
        var noteAdded = 0
        var noteUpdated = 0
        var noteIgnored = 0

        // 1. Merge de Categorias
        for (importCat in payload.categories) {
            val localCat = dbHelper.getCategoryIncludeDeleted(importCat.id)
            val catModel = Category(
                id = importCat.id,
                name = importCat.name,
                color = importCat.color,
                icon = importCat.icon,
                sortOrder = importCat.sort_order,
                createdAt = importCat.created_at,
                updatedAt = importCat.updated_at,
                deletedAt = importCat.deleted_at
            )

            if (localCat != null) {
                if ((importCat.updated_at) > (localCat.updatedAt ?: "")) {
                    dbHelper.updateCategorySync(catModel)
                    catUpdated++
                }
            } else {
                dbHelper.insertCategorySync(catModel)
                catAdded++
            }
        }

        // 2. Merge de Entradas
        for (importEntry in payload.entries) {
            val localEntry = dbHelper.getEntryIncludeDeleted(importEntry.id)

            val (encPwd, pwdNonce) = if (importEntry.password.isEmpty()) {
                Pair("", "")
            } else {
                CryptoManager.encryptField(sessionKey, importEntry.password)
            }

            val (encNotes, notesNonce) = if (importEntry.notes.isEmpty()) {
                Pair("", "")
            } else {
                CryptoManager.encryptField(sessionKey, importEntry.notes)
            }

            val entryModel = VaultEntry(
                id = importEntry.id,
                serviceName = importEntry.service_name,
                login = importEntry.login,
                encryptedPassword = encPwd,
                passwordNonce = pwdNonce,
                encryptedNotes = encNotes,
                notesNonce = if (notesNonce.isEmpty()) null else notesNonce,
                url = importEntry.url,
                categoryId = importEntry.category_id,
                isFavorite = importEntry.is_favorite,
                createdAt = importEntry.created_at,
                updatedAt = importEntry.updated_at,
                deletedAt = importEntry.deleted_at,
                deviceId = null,
                lastModifiedDeviceId = null
            )

            if (localEntry != null) {
                if (importEntry.updated_at > localEntry.updatedAt) {
                    dbHelper.updateEntrySync(entryModel)
                    entryUpdated++
                } else {
                    entryIgnored++
                }
            } else {
                dbHelper.insertEntrySync(entryModel)
                entryAdded++
            }
        }

        // 3. Merge de Notas
        for (importNote in payload.notes) {
            val localNote = dbHelper.getNoteIncludeDeleted(importNote.id)

            val (encContent, contentNonce) = if (importNote.content.isEmpty()) {
                Pair("", "")
            } else {
                CryptoManager.encryptField(sessionKey, importNote.content)
            }

            val noteModel = SecureNote(
                id = importNote.id,
                title = importNote.title,
                encryptedContent = encContent,
                contentNonce = contentNonce,
                category = importNote.category,
                isFavorite = importNote.is_favorite,
                createdAt = importNote.created_at,
                updatedAt = importNote.updated_at,
                deletedAt = importNote.deleted_at,
                deviceId = null,
                lastModifiedDeviceId = null
            )

            if (localNote != null) {
                if (importNote.updated_at > localNote.updatedAt) {
                    dbHelper.updateNoteSync(noteModel)
                    noteUpdated++
                } else {
                    noteIgnored++
                }
            } else {
                dbHelper.insertNoteSync(noteModel)
                noteAdded++
            }
        }
        var attAdded = 0
        var attUpdated = 0
        var attIgnored = 0

        val attachmentsDir = java.io.File(context.filesDir, "attachments").apply {
            if (!exists()) {
                mkdirs()
            }
        }

        // 4. Merge de Anexos
        for (importAtt in payload.attachments) {
            validateStorageId(importAtt.id)
            val localAtt = dbHelper.getAttachmentIncludeDeleted(importAtt.id)
            
            var newEncKey = importAtt.encrypted_key
            var newKeyNonce = importAtt.key_nonce

            if (importAtt.raw_file_key_base64 != null) {
                try {
                    val rawKeyBytes = android.util.Base64.decode(importAtt.raw_file_key_base64, android.util.Base64.NO_WRAP)
                    val (encKeyBytes, nonceBytes) = CryptoManager.encrypt(sessionKey, rawKeyBytes)
                    newEncKey = android.util.Base64.encodeToString(encKeyBytes, android.util.Base64.NO_WRAP)
                    newKeyNonce = android.util.Base64.encodeToString(nonceBytes, android.util.Base64.NO_WRAP)
                } catch (e: Exception) { e.printStackTrace() }
            }

            val attModel = Attachment(
                id = importAtt.id,
                entryId = importAtt.entry_id,
                filename = importAtt.filename,
                mimeType = importAtt.mime_type,
                fileSize = importAtt.file_size,
                encryptedKey = newEncKey,
                keyNonce = newKeyNonce,
                fileNonce = importAtt.file_nonce,
                createdAt = importAtt.created_at,
                updatedAt = importAtt.updated_at,
                deletedAt = importAtt.deleted_at
            )

            var performUpdate = false
            var performInsert = false

            if (localAtt != null) {
                if (importAtt.updated_at > localAtt.updatedAt) {
                    performUpdate = true
                } else {
                    attIgnored++
                }
            } else {
                performInsert = true
            }

            if (performInsert || performUpdate) {
                if (importAtt.deleted_at != null) {
                    val file = java.io.File(attachmentsDir, importAtt.id)
                    if (file.exists()) {
                        file.delete()
                    }
                } else if (importAtt.encrypted_file_bytes_base64 != null) {
                    try {
                        val bytes = android.util.Base64.decode(importAtt.encrypted_file_bytes_base64, android.util.Base64.NO_WRAP)
                        val file = java.io.File(attachmentsDir, importAtt.id)
                        file.writeBytes(bytes)
                    } catch (_: Exception) {}
                }

                if (performInsert) {
                    dbHelper.insertAttachmentSync(attModel)
                    attAdded++
                } else {
                    dbHelper.updateAttachmentSync(attModel)
                    attUpdated++
                }
            }
        }

        var mediaAdded = 0
        var mediaUpdated = 0
        var mediaIgnored = 0

        val mediaDir = java.io.File(context.filesDir, "media_vault").apply {
            if (!exists()) {
                mkdirs()
            }
        }

        // 5. Merge de Media Vault
        val payloadMediaList = payload.media ?: emptyList()
        for (importMedia in payloadMediaList) {
            validateStorageId(importMedia.id)
            val localMedia = dbHelper.getMediaIncludeDeleted(importMedia.id)
            
            var newEncKey = importMedia.encrypted_key
            var newKeyNonce = importMedia.key_nonce

            if (importMedia.raw_file_key_base64 != null) {
                try {
                    val rawKeyBytes = android.util.Base64.decode(importMedia.raw_file_key_base64, android.util.Base64.NO_WRAP)
                    val (encKeyBytes, nonceBytes) = CryptoManager.encrypt(sessionKey, rawKeyBytes)
                    newEncKey = android.util.Base64.encodeToString(encKeyBytes, android.util.Base64.NO_WRAP)
                    newKeyNonce = android.util.Base64.encodeToString(nonceBytes, android.util.Base64.NO_WRAP)
                } catch (e: Exception) { e.printStackTrace() }
            }
            
            val mediaModel = MediaItem(
                id = importMedia.id,
                filename = importMedia.filename,
                mimeType = importMedia.mime_type,
                fileSize = importMedia.file_size,
                encryptedKey = newEncKey,
                keyNonce = newKeyNonce,
                fileNonce = importMedia.file_nonce,
                thumbnailData = importMedia.thumbnail_data,
                thumbnailNonce = importMedia.thumbnail_nonce,
                isFavorite = importMedia.is_favorite,
                createdAt = importMedia.created_at,
                updatedAt = importMedia.updated_at,
                deletedAt = importMedia.deleted_at,
                deviceId = null,
                lastModifiedDeviceId = null
            )

            var performUpdate = false
            var performInsert = false

            if (localMedia != null) {
                if (importMedia.updated_at > localMedia.updatedAt) {
                    performUpdate = true
                } else {
                    mediaIgnored++
                }
            } else {
                performInsert = true
            }

            if (performInsert || performUpdate) {
                if (importMedia.deleted_at != null) {
                    val file = java.io.File(mediaDir, importMedia.id)
                    if (file.exists()) {
                        file.delete()
                    }
                } else if (importMedia.encrypted_file_bytes_base64 != null) {
                    try {
                        val bytes = android.util.Base64.decode(importMedia.encrypted_file_bytes_base64, android.util.Base64.NO_WRAP)
                        val file = java.io.File(mediaDir, importMedia.id)
                        file.writeBytes(bytes)
                    } catch (_: Exception) {}
                }

                if (performInsert) {
                    dbHelper.insertMediaSync(mediaModel)
                    mediaAdded++
                } else {
                    dbHelper.updateMediaSync(mediaModel)
                    mediaUpdated++
                }
            }
        }

        return "Sync concluído: Categoria ($catAdded add, $catUpdated mod), Entrada ($entryAdded add, $entryUpdated mod, $entryIgnored ign), Nota ($noteAdded add, $noteUpdated mod, $noteIgnored ign), Anexo ($attAdded add, $attUpdated mod, $attIgnored ign), Midia ($mediaAdded add, $mediaUpdated mod, $mediaIgnored ign)."
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    private fun readAttachmentFile(id: String): ByteArray? {
        validateStorageId(id)
        val file = java.io.File(context.filesDir, "attachments/$id")
        return if (file.exists()) file.readBytes() else null
    }

    private fun readMediaFile(id: String): ByteArray? {
        validateStorageId(id)
        val file = java.io.File(context.filesDir, "media_vault/$id")
        return if (file.exists()) file.readBytes() else null
    }

    private fun validateStorageId(id: String) {
        require(ID_REGEX.matches(id)) { "ID de arquivo de sincronizacao invalido." }
    }

    private fun decryptFileKeyBase64(sessionKey: ByteArray, encryptedKey: String, keyNonce: String): String {
        val encryptedKeyBytes = android.util.Base64.decode(encryptedKey, android.util.Base64.NO_WRAP)
        val nonceBytes = android.util.Base64.decode(keyNonce, android.util.Base64.NO_WRAP)
        require(nonceBytes.size == CryptoManager.NONCE_LENGTH) { "Nonce invalido." }
        val rawKey = CryptoManager.decrypt(sessionKey, encryptedKeyBytes, nonceBytes)
        require(rawKey.size == CryptoManager.KEY_LENGTH) { "Chave de arquivo invalida." }
        return android.util.Base64.encodeToString(rawKey, android.util.Base64.NO_WRAP)
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        require(len % 2 == 0) { "Invalid hex" }
        return ByteArray(len / 2) {
            Integer.parseInt(hex.substring(it * 2, it * 2 + 2), 16).toByte()
        }
    }
}
