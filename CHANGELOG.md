# Changelog

The versions of the app. The version in `version.properties` is the one that
is built, and the tag of a release has to match it.

## 0.3.0 — not released yet

### Security and privacy
- The Keystore master key is now bound to the user: a second key that requires
  biometric or device credential authentication wraps the master key. The
  database cannot be opened before the user unlocks it. The key is kept in
  memory for the session.
- HEIC/HEIF photos are stripped of their EXIF metadata on ingestion, just like
  JPEG and PNG. The safe, minimal parser only drops the Exif box and never
  corrupts the image.

### Capture: links and clipboard
- A new LINK entry type: add a web address with a small dialog and inline
  validation. Only http and https are accepted. The card shows the address and
  opens the browser when tapped.
- The toolbar has been reorganised: the capture actions (photo, microphone,
  link, paste) stay in the toolbar, while the view actions (favourites, search,
  trash) move to the top bar.
- Paste from the clipboard: a picture becomes a photo entry (with metadata
  removed), an address becomes a link entry, and anything else becomes a text
  entry. The clipboard is read only when the button is tapped. An empty
  clipboard shows a message.
- The entry type mapper is forgiving: an unknown type from an older or newer
  build is shown as a text entry instead of crashing the app.

### The crisis page knows more of the world
- Sweden (SE) has been added to the crisis catalog with verified helplines
  (Mind Självmordslinjen 90101, BRIS 116 111, Jourhavande medmänniska
  08-702 16 80, 1177 Vårdguiden 1177). The emergency number 112 is included in
  the hand-written table. The region picker shows "Sverige" with its flag.
- The helpline records carry the name in the local language (`localName`), the
  opening hours, the languages the line answers in, and the date the number was
  last checked.
- The crisis page now handles Swedish and English. The Swedish copy is careful
  and human-sounding.

### More languages
- The interface is now translated into Swedish. All user-facing strings,
  including accessibility content descriptions, are available in
  `values-sv/strings.xml` for the `app`, `feature_entry`, `feature_widget`,
  `feature_crisis`, `feature_settings`, and `core_ui` modules.

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
- The repository has an Apache License 2.0 - it is public now, and without a
  licence nobody may use the code.

### The everyday list
- The entries are grouped by day, and the grouping can be switched off.
- The search says how much it found.
- The search lets go of the keyboard, so it stops covering the list.
- The recording shows the time that is left before it stops.

### The trash
- The trash can be emptied. It asks first, because the entries and their
  photos and recordings are gone for good.

### The backup
- The export is read back after it is written: a backup that cannot be read is
  worse than no backup, because it gives the feeling of safety.
- The export says how many entries it wrote.

### The lock and the clipboard
- How long the journal stays unlocked after the app was in the background is a
  setting: 30 seconds, a minute, five minutes, or until the app is closed.
- The clipboard is cleared a minute after a copy, so the text of an entry does
  not sit there for hours.
- "Start over" asks before it deletes the journal.

### The cleanups
- The empty state has an "Add an entry" button, so the screen that says what
  to do can also do it.
- The app says that a photo in HEIC keeps the place it was taken.
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
