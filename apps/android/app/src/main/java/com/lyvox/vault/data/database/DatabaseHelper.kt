package com.lyvox.vault.data.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.lyvox.vault.data.model.*
import java.util.UUID

/**
 * SQLite database helper — schema matches the desktop Rust implementation.
 *
 * Tables:
 * - categories (pre-populated)
 * - vault_entries
 * - secure_notes
 * - tags
 * - entry_tags
 * - password_history
 *
 * Version 5: IDs migrated from INTEGER AUTOINCREMENT to TEXT (UUID).
 *            Sync metadata columns added (deleted_at, device_id, last_modified_device_id).
 */
class DatabaseHelper(context: Context) : SQLiteOpenHelper(
    context, DATABASE_NAME, null, DATABASE_VERSION
) {

    companion object {
        private const val DATABASE_NAME = "vault.db"
        private const val DATABASE_VERSION = 7

        private const val TABLE_CATEGORIES = "categories"
        private const val TABLE_ENTRIES = "vault_entries"
        private const val TABLE_NOTES = "secure_notes"
        private const val TABLE_TAGS = "tags"
        private const val TABLE_ENTRY_TAGS = "entry_tags"
        private const val TABLE_PASSWORD_HISTORY = "password_history"
        private const val TABLE_ATTACHMENTS = "attachments"
        private const val TABLE_MEDIA_VAULT = "media_vault"
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Categories table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_CATEGORIES (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL UNIQUE,
                color TEXT NOT NULL DEFAULT '#6366f1',
                icon TEXT NOT NULL DEFAULT 'folder',
                sort_order INTEGER NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL DEFAULT (datetime('now')),
                updated_at TEXT NOT NULL DEFAULT (datetime('now')),
                deleted_at TEXT DEFAULT NULL,
                device_id TEXT DEFAULT NULL,
                last_modified_device_id TEXT DEFAULT NULL
            )
        """)

        // Vault entries table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_ENTRIES (
                id TEXT PRIMARY KEY,
                service_name TEXT NOT NULL,
                login TEXT NOT NULL,
                encrypted_password BLOB NOT NULL,
                password_nonce BLOB NOT NULL,
                encrypted_notes BLOB NOT NULL DEFAULT '',
                notes_nonce BLOB DEFAULT NULL,
                url TEXT NOT NULL DEFAULT '',
                category_id TEXT DEFAULT NULL REFERENCES categories(id) ON DELETE SET NULL,
                is_favorite INTEGER NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL DEFAULT (datetime('now')),
                updated_at TEXT NOT NULL DEFAULT (datetime('now')),
                deleted_at TEXT DEFAULT NULL,
                device_id TEXT DEFAULT NULL,
                last_modified_device_id TEXT DEFAULT NULL
            )
        """)

        // Secure notes table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_NOTES (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL DEFAULT '',
                encrypted_content BLOB NOT NULL DEFAULT '',
                content_nonce BLOB NOT NULL DEFAULT '',
                category TEXT NOT NULL DEFAULT '',
                is_favorite INTEGER NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL DEFAULT (datetime('now')),
                updated_at TEXT NOT NULL DEFAULT (datetime('now')),
                deleted_at TEXT DEFAULT NULL,
                device_id TEXT DEFAULT NULL,
                last_modified_device_id TEXT DEFAULT NULL
            )
        """)

        // Indexes
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_entries_service ON $TABLE_ENTRIES(service_name)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_entries_category ON $TABLE_ENTRIES(category_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_entries_updated ON $TABLE_ENTRIES(updated_at DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_notes_title ON $TABLE_NOTES(title)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_notes_category ON $TABLE_NOTES(category)")

        // Tags table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_TAGS (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL UNIQUE,
                color TEXT NOT NULL DEFAULT '#6366f1',
                created_at TEXT NOT NULL DEFAULT (datetime('now')),
                updated_at TEXT NOT NULL DEFAULT (datetime('now')),
                deleted_at TEXT DEFAULT NULL,
                device_id TEXT DEFAULT NULL,
                last_modified_device_id TEXT DEFAULT NULL
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_ENTRY_TAGS (
                entry_id TEXT NOT NULL REFERENCES $TABLE_ENTRIES(id) ON DELETE CASCADE,
                tag_id TEXT NOT NULL REFERENCES $TABLE_TAGS(id) ON DELETE CASCADE,
                created_at TEXT NOT NULL DEFAULT (datetime('now')),
                PRIMARY KEY (entry_id, tag_id)
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_PASSWORD_HISTORY (
                id TEXT PRIMARY KEY,
                entry_id TEXT NOT NULL REFERENCES $TABLE_ENTRIES(id) ON DELETE CASCADE,
                encrypted_password BLOB NOT NULL,
                password_nonce BLOB NOT NULL,
                created_at TEXT NOT NULL DEFAULT (datetime('now')),
                device_id TEXT DEFAULT NULL
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_pw_history_entry ON $TABLE_PASSWORD_HISTORY(entry_id)")

        // Attachments table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS attachments (
                id TEXT PRIMARY KEY,
                entry_id TEXT NOT NULL REFERENCES $TABLE_ENTRIES(id) ON DELETE CASCADE,
                filename TEXT NOT NULL,
                mime_type TEXT NOT NULL,
                file_size INTEGER NOT NULL,
                encrypted_key TEXT NOT NULL,
                key_nonce TEXT NOT NULL,
                file_nonce TEXT NOT NULL,
                created_at TEXT NOT NULL DEFAULT (datetime('now')),
                updated_at TEXT NOT NULL DEFAULT (datetime('now')),
                deleted_at TEXT DEFAULT NULL,
                device_id TEXT DEFAULT NULL,
                last_modified_device_id TEXT DEFAULT NULL
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_attachments_entry ON attachments(entry_id)")

        // Media Vault table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_MEDIA_VAULT (
                id TEXT PRIMARY KEY,
                filename TEXT NOT NULL,
                mime_type TEXT NOT NULL,
                file_size INTEGER NOT NULL,
                encrypted_key TEXT NOT NULL,
                key_nonce TEXT NOT NULL,
                file_nonce TEXT NOT NULL,
                thumbnail_data TEXT DEFAULT NULL,
                thumbnail_nonce TEXT DEFAULT NULL,
                is_favorite INTEGER NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL DEFAULT (datetime('now')),
                updated_at TEXT NOT NULL DEFAULT (datetime('now')),
                deleted_at TEXT DEFAULT NULL,
                device_id TEXT DEFAULT NULL,
                last_modified_device_id TEXT DEFAULT NULL
            )
        """)

        // Pre-populate default categories
        seedCategories(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("PRAGMA foreign_keys=OFF")
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE_ENTRIES ADD COLUMN url TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 3) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS $TABLE_NOTES (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL DEFAULT '',
                    encrypted_content BLOB NOT NULL DEFAULT '',
                    content_nonce BLOB NOT NULL DEFAULT '',
                    category TEXT NOT NULL DEFAULT '',
                    created_at TEXT NOT NULL DEFAULT (datetime('now')),
                    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
                )
            """)
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE $TABLE_ENTRIES ADD COLUMN is_favorite INTEGER NOT NULL DEFAULT 0")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS $TABLE_TAGS (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL UNIQUE,
                    color TEXT NOT NULL DEFAULT '#6366f1',
                    created_at TEXT NOT NULL DEFAULT (datetime('now'))
                )
            """)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS $TABLE_ENTRY_TAGS (
                    entry_id INTEGER NOT NULL REFERENCES $TABLE_ENTRIES(id) ON DELETE CASCADE,
                    tag_id INTEGER NOT NULL REFERENCES $TABLE_TAGS(id) ON DELETE CASCADE,
                    PRIMARY KEY (entry_id, tag_id)
                )
            """)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS $TABLE_PASSWORD_HISTORY (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    entry_id INTEGER NOT NULL REFERENCES $TABLE_ENTRIES(id) ON DELETE CASCADE,
                    encrypted_password BLOB NOT NULL,
                    password_nonce BLOB NOT NULL,
                    created_at TEXT NOT NULL DEFAULT (datetime('now'))
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_pw_history_entry ON $TABLE_PASSWORD_HISTORY(entry_id)")
        }
        if (oldVersion < 5) {
            // Migration 5: IDs migrated from INTEGER to TEXT (UUID), sync metadata columns added.

            // 1. Categories
            db.execSQL("DROP TABLE IF EXISTS new_categories")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS new_categories (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL UNIQUE,
                    color TEXT NOT NULL DEFAULT '#6366f1',
                    icon TEXT NOT NULL DEFAULT 'folder',
                    sort_order INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL DEFAULT (datetime('now')),
                    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
                    deleted_at TEXT DEFAULT NULL,
                    device_id TEXT DEFAULT NULL,
                    last_modified_device_id TEXT DEFAULT NULL
                )
            """)
            db.execSQL("INSERT INTO new_categories (id, name, color, icon, sort_order, created_at, updated_at) SELECT CAST(id AS TEXT), name, color, icon, sort_order, created_at, created_at FROM categories")
            db.execSQL("DROP TABLE categories")
            db.execSQL("ALTER TABLE new_categories RENAME TO categories")

            // 2. Vault Entries
            db.execSQL("DROP TABLE IF EXISTS new_vault_entries")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS new_vault_entries (
                    id TEXT PRIMARY KEY,
                    service_name TEXT NOT NULL,
                    login TEXT NOT NULL,
                    encrypted_password BLOB NOT NULL,
                    password_nonce BLOB NOT NULL,
                    encrypted_notes BLOB NOT NULL DEFAULT '',
                    notes_nonce BLOB DEFAULT NULL,
                    url TEXT NOT NULL DEFAULT '',
                    category_id TEXT DEFAULT NULL REFERENCES categories(id) ON DELETE SET NULL,
                    is_favorite INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL DEFAULT (datetime('now')),
                    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
                    deleted_at TEXT DEFAULT NULL,
                    device_id TEXT DEFAULT NULL,
                    last_modified_device_id TEXT DEFAULT NULL
                )
            """)
            db.execSQL("INSERT INTO new_vault_entries (id, service_name, login, encrypted_password, password_nonce, encrypted_notes, notes_nonce, url, category_id, is_favorite, created_at, updated_at) SELECT CAST(id AS TEXT), service_name, login, encrypted_password, password_nonce, encrypted_notes, notes_nonce, url, CAST(category_id AS TEXT), is_favorite, created_at, updated_at FROM vault_entries")
            db.execSQL("DROP TABLE vault_entries")
            db.execSQL("ALTER TABLE new_vault_entries RENAME TO vault_entries")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_entries_service ON vault_entries(service_name)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_entries_category ON vault_entries(category_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_entries_updated ON vault_entries(updated_at DESC)")

            // 3. Secure Notes
            db.execSQL("DROP TABLE IF EXISTS new_secure_notes")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS new_secure_notes (
                    id TEXT PRIMARY KEY,
                    title TEXT NOT NULL DEFAULT '',
                    encrypted_content BLOB NOT NULL DEFAULT '',
                    content_nonce BLOB NOT NULL DEFAULT '',
                    category TEXT NOT NULL DEFAULT '',
                    is_favorite INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL DEFAULT (datetime('now')),
                    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
                    deleted_at TEXT DEFAULT NULL,
                    device_id TEXT DEFAULT NULL,
                    last_modified_device_id TEXT DEFAULT NULL
                )
            """)
            db.execSQL("INSERT INTO new_secure_notes (id, title, encrypted_content, content_nonce, category, created_at, updated_at) SELECT CAST(id AS TEXT), title, encrypted_content, content_nonce, category, created_at, updated_at FROM secure_notes")
            db.execSQL("DROP TABLE secure_notes")
            db.execSQL("ALTER TABLE new_secure_notes RENAME TO secure_notes")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_notes_title ON secure_notes(title)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_notes_category ON secure_notes(category)")

            // 4. Tags
            db.execSQL("DROP TABLE IF EXISTS new_tags")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS new_tags (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL UNIQUE,
                    color TEXT NOT NULL DEFAULT '#6366f1',
                    created_at TEXT NOT NULL DEFAULT (datetime('now')),
                    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
                    deleted_at TEXT DEFAULT NULL,
                    device_id TEXT DEFAULT NULL,
                    last_modified_device_id TEXT DEFAULT NULL
                )
            """)
            db.execSQL("INSERT INTO new_tags (id, name, color, created_at, updated_at) SELECT CAST(id AS TEXT), name, color, created_at, created_at FROM tags")
            db.execSQL("DROP TABLE tags")
            db.execSQL("ALTER TABLE new_tags RENAME TO tags")

            // 5. Entry Tags
            db.execSQL("DROP TABLE IF EXISTS new_entry_tags")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS new_entry_tags (
                    entry_id TEXT NOT NULL REFERENCES vault_entries(id) ON DELETE CASCADE,
                    tag_id TEXT NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
                    created_at TEXT NOT NULL DEFAULT (datetime('now')),
                    PRIMARY KEY (entry_id, tag_id)
                )
            """)
            db.execSQL("INSERT INTO new_entry_tags (entry_id, tag_id) SELECT CAST(entry_id AS TEXT), CAST(tag_id AS TEXT) FROM entry_tags")
            db.execSQL("DROP TABLE entry_tags")
            db.execSQL("ALTER TABLE new_entry_tags RENAME TO entry_tags")

            // 6. Password History
            db.execSQL("DROP TABLE IF EXISTS new_password_history")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS new_password_history (
                    id TEXT PRIMARY KEY,
                    entry_id TEXT NOT NULL REFERENCES vault_entries(id) ON DELETE CASCADE,
                    encrypted_password BLOB NOT NULL,
                    password_nonce BLOB NOT NULL,
                    created_at TEXT NOT NULL DEFAULT (datetime('now')),
                    device_id TEXT DEFAULT NULL
                )
            """)
            db.execSQL("INSERT INTO new_password_history (id, entry_id, encrypted_password, password_nonce, created_at) SELECT CAST(id AS TEXT), CAST(entry_id AS TEXT), encrypted_password, password_nonce, created_at FROM password_history")
            db.execSQL("DROP TABLE password_history")
            db.execSQL("ALTER TABLE new_password_history RENAME TO password_history")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_pw_history_entry ON password_history(entry_id)")
        }
        if (oldVersion < 6) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS attachments (
                    id TEXT PRIMARY KEY,
                    entry_id TEXT NOT NULL REFERENCES vault_entries(id) ON DELETE CASCADE,
                    filename TEXT NOT NULL,
                    mime_type TEXT NOT NULL,
                    file_size INTEGER NOT NULL,
                    encrypted_key TEXT NOT NULL,
                    key_nonce TEXT NOT NULL,
                file_nonce TEXT NOT NULL,
                created_at TEXT NOT NULL DEFAULT (datetime('now')),
                updated_at TEXT NOT NULL DEFAULT (datetime('now')),
                deleted_at TEXT DEFAULT NULL,
                device_id TEXT DEFAULT NULL,
                last_modified_device_id TEXT DEFAULT NULL
            )
        """)
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_attachments_entry ON attachments(entry_id)")
        }
        if (oldVersion < 7) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS media_vault (
                    id TEXT PRIMARY KEY,
                    filename TEXT NOT NULL,
                    mime_type TEXT NOT NULL,
                    file_size INTEGER NOT NULL,
                    encrypted_key TEXT NOT NULL,
                    key_nonce TEXT NOT NULL,
                    file_nonce TEXT NOT NULL,
                    thumbnail_data TEXT DEFAULT NULL,
                    thumbnail_nonce TEXT DEFAULT NULL,
                    is_favorite INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL DEFAULT (datetime('now')),
                    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
                    deleted_at TEXT DEFAULT NULL,
                    device_id TEXT DEFAULT NULL,
                    last_modified_device_id TEXT DEFAULT NULL
                )
            """)
        }
        ensureColumn(db, TABLE_ATTACHMENTS, "device_id", "TEXT DEFAULT NULL")
        ensureColumn(db, TABLE_ATTACHMENTS, "last_modified_device_id", "TEXT DEFAULT NULL")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_attachments_deleted_at ON attachments(deleted_at)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_media_vault_deleted_at ON media_vault(deleted_at)")
        db.execSQL("PRAGMA foreign_keys=ON")
        db.rawQuery("PRAGMA foreign_key_check", null).use { check ->
            if (check.moveToFirst()) {
                throw android.database.sqlite.SQLiteException("Falha de integridade referencial apÃ³s migraÃ§Ã£o.")
            }
        }
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw android.database.sqlite.SQLiteException(
            "Downgrade de banco bloqueado para proteger dados. VersÃ£o atual: $oldVersion; APK: $newVersion."
        )
    }

    private fun ensureColumn(db: SQLiteDatabase, table: String, column: String, definition: String) {
        db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == column) return
            }
        }
        db.execSQL("ALTER TABLE $table ADD COLUMN $column $definition")
    }

    private fun categoryExists(db: SQLiteDatabase, id: String): Boolean {
        db.rawQuery("SELECT 1 FROM $TABLE_CATEGORIES WHERE id = ? AND deleted_at IS NULL LIMIT 1", arrayOf(id)).use {
            return it.moveToFirst()
        }
    }

    private fun seedCategories(db: SQLiteDatabase) {
        val categories = listOf(
            CategoryData("Pessoal", "#6366f1", "user", 0),
            CategoryData("Trabalho", "#f59e0b", "briefcase", 1),
            CategoryData("Bancos", "#10b981", "landmark", 2),
            CategoryData("Outros", "#6b7280", "folder", 3)
        )
        for (cat in categories) {
            val values = ContentValues().apply {
                put("id", (cat.sortOrder + 1).toString())
                put("name", cat.name)
                put("color", cat.color)
                put("icon", cat.icon)
                put("sort_order", cat.sortOrder)
            }
            db.insertWithOnConflict(TABLE_CATEGORIES, null, values, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    // ─── Categories ────────────────────────────────────────

    fun listCategories(): List<Category> {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT id, name, color, icon, sort_order FROM $TABLE_CATEGORIES ORDER BY sort_order",
            null
        )
        val categories = mutableListOf<Category>()
        cursor.use {
            while (it.moveToNext()) {
                categories.add(Category(
                    id = it.getString(0),
                    name = it.getString(1),
                    color = it.getString(2),
                    icon = it.getString(3),
                    sortOrder = it.getInt(4)
                ))
            }
        }
        return categories
    }

    fun getCategoryName(id: String): String? {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT name FROM $TABLE_CATEGORIES WHERE id = ?",
            arrayOf(id)
        )
        return cursor.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }

    // ─── Vault Entries ─────────────────────────────────────

    fun createEntry(
        serviceName: String, login: String,
        encryptedPassword: String, passwordNonce: String,
        encryptedNotes: String, notesNonce: String?,
        url: String, categoryId: String?, isFavorite: Boolean = false
    ): String {
        val db = writableDatabase
        val id = UUID.randomUUID().toString()
        val values = ContentValues().apply {
            put("id", id)
            put("service_name", serviceName)
            put("login", login)
            put("encrypted_password", encryptedPassword)
            put("password_nonce", passwordNonce)
            put("encrypted_notes", encryptedNotes)
            put("notes_nonce", notesNonce)
            put("url", url)
            put("category_id", categoryId)
            put("is_favorite", if (isFavorite) 1 else 0)
        }
        db.insert(TABLE_ENTRIES, null, values)
        return id
    }

    fun listEntries(): List<VaultEntry> {
        val db = readableDatabase
        val cursor = db.rawQuery(
            """SELECT id, service_name, login, encrypted_password, password_nonce,
                     encrypted_notes, notes_nonce, url, category_id, created_at, updated_at, is_favorite,
                     deleted_at, device_id, last_modified_device_id
              FROM $TABLE_ENTRIES WHERE deleted_at IS NULL ORDER BY updated_at DESC""", null
        )
        return cursor.use { parseEntries(it) }
    }

    fun searchEntries(query: String): List<VaultEntry> {
        val pattern = "%$query%"
        val db = readableDatabase
        val cursor = db.rawQuery(
            """SELECT id, service_name, login, encrypted_password, password_nonce,
                     encrypted_notes, notes_nonce, url, category_id, created_at, updated_at, is_favorite,
                     deleted_at, device_id, last_modified_device_id
              FROM $TABLE_ENTRIES
              WHERE deleted_at IS NULL AND (service_name LIKE ? OR login LIKE ? OR url LIKE ?)
              ORDER BY updated_at DESC""",
            arrayOf(pattern, pattern, pattern)
        )
        return cursor.use { parseEntries(it) }
    }

    fun getEntry(id: String): VaultEntry? {
        val db = readableDatabase
        val cursor = db.rawQuery(
            """SELECT id, service_name, login, encrypted_password, password_nonce,
                     encrypted_notes, notes_nonce, url, category_id, created_at, updated_at, is_favorite,
                     deleted_at, device_id, last_modified_device_id
              FROM $TABLE_ENTRIES WHERE id = ? AND deleted_at IS NULL""",
            arrayOf(id)
        )
        return cursor.use { if (it.moveToFirst()) parseEntry(it) else null }
    }

    fun updateEntry(
        id: String, serviceName: String, login: String,
        encryptedPassword: String, passwordNonce: String,
        encryptedNotes: String, notesNonce: String?,
        url: String, categoryId: String?, isFavorite: Boolean
    ) {
        val db = writableDatabase

        // 1. Check if the password has changed to save the old one in history
        val oldCursor = db.rawQuery("SELECT encrypted_password, password_nonce FROM $TABLE_ENTRIES WHERE id = ?", arrayOf(id))
        if (oldCursor.moveToFirst()) {
            val oldEncPwd = oldCursor.getString(0)
            val oldNonce = oldCursor.getString(1)
            // If the old password exists and is different from the new one
            if (oldEncPwd.isNotEmpty() && oldEncPwd != encryptedPassword) {
                val histValues = ContentValues().apply {
                    put("id", UUID.randomUUID().toString())
                    put("entry_id", id)
                    put("encrypted_password", oldEncPwd)
                    put("password_nonce", oldNonce)
                }
                db.insert(TABLE_PASSWORD_HISTORY, null, histValues)
            }
        }
        oldCursor.close()

        // 2. Update the entry
        val values = ContentValues().apply {
            put("service_name", serviceName)
            put("login", login)
            put("encrypted_password", encryptedPassword)
            put("password_nonce", passwordNonce)
            put("encrypted_notes", encryptedNotes)
            put("notes_nonce", notesNonce)
            put("url", url)
            put("category_id", categoryId)
            put("is_favorite", if (isFavorite) 1 else 0)
            put("updated_at", getCurrentTimestamp())
        }
        db.update(TABLE_ENTRIES, values, "id = ?", arrayOf(id))
    }

    fun deleteEntry(id: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("deleted_at", getCurrentTimestamp())
            put("updated_at", getCurrentTimestamp())
        }
        db.update(TABLE_ENTRIES, values, "id = ?", arrayOf(id))
    }

    fun rawUpdateEntry(id: String, encryptedPassword: String, passwordNonce: String,
                       encryptedNotes: String, notesNonce: String?) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("encrypted_password", encryptedPassword)
            put("password_nonce", passwordNonce)
            put("encrypted_notes", encryptedNotes)
            put("notes_nonce", notesNonce)
            put("updated_at", getCurrentTimestamp())
        }
        db.update(TABLE_ENTRIES, values, "id = ?", arrayOf(id))
    }

    // ─── Secure Notes ──────────────────────────────────────

    fun createNote(title: String, encryptedContent: String, contentNonce: String, category: String): String {
        val db = writableDatabase
        val id = UUID.randomUUID().toString()
        val values = ContentValues().apply {
            put("id", id)
            put("title", title)
            put("encrypted_content", encryptedContent)
            put("content_nonce", contentNonce)
            put("category", category)
        }
        db.insert(TABLE_NOTES, null, values)
        return id
    }

    fun listNotes(): List<SecureNote> {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT id, title, encrypted_content, content_nonce, category, created_at, updated_at, is_favorite, deleted_at, device_id, last_modified_device_id FROM $TABLE_NOTES WHERE deleted_at IS NULL ORDER BY updated_at DESC",
            null
        )
        return cursor.use { parseNotes(it) }
    }

    fun searchNotes(query: String): List<SecureNote> {
        val pattern = "%$query%"
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT id, title, encrypted_content, content_nonce, category, created_at, updated_at, is_favorite, deleted_at, device_id, last_modified_device_id FROM $TABLE_NOTES WHERE deleted_at IS NULL AND (title LIKE ? OR category LIKE ?) ORDER BY updated_at DESC",
            arrayOf(pattern, pattern)
        )
        return cursor.use { parseNotes(it) }
    }

    fun getNote(id: String): SecureNote? {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT id, title, encrypted_content, content_nonce, category, created_at, updated_at, is_favorite, deleted_at, device_id, last_modified_device_id FROM $TABLE_NOTES WHERE id = ? AND deleted_at IS NULL",
            arrayOf(id)
        )
        return cursor.use { if (it.moveToFirst()) parseNote(it) else null }
    }

    fun updateNote(id: String, title: String, encryptedContent: String, contentNonce: String, category: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("title", title)
            put("encrypted_content", encryptedContent)
            put("content_nonce", contentNonce)
            put("category", category)
            put("updated_at", getCurrentTimestamp())
        }
        db.update(TABLE_NOTES, values, "id = ?", arrayOf(id))
    }

    fun deleteNote(id: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("deleted_at", getCurrentTimestamp())
            put("updated_at", getCurrentTimestamp())
        }
        db.update(TABLE_NOTES, values, "id = ?", arrayOf(id))
    }

    fun rawUpdateNote(id: String, encryptedContent: String, contentNonce: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("encrypted_content", encryptedContent)
            put("content_nonce", contentNonce)
            put("updated_at", getCurrentTimestamp())
        }
        db.update(TABLE_NOTES, values, "id = ?", arrayOf(id))
    }

    fun clearAllData() {
        val db = writableDatabase
        db.delete(TABLE_ENTRIES, null, null)
        db.delete(TABLE_NOTES, null, null)
    }

    // ─── Helpers ───────────────────────────────────────────

    private fun getCurrentTimestamp(): String {
        val df = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        return df.format(java.util.Date())
    }

    private fun parseEntry(c: android.database.Cursor) = VaultEntry(
        id = c.getString(0),
        serviceName = c.getString(1),
        login = c.getString(2),
        encryptedPassword = c.getString(3),
        passwordNonce = c.getString(4),
        encryptedNotes = c.getString(5),
        notesNonce = c.getString(6),
        url = c.getString(7),
        categoryId = if (c.isNull(8)) null else c.getString(8),
        createdAt = c.getString(9),
        updatedAt = c.getString(10),
        isFavorite = if (!c.isNull(11)) c.getInt(11) == 1 else false,
        deletedAt = if (c.columnCount > 12 && !c.isNull(12)) c.getString(12) else null,
        deviceId = if (c.columnCount > 13 && !c.isNull(13)) c.getString(13) else null,
        lastModifiedDeviceId = if (c.columnCount > 14 && !c.isNull(14)) c.getString(14) else null
    )

    private fun parseEntries(c: android.database.Cursor): List<VaultEntry> {
        val list = mutableListOf<VaultEntry>()
        while (c.moveToNext()) {
            list.add(parseEntry(c))
        }
        return list
    }

    private fun parseNote(c: android.database.Cursor) = SecureNote(
        id = c.getString(0),
        title = c.getString(1),
        encryptedContent = c.getString(2),
        contentNonce = c.getString(3),
        category = c.getString(4),
        createdAt = c.getString(5),
        updatedAt = c.getString(6),
        isFavorite = if (c.columnCount > 7 && !c.isNull(7)) c.getInt(7) == 1 else false,
        deletedAt = if (c.columnCount > 8 && !c.isNull(8)) c.getString(8) else null,
        deviceId = if (c.columnCount > 9 && !c.isNull(9)) c.getString(9) else null,
        lastModifiedDeviceId = if (c.columnCount > 10 && !c.isNull(10)) c.getString(10) else null
    )

    private fun parseNotes(c: android.database.Cursor): List<SecureNote> {
        val list = mutableListOf<SecureNote>()
        while (c.moveToNext()) {
            list.add(parseNote(c))
        }
        return list
    }

    // ─── Sincronização Local (Sync) ────────────────────────

    fun listAllCategoriesForSync(): List<Category> {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT id, name, color, icon, sort_order, created_at, updated_at, deleted_at FROM $TABLE_CATEGORIES",
            null
        )
        val list = mutableListOf<Category>()
        cursor.use {
            while (it.moveToNext()) {
                list.add(Category(
                    id = it.getString(0),
                    name = it.getString(1),
                    color = it.getString(2),
                    icon = it.getString(3),
                    sortOrder = it.getInt(4),
                    createdAt = it.getString(5),
                    updatedAt = it.getString(6),
                    deletedAt = if (!it.isNull(7)) it.getString(7) else null
                ))
            }
        }
        return list
    }

    fun listAllEntriesForSync(): List<VaultEntry> {
        val db = readableDatabase
        val cursor = db.rawQuery(
            """SELECT id, service_name, login, encrypted_password, password_nonce,
                     encrypted_notes, notes_nonce, url, category_id, created_at, updated_at, is_favorite,
                     deleted_at, device_id, last_modified_device_id
              FROM $TABLE_ENTRIES""", null
        )
        return cursor.use { parseEntries(it) }
    }

    fun listAllNotesForSync(): List<SecureNote> {
        val db = readableDatabase
        val cursor = db.rawQuery(
            """SELECT id, title, encrypted_content, content_nonce, category, created_at, updated_at, is_favorite,
                     deleted_at, device_id, last_modified_device_id
              FROM $TABLE_NOTES""", null
        )
        return cursor.use { parseNotes(it) }
    }

    fun getCategoryIncludeDeleted(id: String): Category? {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT id, name, color, icon, sort_order, created_at, updated_at, deleted_at FROM $TABLE_CATEGORIES WHERE id = ?",
            arrayOf(id)
        )
        return cursor.use {
            if (it.moveToFirst()) {
                Category(
                    id = it.getString(0),
                    name = it.getString(1),
                    color = it.getString(2),
                    icon = it.getString(3),
                    sortOrder = it.getInt(4),
                    createdAt = it.getString(5),
                    updatedAt = it.getString(6),
                    deletedAt = if (!it.isNull(7)) it.getString(7) else null
                )
            } else null
        }
    }

    fun getEntryIncludeDeleted(id: String): VaultEntry? {
        val db = readableDatabase
        val cursor = db.rawQuery(
            """SELECT id, service_name, login, encrypted_password, password_nonce,
                     encrypted_notes, notes_nonce, url, category_id, created_at, updated_at, is_favorite,
                     deleted_at, device_id, last_modified_device_id
              FROM $TABLE_ENTRIES WHERE id = ?""",
            arrayOf(id)
        )
        return cursor.use { if (it.moveToFirst()) parseEntry(it) else null }
    }

    fun getNoteIncludeDeleted(id: String): SecureNote? {
        val db = readableDatabase
        val cursor = db.rawQuery(
            """SELECT id, title, encrypted_content, content_nonce, category, created_at, updated_at, is_favorite,
                     deleted_at, device_id, last_modified_device_id
              FROM $TABLE_NOTES WHERE id = ?""",
            arrayOf(id)
        )
        return cursor.use { if (it.moveToFirst()) parseNote(it) else null }
    }

    fun insertCategorySync(cat: Category) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("id", cat.id)
            put("name", cat.name)
            put("color", cat.color)
            put("icon", cat.icon)
            put("sort_order", cat.sortOrder)
            put("created_at", cat.createdAt ?: getCurrentTimestamp())
            put("updated_at", cat.updatedAt ?: getCurrentTimestamp())
            put("deleted_at", cat.deletedAt)
        }
        db.insertWithOnConflict(TABLE_CATEGORIES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun updateCategorySync(cat: Category) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("name", cat.name)
            put("color", cat.color)
            put("icon", cat.icon)
            put("sort_order", cat.sortOrder)
            put("created_at", cat.createdAt)
            put("updated_at", cat.updatedAt)
            put("deleted_at", cat.deletedAt)
        }
        db.update(TABLE_CATEGORIES, values, "id = ?", arrayOf(cat.id))
    }

    fun insertEntrySync(entry: VaultEntry) {
        val db = writableDatabase
        val categoryId = entry.categoryId?.takeIf { categoryExists(db, it) }
        val values = ContentValues().apply {
            put("id", entry.id)
            put("service_name", entry.serviceName)
            put("login", entry.login)
            put("encrypted_password", entry.encryptedPassword)
            put("password_nonce", entry.passwordNonce)
            put("encrypted_notes", entry.encryptedNotes)
            put("notes_nonce", entry.notesNonce)
            put("url", entry.url)
            put("category_id", categoryId)
            put("is_favorite", if (entry.isFavorite) 1 else 0)
            put("created_at", entry.createdAt)
            put("updated_at", entry.updatedAt)
            put("deleted_at", entry.deletedAt)
            put("device_id", entry.deviceId)
            put("last_modified_device_id", entry.lastModifiedDeviceId)
        }
        db.insertWithOnConflict(TABLE_ENTRIES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun updateEntrySync(entry: VaultEntry) {
        val db = writableDatabase
        val categoryId = entry.categoryId?.takeIf { categoryExists(db, it) }
        val values = ContentValues().apply {
            put("service_name", entry.serviceName)
            put("login", entry.login)
            put("encrypted_password", entry.encryptedPassword)
            put("password_nonce", entry.passwordNonce)
            put("encrypted_notes", entry.encryptedNotes)
            put("notes_nonce", entry.notesNonce)
            put("url", entry.url)
            put("category_id", categoryId)
            put("is_favorite", if (entry.isFavorite) 1 else 0)
            put("created_at", entry.createdAt)
            put("updated_at", entry.updatedAt)
            put("deleted_at", entry.deletedAt)
            put("device_id", entry.deviceId)
            put("last_modified_device_id", entry.lastModifiedDeviceId)
        }
        db.update(TABLE_ENTRIES, values, "id = ?", arrayOf(entry.id))
    }

    fun insertNoteSync(note: SecureNote) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("id", note.id)
            put("title", note.title)
            put("encrypted_content", note.encryptedContent)
            put("content_nonce", note.contentNonce)
            put("category", note.category)
            put("is_favorite", if (note.isFavorite) 1 else 0)
            put("created_at", note.createdAt)
            put("updated_at", note.updatedAt)
            put("deleted_at", note.deletedAt)
            put("device_id", note.deviceId)
            put("last_modified_device_id", note.lastModifiedDeviceId)
        }
        db.insertWithOnConflict(TABLE_NOTES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun updateNoteSync(note: SecureNote) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("title", note.title)
            put("encrypted_content", note.encryptedContent)
            put("content_nonce", note.contentNonce)
            put("category", note.category)
            put("is_favorite", if (note.isFavorite) 1 else 0)
            put("created_at", note.createdAt)
            put("updated_at", note.updatedAt)
            put("deleted_at", note.deletedAt)
            put("device_id", note.deviceId)
            put("last_modified_device_id", note.lastModifiedDeviceId)
        }
        db.update(TABLE_NOTES, values, "id = ?", arrayOf(note.id))
    }

    // ─── Attachments (Cofre de Mídias e Arquivos) ──────────

    fun createAttachment(
        id: String,
        entryId: String,
        filename: String,
        mimeType: String,
        fileSize: Long,
        encryptedKey: String,
        keyNonce: String,
        fileNonce: String
    ) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("id", id)
            put("entry_id", entryId)
            put("filename", filename)
            put("mime_type", mimeType)
            put("file_size", fileSize)
            put("encrypted_key", encryptedKey)
            put("key_nonce", keyNonce)
            put("file_nonce", fileNonce)
            put("device_id", null as String?)
            put("last_modified_device_id", null as String?)
        }
        db.insert(TABLE_ATTACHMENTS, null, values)
    }

    fun listAttachments(entryId: String): List<Attachment> {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT id, entry_id, filename, mime_type, file_size, encrypted_key, key_nonce, file_nonce, created_at, updated_at, deleted_at, device_id, last_modified_device_id FROM $TABLE_ATTACHMENTS WHERE entry_id = ? AND deleted_at IS NULL ORDER BY created_at DESC",
            arrayOf(entryId)
        )
        return cursor.use { parseAttachments(it) }
    }

    fun getAttachment(id: String): Attachment? {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT id, entry_id, filename, mime_type, file_size, encrypted_key, key_nonce, file_nonce, created_at, updated_at, deleted_at, device_id, last_modified_device_id FROM $TABLE_ATTACHMENTS WHERE id = ? AND deleted_at IS NULL",
            arrayOf(id)
        )
        return cursor.use { if (it.moveToFirst()) parseAttachment(it) else null }
    }

    fun deleteAttachment(id: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("deleted_at", getCurrentTimestamp())
            put("updated_at", getCurrentTimestamp())
        }
        db.update(TABLE_ATTACHMENTS, values, "id = ?", arrayOf(id))
    }

    fun listAllAttachmentsForSync(): List<Attachment> {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT id, entry_id, filename, mime_type, file_size, encrypted_key, key_nonce, file_nonce, created_at, updated_at, deleted_at, device_id, last_modified_device_id FROM $TABLE_ATTACHMENTS",
            null
        )
        return cursor.use { parseAttachments(it) }
    }

    fun getAttachmentIncludeDeleted(id: String): Attachment? {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT id, entry_id, filename, mime_type, file_size, encrypted_key, key_nonce, file_nonce, created_at, updated_at, deleted_at, device_id, last_modified_device_id FROM $TABLE_ATTACHMENTS WHERE id = ?",
            arrayOf(id)
        )
        return cursor.use { if (it.moveToFirst()) parseAttachment(it) else null }
    }

    fun insertAttachmentSync(att: Attachment) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("id", att.id)
            put("entry_id", att.entryId)
            put("filename", att.filename)
            put("mime_type", att.mimeType)
            put("file_size", att.fileSize)
            put("encrypted_key", att.encryptedKey)
            put("key_nonce", att.keyNonce)
            put("file_nonce", att.fileNonce)
            put("created_at", att.createdAt)
            put("updated_at", att.updatedAt)
            put("deleted_at", att.deletedAt)
            put("device_id", att.deviceId)
            put("last_modified_device_id", att.lastModifiedDeviceId)
        }
        db.insertWithOnConflict(TABLE_ATTACHMENTS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun updateAttachmentSync(att: Attachment) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("entry_id", att.entryId)
            put("filename", att.filename)
            put("mime_type", att.mimeType)
            put("file_size", att.fileSize)
            put("encrypted_key", att.encryptedKey)
            put("key_nonce", att.keyNonce)
            put("file_nonce", att.fileNonce)
            put("created_at", att.createdAt)
            put("updated_at", att.updatedAt)
            put("deleted_at", att.deletedAt)
            put("device_id", att.deviceId)
            put("last_modified_device_id", att.lastModifiedDeviceId)
        }
        db.update(TABLE_ATTACHMENTS, values, "id = ?", arrayOf(att.id))
    }

    private fun parseAttachment(c: android.database.Cursor) = Attachment(
        id = c.getString(0),
        entryId = c.getString(1),
        filename = c.getString(2),
        mimeType = c.getString(3),
        fileSize = c.getLong(4),
        encryptedKey = c.getString(5),
        keyNonce = c.getString(6),
        fileNonce = c.getString(7),
        createdAt = c.getString(8),
        updatedAt = c.getString(9),
        deletedAt = if (!c.isNull(10)) c.getString(10) else null,
        deviceId = if (c.columnCount > 11 && !c.isNull(11)) c.getString(11) else null,
        lastModifiedDeviceId = if (c.columnCount > 12 && !c.isNull(12)) c.getString(12) else null
    )

    private fun parseAttachments(c: android.database.Cursor): List<Attachment> {
        val list = mutableListOf<Attachment>()
        while (c.moveToNext()) {
            list.add(parseAttachment(c))
        }
        return list
    }

    // ─── Media Vault ──────────────────────────────────────────

    fun createMedia(item: MediaItem) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("id", item.id)
            put("filename", item.filename)
            put("mime_type", item.mimeType)
            put("file_size", item.fileSize)
            put("encrypted_key", item.encryptedKey)
            put("key_nonce", item.keyNonce)
            put("file_nonce", item.fileNonce)
            put("thumbnail_data", item.thumbnailData)
            put("thumbnail_nonce", item.thumbnailNonce)
            put("is_favorite", if (item.isFavorite) 1 else 0)
            put("created_at", item.createdAt)
            put("updated_at", item.updatedAt)
            put("deleted_at", item.deletedAt)
            put("device_id", item.deviceId)
            put("last_modified_device_id", item.lastModifiedDeviceId)
        }
        db.insert(TABLE_MEDIA_VAULT, null, values)
    }

    fun listMedia(): List<MediaItem> {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT id, filename, mime_type, file_size, encrypted_key, key_nonce, file_nonce, thumbnail_data, thumbnail_nonce, is_favorite, created_at, updated_at, deleted_at, device_id, last_modified_device_id FROM $TABLE_MEDIA_VAULT WHERE deleted_at IS NULL ORDER BY updated_at DESC",
            null
        )
        return cursor.use { parseMediaItems(it) }
    }

    fun getMedia(id: String): MediaItem? {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT id, filename, mime_type, file_size, encrypted_key, key_nonce, file_nonce, thumbnail_data, thumbnail_nonce, is_favorite, created_at, updated_at, deleted_at, device_id, last_modified_device_id FROM $TABLE_MEDIA_VAULT WHERE id = ? AND deleted_at IS NULL",
            arrayOf(id)
        )
        return cursor.use { if (it.moveToFirst()) parseMediaItem(it) else null }
    }

    fun deleteMedia(id: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("deleted_at", getCurrentTimestamp())
            put("updated_at", getCurrentTimestamp())
        }
        db.update(TABLE_MEDIA_VAULT, values, "id = ?", arrayOf(id))
    }

    fun listAllMediaForSync(): List<MediaItem> {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT id, filename, mime_type, file_size, encrypted_key, key_nonce, file_nonce, thumbnail_data, thumbnail_nonce, is_favorite, created_at, updated_at, deleted_at, device_id, last_modified_device_id FROM $TABLE_MEDIA_VAULT", null
        )
        return cursor.use { parseMediaItems(it) }
    }

    fun getMediaIncludeDeleted(id: String): MediaItem? {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT id, filename, mime_type, file_size, encrypted_key, key_nonce, file_nonce, thumbnail_data, thumbnail_nonce, is_favorite, created_at, updated_at, deleted_at, device_id, last_modified_device_id FROM $TABLE_MEDIA_VAULT WHERE id = ?",
            arrayOf(id)
        )
        return cursor.use { if (it.moveToFirst()) parseMediaItem(it) else null }
    }

    fun insertMediaSync(item: MediaItem) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("id", item.id)
            put("filename", item.filename)
            put("mime_type", item.mimeType)
            put("file_size", item.fileSize)
            put("encrypted_key", item.encryptedKey)
            put("key_nonce", item.keyNonce)
            put("file_nonce", item.fileNonce)
            put("thumbnail_data", item.thumbnailData)
            put("thumbnail_nonce", item.thumbnailNonce)
            put("is_favorite", if (item.isFavorite) 1 else 0)
            put("created_at", item.createdAt)
            put("updated_at", item.updatedAt)
            put("deleted_at", item.deletedAt)
            put("device_id", item.deviceId)
            put("last_modified_device_id", item.lastModifiedDeviceId)
        }
        db.insertWithOnConflict(TABLE_MEDIA_VAULT, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun updateMediaSync(item: MediaItem) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("filename", item.filename)
            put("mime_type", item.mimeType)
            put("file_size", item.fileSize)
            put("thumbnail_data", item.thumbnailData)
            put("thumbnail_nonce", item.thumbnailNonce)
            put("is_favorite", if (item.isFavorite) 1 else 0)
            put("updated_at", item.updatedAt)
            put("deleted_at", item.deletedAt)
            put("last_modified_device_id", item.lastModifiedDeviceId)
        }
        db.update(TABLE_MEDIA_VAULT, values, "id = ?", arrayOf(item.id))
    }

    private fun parseMediaItem(c: android.database.Cursor) = MediaItem(
        id = c.getString(0),
        filename = c.getString(1),
        mimeType = c.getString(2),
        fileSize = c.getLong(3),
        encryptedKey = c.getString(4),
        keyNonce = c.getString(5),
        fileNonce = c.getString(6),
        thumbnailData = if (c.isNull(7)) null else c.getString(7),
        thumbnailNonce = if (c.isNull(8)) null else c.getString(8),
        isFavorite = c.getInt(9) == 1,
        createdAt = c.getString(10),
        updatedAt = c.getString(11),
        deletedAt = if (c.isNull(12)) null else c.getString(12),
        deviceId = if (c.isNull(13)) null else c.getString(13),
        lastModifiedDeviceId = if (c.isNull(14)) null else c.getString(14)
    )

    private fun parseMediaItems(c: android.database.Cursor): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        while (c.moveToNext()) {
            list.add(parseMediaItem(c))
        }
        return list
    }

    private data class CategoryData(val name: String, val color: String, val icon: String, val sortOrder: Int)
}
