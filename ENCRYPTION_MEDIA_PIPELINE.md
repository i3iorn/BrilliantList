# Encryption & Media Pipeline

## Overview
This system ensures all user data (structured + media) is encrypted at rest using strong, modern cryptography.

---

## Key Management
- Android Keystore stores the master AES key
- Key never leaves device
- No plaintext persistence

The key is **versioned through its alias**: `brilliantlist_master_key` is version 1,
`brilliantlist_master_key_v2` is version 2. The version of the key that protects
the passphrase is stored next to the passphrase. When the key specification
changes, the passphrase is re-wrapped with a key of the new version and the old
key is deleted - the database stays readable. The new passphrase is written
before the old key is deleted, so an interrupted rotation can never lose data.

The passphrase of the database is **32 random bytes**, it is *not* derived from
the master key. It is encrypted with the master key and only the ciphertext is
persisted (base64 of IV + ciphertext in the shared preferences `db_keys`).

---

## Crypto Engine
AES-GCM used for:
- Confidentiality
- Integrity
- Authenticity

Format: 12 byte IV prefix, then the ciphertext, 128 bit GCM tag. Every
encryption uses a new random IV.

---

## Media Storage Strategy
Flow:
- InputStream → encrypt → file storage
- file storage → decrypt stream on demand

Files:
/ files/media/<uuid>.enc            (images and the old M4A recordings)
/ files/media/<uuid>.pcm.enc        (the raw PCM recordings)

The suffix marks the format of the media, so the two kinds of recording can be
told apart.

---

## Audio Handling
- The microphone is read with `AudioRecord`, the samples are written **straight
  into the encrypted media file** through an encrypting stream
- There is no temp file, so the recording never exists as plaintext on the disk
- The format is raw PCM (44.1 kHz, mono, 16 bit); a WAV header is added when
  the clip is played
- The recordings of the first versions were M4A files written by `MediaRecorder`
  into the cache. They are still playable, and the leftovers of an interrupted
  recording are swept on app start

---

## Image Handling
- ContentResolver stream
- No bitmap full decode unless required
- Encrypt stream directly
- The EXIF metadata is removed on the way in: a photo carries the location of
  the person and the device, and that has no place in a journal. The metadata
  is removed from the container (the JPEG APP1 segment, the PNG eXIf chunk), the
  pixels are untouched

---

## Database Encryption
- SQLCipher wraps Room database
- The passphrase is random, it is stored encrypted with the master key

---

## Secure Streaming
- CipherInputStream for reads
- CipherOutputStream for writes
- Media is streamed, the plaintext only exists while it is decoded
- The audio playback loads the clip into memory, because MediaPlayer needs
  random access

---

## Backup
The export is encrypted with a passphrase the user chooses, not with the device
key: the file has to be readable on another device, and the user has to get
their entries back when this device is gone.

- PBKDF2-HMAC-SHA256 with 600 000 iterations and a random salt derives the
  key from the passphrase (the work factor recommended by the OWASP password
  storage cheat sheet)
- The work factor is stored in the header, so it can be raised later without
  invalidating the exports that exist
- The file holds the entries and their media, encrypted with AES-GCM
- The container is streamed: an export or an import never needs the whole
  journal in memory

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
