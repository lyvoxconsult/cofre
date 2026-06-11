-- Migration 003: Create secure_notes table
-- Content is encrypted; title and category are plain text (not sensitive)

CREATE TABLE IF NOT EXISTS secure_notes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL DEFAULT '',
    encrypted_content BLOB NOT NULL DEFAULT '',
    content_nonce BLOB NOT NULL DEFAULT '',
    category TEXT NOT NULL DEFAULT '',
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_notes_title ON secure_notes(title);
CREATE INDEX IF NOT EXISTS idx_notes_category ON secure_notes(category);
CREATE INDEX IF NOT EXISTS idx_notes_updated_at ON secure_notes(updated_at DESC);
