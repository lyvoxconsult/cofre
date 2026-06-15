# Android

Architecture:

- Kotlin.
- Compose + Material 3.
- Navigation Compose.
- ViewModel per feature.
- Repository + domain layer.
- SQLite local.
- Android Keystore + BiometricPrompt.
- SAF and Photo Picker.

Required screens:

- unlock/setup;
- vault;
- entry form/detail;
- notes;
- media;
- generator;
- settings;
- backup/restore;
- sync file/QR;
- recovery;
- audit;
- CSV import.

Validation:

- `adb devices`;
- install APK in emulator and physical device when present;
- launch with monkey;
- logcat normal and crash buffer;
- check no sensitive logs.

## Settings validation - 2026-06-15

Implemented and tested on `emulator-5554`:

- premium mobile-first Settings screen matching `configurações mobile.png`;
- header with Lyvox Vault logo, search/more actions, back button, title and subtitle;
- sections: Seguranca, Aparencia, Auditoria, Importacao de Credenciais, Backup, Sincronizacao Local, Recuperacao, Excluir Dados, Biometria Android, Sessao and Sobre;
- auto-lock options `1/5/15/30/Nunca`, clipboard options `15/30/60/120/Nunca`, theme chips, privacy mode and read-only switch;
- settings persisted in `config.json` and `lyvox_prefs.xml`; after restart the stored values stayed as `autoLockMinutes=15`, `clipboardClearSeconds=60`, `theme=dark`, `privacy_mode=true`, `read_only_mode=false`;
- read-only mode blocks mutation screens and requires master password to disable;
- biometric setting handles the tested emulator as unavailable and keeps master-password fallback.

Artifacts:

- `artifacts/android_settings_final.png`;
- `artifacts/android_settings_mid.png`;
- `artifacts/android_settings_bottom.png`;
- `artifacts/android_settings_end.xml`;
- `artifacts/android_logcat_settings.txt`;
- `artifacts/android_logcat_settings_crash.txt`.

Commands passed:

```powershell
.\gradlew.bat test assembleDebug lintDebug
adb -s emulator-5554 install -r app\build\outputs\apk\debug\app-debug.apk
adb -s emulator-5554 shell monkey -p com.lyvox.vault.next.debug -c android.intent.category.LAUNCHER 1
adb -s emulator-5554 logcat -d
adb -s emulator-5554 logcat -d -b crash
```
