# Security

## Threat model

Primary attacker has local disk access, copied backup/sync files, or device access while locked.

## Controls

- Master password derives a KEK with Argon2id.
- Vault key is random and wrapped by the KEK.
- Field encryption uses AES-256-GCM with AAD.
- File encryption uses a random file key per attachment/media item.
- Android biometrics unwrap a device-bound protected key and do not replace the master password.
- Clipboard is cleared after configured timeout only if it still contains app-copied data.
- Privacy mode hides sensitive previews in UI surfaces that display logins, passwords or note previews.
- Read-only mode is persisted locally and blocks create/edit/delete/import/restore/sync-import mutations. Disabling read-only requires the active master password.
- Android destructive data deletion is blocked while read-only mode is enabled and otherwise requires password plus double confirmation.

## Prohibited

- Logging passwords, notes, sync payloads, recovery answers, vault keys, file keys.
- Storing service login/password/notes/media filenames in plaintext.
- Using Last Write Wins for destructive conflicts without user-visible conflict record.

## Settings security checks - 2026-06-15

- Android logcat main and crash buffers were read after Settings validation; no `FATAL EXCEPTION`, ANR or sensitive password string was found.
- Desktop build and installed app opened without critical runtime error during Settings validation.
- Biometria Android uses Keystore/BiometricPrompt path and stores no master password; tested emulator exposed the unavailable-device fallback.
