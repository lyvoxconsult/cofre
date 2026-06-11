package com.lyvox.vault.data.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.lyvox.vault.data.model.*

/**
 * SQLite database helper — schema matches the desktop Rust implementation.
 *
 * Tables:
 * - categories (pre-populated)
 * - vault_entries
 * - secure_notes
 */
class DatabaseHelper(context: Context) : SQLiteOpenHelper(
    context, DATABASE_NAME, null, DATABASE_VERSION
) {

    companion object {
        private const val DATABASE_NAME = "vault.db"
        private const val DATABASE_VERSION = 4

        private const val TABLE_CATEGORIES = "categories"
        private const val TABLE_ENTRIES = "vault_entries"
        private const val TABLE_NOTES = "secure_notes"
        private const val TABLE_TAGS = "tags"
        private const val TABLE_ENTRY_TAGS = "entry_tags"
        private const val TABLE_PASSWORD_HISTORY = "password_history"
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Categories table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_CATEGORIES (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                color TEXT NOT NULL DEFAULT '#6366f1',
                icon TEXT NOT NULL DEFAULT 'folder',
                sort_order INTEGER NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL DEFAULT (datetime('now'))
            )
        """)

        // Vault entries table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_ENTRIES (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                service_name TEXT NOT NULL,
                login TEXT NOT NULL,
                encrypted_password BLOB NOT NULL,
                password_nonce BLOB NOT NULL,
                encrypted_notes BLOB NOT NULL DEFAULT '',
                notes_nonce BLOB DEFAULT NULL,
                url TEXT NOT NULL DEFAULT '',
                category_id INTEGER DEFAULT NULL REFERENCES categories(id),
                is_favorite INTEGER NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL DEFAULT (datetime('now')),
                updated_at TEXT NOT NULL DEFAULT (datetime('now'))
            )
        """)

        // Secure notes table
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

        // Indexes
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_entries_service ON $TABLE_ENTRIES(service_name)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_entries_category ON $TABLE_ENTRIES(category_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_entries_updated ON $TABLE_ENTRIES(updated_at DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_notes_title ON $TABLE_NOTES(title)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_notes_category ON $TABLE_NOTES(category)")

        // Track C tables
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

        // Pre-populate default categories
        seedCategories(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
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
                    id = it.getLong(0),
                    name = it.getString(1),
                    color = it.getString(2),
                    icon = it.getString(3),
                    sortOrder = it.getInt(4)
                ))
            }
        }
        return categories
    }

    fun getCategoryName(id: Long): String? {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT name FROM $TABLE_CATEGORIES WHERE id = ?",
            arrayOf(id.toString())
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
        url: String, categoryId: Long?, isFavorite: Boolean = false
    ): Long {
        val db = writableDatabase
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
        }
        return db.insert(TABLE_ENTRIES, null, values)
    }

    fun listEntries(): List<VaultEntry> {
        val db = readableDatabase
        val cursor = db.rawQuery(
            """SELECT id, service_name, login, encrypted_password, password_nonce,
                     encrypted_notes, notes_nonce, url, category_id, created_at, updated_at, is_favorite
              FROM $TABLE_ENTRIES ORDER BY updated_at DESC""", null
        )
        return cursor.use { parseEntries(it) }
    }

    fun searchEntries(query: String): List<VaultEntry> {
        val pattern = "%$query%"
        val db = readableDatabase
        val cursor = db.rawQuery(
            """SELECT id, service_name, login, encrypted_password, password_nonce,
                     encrypted_notes, notes_nonce, url, category_id, created_at, updated_at, is_favorite
              FROM $TABLE_ENTRIES
              WHERE service_name LIKE ? OR login LIKE ? OR url LIKE ?
              ORDER BY updated_at DESC""",
            arrayOf(pattern, pattern, pattern)
        )
        return cursor.use { parseEntries(it) }
    }

    fun getEntry(id: Long): VaultEntry? {
        val db = readableDatabase
        val cursor = db.rawQuery(
            """SELECT id, service_name, login, encrypted_password, password_nonce,
                     encrypted_notes, notes_nonce, url, category_id, created_at, updated_at, is_favorite
              FROM $TABLE_ENTRIES WHERE id = ?""",
            arrayOf(id.toString())
        )
        return cursor.use { if (it.moveToFirst()) parseEntry(it) else null }
    }

    fun updateEntry(
        id: Long, serviceName: String, login: String,
        encryptedPassword: String, passwordNonce: String,
        encryptedNotes: String, notesNonce: String?,
        url: String, categoryId: Long?, isFavorite: Boolean
    ) {
        val db = writableDatabase
        
        // 1. Check if the password has changed to save the old one in history
        val oldCursor = db.rawQuery("SELECT encrypted_password, password_nonce FROM $TABLE_ENTRIES WHERE id = ?", arrayOf(id.toString()))
        if (oldCursor.moveToFirst()) {
            val oldEncPwd = oldCursor.getString(0)
            val oldNonce = oldCursor.getString(1)
            // If the old password exists and is different from the new one
            if (oldEncPwd.isNotEmpty() && oldEncPwd != encryptedPassword) {
                val histValues = ContentValues().apply {
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
        db.update(TABLE_ENTRIES, values, "id = ?", arrayOf(id.toString()))
    }

    fun deleteEntry(id: Long) {
        writableDatabase.delete(TABLE_ENTRIES, "id = ?", arrayOf(id.toString()))
    }

    fun rawUpdateEntry(id: Long, encryptedPassword: String, passwordNonce: String,
                       encryptedNotes: String, notesNonce: String?) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("encrypted_password", encryptedPassword)
            put("password_nonce", passwordNonce)
            put("encrypted_notes", encryptedNotes)
            put("notes_nonce", notesNonce)
            put("updated_at", getCurrentTimestamp())
        }
        db.update(TABLE_ENTRIES, values, "id = ?", arrayOf(id.toString()))
    }

    // ─── Secure Notes ──────────────────────────────────────

    fun createNote(title: String, encryptedContent: String, contentNonce: String, category: String): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("title", title)
            put("encrypted_content", encryptedContent)
            put("content_nonce", contentNonce)
            put("category", category)
        }
        return db.insert(TABLE_NOTES, null, values)
    }

    fun listNotes(): List<SecureNote> {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT id, title, encrypted_content, content_nonce, category, created_at, updated_at FROM $TABLE_NOTES ORDER BY updated_at DESC",
            null
        )
        return cursor.use { parseNotes(it) }
    }

    fun searchNotes(query: String): List<SecureNote> {
        val pattern = "%$query%"
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT id, title, encrypted_content, content_nonce, category, created_at, updated_at FROM $TABLE_NOTES WHERE title LIKE ? OR category LIKE ? ORDER BY updated_at DESC",
            arrayOf(pattern, pattern)
        )
        return cursor.use { parseNotes(it) }
    }

    fun getNote(id: Long): SecureNote? {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT id, title, encrypted_content, content_nonce, category, created_at, updated_at FROM $TABLE_NOTES WHERE id = ?",
            arrayOf(id.toString())
        )
        return cursor.use { if (it.moveToFirst()) parseNote(it) else null }
    }

    fun updateNote(id: Long, title: String, encryptedContent: String, contentNonce: String, category: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("title", title)
            put("encrypted_content", encryptedContent)
            put("content_nonce", contentNonce)
            put("category", category)
            put("updated_at", getCurrentTimestamp())
        }
        db.update(TABLE_NOTES, values, "id = ?", arrayOf(id.toString()))
    }

    fun deleteNote(id: Long) {
        writableDatabase.delete(TABLE_NOTES, "id = ?", arrayOf(id.toString()))
    }

    fun rawUpdateNote(id: Long, encryptedContent: String, contentNonce: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("encrypted_content", encryptedContent)
            put("content_nonce", contentNonce)
            put("updated_at", getCurrentTimestamp())
        }
        db.update(TABLE_NOTES, values, "id = ?", arrayOf(id.toString()))
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
        id = c.getLong(0),
        serviceName = c.getString(1),
        login = c.getString(2),
        encryptedPassword = c.getString(3),
        passwordNonce = c.getString(4),
        encryptedNotes = c.getString(5),
        notesNonce = c.getString(6),
        url = c.getString(7),
        categoryId = if (c.isNull(8)) null else c.getLong(8),
        createdAt = c.getString(9),
        updatedAt = c.getString(10),
        isFavorite = if (c.columnCount > 11 && !c.isNull(11)) c.getInt(11) == 1 else false
    )

    private fun parseEntries(c: android.database.Cursor): List<VaultEntry> {
        val list = mutableListOf<VaultEntry>()
        while (c.moveToNext()) {
            list.add(parseEntry(c))
        }
        return list
    }

    private fun parseNote(c: android.database.Cursor) = SecureNote(
        id = c.getLong(0),
        title = c.getString(1),
        encryptedContent = c.getString(2),
        contentNonce = c.getString(3),
        category = c.getString(4),
        createdAt = c.getString(5),
        updatedAt = c.getString(6)
    )

    private fun parseNotes(c: android.database.Cursor): List<SecureNote> {
        val list = mutableListOf<SecureNote>()
        while (c.moveToNext()) {
            list.add(parseNote(c))
        }
        return list
    }

    private data class CategoryData(val name: String, val color: String, val icon: String, val sortOrder: Int)
}
