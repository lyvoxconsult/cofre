# QA

Required gates:

- core: `cargo test`;
- desktop: `npm run build`, `cargo check`, `cargo test`, `npm run tauri build`;
- android: `gradlew clean test assembleDebug`;
- emulator install and launch;
- logcat normal and crash buffer;
- physical device when connected.

Do not mark OK when only code exists.

Evidence must include commands, result, artifact path and log summary.

## Settings QA run - 2026-06-15

Automated:

- `core`: `cargo check` passed; `cargo test` passed, 7 tests.
- `apps/desktop/src-tauri`: `cargo check` passed; `cargo test` passed, 38 tests.
- `apps/desktop`: `npm run build` passed; `npm run tauri build` passed and generated NSIS installer.
- `apps/android`: `.\gradlew.bat test assembleDebug lintDebug` passed.

Android manual on `emulator-5554`:

- APK installed from `apps/android/app/build/outputs/apk/debug/app-debug.apk`.
- App launched with monkey.
- Created vault, opened Settings, tested auto-lock, clipboard, privacy mode, read-only on/off with master password, visual scroll through all Settings sections, biometric unavailable fallback and restart persistence by reading local config after force-stop.
- Logcat after final Settings exercise: no fatal exception, no ANR, no critical Lyvox error and no entered password string.

Desktop manual:

- Built executable launched from `apps/desktop/src-tauri/target/release/lyvox-vault.exe`.
- Settings opened with `Ctrl+5`; active sidebar and Settings grid visually checked.
- NSIS installer ran silently and installed `%LOCALAPPDATA%\Lyvox Vault Next\lyvox-vault.exe`; installed app opened and Settings screen was captured.

Artifacts:

- `artifacts/android_settings_final.png`;
- `artifacts/android_settings_bottom.png`;
- `artifacts/android_logcat_settings.txt`;
- `artifacts/android_logcat_settings_crash.txt`;
- `artifacts/desktop_settings_final.png`;
- `artifacts/desktop_installed_settings.png`.
