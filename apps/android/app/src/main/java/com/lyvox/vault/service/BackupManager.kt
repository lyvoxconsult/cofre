package com.lyvox.vault.service

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.lyvox.vault.crypto.CryptoManager
import com.lyvox.vault.crypto.KeyDerivation
import com.lyvox.vault.data.database.DatabaseHelper
import com.lyvox.vault.data.model.*

/**
 * Backup/restore manager — 100% compatible with the desktop format.
 *
 * Desktop format (from backup.rs):
 * - Envelope: JSON with { app, version, created_at, encrypted_data, nonce, salt }
 * - Payload: JSON with { app, version, created_at, entries[], notes[] }
 * - Encryption: AES-256-GCM with Argon2id-derived key
 * - Salt: 16 random bytes per backup
 *
 * This implementation produces/consumes the exact same format.
 */

data class BackupEntry(
    val service_name: String,
    val login: String,
    val password: String,
    val notes: String,
    val url: String,
    val category_id: String?
)

data class BackupNote(
    val title: String,
    val content: String,
    val category: String
)

data class BackupPayload(
    val app: String,
    val version: String,
    val created_at: String,
    val entries: List<BackupEntry>,
    val notes: List<BackupNote>
)

data class BackupEnvelope(
    val app: String,
    val version: String,
    val created_at: String,
    val encrypted_data: String,   // base64
    val nonce: String,            // base64
    val salt: String              // hex
)

class BackupManager(private val context: Context) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    companion object {
        private const val APP_NAME = "lyvox-vault"
        private const val BACKUP_VERSION = "1"
    }

    /**
     * Exports all vault entries and secure notes to an encrypted backup.
     *
     * @param dbHelper database helper to read decrypted data
     * @param sessionKey current master session key for decrypting individual fields
     * @param backupPassword password to protect the backup
     * @return JSON string of the BackupEnvelope (ready to save as file)
     */
    fun exportBackup(
        dbHelper: DatabaseHelper,
        sessionKey: ByteArray,
        backupPassword: String
    ): String {
        // Read and decrypt all entries
        val rawEntries = dbHelper.listEntries()
        val entries = rawEntries.map { entry ->
            val password = if (entry.encryptedPassword.isEmpty()) ""
                else CryptoManager.decryptField(sessionKey, entry.encryptedPassword, entry.passwordNonce)
            val notes = if (entry.encryptedNotes.isEmpty()) ""
                else try { CryptoManager.decryptField(sessionKey, entry.encryptedNotes, entry.notesNonce ?: "") }
                    catch (_: Exception) { "" }

            BackupEntry(
                service_name = entry.serviceName,
                login = entry.login,
                password = password,
                notes = notes,
                url = entry.url,
                category_id = entry.categoryId
            )
        }

        // Read and decrypt all notes
        val rawNotes = dbHelper.listNotes()
        val notes = rawNotes.map { note ->
            val content = if (note.encryptedContent.isEmpty()) ""
                else CryptoManager.decryptField(sessionKey, note.encryptedContent, note.contentNonce)
            BackupNote(
                title = note.title,
                content = content,
                category = note.category
            )
        }

        val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date())

        // Build payload
        val payload = BackupPayload(
            app = APP_NAME,
            version = BACKUP_VERSION,
            created_at = now,
            entries = entries,
            notes = notes
        )

        val payloadJson = gson.toJson(payload)

        // Derive backup key (Argon2id with random salt — matching desktop)
        val backupSalt = KeyDerivation.generateSalt()
        val backupKey = KeyDerivation.deriveKey(backupPassword, backupSalt)

        // Encrypt with AES-256-GCM
        val (ciphertext, nonce) = CryptoManager.encrypt(backupKey, payloadJson.toByteArray(Charsets.UTF_8))

        val encryptedB64 = android.util.Base64.encodeToString(ciphertext, android.util.Base64.NO_WRAP)
        val nonceB64 = android.util.Base64.encodeToString(nonce, android.util.Base64.NO_WRAP)
        val saltHex = bytesToHex(backupSalt)

        // Build envelope
        val envelope = BackupEnvelope(
            app = APP_NAME,
            version = BACKUP_VERSION,
            created_at = now,
            encrypted_data = encryptedB64,
            nonce = nonceB64,
            salt = saltHex
        )

        return gson.toJson(envelope)
    }

    /**
     * Imports a backup, restoring all entries and notes.
     *
     * @param backupJson JSON string of the BackupEnvelope
     * @param backupPassword password to decrypt the backup
     * @param sessionKey current master session key for encrypting fields
     * @param dbHelper database helper for writing data
     * @param replace if true, clears existing data before restoring
     * @return summary string of restored items
     */
    fun importBackup(
        backupJson: String,
        backupPassword: String,
        sessionKey: ByteArray,
        dbHelper: DatabaseHelper,
        replace: Boolean
    ): String {
        // Parse envelope
        val envelope = try {
            gson.fromJson(backupJson, BackupEnvelope::class.java)
        } catch (e: Exception) {
            throw IllegalArgumentException("Arquivo de backup inválido")
        }

        // Validate app identifier
        if (envelope.app != APP_NAME) {
            throw IllegalArgumentException("Arquivo de backup não reconhecido")
        }

        // Decode base64
        val encryptedBytes = try {
            android.util.Base64.decode(envelope.encrypted_data, android.util.Base64.NO_WRAP)
        } catch (_: Exception) {
            throw IllegalArgumentException("Backup corrompido (base64 inválido)")
        }

        val nonceBytes = try {
            android.util.Base64.decode(envelope.nonce, android.util.Base64.NO_WRAP)
        } catch (_: Exception) {
            throw IllegalArgumentException("Backup corrompido (nonce inválido)")
        }

        require(nonceBytes.size == CryptoManager.NONCE_LENGTH) {
            throw IllegalArgumentException("Backup corrompido (nonce com tamanho inválido)")
        }

        val saltBytes = try {
            hexToBytes(envelope.salt)
        } catch (_: Exception) {
            throw IllegalArgumentException("Backup corrompido (salt inválido)")
        }

        require(saltBytes.size == 16) {
            throw IllegalArgumentException("Backup corrompido (salt com tamanho inválido)")
        }

        // Derive backup key
        val backupKey = KeyDerivation.deriveKey(backupPassword, saltBytes)

        // Decrypt (AES-256-GCM validates integrity via authentication tag)
        val decryptedBytes = try {
            CryptoManager.decrypt(backupKey, encryptedBytes, nonceBytes)
        } catch (e: Exception) {
            throw IllegalArgumentException("Senha de backup incorreta ou arquivo corrompido")
        }

        val payloadJson = String(decryptedBytes, Charsets.UTF_8)
        val payload = try {
            gson.fromJson(payloadJson, BackupPayload::class.java)
        } catch (_: Exception) {
            throw IllegalArgumentException("Backup corrompido (dados inválidos)")
        }

        // Replace or merge
        if (replace) {
            dbHelper.clearAllData()
        }

        // Restore entries
        var entriesRestored = 0
        for (entry in payload.entries) {
            val (encPwd, pwdNonce) = if (entry.password.isEmpty()) {
                Pair("", "")
            } else {
                CryptoManager.encryptField(sessionKey, entry.password)
            }

            val (encNotes, notesNonce) = if (entry.notes.isEmpty()) {
                Pair("", "")
            } else {
                CryptoManager.encryptField(sessionKey, entry.notes)
            }

            dbHelper.createEntry(
                serviceName = entry.service_name,
                login = entry.login,
                encryptedPassword = encPwd,
                passwordNonce = pwdNonce,
                encryptedNotes = encNotes,
                notesNonce = if (notesNonce.isEmpty()) null else notesNonce,
                url = entry.url,
                categoryId = entry.category_id
            )
            entriesRestored++
        }

        // Restore notes
        var notesRestored = 0
        for (note in payload.notes) {
            val (encContent, contentNonce) = if (note.content.isEmpty()) {
                Pair("", "")
            } else {
                CryptoManager.encryptField(sessionKey, note.content)
            }

            dbHelper.createNote(
                title = note.title,
                encryptedContent = encContent,
                contentNonce = contentNonce,
                category = note.category
            )
            notesRestored++
        }

        return "Restauradas $entriesRestored entradas e $notesRestored notas."
    }

    // ─── Hex utilities (matching desktop) ──────────────────

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        require(len % 2 == 0) { "Invalid hex" }
        return ByteArray(len / 2) {
            Integer.parseInt(hex.substring(it * 2, it * 2 + 2), 16).toByte()
        }
    }
}
