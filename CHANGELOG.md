# Changelog

The versions of the app. The version in `version.properties` is the one that
is built, and the tag of a release has to match it.

## 0.2.0 — not released yet

### The update path
- The version comes from `version.properties` and the release refuses a tag
  that does not match it.
- The CI starts the release build, so a broken shrinking fails the build
  instead of shipping.
- The update over an installed v0.1.0 is proven: the journal stays untouched.

### The app says what it is
- An "About" section with the version, the licence, the source and the
  fingerprint of the signing key.
- The credit to *Every Brilliant Thing* - it belongs where the people who use
  the app look, not only in the README.
- The number of entries and the size of the journal on the disk.

### The everyday list
- The entries are grouped by day.
- The search says how much it found.
- The recording shows how long is left before it stops.
- The trash is a separate place: the capture actions are switched off there.
- "Empty the trash".

### The backup
- The export is read back after it is written, so a broken export is reported
  instead of discovered later.
- The export says how many entries it wrote.

## 0.1.0

The first release.

- Write a note, add a photo, record a voice note - the recording is written
  straight into the encrypted file, so it never exists as plaintext.
- Search, favourites, a list that can be reordered, and a trash where a
  deleted entry can be restored.
- The crisis page: offline, region aware, with the right action for every
  helpline - a call, a text, or just information.
- The widget shows an entry, or nothing at all if you prefer.
- The journal can be exported as an encrypted file with a passphrase of your
  choice, and imported again.
- The app lock asks for the screen lock or a fingerprint before the journal
  opens.
- The database and the media are encrypted, the photos lose their location
  data, and the app has no internet permission at all.
