# Threat Model

## In scope

- Stolen `.vault` backup.
- Stolen `.vaultsync` package.
- Copied SQLite database.
- Device left unlocked briefly.
- Clipboard leakage.
- Media original still visible in Android gallery.

## Out of scope

- Compromised OS kernel.
- Active malware with screen/keylogging access.
- User choosing weak master password after warning.

## Required mitigations

- Authenticated encryption everywhere.
- Backup/sync reject tampering.
- Auto-lock and key zeroization.
- Android private storage plus `.nomedia`.
- Recovery without plaintext answer storage.
