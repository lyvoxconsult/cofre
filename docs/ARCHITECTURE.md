# Architecture

Lyvox Vault Next is a local-first password, note and media vault.

## Non-negotiable boundaries

- No cloud database.
- No server-side account.
- No telemetry.
- No sensitive data in logs.
- No permanent plaintext for secrets, notes, media, attachments, recovery answers or keys.

## Platform shape

- Desktop owns UX through Tauri + Svelte. Rust owns crypto, storage, backup, sync and file I/O.
- Android owns UX through Kotlin + Compose. Kotlin implements the same specs and vectors.
- Shared truth lives in `specs/` and `docs/`, not in copied platform code.

## Current legacy decision

The legacy project remains read-only reference. The new base must not import legacy migrations, recovery, HTTP sync or backup implementation without re-audit.
