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

## Phase 2 - The everyday list (done)

### 2.1 The entries are grouped by day (done, with a setting)

A journal you cannot browse is a list you cannot read. "Today", "Yesterday",
then the date - the same thing the timestamp says, but grouped.

- **The setting**: `groupByDay: Boolean = true` in `Settings`,
  `group_by_day` in the DataStore, a switch in the journal section of the
  settings.
- **The list**: the heading of the day is drawn above the first entry of the
  day. It belongs to the entry and is not a row of its own, so the order of
  the list and the order of the entries stay the same - the drag to
  reorder keeps working.
- **The date**: the local date of the entry. The app needs API 26 for
  `java.time` and it needs 33, so it is there.
- The header text: "Today", "Yesterday", otherwise the date in the format of
  the system.

The headers are not sticky - that needs an experimental API of the foundation
library, and a list of a few hundred entries does not need it.

- Files: `core_model/Models.kt`, `core_data/.../SettingsRepository.kt`,
  `feature_entry/EntryList.kt`, `feature_settings/SettingsScreen.kt`, strings
- Verify: dayStarts() is a pure function, so it is tested on the JVM
  (DayGroupingTest).

### 2.2 The search says how much it found (done)

"12 entries" under the search field, so a search that finds nothing is
obvious. The list is already in the ViewModel, so this is the size of the
list it holds.

### 2.3 The search lets go of the keyboard (done)

The search field keeps the focus after the search, so the keyboard covers the
list. The IME action "Search" gives the focus back.

### 2.4 The recording shows the time that is left (done)

The recording stops after two minutes - that is a limit the user should see
coming, not discover. The indicator sits on its own line above the toolbar
(the microphone being on has to be obvious, and the time needs the room) and
shows the elapsed time and the time that is left.

---

## Phase 3 — The trash (done)

### 3.1 The trash is a separate place (done)

The capture field and the capture buttons are hidden while the trash is open,
so a photo cannot be added into a list the user is not looking at.

### 3.2 "Empty the trash" (done)

`EntryViewModel.emptyTrash()` exists and no button reaches it - either the
button comes or the function goes. The button comes: it is in the trash view,
it only shows when the trash is not empty, and it asks first, because it
deletes the entries and their media at once.

- Files: `feature_entry/EntryScreen.kt`, `feature_entry/EntryViewModel.kt`,
  strings

---

## Phase 4 — The backup is honest (done)

### 4.1 The export checks itself (done)

A backup that is broken is worse than no backup, because it gives the feeling
of safety. Today the export says "the export was written" - it does not say
how many entries are in it, and nothing checks that the file can be read
back.

- After the file is written, it is read back, decrypted and counted. The
  count has to match the number of entries that were written.
- The file is read, not written again, so the check costs one decryption.
- If the check fails, the export is reported as broken instead of as done.

### 4.2 The export says what it wrote (done)

"23 entries written" instead of "the export was written". The import already
says how many entries it restored.

---

## Phase 5 — The lock and the clipboard (done)

### 5.1 The time until the app locks again is a setting (done)

30 seconds is a constant today. The user chooses: 30 seconds, a minute, five
minutes, or only when the app is closed.

- **The setting**: `appLockDelay: AppLockDelay` (an enum in `core_model`:
  `THIRTY_SECONDS`, `ONE_MINUTE`, `FIVE_MINUTES`, `UNTIL_CLOSED`), stored in
  the DataStore, chosen with a row of chips next to the app lock.
- **The enforcement**: `AppLockViewModel` uses the delay instead of the
  constant. `UNTIL_CLOSED` means the journal stays unlocked while the app is
  open - it locks again when the app is closed, which is the default anyway.

### 5.2 The clipboard is cleared (done)

The text of an entry should not sit in the clipboard of the system for hours.
A minute after the copy, the clipboard is cleared - but only if it still holds
the text that was copied, so a copy of something else is not touched.

### 5.3 "Start over" asks first (done)

The screen for a lost encryption key deletes the journal. That deserves a
question first.

---

## Phase 6 — The cleanups (done)

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
---

## v0.3.0 - "Links, the clipboard and more of the world"

**In one line:** the journal can hold a link and whatever was on the
clipboard, and the crisis page knows far more of the world - with the
numbers that are verified and the names in the language of the country.

v0.2.0 made the app pleasant to use every day. v0.3.0 makes it bigger:
two more ways to put something into the journal, and a crisis page that
covers 24 countries instead of 5, with the local names, the opening
hours, the languages and the date the number was last checked.

**Done when:**

- A link can be added, is stored encrypted, and opens in the browser when
  it is tapped.
- Whatever was on the clipboard - a screenshot, a link or a text - can be
  added with one tap.
- The crisis page covers 24 countries with verified numbers, shows the
  name in the language of the country, the hours, and the date the
  number was last checked.
- The interface speaks more than one language.

## The three things, and what they mean

### A link

A new kind of entry: a URL. The app has no internet permission, so it
cannot show a preview of the page - and it does not need to. The link is
stored like every other entry (encrypted), the card shows the address, and
tapping it hands it to the browser of the device.

Two rules, because the address comes from outside:

- **Only `http` and `https`.** The app hands the address to the system
  with `ACTION_VIEW`. An `intent://` or `javascript:` address could make
  the system do something the user did not expect, so everything else is
  refused with a clear message.
- **The address is shown as text.** A link that looks like
  `brilliant-list.example` has to be read, not trusted.

### From the clipboard

One button that looks at the clipboard and does the right thing:

- a picture (a screenshot) becomes a photo entry, with the metadata
  removed like every other photo,
- an address becomes a link entry,
- anything else becomes a text entry.

The clipboard is only read when the button is tapped, never on its own:
the clipboard of Android is shared with every app on the device, and it
often holds something private.

### More languages and regions

Two different things, and they are treated differently.

**The regions come from data.** The
[open-helplines](https://github.com/Kouki-odaka/open-helplines) registry
holds verified helplines for 24 countries - AU, BD, BR, CA, CN, DE, EG,
FR, GB, ID, IN, JP, KR, MX, NG, NZ, PH, PK, RU, TR, UA, US, VN, ZA -
as [CC0 data](https://github.com/Kouki-odaka/open-helplines/blob/main/LICENSE-DATA):
public domain, no attribution needed, and every record carries the date
it was last checked. The five hand-written countries of the app today
become the first five of twenty-four, and the hand-written numbers are
replaced by verified ones.

**The languages of the crisis data come with it.** Every record has the
name in the language of the country (`local_name`), the languages the
line answers in, the opening hours, and whether it is free and anonymous.

**The languages of the interface are work.** The interface is five
modules of strings. A translation has to be right - this is an app about
low moods, and a bad translation does harm. German can be done well
here; the others want a native speaker.

## The phases

### Phase 0 - The groundwork

#### 0.1 An unknown entry type must not crash the app

The type of an entry is a word in the database. A new type ("LINK")
makes an **older** build crash when it reads such an entry - and people
do install an older build over a newer one.

- The mapper becomes forgiving: an unknown type is shown as a text entry
  instead of crashing.
- Files: `core_data/.../mapper/EntryMappers.kt`
- Verify: a unit test that maps an unknown type.

#### 0.2 The toolbar gets room

The toolbar has five buttons; the two new capture actions would make
seven, and seven do not fit on a phone.

- The **capture** actions stay in the toolbar: photo, microphone, link,
  paste.
- The **view** actions (favourites, search, trash) move into the top bar,
  where there is room for them.
- Files: `feature_entry/EntryScreen.kt`, `app/ui/AppNavigation.kt`

### Phase 1 - A link

- A new kind of entry, `LINK`, with the address as its text. The type
  column is a word, so **no migration is needed** - but see 0.1.
- The card shows the address and the domain; tapping it opens the
  browser, the address is checked first (only `http` and `https`).
- The entry is added with a small dialog: the address, an inline error,
  and the keyboard.
- Files: `core_model/Models.kt`, `feature_entry/EntryCard.kt` (via
  `core_ui`), `feature_entry/EntryScreen.kt`
- Verify: a unit test for the check of the address, a compose test that
  the card shows the address.

### Phase 2 - From the clipboard

- One button, one decision: a picture, an address or a text.
- A picture from the clipboard goes through the same path as a photo
  from the picker, so the metadata is removed and the file is encrypted.
- An empty clipboard says so instead of doing nothing.
- Files: `feature_entry/EntryViewModel.kt`, `feature_entry/EntryScreen.kt`
- Verify: an instrumented test that pastes a text and a link.

### Phase 3 - The crisis page knows more of the world

- A script downloads the registry and writes a Kotlin file with the
  records: `scripts/import_helplines.py` ->
  `core_model/.../HelplineCatalog.kt`. The file says that it is
  generated, and the script is the only thing that ever changes it.
- The catalog stays pure Kotlin and unit tested, the app needs no JSON
  parser at runtime and no internet.
- The app shows what the data has: the name in the language of the
  country, the description, the hours, whether it is free and anonymous,
  the languages it answers in, and the date the number was last checked.
- **The emergency number is not in the data.** Every country has one and
  it is the first thing a person in a crisis needs, so it stays a small
  table in the app - and it is the one thing in the crisis page that has
  to be right.
- The region picker lists the 24 countries with their flag and their name
  in their own language.
- Files: `scripts/import_helplines.py`, `core_model/.../Models.kt`,
  `core_model/.../CrisisCatalog.kt`, `feature_crisis/CrisisScreen.kt`
- Verify: a unit test per country that the numbers are there, the method
  (call, text) is right, and the date is not older than a year.

### Phase 4 - More languages

- The strings of the app are exported as a table
  (`scripts/export_strings.py`), translated, and imported back
  (`scripts/import_strings.py`) into the five modules. Doing it by hand in
  five XML files per language does not scale, and the export makes it
  clear what has to be translated.
- German first. It can be done well here, and it is the language of one
  of the countries in the catalog.
- The other languages want a native speaker. The crisis wording
  especially: "Are you safe right now?" is not a sentence to have
  translated by a machine.
- The crisis data is already in the language of the country; the
  descriptions of the registry are in English, so a reader gets the
  local name and an English description. That is stated in the app.

### Phase 5 - The release

The same seven steps as v0.2.0: the version, the changelog, the green
pipeline, **the update on a real phone**, the tag, the download, the
signature.

## The risks

| Risk | What happens | What we do about it |
|---|---|---|
| A wrong crisis number | Someone in a crisis calls a number that does not help. | The data comes from a verified registry with a date, the date is shown in the app, and the emergency number - the one number that is not in the registry - is a small table that is reviewed by a human. |
| The registry is not updated any more | The numbers get older. | The app shows the date the number was last checked. A check in the pipeline reports how old the data is. |
| A link opens something unexpected | An address with a scheme the app does not know makes the system do something. | Only `http` and `https` are accepted, everything else is refused with a message. |
| The clipboard holds something private | A password or a secret ends up in the journal. | The clipboard is only read when the button is tapped, and the app never reads it on its own. |
| A translation is wrong | A person in a low mood reads something that does not help. | The interface is translated by native speakers, and the crisis wording is reviewed by a human before it ships. |
| The toolbar gets crowded again | The buttons do not fit on a small phone. | The capture actions and the view actions are separated. |

## The open questions

1. **Which languages?** German is the one that can be done well here. Which
   others do the people who use the app speak? Is there someone who can
   check the translations - especially the crisis wording?
2. **The clipboard: pictures too?** A screenshot is the most likely thing
   on a clipboard. It is also the most private. Both are reasonable, and
   the answer is one line of code.
3. **The emergency numbers.** They are the one thing in the crisis page
   that the registry does not have. They are stable facts, but they have
   to be right - shall I write them out for the 24 countries and you
   check them?
4. **The links: open them in the browser?** The app has no internet
   permission, so it cannot show what the page says. The alternative is to
   store the link and never open it, which seems pointless.