# QR Sync

Desktop starts a temporary local pairing session.

QR payload:

- protocol;
- version;
- vault_id;
- endpoint;
- temporary port;
- session token;
- public key;
- expires_at_utc;
- nonce;
- device name.

Security:

- token is one-use;
- session expires quickly;
- endpoint stops after sync;
- no fixed port;
- no payload data in logs;
- reject invalid vault/session/device.

Android:

- prefer GMS Barcode Scanner;
- handle no Play Services;
- handle camera denial;
- handle cancel;
- no crash on invalid QR.
# QR Sync

## Settings integration - 2026-06-15

- Desktop Settings starts/stops the temporary LAN sync server and renders the QR payload in the app.
- Android Settings routes QR Code card to the sync flow.
- The flow is local network only and does not use cloud services.
- Read-only mode blocks starting/importing destructive sync flows where the device would merge incoming data.
