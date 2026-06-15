# Android App Plan

This folder is reserved for the Kotlin/Compose implementation after core/specs are frozen.

Mandatory implementation rules:

- package: `com.lyvox.vault.next`;
- no `usesCleartextTraffic=true` in release;
- permissions requested just-in-time;
- GMS Barcode Scanner for QR first;
- SAF/Photo Picker for files and media;
- `FLAG_SECURE` on sensitive screens;
- ViewModel + repository + domain layers;
- tests for crypto vectors, migrations, backup and sync.
