-- Migration 002: Add url field to vault_entries
-- URL is stored in plain text (not sensitive data)

ALTER TABLE vault_entries ADD COLUMN url TEXT NOT NULL DEFAULT '';
