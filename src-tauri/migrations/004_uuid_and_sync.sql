-- Migration 004: UUIDs and Sync metadata
-- Converts existing tables to use TEXT IDs (UUIDs) and adds fields for local-first sync and organization.

PRAGMA foreign_keys=OFF;

-- 1. CATEGORIES
DROP TABLE IF EXISTS new_categories;
CREATE TABLE new_categories (
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
);

INSERT INTO new_categories (id, name, color, icon, sort_order, created_at, updated_at)
SELECT cast(id as TEXT), name, color, icon, sort_order, created_at, created_at FROM categories;

DROP TABLE categories;
ALTER TABLE new_categories RENAME TO categories;

-- 2. VAULT ENTRIES
DROP TABLE IF EXISTS new_vault_entries;
CREATE TABLE new_vault_entries (
    id TEXT PRIMARY KEY,
    service_name TEXT NOT NULL,
    login TEXT NOT NULL,
    encrypted_password TEXT NOT NULL,
    password_nonce TEXT NOT NULL,
    encrypted_notes TEXT NOT NULL DEFAULT '',
    notes_nonce TEXT DEFAULT NULL,
    url TEXT NOT NULL DEFAULT '',
    category_id TEXT DEFAULT NULL REFERENCES categories(id) ON DELETE SET NULL,
    is_favorite INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
    deleted_at TEXT DEFAULT NULL,
    device_id TEXT DEFAULT NULL,
    last_modified_device_id TEXT DEFAULT NULL
);

INSERT INTO new_vault_entries (id, service_name, login, encrypted_password, password_nonce, encrypted_notes, notes_nonce, url, category_id, created_at, updated_at)
SELECT cast(id as TEXT), service_name, login, encrypted_password, password_nonce, encrypted_notes, notes_nonce, url, cast(category_id as TEXT), created_at, updated_at FROM vault_entries;

DROP TABLE vault_entries;
ALTER TABLE new_vault_entries RENAME TO vault_entries;

CREATE INDEX idx_entries_service_name ON vault_entries(service_name);
CREATE INDEX idx_entries_category_id ON vault_entries(category_id);
CREATE INDEX idx_entries_updated_at ON vault_entries(updated_at DESC);

-- 3. SECURE NOTES
DROP TABLE IF EXISTS new_secure_notes;
CREATE TABLE new_secure_notes (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    encrypted_content TEXT NOT NULL,
    content_nonce TEXT NOT NULL,
    category TEXT NOT NULL,
    is_favorite INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
    deleted_at TEXT DEFAULT NULL,
    device_id TEXT DEFAULT NULL,
    last_modified_device_id TEXT DEFAULT NULL
);

INSERT INTO new_secure_notes (id, title, encrypted_content, content_nonce, category, created_at, updated_at)
SELECT cast(id as TEXT), title, encrypted_content, content_nonce, category, created_at, updated_at FROM secure_notes;

DROP TABLE secure_notes;
ALTER TABLE new_secure_notes RENAME TO secure_notes;

-- 4. TAGS
CREATE TABLE IF NOT EXISTS tags (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    color TEXT NOT NULL DEFAULT '#6366f1',
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
    deleted_at TEXT DEFAULT NULL,
    device_id TEXT DEFAULT NULL,
    last_modified_device_id TEXT DEFAULT NULL
);

CREATE TABLE IF NOT EXISTS entry_tags (
    entry_id TEXT NOT NULL REFERENCES vault_entries(id) ON DELETE CASCADE,
    tag_id TEXT NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    PRIMARY KEY (entry_id, tag_id)
);

-- 5. PASSWORD HISTORY
CREATE TABLE IF NOT EXISTS password_history (
    id TEXT PRIMARY KEY,
    entry_id TEXT NOT NULL REFERENCES vault_entries(id) ON DELETE CASCADE,
    encrypted_password TEXT NOT NULL,
    password_nonce TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    device_id TEXT DEFAULT NULL
);
