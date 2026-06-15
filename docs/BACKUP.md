# Backup `.vault`

Backup v1 is a complete encrypted archive.

Must include:

- vault metadata;
- devices;
- categories;
- tags;
- entries;
- entry secrets;
- notes;
- password history;
- attachments;
- media;
- thumbnails;
- tombstones;
- checksums.

Import flow:

1. Validate magic/version.
2. Derive backup key.
3. Decrypt manifest.
4. Validate chunk checksums.
5. Show preview.
6. Dry-run merge/replace.
7. Create automatic pre-restore backup.
8. Apply transactionally.

Legacy backup is not acceptable because it excludes media and attachments.
# Backup

## Settings integration - 2026-06-15

- Android Settings exposes `Backup .vault` and routes to the real backup screen.
- Desktop Settings exposes export/import controls directly in the settings grid.
- Read-only mode permits export and blocks restore/import.
- Restore requires the backup password and rejects invalid/corrupt payloads through the backup service.
- Destructive operations keep the warning UI visible and require explicit confirmation.
