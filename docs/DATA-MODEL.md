# Data Model

Source of truth: `specs/schema/v1.sql`.

Rules:

- IDs are UUID strings.
- Timestamps are UTC RFC3339 only.
- Deletes are tombstones.
- `schema_migrations` is mandatory.
- Sensitive user fields are encrypted.
- Blind indexes may support local search without plaintext.

Core entities:

- `vault_meta`
- `devices`
- `categories`
- `tags`
- `entries`
- `entry_secrets`
- `secure_notes`
- `password_history`
- `attachments`
- `media_items`
- `sync_tombstones`
- `audit_cache`
