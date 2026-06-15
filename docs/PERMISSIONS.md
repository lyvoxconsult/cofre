# Android Permissions

| Permission | Need | When request | Fallback | Risk |
|---|---|---|---|---|
| CAMERA | QR scan if GMS scanner requires camera | Only when QR sync opened | File sync | Camera privacy |
| INTERNET | Local LAN socket for QR sync | Only build/flavor with QR sync | File sync | Must not contact cloud |
| ACCESS_NETWORK_STATE | Detect LAN state | QR sync screen | Manual file sync | Low |
| ACCESS_WIFI_STATE | Local network diagnostics | QR sync screen | Manual file sync | Low |
| CHANGE_WIFI_MULTICAST_STATE | mDNS discovery | Only if mDNS enabled | QR endpoint fallback | Medium |
| USE_BIOMETRIC | Biometric unlock | User enables biometric | Master password | Device-bound auth |
| POST_NOTIFICATIONS | Long backup/sync progress | Only for long background operation | In-app progress | Low |

Avoid by default:

- MANAGE_EXTERNAL_STORAGE.
- Accessibility Service.
- broad media permissions when Photo Picker/SAF is enough.

FLAG_SECURE is required on sensitive screens.

## Settings permission behavior - 2026-06-15

- Settings screen itself requests no permission on open.
- Biometria uses `USE_BIOMETRIC` only when the user enables the option; emulator without available biometric capability is handled with an in-app unavailable state.
- QR sync routes to the sync flow; camera/network permissions are requested by that flow when scanner or LAN session starts.
- Backup, sync file and CSV import use picker/SAF flows at action time rather than broad storage permission at startup.
