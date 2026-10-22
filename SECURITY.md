# Security Policy

BrilliantList is designed with privacy and security as core principles. We use strong encryption for your data and media, and never collect or transmit your information unless you explicitly enable such features.

## Practices
- **Database Encryption:** All data is stored using SQLCipher.
- **Media Encryption:** Images and audio are encrypted with AES-GCM. A recording is written straight into the encrypted file, so it never exists as plaintext on the disk.
- **The microphone stops when you leave the app.** A recording is cut off when the app goes into the background, and it is limited to two minutes. A recording that was interrupted does not leave anything behind.
- **Key Management:** Encryption keys are managed by the Android Keystore and never leave the device. The key is versioned, so the specification can be changed without losing the entries.
- **App Lock:** The journal asks for your screen lock (or a fingerprint) before it opens, so the person holding your phone cannot read it. It can be turned off in the settings.
- **No Plaintext Sensitive Storage:** Sensitive data is never stored unencrypted. The photos lose their EXIF metadata - the location of the person and the device - on the way in.
- **No Network Calls by Default:** The app does not connect to the internet unless you enable future sync features. The build fails if the merged manifest declares the `INTERNET` permission.
- **Your Backup, Your Choice:** The export is encrypted with a passphrase you choose, so it can be restored on another device. The passphrase is never stored, it has to be typed twice and the app shows how strong it is.
- **The build is checked:** The release build is signed with a real key (never the public debug key) and the app is checked for network permissions and for native libraries that break on 16 KB page size devices.

## What is not stored
- No analytics, no crash reports, no advertising identifiers.
- No network permission at all - the app cannot send anything anywhere.

## Reporting Vulnerabilities
If you discover a security issue, please open an issue or contact the maintainer directly. We take all reports seriously and will respond promptly.

## Disclaimer
While we strive to follow best practices, no security guarantees are made. Use at your own risk.
