CREATE TABLE IF NOT EXISTS schema_migrations (
  version INTEGER PRIMARY KEY,
  checksum_sha256 TEXT NOT NULL,
  applied_at_utc TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS vault_meta (
  vault_id TEXT PRIMARY KEY,
  schema_version INTEGER NOT NULL,
  created_at_utc TEXT NOT NULL,
  updated_at_utc TEXT NOT NULL,
  device_id TEXT NOT NULL,
  app_version TEXT NOT NULL,
  kdf_params_version INTEGER NOT NULL,
  crypto_version INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS devices (
  device_id TEXT PRIMARY KEY,
  device_name TEXT NOT NULL,
  platform TEXT NOT NULL CHECK(platform IN ('desktop','android')),
  public_key TEXT,
  trusted INTEGER NOT NULL DEFAULT 0,
  created_at_utc TEXT NOT NULL,
  last_seen_at_utc TEXT
);

CREATE TABLE IF NOT EXISTS categories (
  id TEXT PRIMARY KEY,
  encrypted_name TEXT NOT NULL,
  name_blind_index TEXT,
  color TEXT NOT NULL,
  icon TEXT NOT NULL,
  sort_order INTEGER NOT NULL DEFAULT 0,
  created_at_utc TEXT NOT NULL,
  updated_at_utc TEXT NOT NULL,
  deleted_at_utc TEXT,
  created_by_device_id TEXT NOT NULL,
  last_modified_device_id TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS tags (
  id TEXT PRIMARY KEY,
  encrypted_name TEXT NOT NULL,
  name_blind_index TEXT,
  color TEXT NOT NULL,
  created_at_utc TEXT NOT NULL,
  updated_at_utc TEXT NOT NULL,
  deleted_at_utc TEXT,
  created_by_device_id TEXT NOT NULL,
  last_modified_device_id TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS entries (
  id TEXT PRIMARY KEY,
  encrypted_service_name TEXT NOT NULL,
  service_name_blind_index TEXT,
  category_id TEXT REFERENCES categories(id) ON DELETE SET NULL,
  is_favorite INTEGER NOT NULL DEFAULT 0,
  reminder_at_utc TEXT,
  created_at_utc TEXT NOT NULL,
  updated_at_utc TEXT NOT NULL,
  deleted_at_utc TEXT,
  created_by_device_id TEXT NOT NULL,
  last_modified_device_id TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS entry_secrets (
  entry_id TEXT PRIMARY KEY REFERENCES entries(id) ON DELETE CASCADE,
  encrypted_login TEXT NOT NULL,
  login_blind_index TEXT,
  encrypted_password TEXT NOT NULL,
  encrypted_url TEXT,
  encrypted_notes TEXT,
  encrypted_custom_fields TEXT,
  updated_at_utc TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS entry_tags (
  entry_id TEXT NOT NULL REFERENCES entries(id) ON DELETE CASCADE,
  tag_id TEXT NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
  created_at_utc TEXT NOT NULL,
  PRIMARY KEY(entry_id, tag_id)
);

CREATE TABLE IF NOT EXISTS secure_notes (
  id TEXT PRIMARY KEY,
  encrypted_title TEXT NOT NULL,
  title_blind_index TEXT,
  encrypted_content TEXT NOT NULL,
  category_id TEXT REFERENCES categories(id) ON DELETE SET NULL,
  is_favorite INTEGER NOT NULL DEFAULT 0,
  created_at_utc TEXT NOT NULL,
  updated_at_utc TEXT NOT NULL,
  deleted_at_utc TEXT,
  created_by_device_id TEXT NOT NULL,
  last_modified_device_id TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS password_history (
  id TEXT PRIMARY KEY,
  entry_id TEXT NOT NULL REFERENCES entries(id) ON DELETE CASCADE,
  encrypted_password TEXT NOT NULL,
  created_at_utc TEXT NOT NULL,
  created_by_device_id TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS attachments (
  id TEXT PRIMARY KEY,
  owner_type TEXT NOT NULL CHECK(owner_type IN ('entry','note')),
  owner_id TEXT NOT NULL,
  encrypted_filename TEXT NOT NULL,
  mime_type TEXT NOT NULL,
  file_size INTEGER NOT NULL,
  sha256_ciphertext TEXT NOT NULL,
  encrypted_file_key TEXT NOT NULL,
  file_nonce TEXT NOT NULL,
  internal_path TEXT NOT NULL,
  created_at_utc TEXT NOT NULL,
  updated_at_utc TEXT NOT NULL,
  deleted_at_utc TEXT,
  created_by_device_id TEXT NOT NULL,
  last_modified_device_id TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS media_items (
  id TEXT PRIMARY KEY,
  media_type TEXT NOT NULL CHECK(media_type IN ('photo','video')),
  encrypted_filename TEXT NOT NULL,
  mime_type TEXT NOT NULL,
  file_size INTEGER NOT NULL,
  duration_ms INTEGER,
  encrypted_thumbnail TEXT,
  encrypted_file_key TEXT NOT NULL,
  file_nonce TEXT NOT NULL,
  internal_path TEXT NOT NULL,
  album_blind_index TEXT,
  created_at_utc TEXT NOT NULL,
  updated_at_utc TEXT NOT NULL,
  deleted_at_utc TEXT,
  created_by_device_id TEXT NOT NULL,
  last_modified_device_id TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS sync_tombstones (
  entity_type TEXT NOT NULL,
  entity_id TEXT NOT NULL,
  deleted_at_utc TEXT NOT NULL,
  deleted_by_device_id TEXT NOT NULL,
  reason TEXT,
  PRIMARY KEY(entity_type, entity_id)
);

CREATE TABLE IF NOT EXISTS audit_cache (
  id TEXT PRIMARY KEY,
  kind TEXT NOT NULL,
  entity_id TEXT NOT NULL,
  result_json TEXT NOT NULL,
  calculated_at_utc TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_entries_service_blind ON entries(service_name_blind_index);
CREATE INDEX IF NOT EXISTS idx_entry_secrets_login_blind ON entry_secrets(login_blind_index);
CREATE INDEX IF NOT EXISTS idx_notes_title_blind ON secure_notes(title_blind_index);
CREATE INDEX IF NOT EXISTS idx_media_type ON media_items(media_type);
CREATE INDEX IF NOT EXISTS idx_tombstones_deleted ON sync_tombstones(deleted_at_utc);
