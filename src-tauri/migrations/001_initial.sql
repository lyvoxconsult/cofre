-- Migration 001: Initial schema
-- Creates the vault_entries and categories tables

CREATE TABLE IF NOT EXISTS categories (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,
    color TEXT NOT NULL DEFAULT '#6366f1',
    icon TEXT NOT NULL DEFAULT 'folder',
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

INSERT OR IGNORE INTO categories (id, name, color, icon, sort_order) VALUES 
    (1, 'Pessoal', '#6366f1', 'user', 0),
    (2, 'Trabalho', '#f59e0b', 'briefcase', 1),
    (3, 'Bancos', '#10b981', 'landmark', 2),
    (4, 'Outros', '#6b7280', 'folder', 3);

CREATE TABLE IF NOT EXISTS vault_entries (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    service_name TEXT NOT NULL,
    login TEXT NOT NULL,
    encrypted_password BLOB NOT NULL,
    password_nonce BLOB NOT NULL,
    encrypted_notes BLOB NOT NULL DEFAULT '',
    notes_nonce BLOB DEFAULT NULL,
    category_id INTEGER DEFAULT NULL REFERENCES categories(id),
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_entries_service_name ON vault_entries(service_name);
CREATE INDEX IF NOT EXISTS idx_entries_category_id ON vault_entries(category_id);
CREATE INDEX IF NOT EXISTS idx_entries_updated_at ON vault_entries(updated_at DESC);
