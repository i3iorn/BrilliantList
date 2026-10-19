# Encryption & Media Pipeline

## Overview
This system ensures all user data (structured + media) is encrypted at rest using strong, modern cryptography.

---

## Key Management
- Android Keystore stores master AES key
- Key never leaves device
- No plaintext persistence

---

## Crypto Engine
AES-GCM used for:
- Confidentiality
- Integrity
- Authenticity

---

## Media Storage Strategy

Flow:
- InputStream → encrypt → file storage
- file storage → decrypt stream on demand

Files:
/ files/media/<uuid>.enc

Format:
- IV (prefix)
- Ciphertext

---

## Audio Handling
- Record to temporary file
- Encrypt after completion
- Delete raw file immediately

---

## Image Handling
- ContentResolver stream
- No bitmap full decode unless required
- Encrypt stream directly

---

## Database Encryption
- SQLCipher wraps Room database
- Key derived from master key via HKDF

---

## Secure Streaming
- CipherInputStream for reads
- CipherOutputStream for writes
- No full in-memory buffering

---

## Security Rules
- Never reuse IVs
- Never log plaintext
- Never persist raw media
- Always fail closed on decryption errors

---

## Future Sync Model
- End-to-end encryption
- Zero knowledge backend
- Device-derived keys only
