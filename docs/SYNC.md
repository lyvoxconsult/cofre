# Sync `.vaultsync`

Sync v1 is local-only and encrypted.

Transport options:

- File picker/SAF.
- USB file transfer.
- QR local pairing.

Merge rules:

- update/update: preserve both or field-level merge when safe;
- delete/update: preserve conflict for user review;
- tombstone respected;
- no silent destructive overwrite.

Timestamps:

- UTC RFC3339 only.
- No string comparison between mixed formats.

Legacy HTTP sync is reference only. New QR sync must use temporary port, one-use token, expiry and encrypted handshake.
# Sync

## Settings integration - 2026-06-15

- Android Settings exposes QR Code, Cabo USB and Arquivo `.vaultsync` cards.
- Desktop Settings exposes QR Code local server, USB informational card and `.vaultsync` export/import.
- QR sync remains local-first, cloudless and tokenized by the Desktop local server flow.
- `.vaultsync` export is allowed in read-only mode; `.vaultsync` import is blocked in read-only mode to avoid destructive merge.
- Corrupt package and wrong password paths are handled by the sync service.
