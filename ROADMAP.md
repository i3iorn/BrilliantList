# Roadmap

## v0.2.0 — "Everyday"

**In one line:** the app becomes something you would actually use every day, and
an update over an installed v0.1.0 works.

v0.1.0 is the app that exists and is safe. It is missing the things that make
a journal worth keeping open, and it does not yet say which version it is.

**Done when:**

- An installed v0.1.0 becomes v0.2.0 on a real phone without losing a single
  entry.
- The app tells you which version it is, who inspired it and what it stores.
- The list can be browsed by day, the trash can be emptied, and an export
  says what it wrote.
- The CI *starts* the release APK, so a broken shrinking fails the build.

### The decisions

| Question | Answer |
|---|---|
| Does the repository stay private? | **No** - it is public, so the download URL works for everybody without a login, and an installer like Obtainium can update the app on its own. |
| The day grouping: always, or a setting? | **A setting.** The entries are grouped by day, and the grouping can be switched off for a flat list. |
| How fast should the app lock again? | **A setting.** The user chooses: 30 seconds, a minute, five minutes, or only when the app is closed. |
| HEIC: strip the metadata, or say so? | **Say so.** The photo picker hands out JPEG most of the time; a note in the app is honest, a half done HEIF parser is not. The stripping itself is planned for v0.3.0. |

### Not in v0.2.0

| Idea | Why not now |
|---|---|
| Sync, accounts, a server | The app has no internet permission on purpose. That is the product, not a limitation to fix. |
| More capture types (video, drawings) | Every new type needs a player, an editor and a migration. The three we have are enough. |
| Themes, colours, home screen widgets | "Radical simplicity - every feature must justify its existence." |
| The Keystore key bound to the user | The design is written up in `AGENTS.md` (Known gaps). It is a security change that touches every read of the journal; it deserves its own release. Plan: v0.3.0. |
| HEIC metadata removal | A note in the app is honest, a half done HEIF parser is not. |
| Splitting the modules | Invisible to the user. Only if the phases below are done and there is time. |

---

## The order of the work

| Phase | What | Size |
|---|---|---|
| 0 | The update path | **done** |
| 1 | The app says what it is | M |
| 2 | The everyday list | L |
| 3 | The trash | S |
| 4 | The backup is honest | M |
| 5 | The lock and the clipboard | M |
| 6 | The cleanups | S |
| 7 | The release | S |

The phases are ordered by what unblocks the most: Phase 1 needs the version
plumbing of Phase 0, and Phase 2 is the biggest change to the screen the
user looks at every day.

---

## Phase 0 — The update path (done)

Everything else can wait, this cannot: the point of a release is that it
installs over the one you have.

### 0.1 The version comes from one place (done)

The app said `versionName = "1.0"` while the release was `v0.1.0`. The
version now lives in `version.properties` in the root, the build reads it and
the release workflow **refuses a tag that does not match it**.

- `version.properties`: `versionName` and `versionCode` (the scheme is
  `major * 10000 + minor * 100 + patch`, so 0.2.0 is 200).
- `app/build.gradle.kts` reads the file.
- `.github/workflows/release.yml` compares the tag with the file.

### 0.2 The CI starts the release APK (done)

R8 was never executed anywhere: the release build was compiled, signed and
published, but nobody had ever seen it start. A missing keep rule would have
shipped an app that crashes on the first screen.

- The build job produces the signed APK and uploads it.
- The instrumented job downloads it, installs it and starts it, and looks for
  a crash in the log.

### 0.3 The update keeps the journal (done, once more on a phone)

The published v0.1.0 was installed, an entry was written, and the new build
was installed over it: the update goes through and the journal is untouched.
It should be repeated on a real phone before the release goes out.

### 0.4 The changelog (done)

`CHANGELOG.md` with the versions, and the release notes in
`.github/release-notes.md`.

---

## Phase 1 — The app says what it is (done)

### 1.1 The repository gets a licence (done)

The repository is public now, and without a licence nobody may use the code -
the README even links to a `LICENSE` that did not exist. It has the Apache
License 2.0, the text from apache.org. MIT is one command away if that suits
the project better.

### 1.2 The About section (done)

At the bottom of the settings, under a new heading "About":

- The version, e.g. "0.2.0".
- The licence.
- The link to the source.
- The fingerprint of the signing key, so a person can check that the app on
  their phone is the one from this project.
- The credit to *Every Brilliant Thing* - it belongs where the people who use
  the app look, not only in the README.

The version comes from the app module, the feature module does not know it:
`SettingsScreen(versionName = ...)` gets it as a parameter, and
`AppNavigation` passes `BuildConfig.VERSION_NAME`. The fingerprint is a
constant in the strings of the feature, with a comment that it has to match
the release key.

The links are opened with `LocalUriHandler`, so they work without a browser
in the app.

- Files: `feature_settings/SettingsScreen.kt`, `feature_settings/.../strings.xml`,
  `app/ui/AppNavigation.kt`, `app/build.gradle.kts` (`buildConfig = true`)
- Verify: a compose test that finds the version and that the last row of the
  settings is reachable.

### 1.3 What the app stores (done)

"Encrypted with a key that never leaves this device" is a claim; the number of
entries and the size on the disk make it a fact.

- `JournalStats(entryCount, mediaBytes, databaseBytes)` in `core_model`.
- `EntryRepository.journalStats()`: a count query for the entries, the size of
  the media directory and the size of the database file.
- A row in the privacy section: "23 entries · 4.2 MB on this device".

The numbers are read when the settings open, not watched - they change rarely
and the reading touches the disk.

- Files: `core_model/Models.kt`, `core_data/.../EntryRepository.kt`,
  `core_data/.../EntryDao.kt`, `feature_settings/SettingsScreen.kt`
- Verify: a compose test that finds the row.

---

## Phase 2 — The everyday list

### 2.1 The entries are grouped by day (with a setting)

A journal you cannot browse is a list you cannot read. "Today", "Yesterday",
then the date - the same thing the timestamp says, but grouped.

- **The setting**: `groupByDay: Boolean = true` in `Settings`,
  `group_by_day` in the DataStore, a switch in the journal section of the
  settings.
- **The list**: the visible list is turned into a flat list of items - headers
  and entries. A header is drawn when the date of an entry differs from the
  date of the one before it, so the headers follow the order the user sees
  (the manual order or the newest first).
- **The date**: the local date of the entry. The app needs API 26 for
  `java.time` and it needs 33, so it is there.
- The header text: "Today", "Yesterday", otherwise the date in the format of
  the system.

The headers are not sticky - that needs an experimental API of the foundation
library, and a list of a few hundred entries does not need it.

- Files: `core_model/Models.kt`, `core_data/.../SettingsRepository.kt`,
  `feature_entry/EntryList.kt`, `feature_settings/SettingsScreen.kt`, strings
- Verify: a compose test that the headers are there, and one that they are
  gone when the grouping is off.

**Size: L.**

### 2.2 The search says how much it found

"12 entries" under the search field, so a search that finds nothing is
obvious. The list is already in the ViewModel, so this is the size of the
list it holds.

**Size: S.**

### 2.3 The search lets go of the keyboard

The search field keeps the focus after the search, so the keyboard covers the
list. The IME action "Search" gives the focus back.

**Size: S.**

### 2.4 The recording shows the time that is left

The recording stops after two minutes - that is a limit the user should see
coming, not discover. The indicator shows the time that is left next to the
elapsed time.

**Size: S.**

---

## Phase 3 — The trash

### 3.1 The trash is a separate place (done)

The capture field and the capture buttons are hidden while the trash is open,
so a photo cannot be added into a list the user is not looking at.

### 3.2 "Empty the trash"

`EntryViewModel.emptyTrash()` exists and no button reaches it - either the
button comes or the function goes. The button comes: it is in the trash view,
it only shows when the trash is not empty, and it asks first, because it
deletes the entries and their media at once.

- Files: `feature_entry/EntryScreen.kt`, `feature_entry/EntryViewModel.kt`,
  strings

**Size: S.**

---

## Phase 4 — The backup is honest

### 4.1 The export checks itself

A backup that is broken is worse than no backup, because it gives the feeling
of safety. Today the export says "the export was written" - it does not say
how many entries are in it, and nothing checks that the file can be read
back.

- After the file is written, it is read back, decrypted and counted. The
  count has to match the number of entries that were written.
- The file is read, not written again, so the check costs one decryption.
- If the check fails, the export is reported as broken instead of as done.

**Size: M.**

### 4.2 The export says what it wrote

"23 entries written" instead of "the export was written". The import already
says how many entries it restored.

**Size: S.**

---

## Phase 5 — The lock and the clipboard

### 5.1 The time until the app locks again is a setting

30 seconds is a constant today. The user chooses: 30 seconds, a minute, five
minutes, or only when the app is closed.

- **The setting**: `appLockDelay: AppLockDelay` (an enum in `core_model`:
  `THIRTY_SECONDS`, `ONE_MINUTE`, `FIVE_MINUTES`, `UNTIL_CLOSED`), stored in
  the DataStore, chosen with a row of chips next to the app lock.
- **The enforcement**: `AppLockViewModel` uses the delay instead of the
  constant. `UNTIL_CLOSED` means the journal stays unlocked while the app is
  open - it locks again when the app is closed, which is the default anyway.

**Size: M.**

### 5.2 The clipboard is cleared

The text of an entry should not sit in the clipboard of the system for hours.
A minute after the copy, the clipboard is cleared - but only if it still holds
the text that was copied, so a copy of something else is not touched.

**Size: S.**

### 5.3 "Start over" asks first

The screen for a lost encryption key deletes the journal. That deserves a
question first.

**Size: S.**

---

## Phase 6 — The cleanups

| Item | Size | Note |
|---|---|---|
| The unused strings go (`entry_empty_action`, `entry_capture_action`, `crisis_call`) | S | A string nobody reads is a lie about the screen. The first one comes back with the next item. |
| The empty state gets its "Add an entry" button | S | The string is there, the button is not. It focuses the capture field. |
| The photos that keep their location data are named | S | "A photo in HEIC keeps its location data." Say it plainly in the privacy section. |
| The `crisis_call` string is replaced by the two action labels | S | Done with the crisis page, the old string is left over. |

---

## Phase 7 — The release

The checklist, in order:

1. The version in `version.properties` is right and matches the tag.
2. The changelog and the release notes are written.
3. The CI is green - both jobs, including the release build that starts.
4. **The update is tested on a real phone**: install v0.1.0, write an entry,
   install the release over it, check the entry is still there.
5. The tag is pushed: `git tag -a v0.2.0 -m "..." && git push origin v0.2.0`.
6. The release is published by the workflow. The download is checked: the URL
   works without a login, the signature is the fingerprint of the key, the
   checksum matches.

---

## The two new settings

| Setting | Where it lives | Default | What it changes |
|---|---|---|---|
| `groupByDay` | `Settings`, the DataStore key `group_by_day` | on | The list is grouped by the day of the entries. |
| `appLockDelay` | `Settings`, the DataStore key `app_lock_delay` | 30 seconds | How long the journal stays unlocked after the app was in the background. |

Both are in `Settings` (`core_model`), written by `SettingsRepository`
(`core_data`) and changed in the settings screen (`feature_settings`). Neither
needs a migration: they are new keys in the DataStore.

---

## The risks

| Risk | What happens | What we do about it |
|---|---|---|
| The release key is lost | The app can never be updated again, everybody has to uninstall it, the journal goes with it. | The key is in `keystore/` on this machine and in the secrets of the repository. Back it up somewhere else. It is the most valuable file of the project. |
| The version and the tag drift apart | An update does not install, or installs the wrong build. | The version comes from one file and the release refuses a tag that does not match it. |
| The day grouping makes the list slow | A long journal with many days is more work to draw. | The list is lazy already. The headers are drawn per item, not per day. |
| The export check doubles the work of an export | An export of a big journal takes twice as long. | The check reads the file back, it does not encrypt it again. The export is a rare action. |
| The app lock setting is confusing | A user picks "only when the app is closed" and forgets why the journal was open. | The options are named after what they do, and the default stays at 30 seconds. |
| The public repository gets a fork or a pull request | Somebody wants to change the app. | That is the point of a public repository. The licence says what they may do with it. |

---

## The open questions

None for now - the four that were open are decided (see the decisions above).
If something else comes up, it goes here.
