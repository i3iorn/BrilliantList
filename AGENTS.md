# AGENTS.md

## Project

BrilliantList — privacy-first Android journaling app (Kotlin, Compose, Room+SQLCipher, Hilt, Glance, WorkManager).
Package root: `dev.schrammel.brilliantlist`. Product rules live in `README.md`, the security posture in `SECURITY.md`, the crypto/media design in `ENCRYPTION_MEDIA_PIPELINE.md`.

## Build & verify (Windows: `.\gradlew.bat`, POSIX: `./gradlew`)

```bash
./gradlew assembleDebug                    # compile everything (CI does this)
./gradlew :app:assembleDebug               # debug APK
./gradlew :feature_entry:assembleDebug     # single module
./gradlew :core_model:test                 # pure Kotlin unit tests (no device)
./gradlew :app:testDebugUnitTest
./gradlew :feature_entry:testDebugUnitTest
./gradlew :core_data:connectedDebugAndroidTest        # needs a device/emulator
./gradlew :core_encryption:connectedDebugAndroidTest
./gradlew :feature_entry:connectedDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest   # includes the accessibility checks
./gradlew :app:pixel2api33DebugAndroidTest  # the same tests on a managed device (no hardware needed)
python3 scripts/verify_privacy_invariants.py          # CI guard, run after assembleDebug
```

- CI (`.github/workflows/ci.yml`, JDK 21) runs `assembleDebug :core_model:test :core_data:testDebugUnitTest :feature_entry:testDebugUnitTest` and then `scripts/verify_privacy_invariants.py`. A second job runs the instrumented tests on a **managed device** (`pixel2api33`), so the accessibility, crypto and backup tests are part of the build.
- `lint` needs network on first run: its classpath pulls the JVM/desktop variants of the KMP artifacts (`datastore-preferences-jvm`, `material-icons-core-desktop`) which are not in the local cache. It fails offline **on a pristine checkout too** — not a code problem.
- **Instrumented vs JVM**: anything touching `AndroidKeyStore`, SQLCipher or `MediaPlayer` must stay in `src/androidTest` (device required). Pure logic belongs in `core_model` (a plain JVM module) so it can be unit tested — the crisis catalog and the drag-reorder math live there for exactly that reason.
- Toolchain: Gradle 9.4.1, AGP 9.2.0, Kotlin 2.2.10, KSP 2.3.12, Hilt 2.60.1, compileSdk 36, JDK 21 (`jvmToolchain(21)`).
  You do **not** need JDK 21 installed: `gradle.properties` sets `org.gradle.java.installations.auto-download=true` and the foojay resolver in `settings.gradle.kts` provisions it.
- minSdk is 33 in `:app` but 23 in every library module — don't "fix" the mismatch, it is the current state.
- Room codegen runs through KSP (already wired) — no manual codegen step. `core_data` also runs KSP for the `androidTest` source set (`kspAndroidTest`), because the migration test declares the old schema.

## Modules

`app` → `feature_entry` / `feature_widget` / `feature_crisis` / `feature_settings` → `core_data` → `core_encryption`; `core_model` is a plain Kotlin **JVM** module (not an Android library), `core_ui` holds the shared Compose theme + `EntryCard`.

- Every inter-module dependency is `implementation`, never `api` → **types are not visible transitively**. A module that touches `Entry`/`Settings` must declare `implementation(project(":core_model"))` itself; same for `core_data` repositories and for `Room` types in `androidTest` (see `feature_entry/build.gradle.kts`).
- `core_data` declares `core_encryption` as `implementation`, so `CryptoManager` is *not* on the feature classpath. Features go through `EntryRepository` / `SettingsRepository`; never add a direct `core_encryption` dependency to a feature. To detect a locked device from a feature, use `DatabaseKeyManager.isAvailable()` (core_data) instead of catching the crypto exception.
- `core_model` holds the crisis catalog (`CrisisRegion`, `CrisisCatalog`) so region mapping is unit testable without a device.
- Icons: use the core icon set that ships with material3. Do **not** declare `material-icons-core` explicitly — lint then resolves the multiplatform desktop artifact. The camera/mic/stop glyphs are local vector drawables in `feature_entry/src/main/res/drawable/`.
- Strings: every user-facing string belongs in that module's `res/values/strings.xml`. R classes are non-transitive, so each module needs its own copy.

## Crash-avoidance invariants (each one is a real past crash — keep them)

- **Manifest must declare `android:name=".MainApp"`** (`@HiltAndroidApp`) or every `@AndroidEntryPoint` crashes with `IllegalStateException`.
- **`@ApplicationContext` qualifier** on any injected `Context` (repositories, ViewModels, workers) — a bare `Context` fails Dagger with `MissingBinding`.
- **`WorkManagerInitializer` must stay removed** from the manifest (`tools:node="remove"` on the `androidx.startup` provider). `MainApp` implements `Configuration.Provider`; if the default initializer runs, Hilt worker injection fails at runtime.
- **`hiltViewModel()`** (from `androidx.hilt:hilt-navigation-compose`) for ViewModels used inside NavHost destinations — plain `viewModel()` can't build injected constructors and crashes.
- **Glance receivers cannot use `@AndroidEntryPoint`** — `BrilliantListWidgetReceiver` reaches the graph through `@EntryPoint` + `EntryPointAccessors.fromApplication(...)` (`WidgetEntryPoint` in `BrilliantListWidget.kt`). Keep that pattern.
- **Glance `provideContent {}`'s lambda is not a coroutine scope** — fetch the entry *before* calling it.
- **Runtime permissions must be in the manifest**: `RECORD_AUDIO` is declared in `app/src/main/AndroidManifest.xml`; without it `AudioRecord.startRecording()` throws `SecurityException`.
- **The recorder writes into the encrypted media file.** `AudioRecorder` reads the microphone with `AudioRecord` and writes the samples into the stream of a `MediaSink` (`core_data`), which encrypts them on the way to the disk. There is no temp file any more, so a recording never exists as plaintext. The stream has to be closed (it writes the GCM tag) — `MediaSink.close()` does that.
- **The microphone must not stay on in the background.** The recording is stopped when the app leaves the foreground (`Lifecycle.Event.ON_STOP` in `EntryScreen`); the note is kept, it is only cut off. A recording that keeps running in the background would keep the microphone open and the file growing. If the app ever wants to record with the screen off, it needs a foreground service with `foregroundServiceType="microphone"` and the `FOREGROUND_SERVICE_MICROPHONE` permission (Android 14+), which also needs a notification.
- **A recording is limited to two minutes** (`AudioRecorder.MAX_DURATION_SECONDS`). The recording is raw PCM, so it is much bigger than a compressed one and it is loaded into memory to be played.
- **A recording that was interrupted leaves an orphan file.** If the process is killed while the microphone is on, the entry was never written. `EntryRepository.sweepOrphanedMedia()` deletes the files that no entry points to; the app calls it on start.

## Security & privacy invariants

- **The app must never hold the `INTERNET` permission.** It is what makes "no network" true rather than aspirational. `scripts/verify_privacy_invariants.py` fails the build if the merged manifest declares it — keep that script green.
- **Native libraries must be 16 KB page size aligned** (Android 15+ devices). `sqlcipher-android` 4.6.0+ ships aligned binaries; the same script checks the packaged `.so` files. Upgrading SQLCipher below 4.6.0 would silently break the app on those devices.
- **SQLCipher 4.6+ API**: `net.zetetic:sqlcipher-android`, `System.loadLibrary("sqlcipher")` (the app must load it, there is no `loadLibs` anymore) and `SupportOpenHelperFactory(passphrase)`.
- **The Keystore key requires an unlocked device** (`setUnlockedDeviceRequired(true)`), so the database cannot be opened while the device is locked. The app must therefore:
  - check `DatabaseKeyManager.isAvailable()` before touching the database (the app shell shows the locked screen, the widget shows a hint, the worker returns `Result.retry()`),
  - never crash when the key is unavailable — fail closed and say so.
  - Note: a key that does not exist yet counts as *usable*: a fresh install has no key and is not locked. Getting this wrong bricks the app on the first launch.
- **The key is versioned through its alias.** `CryptoManager.KEY_VERSION` is the version of the key specification, `CryptoManager.aliasFor(version)` its alias. `DatabaseKeyManager` stores the alias and the version next to the passphrase and rotates them when the version changes: the passphrase is re-wrapped with a key of the new version, the new passphrase is written *before* the old key is deleted. Never delete the old key first — that loses the database of every install.
- **`DatabaseKeyManager.rotateKeyToNewAlias()`** replaces the key without changing the passphrase. It is what "erase everything" uses: the data is deleted and the key that protected it is gone.
- **A lost key must be reported, not crash**: `DatabaseKeyManager.isKeyLost()` is true when a passphrase exists but its key is gone. The entries cannot be decrypted any more.
- **The app lock** (`AppLockViewModel` in `:app`) asks for the screen lock or a fingerprint before the journal opens. It is only active when the device can authenticate (`canAuthenticate`), and the instrumented tests turn it off (`DisableAppLockRule`), because a locked device would only show the lock screen to the tests.
- **`FLAG_SECURE` is set on the activity.** Journal content must not end up in screenshots, screen recordings or the recents thumbnail. (Side effect: `adb shell screencap` returns a black image for this app — use `uiautomator dump` to inspect the UI.)
- **No plaintext content at rest.** Media is AES-GCM encrypted to `filesDir/media/<uuid>.enc` (the recordings use `<uuid>.pcm.enc`, the suffix marks the format). The photos lose their EXIF metadata on the way in (`ExifStripper`), so the location of the person does not end up in the journal.
- **Audio playback must not write a temp file.** The clip is decrypted into RAM and served to `MediaPlayer` through a `MediaDataSource` (`EncryptedAudioPlayer`) — `MediaPlayer` needs random access, and `SharedMemory.getFileDescriptor()` is `@hide`, so a shared-memory fd is not an option. Note `MediaDataSource.close()` is abstract in the SDK stub and must be overridden. The raw PCM recordings get a WAV header in memory (`Wav.wrap`) before they are played.
- `CryptoManager` format: 12-byte IV prefix + ciphertext, 128-bit GCM tag, key alias `brilliantlist_master_key` in `AndroidKeyStore` (non-exportable). It sets `setRandomizedEncryptionRequired(false)` because it supplies its own IV — removing that line breaks encryption. The decrypt path must read the IV in a loop (a single `read()` may be partial).
- The Keystore key is never persisted. The SQLCipher passphrase is 32 random bytes stored **only as ciphertext** (base64 of IV+ciphertext) in SharedPreferences `db_keys` — that SharedPreferences use is intentional.
- `android:allowBackup="false"` is required: restoring the ciphertext without the non-exportable Keystore key makes the DB undecryptable and startup crashes.
- **The export is encrypted with a passphrase of the user** (`BackupContainer`, PBKDF2-HMAC-SHA256 with 600 000 iterations + AES-GCM), not with the device key — it has to be restorable on another device. The passphrase is never stored and is cleared after the export or the import. The work factor is stored in the header, so the format can be made stronger later (the OWASP cheat sheet recommends storing it).
- **The export is streamed.** `BackupContainer.read` hands the entries to a callback one after the other and the media is a stream, so neither an export nor an import needs the whole journal in memory. The callback has to read the media of an entry before it returns.
- **The passphrase of an export is typed twice** and the strength is shown: a passphrase that is not written down is a backup that cannot be restored.
- **The build that ships must not be signed with the debug key.** The debug key is public. The Gradle build fails a release build in CI without a release key, and `scripts/verify_privacy_invariants.py` checks the signature.

## Data invariants

- **The data layer owns the threading.** `EntryRepository` moves the work that touches the disk (reading a photo, encrypting it, the export, the import) onto the IO dispatcher itself, so a caller cannot get it wrong. The same is true for the Keystore: the first call can take a moment, so `DatabaseKeyManager.warmUp()` creates the key in the background when the app starts.
- **Nothing heavy runs on the main thread.** The rules of thumb: no encryption of a photo or a recording in the UI thread (the repository does it), no `ContentResolver` call in the UI thread, no file listing in the UI thread. The Keystore is the slowest thing in the app - it is warmed up while the app starts.
- **The image pipeline must not copy byte by byte.** `ExifStripper` pulls the bytes and copies them in blocks; a photo is not held in memory while the metadata is removed. `MediaLoader` decrypts a photo once and decodes the bounds and the bitmap from the same buffer.
- **The thumbnails are cached.** Decoding a photo is the most expensive thing the list does, so the decoded bitmaps are kept in a small cache with a budget in bytes.
- **An import is atomic.** Either all the entries of an export are restored or none of them: a half imported journal is worse than a failed import.

- **Room schema changes need a real migration.** `AppDatabase` is at version 2 with `MIGRATION_1_2`; `exportSchema = false` and `fallbackToDestructiveMigration(dropAllTables = true)` are the last resort, so a missing migration destroys the user's data. Add the migration **and** a test in `core_data/src/androidTest/.../MigrationTest.kt` (it declares the old schema, which is why Room has to run for `androidTest`).
- **Fail closed on broken media**: `EntryRepository.openDecryptedMedia` returns null instead of throwing, and `MediaLoader` swallows decode errors. A corrupt file must never crash the screen that renders it.
- **State that references an entry lives in the encrypted database** (`app_state` table, `AppStateRepository`), not in DataStore. DataStore holds only non-sensitive preferences. Do not put entry ids or content back into DataStore.
- `EntryRepository.reorder(idsInOrder)` rewrites positions only for the ids passed — always pass the **full** active (non-deleted) list.
- `EntryDao.observeAll()` filters out soft-deleted rows. Use `getById(id)` to inspect a deleted row. Delete is a soft delete; `EntryRepository.restore(entry)` undoes it (the UI shows an Undo snackbar).
- `SettingsRepository` uses DataStore Preferences (`settings`), not SharedPreferences. `SettingsScreen` keeps the interval slider in local Compose state and commits on `onValueChangeFinished` — writing on every drag step floods disk I/O.
- **Sort order**: `SortMode.MANUAL` keeps the dragged order, `SortMode.NEWEST_FIRST` sorts by creation date. Reordering is disabled in every mode but MANUAL (and while the favorites filter is on) — the visible list is then not the list the positions belong to.
- **Crisis region**: only canonical `CrisisRegion` codes are persisted; `CrisisCatalog.normalize()` maps aliases ("usa", "United States", "UK"). An unrecognized region must show the explicit warning on the crisis screen — never silently fall back to the generic list, that is the worst failure mode in the app.
- Encryption is unconditional and notifications are not implemented, so `Settings` has no toggles for them. Don't reintroduce switches that do nothing.

## Widget & rotation

- The widget shows **TEXT** entries only (image/audio entries have `null` textContent) and is tappable: `feature_widget` cannot depend on `:app`, so the activity is addressed with `Intent.setClassName(context, context.packageName + ".MainActivity")`.
- The user can hide the entry text on the widget (`settings.widgetShowContent`).
- WorkManager's minimum periodic interval is 15 minutes (`coerceAtLeast`). The period is fixed when the work is enqueued, so `WidgetRotationScheduler.schedule(context)` (public) is called by the app whenever the interval setting changes — without that, a new interval never takes effect. An interval longer than 15 minutes is scheduled as a single delayed run (`scheduleNext`), because the periodic work cannot wait longer.
- The worker and the widget must check `DatabaseKeyManager.isAvailable()` first: while the device is locked the database cannot be opened.

## Accessibility

- Every actionable element needs a content description that makes sense out of context; the tests in `app/src/androidTest/.../AccessibilityTest.kt` and `feature_entry/src/androidTest/.../EntryToolbarAccessibilityTest.kt` enforce it (descriptions, 48 dp touch targets, 2x font scale). Add to them when you add a control.
- **A screen that can grow has to scroll.** `SettingsScreen` is a `Column` with `verticalScroll` — without it the controls below the fold are cut off and cannot be reached at all. The test in `app/src/androidTest/.../SettingsAccessibilityTest.kt` scrolls to the last setting.
- **A label shares the row with its control**: the `Text` of a switch needs `Modifier.weight(1f)`, otherwise the switch is pushed out of the screen when the font is large. The same test checks that the switches stay inside the screen.
- Reordering is available through accessibility actions ("Move up"/"Move down") and the card announces its position via `stateDescription` ("Position 2 of 5").
- The undo snackbar uses `SnackbarDuration.Long` — a short snackbar cannot be reached with a screen reader.
- The entry dialog has a label (not only a placeholder) and is focused automatically.
- The capture field (not a dialog) is the primary action of the entries screen; the IME action "Done" saves it.

## Known gaps (and the plan)

- **The Keystore key is not bound to the user.** The app lock (`AppLockViewModel`) asks for the screen lock or a fingerprint before the journal opens, but the master key itself is only bound to the *unlocked device* (`setUnlockedDeviceRequired`). Anything that runs as this user id could still use the key. The plan: a second key that requires the user authentication (`setUserAuthenticationRequired(true)` together with `setUserAuthenticationParameters(0, AUTH_BIOMETRIC_STRONG or AUTH_DEVICE_CREDENTIAL)`) wraps the master key. The app lock unwraps it after the authentication and keeps it in memory for the session - the media path needs it for every decrypt, so it cannot be a per-use key. The catch: the wrapped key has to be unwrapped again after every restart of the app, so the database cannot be opened before the user unlocked it.
- **The EXIF metadata is only removed from JPEG and PNG.** A photo in HEIC (the format of many camera photos) keeps its location data. The photo picker hands out JPEG most of the time, but a photo that is picked from the gallery can be HEIC. Removing the metadata from the HEIF container is a bigger job than the JPEG and PNG cases, and a photo that cannot be rewritten is worth more than its coordinates.
- **The app lock does not bind to the key yet** (see above), and the 30 seconds until the app locks again is a constant, not a setting.

## Conventions

- Package layout per module mirrors the source root: `src/main/java/dev/schrammel/brilliantlist/<module>/…`.
- This codebase documents *why* in comments, especially around crash/race/security workarounds. Follow that style when adding code near the invariants above.
- `kotlin.code.style=official`; no custom formatter, so match surrounding style by hand.
