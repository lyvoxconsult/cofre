package com.lyvox.vault.service

import android.content.Context
import android.net.Uri
import com.lyvox.vault.crypto.CryptoManager
import com.lyvox.vault.data.database.DatabaseHelper

/**
 * CSV Importer — supports formats from:
 * - Google Chrome / Edge (service_name, url, username, password)
 * - Bitwarden (name, login_uri, login_username, login_password, notes)
 * - 1Password (Title, Url, Username, Password, Notes)
 * - Generic (any CSV with detectable columns)
 *
 * ALL imports happen locally — no cloud involved.
 */
class CsvImporter(private val context: Context) {

    data class CsvEntry(
        val serviceName: String,
        val url: String,
        val login: String,
        val password: String,
        val notes: String
    )

    data class ImportResult(
        val imported: Int,
        val skipped: Int,
        val errors: List<String>
    )

    /**
     * Reads CSV from a Uri (content:// or file://) and returns parsed entries.
     */
    fun parseCsv(uri: Uri): List<CsvEntry> {
        val lines = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readLines()
            ?: return emptyList()

        if (lines.isEmpty()) return emptyList()

        val header = parseLine(lines[0]).map { it.trim().lowercase() }
        val mapping = detectMapping(header)

        return lines.drop(1).mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val cols = parseLine(line)
            try {
                CsvEntry(
                    serviceName = cols.getOrElse(mapping.serviceCol) { "" }.trim(),
                    url        = cols.getOrElse(mapping.urlCol) { "" }.trim(),
                    login      = cols.getOrElse(mapping.loginCol) { "" }.trim(),
                    password   = cols.getOrElse(mapping.passwordCol) { "" }.trim(),
                    notes      = if (mapping.notesCol >= 0) cols.getOrElse(mapping.notesCol) { "" }.trim() else ""
                ).takeIf { it.serviceName.isNotBlank() || it.url.isNotBlank() }
            } catch (_: Exception) { null }
        }
    }

    /**
     * Imports parsed entries into the vault, encrypting each field.
     */
    fun importEntries(
        entries: List<CsvEntry>,
        sessionKey: ByteArray,
        dbHelper: DatabaseHelper,
        skipDuplicates: Boolean = true
    ): ImportResult {
        var imported = 0
        var skipped = 0
        val errors = mutableListOf<String>()

        val existing = if (skipDuplicates) dbHelper.listEntries() else emptyList()

        for (e in entries) {
            try {
                // Skip if service+login already exists
                if (skipDuplicates && existing.any {
                    it.serviceName.equals(e.serviceName, ignoreCase = true) &&
                    it.login.equals(e.login, ignoreCase = true)
                }) {
                    skipped++
                    continue
                }

                val (encPwd, pwdNonce) = if (e.password.isEmpty()) Pair("", "")
                    else CryptoManager.encryptField(sessionKey, e.password)

                val (encNotes, notesNonce) = if (e.notes.isEmpty()) Pair("", "")
                    else CryptoManager.encryptField(sessionKey, e.notes)

                // Normalize URL
                val url = when {
                    e.url.isBlank() -> ""
                    e.url.startsWith("http://", ignoreCase = true) -> e.url
                    e.url.startsWith("https://", ignoreCase = true) -> e.url
                    else -> "https://${e.url}"
                }

                dbHelper.createEntry(
                    serviceName      = e.serviceName.ifBlank { url },
                    login            = e.login,
                    encryptedPassword = encPwd,
                    passwordNonce    = pwdNonce,
                    encryptedNotes   = encNotes,
                    notesNonce       = if (notesNonce.isEmpty()) null else notesNonce,
                    url              = url,
                    categoryId       = null
                )
                imported++
            } catch (ex: Exception) {
                errors.add("Erro em '${e.serviceName}': ${ex.message}")
            }
        }

        return ImportResult(imported, skipped, errors)
    }

    // ─── Column Mapping ─────────────────────────────────────────

    private data class ColMapping(
        val serviceCol: Int,
        val urlCol: Int,
        val loginCol: Int,
        val passwordCol: Int,
        val notesCol: Int
    )

    private fun detectMapping(header: List<String>): ColMapping {
        // Chrome/Edge export: name, url, username, password
        // Bitwarden: name, login_uri, login_username, login_password, notes
        // 1Password: title, url, username, password, notes
        // Generic fallback

        fun idx(vararg keys: String) = keys.firstNotNullOfOrNull { k ->
            header.indexOfFirst { it == k || it.contains(k) }.takeIf { it >= 0 }
        } ?: -1

        val service  = idx("name", "title", "service", "service_name")
        val url      = idx("url", "login_uri", "website", "login_url")
        val login    = idx("username", "login_username", "email", "login", "user")
        val password = idx("password", "login_password", "pass")
        val notes    = idx("notes", "note", "comments", "extra")

        // If service col not found, use url col as fallback
        return ColMapping(
            serviceCol  = if (service >= 0) service else if (url >= 0) url else 0,
            urlCol      = if (url >= 0) url else -1,
            loginCol    = if (login >= 0) login else 2,
            passwordCol = if (password >= 0) password else 3,
            notesCol    = notes
        )
    }

    // ─── CSV Parser (handles quoted fields with commas inside) ───

    private fun parseLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var inQuote = false
        val current = StringBuilder()

        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && !inQuote -> inQuote = true
                c == '"' && inQuote -> {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"'); i++  // escaped quote
                    } else {
                        inQuote = false
                    }
                }
                c == ',' && !inQuote -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        result.add(current.toString())
        return result
    }
}
