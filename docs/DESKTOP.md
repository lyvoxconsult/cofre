# Desktop

Architecture:

- Tauri 2.
- Rust for crypto, database, backup, sync and sensitive files.
- Svelte UI.
- Minimal Tauri command surface.
- SQLite local.

UI:

- mac-style window chrome;
- left sidebar;
- right panel forms;
- premium dark/glass style;
- media grid;
- settings cards.

## Settings validation - 2026-06-15

Implemented and tested:

- Settings desktop follows `configuraçoes desktop.png` with mac-style shell, active sidebar item, title/subtitle/search, card grid, dark glass cards and purple/blue accents;
- sections: Seguranca, Aparencia, Auditoria de Seguranca, Importacao de Credenciais, Backup, Sincronizacao Local, Recuperacao de Senha, Zona de Perigo, Biometria Android and Sobre;
- auto-lock, clipboard, theme, privacy and read-only mode persist in `%APPDATA%\lyvox-vault-next\config.json`;
- read-only mode blocks create/edit/delete/import/restore/sync import and requires master password through `verify_master_password` before disabling;
- Desktop biometria is explicitly informational as Android-only;
- backup `.vault`, sync `.vaultsync`, QR sync server, CSV import, audit and dangerous delete are connected to real commands/screens.

Artifacts:

- `artifacts/desktop_settings_final.png`;
- `artifacts/desktop_installed_settings.png`;
- installer `apps/desktop/src-tauri/target/release/bundle/nsis/Lyvox Vault Next_1.0.3_x64-setup.exe`;
- installed executable `%LOCALAPPDATA%\Lyvox Vault Next\lyvox-vault.exe`.

Commands passed:

```powershell
npm run build
npm run tauri build
cargo check
cargo test
```

Hardening:

- restrict capabilities;
- avoid arbitrary path reads/writes;
- no updater in MVP;
- CSP tightened before release.
