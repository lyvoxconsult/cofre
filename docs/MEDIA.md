# Media Vault

Rules:

- Import via Photo Picker/SAF.
- Copy into app-private storage.
- Encrypt with random file key.
- Wrap file key with vault key.
- Store `.nomedia` on Android.
- Thumbnail is encrypted.
- Original deletion is explicit and OS-mediated.
- Large files use streaming/chunks.

Desktop:

- media sidebar;
- grid;
- filters;
- viewer;
- export with warning.

Android:

- bottom navigation;
- grid;
- filters;
- picker cancel path;
- no UI thread blocking.
