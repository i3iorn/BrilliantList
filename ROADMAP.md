# Roadmap

## v0.2.0 — "Everyday"

**In one line:** the app becomes something you would actually use every day, and
an update over an installed v0.1.0 works.

v0.1.0 is the app that exists and is safe. It is missing the things that make
a journal worth keeping open, and it does not yet say which version it is.

**Done when:**

- An installed v0.1.0 becomes v0.2.0 on a real phone without losing a single
  entry.
- The app tells you which version it is, and who inspired it.
- The list can be browsed by day, the trash can be emptied, and an export says
  what it wrote.
- The CI *starts* the release APK, so a broken shrinking fails the build.

### Not in v0.2.0

| Idea | Why not now |
|---|---|
| Sync, accounts, a server | The app has no internet permission on purpose. That is the product, not a limitation to fix. |
| More capture types (video, drawings) | Every new type needs a player, an editor and a migration. The three we have are enough. |
| Themes, colours, widgets of the home screen | "Radical simplicity - every feature must justify its existence." |
| The Keystore key bound to the user | The design is written up in `AGENTS.md` (Known gaps). It is a security change that touches every read of the journal; it deserves its own release. Plan: v0.3.0. |
| HEIC metadata removal | The photo picker hands out JPEG most of the time. A note in the app is honest, a half done HEIF parser is not. |
| Splitting the modules | Invisible to the user. Only if the phases above are done and there is time. |

### The decisions

| Question | Answer |
|---|---|
| Does the repository stay private? | **No** - it is public, so the download URL works for everybody without a login, and an installer like Obtainium can update the app on its own. |
| The day grouping: always, or a setting? | **A setting.** The entries are grouped by day, and the grouping can be switched off for a flat list. |
| How fast should the app lock again? | **A setting.** The time until the journal locks again is chosen by the user: 30 seconds, a minute, five minutes, or only when the app is closed. |
| HEIC: strip the metadata, or say so? | **Say so.** The photo picker hands out JPEG most of the time; a note in the app is honest, a half done HEIF parser is not. The stripping itself is planned for v0.3.0. |

---

## Phase 0 — The update path (blocks the release)

Everything else can wait, this cannot: the point of a release is that it
installs over the one you have.

### 0.1 The version comes from the release, not from a constant

Today the app says `versionName = "1.0"` and `versionCode = 1` while the
release is `v0.1.0`. An update with the same version code is fragile, and
nobody can tell which build is on their phone.

- The version is set from a Gradle property (`-PversionName=0.2.0
  -PversionCode=2`), and the release workflow derives both from the tag.
- `BuildConfig.VERSION_NAME` is used where the version is shown.
- Files: `app/build.gradle.kts`, `.github/workflows/release.yml`
- Verify: `aapt2 dump badging` on the APK says `versionName='0.2.0'`.

**Size: S.**

### 0.2 The CI starts the release APK

R8 is never executed anywhere: the release build is compiled, signed and
published, but nobody has ever seen it start. A missing keep rule would ship
an app that crashes on the first screen.

- The instrumented job installs the **release** APK on the emulator and starts
  it, after the debug tests.
- Files: `.github/workflows/ci.yml`, `.github/run-instrumented-tests.sh`
- Verify: the CI fails when the release APK does not start.

**Size: S.**

### 0.3 The update is tested on a real device

The whole reason for the release key is that an update keeps the journal. It
has never been proven.

- Install v0.1.0, write an entry, install v0.2.0 over it, check the entry is
  still there and the app starts.
- The result goes into the release notes.

**Done** (on the emulator, with the published v0.1.0 and the new build): the
update installs over the old one, the journal is untouched and the app
starts. It should be repeated on a real phone before the release goes out.

**Size: S, but it needs a phone.**

### 0.4 The changelog

- `CHANGELOG.md` with the versions, and the release notes for v0.2.0 in
  `.github/release-notes.md`.

**Size: S.**

---

## Phase 1 — The app says what it is

### 1.1 An "About" section

The version, the licence, the link to the source, the fingerprint of the signing
key - and the credit to *Every Brilliant Thing*. The credit is in the README
today, and the README is not where the people who use the app look.

- Files: `feature_settings/SettingsScreen.kt`, `feature_settings/.../strings.xml`
- Verify: a compose test that finds the version and the credit.

**Size: S.**

### 1.2 The app says what it stores

"Encrypted with a key that never leaves this device" is a claim; the number of
entries and the size on the disk make it a fact.

- The number of entries, the size of the media, and when it was last changed.
- Files: `feature_settings/SettingsScreen.kt`, `core_data/.../EntryRepository.kt`
- Verify: the values match the database and the media directory.

**Size: M.**

---

## Phase 2 — The everyday list

### 2.1 Group the entries by day

A journal you cannot browse is a list you cannot read. "Today", "Yesterday",
then the date - the same thing the timestamp says, but grouped.

- **The grouping is a setting**: some people want a flat list, and the
  entries are the same either way.
- Files: `feature_entry/EntryList.kt`, `core_ui` (a section header), strings
- Verify: a compose test that the headers are there; a look on the device.

**Size: M.**

### 2.2 The search says how much it found

"12 entries" after the search, so a search that finds nothing is obvious.

**Size: S.**

### 2.3 The recording shows what is left

The recording stops after two minutes - that is a limit the user should see
coming, not discover.

- The indicator shows the time that is left, and the recording stops visibly.
- Files: `feature_entry/EntryScreen.kt`, `feature_entry/AudioRecorder.kt`

**Size: S.**

### 2.4 The trash is a separate place

Today the capture actions still work while the trash is open: a photo can be
added and never seen. The capture field is hidden there, the buttons should be
too.

**Size: S.**

### 2.5 The search lets go of the keyboard

The search field keeps the focus after the search, so the keyboard covers the
list.

**Size: S.**

---

## Phase 3 — The trash and the backup are honest

### 3.1 "Empty the trash"

`EntryViewModel.emptyTrash()` exists and no button reaches it. Either the
button comes or the function goes.

- Files: `feature_entry/EntryScreen.kt`, `feature_entry/EntryViewModel.kt`

**Size: S.**

### 3.2 The export is checked after it is written

The export says "the export was written" - it does not say how many entries
are in it, and nothing checks that the file can be read back. A backup that
is broken is worse than no backup, because it gives the feeling of safety.

- The file is read back after it is written and the number of entries is
  checked; the message says the count.
- Files: `core_data/.../EntryRepository.kt`, `feature_settings/SettingsViewModel.kt`
- Verify: a test that reads the export back and compares the count.

**Size: M.**

---

## Phase 4 — The smaller things

| Item | Size | Note |
|---|---|---|
| The clipboard is cleared after a minute | S | The text of an entry should not sit in the clipboard of the system for hours. |
| The time until the app locks again becomes a setting | M | 30 seconds is a constant today. The user chooses: 30 seconds, a minute, five minutes, or only when the app is closed. |
| "Start over" asks before it deletes the journal | S | It deletes everything. |
| The unused strings go (`entry_empty_action`, `entry_capture_action`, `crisis_call`) | S | They are leftovers; a string nobody reads is a lie about the screen. |
| The empty state gets its "Add an entry" button | S | The string is there, the button is not. |
| HEIC is documented in the app | S | "The photos of some cameras keep their location data." Say it plainly. |

---

## The risks

| Risk | What happens | What we do about it |
|---|---|---|
| The release key is lost | The app can never be updated again, everybody has to uninstall it, the journal goes with it. | The key is in `keystore/` on this machine and in the secrets of the repository. Back it up somewhere else. It is the most valuable file of the project. |
| The version and the tag drift apart | An update does not install, or installs the wrong build. | The version comes from the tag (0.1) and the CI checks it. |
| The day grouping makes the list slow | A long journal with many days is more work to draw. | The list is lazy already; the headers are drawn per item, not per day. |
| The export check doubles the work of an export | An export of a big journal takes twice as long. | The check only reads the file back, it does not encrypt it again. The export is a rare action. |

---

## The open questions

None for now - the four that were open are decided (see the decisions above).
If something else comes up, it goes here.
