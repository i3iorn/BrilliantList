# AGENTS.md

## Project

BrilliantList — privacy-first Android journaling app (Kotlin, Compose, Room+SQLCipher, Hilt, Glance, WorkManager).
Package root: `dev.schrammel.brilliantlist`. Product rules in `README.md`, security posture in `SECURITY.md`, crypto/media design in `ENCRYPTION_MEDIA_PIPELINE.md`.

## Build & verify

Windows: `.\gradlew.bat`, POSIX: `./gradlew`.

```bash
./gradlew assembleDebug                    # compile everything (CI does this)
./gradlew :app:assembleDebug               # debug APK
./gradlew :core_model:test                 # pure JVM unit tests
./gradlew :app:testDebugUnitTest
./gradlew :core_data:connectedDebugAndroidTest # needs device/emulator
python3 scripts/verify_privacy_invariants.py   # run after assembleDebug
```

- CI (JDK 21) runs `assembleDebug :core_model:test :core_data:testDebugUnitTest :feature_entry:testDebugUnitTest`, builds the signed release APK and runs the privacy script. A second job runs instrumented tests on an emulator. The emulator needs a screen lock (the Keystore key is bound to the unlocked device), the full system image (the AOSP image cannot create the key) and about 7 GB of disk; the runner frees the NDK and old platforms first.
- Instrumented vs JVM: anything touching `AndroidKeyStore`, SQLCipher or `MediaPlayer` must stay in `src/androidTest` (device required). Pure logic belongs in `core_model` (plain JVM module).
- Lint needs network on first run (pulls KMP JVM/desktop artifacts). It fails offline on a pristine checkout — not a code problem.
- Toolchain: Gradle 9.4.1, AGP 9.2.0, Kotlin 2.2.10, KSP 2.3.12, Hilt 2.60.1, compileSdk 36, JDK 21. JDK auto-downloads via foojay.
- minSdk is 33 in `:app` but 23 in every library module — don't "fix" the mismatch.
- Room codegen runs through KSP (including `kspAndroidTest` in `core_data` for the migration test).

## Architecture & modules

`app` → `feature_entry` / `feature_widget` / `feature_crisis` / `feature_settings` → `core_data` → `core_encryption`; `core_model` is a plain JVM module; `core_ui` holds shared Compose theme + `EntryCard`.

- Every inter-module dependency is `implementation`, never `api` → types are not visible transitively. A module that touches `Entry`/`Settings` must declare its own `core_model` dependency; same for `core_data` repositories and Room types in `androidTest`.
- Features go through `EntryRepository`/`SettingsRepository`; never add a direct `core_encryption` dependency to a feature. Use `DatabaseKeyManager.isAvailable()` to detect a locked device.
- Icons: use material3 core icons. Do NOT declare `material-icons-core` explicitly. Local vector drawables in `feature_entry/src/main/res/drawable/`.
- Strings: each module needs its own `res/values/strings.xml` (R classes are non-transitive).

## Crash-avoidance invariants

- Manifest must declare `android:name=".MainApp"` (`@HiltAndroidApp`) or every `@AndroidEntryPoint` crashes.
- Use `@ApplicationContext` on any injected `Context`.
- Keep `WorkManagerInitializer` removed (`tools:node="remove"`); `MainApp` implements `Configuration.Provider`.
- Use `hiltViewModel()` in NavHost destinations; plain `viewModel()` crashes.
- Glance receivers cannot use `@AndroidEntryPoint`; use `@EntryPoint` + `EntryPointAccessors.fromApplication()`.
- Glance `provideContent {}` lambda is not a coroutine scope; fetch the entry before calling.
- `RECORD_AUDIO` must be in the manifest.
- Recorder writes into encrypted `MediaSink`; no temp file. `MediaSink.close()` writes the GCM tag. Stop on `ON_STOP` (no background mic). Limit 2 minutes. Sweep orphans with `EntryRepository.sweepOrphanedMedia()`.

## Security & privacy invariants

- **No `INTERNET` permission.** The verify script fails if the merged manifest declares it.
- **16 KB page alignment:** native libs must be aligned. SQLCipher >= 4.6.0 (currently 4.19.1).
- **Keystore key:** requires unlocked device. Check `DatabaseKeyManager.isAvailable()` before DB access. Fail closed. A fresh install (no key) counts as usable.
- **Key rotation:** `DatabaseKeyManager.rotateKeyToNewAlias()` replaces the key without changing the passphrase. Write the new passphrase before deleting the old key.
- **Key lost:** `DatabaseKeyManager.isKeyLost()` means entries cannot be decrypted. Report, don't crash.
- **App lock:** `AppLockViewModel` (screen lock/fingerprint). Tests use `DisableAppLockRule`.
- **FLAG_SECURE** is set; `adb screencap` is black. Use `uiautomator dump`.
- **No plaintext at rest.** Media AES-GCM encrypted to `filesDir/media/<uuid>.enc` (recordings `.pcm.enc`). EXIF stripped from JPEG/PNG.
- **Audio playback:** decrypted into RAM, served via `MediaDataSource` (`EncryptedAudioPlayer`). Override `MediaDataSource.close()`. PCM gets a WAV header in memory (`Wav.wrap`).
- **Crypto format:** 12-byte IV prefix + ciphertext, 128-bit GCM tag. `setRandomizedEncryptionRequired(false)`. Decrypt reads the IV in a loop.
- **Keystore key not persisted.** SQLCipher passphrase 32 random bytes as ciphertext in SharedPreferences `db_keys`.
- **`allowBackup="false"`** required.
- **Export:** encrypted with user passphrase (PBKDF2-HMAC-SHA256 600k iterations + AES-GCM). Streamed, atomic import. Passphrase typed twice. Work factor stored in header.
- **Release signature:** the shipping APK must NOT be signed with the debug key. The Gradle build fails in CI without a release key; the verify script checks the signature.

## Data invariants

- **Threading:** `EntryRepository` moves disk work to the IO dispatcher. No encryption/ContentResolver/file listing on the main thread. Keystore warmed up in background.
- **Migrations:** `AppDatabase` version 2 with `MIGRATION_1_2`. `exportSchema=false`, destructive fallback. Add the migration AND a test in `core_data/src/androidTest/.../MigrationTest.kt`.
- **Fail closed on broken media:** `openDecryptedMedia` returns null; `MediaLoader` swallows decode errors.
- **State:** `app_state` table (`AppStateRepository`) for entry-referencing state. DataStore only for non-sensitive prefs.
- **Reorder:** `EntryRepository.reorder(idsInOrder)` needs the full active list.
- **Soft delete:** `observeAll()` filters deleted. Use `getById()` to inspect. `restore(entry)` undoes.
- **Settings:** DataStore Preferences (`settings`). Slider commits on `onValueChangeFinished`.
- **Sort:** `MANUAL` keeps dragged order; `NEWEST_FIRST` by creation date. Reorder disabled in non-MANUAL or favorites filter.
- **Crisis region:** only canonical `CrisisCountry` codes. `CrisisCatalog.normalize()` maps aliases. Unrecognized region must show the warning, never fallback to the generic list.
- **Helpline data:** `HelplineCatalog.kt` is generated by `scripts/import_helplines.py`; do not edit it by hand. Extra lines live in `CrisisCatalog.kt`. Every new number needs a `verifiedAt` date. Swedish lines are already in the catalog; verify their numbers before release.

## Widget & rotation

- Widget shows TEXT entries only. Tappable via `Intent.setClassName(context, context.packageName + ".MainActivity")`.
- User can hide text (`settings.widgetShowContent`).
- WorkManager min periodic interval is 15 mins. `WidgetRotationScheduler.schedule(context)` called on interval change. Interval >15 mins uses `scheduleNext`.
- Worker and widget check `DatabaseKeyManager.isAvailable()` first.

## Accessibility

- Actionable elements need content descriptions. Tests enforce descriptions, 48 dp touch targets, 2x font scale.
- Screens must scroll if they can grow. `SettingsScreen` uses `verticalScroll`.
- Label shares row with control: use `Modifier.weight(1f)`.
- Reordering via accessibility actions ("Move up"/"Move down") and `stateDescription`.
- Undo snackbar uses `SnackbarDuration.Long`.
- Entry dialog has a label and auto-focus.
- Capture field is primary action; IME "Done" saves.

## Localization

- The app is fully covered in English (`values/strings.xml`) and Swedish (`values-sv/strings.xml`) in every module with user-facing text. Add both when you add a string.
- The crisis page carries the local name of each line and its language codes. A new helpline needs English and Swedish text, a `verifiedAt` date, and the local name where one exists.
- The crisis wording is reviewed by a human, not machine-translated. If a string is missing from `values-sv`, the Swedish screen falls back to English — acceptable for internal text, not for crisis copy.

## Release

- The app is distributed as a signed APK, not through the Play Store. The release key is in `keystore/` (ignored by git) and in the repository secrets (`RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`). Losing it means the app can never be updated again.
- A tag (`git tag -a v0.3.0 ...`) publishes a release via `.github/workflows/release.yml`. The version in `version.properties` must match the tag; the workflow refuses a mismatch.
- The shipping APK must not be signed with the debug key. `scripts/verify_privacy_invariants.py` checks the signature.
- The release workflow builds the signed APK, runs the privacy script, and publishes the APK, its checksum and the R8 mapping.

## Conventions

- Package layout mirrors source root: `src/main/java/dev/schrammel/brilliantlist/<module>/...`.
- Document *why* in comments, especially crash/race/security workarounds.
- `kotlin.code.style=official`; match surrounding style.
