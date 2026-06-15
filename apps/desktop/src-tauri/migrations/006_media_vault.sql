-- Migration 006: Media Vault
-- Creates a dedicated table for storing photos, videos, and other standalone media.

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
);

CREATE INDEX IF NOT EXISTS idx_media_vault_deleted_at ON media_vault(deleted_at);
