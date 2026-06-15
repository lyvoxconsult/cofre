# Release

Desktop:

- MSI and NSIS generated from clean build.
- Version aligned in package, Cargo and Tauri config.

Android:

- Debug APK for QA.
- Release APK/AAB must use real keystore, not debug signing.
- R8 enabled and tested.

No release if:

- tests fail;
- logcat has app crash;
- backup/sync compatibility is not proven;
- sensitive plaintext appears in DB/logs.
# Release

## Settings validation build - 2026-06-15

- Android debug APK: `apps/android/app/build/outputs/apk/debug/app-debug.apk`.
- Desktop executable: `apps/desktop/src-tauri/target/release/lyvox-vault.exe`.
- Desktop installer: `apps/desktop/src-tauri/target/release/bundle/nsis/Lyvox Vault Next_1.0.3_x64-setup.exe`.
- Installed Desktop executable tested at `%LOCALAPPDATA%\Lyvox Vault Next\lyvox-vault.exe`.
