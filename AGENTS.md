# AGENTS.md

## Project

BrilliantList — privacy-first Android journaling app. Kotlin, Compose, Room+SQLCipher, Hilt, Glance, WorkManager.
Package root: `dev.schrammel.brilliantlist`. Product rules in `README.md`, security posture in `SECURITY.md`, crypto/media design in `ENCRYPTION_MEDIA_PIPELINE.md`.

## Build & verify (Windows: `.\gradlew.bat`, POSIX: `./gradlew`)

```bash
./gradlew assembleDebug                    # compile everything (CI does this)
./gradlew :core_model:test                 # pure Kotlin unit tests (no device)
./gradlew :app:testDebugUnitTest
./gradlew :feature_entry:testDebugUnitTest
./gradlew :core_data:connectedDebugAndroidTest   # needs a device/emulator
python3 scripts/verify_privacy_invariants.py      # CI guard, run after assembleDebug
```

- CI (`.github/workflows/ci.yml`, JDK 21) runs `assembleDebug :core_model:test :core_data:testDebugUnitTest :feature_entry:testDebugUnitTest`, then builds the **signed** release APK and runs the privacy script. A second job runs the instrumented tests on an emulator.
- **Always run the full `assembleDebug` before pushing** when changes span feature modules: `:feature_entry:assembleDebug` only compiles `core_model`/`core_data`/`core_encryption` transitively and silently skips `:app`, `:feature_crisis`, `:feature_settings`, `:core_ui`, `:feature_widget`.
- The instrumented job needs three easy-to-forget things: **disk** (the emulator wants ~7 GB for the user data partition; the runner frees the NDK and old platforms first), a **screen lock** (the keystore cannot create a device-bound key without one, so the emulator gets a pin), and the **full system image** (the automated-test image has no keystore that can do it).
- `lint` needs network on first run (KMP JVM/desktop variants are not in a pristine cache). Failing offline is not a code problem.
- **Instrumented vs JVM**: anything touching `AndroidKeyStore`, SQLCipher, `BiometricPrompt` or `MediaPlayer` must stay in `src/androidTest` (device required). Pure logic belongs in `core_model` (plain JVM module) so it can be unit tested.
- Toolchain: Gradle 9.4.1, AGP 9.2.0, Kotlin 2.2.10, KSP 2.3.12, Hilt 2.60.1, compileSdk 36, JDK 21 (`jvmToolchain(21)`).

## Architecture & modules

`app` → `feature_entry` / `feature_widget` / `feature_crisis` / `feature_settings` → `core_data` → `core_encryption`; `core_model` is a plain JVM module; `core_ui` holds shared Compose theme + `EntryCard`.

- Every inter-module dependency is `implementation`, never `api` → types are not visible transitively. A module that touches `Entry`/`Settings` must declare its own `core_model` dependency; same for `core_data` repositories and Room types in `androidTest`.
- Features go through `EntryRepository`/`SettingsRepository`; never add a direct `core_encryption` dependency to a feature. Use `DatabaseKeyManager.isAvailable()` to detect a locked device.
- Icons: use material3 core icons; do NOT declare `material-icons-core` explicitly. Local vector drawables in `feature_entry/src/main/res/drawable/`.
- Strings: each module needs its own `res/values/strings.xml` (R classes are non-transitive). The app is fully covered in **English** (`values/`) and **Swedish** (`values-sv/`); add both when you add a string.

## Crash-avoidance invariants

- Manifest must declare `android:name=".MainApp"` (`@HiltAndroidApp`) or every `@AndroidEntryPoint` crashes.
- Use `@ApplicationContext` on any injected `Context`.
- `WorkManagerInitializer` is removed (`tools:node="remove"`); `MainApp` implements `Configuration.Provider`.
- Use `hiltViewModel()` in NavHost destinations; plain `viewModel()` crashes.
- Glance receivers cannot use `@AndroidEntryPoint`; use `@EntryPoint` + `EntryPointAccessors.fromApplication()`.
- Glance `provideContent {}` is not a coroutine scope; fetch data before calling it.
- `RECORD_AUDIO` must be in the manifest.

## Security & privacy invariants

- **No `INTERNET` permission.** The app is offline by design; `scripts/verify_privacy_invariants.py` fails the build if the merged manifest declares it. Also blocks debug-signed release builds and checks 16 KB page alignment of native libraries.
- **`warmUp` must never replace an existing key.** `CryptoManager.createKey` deletes and regenerates the key under an alias. Only `CryptoManager.getOrCreateKey` (create-if-absent) is safe on an alias that may already protect data — `warmUp()` runs at every app start and destroyed every installed journal in v0.3.0 until this was fixed. Never call `createKey` on `activeAlias()`.
- **The media key and the passphrase wrap key are separate.** Media has no per-file key version, so recreating the media key (`activeAlias()`) makes every photo/recording unreadable. `rotateKey`/`onAppLockDisabled` must use their own aliases and delete the old wrap key only when it is not also the media key.
- **Key rotation must be crash-safe**: write the new ciphertext under the stored alias *before* deleting the old key, and prefer a fresh (timestamped) alias over re-creating a key in place.
- **Keystore key requires an unlocked device** (`setUnlockedDeviceRequired(true)`). Check `DatabaseKeyManager.isAvailable()` before DB access; fail closed. A fresh install (no stored passphrase) counts as usable.
- **Auth keys (per-use) need the CryptoObject ceremony.** A wrap key created with `setUserAuthenticationRequired(true)` rejects every cipher operation unless the cipher was authenticated by a `BiometricPrompt.CryptoObject`. The flow: `DatabaseKeyManager.prepareUnlock()` → `BiometricPrompt.authenticate(promptInfo, CryptoObject(session.cipher))` → `completeUnlock(session)`. Calling `getOrCreatePassphrase()` directly on an auth-wrapped passphrase throws and permanently locks the user out (this shipped broken in v0.3.0).
- **`isAvailable()` must not probe per-use keys with a plain cipher init** — the probe fails for auth keys even when unlocked. Check device-lock state and key *existence* instead.
- **Key lost:** `DatabaseKeyManager.isKeyLost()` means entries cannot be decrypted (Keystore reset / key invalidated). Report via `KeyLostScreen` and offer `startOver`; never crash.
- **App lock:** `AppLockViewModel` gates the journal on the screen lock or fingerprint; the key manager follows the setting through the `AUTH_BOUND_PREF` mirror in `db_keys` (written by `SettingsRepository.setAppLockEnabled`), because the widget/worker path needs a synchronous check. Tests disable it (`AUTH_BOUND_PREF=false` or `DisableAppLockRule`).
- **FLAG_SECURE** is set in `MainActivity`; `adb screencap` returns black. Use `uiautomator dump` in tests.
- **No plaintext at rest.** Media AES-GCM encrypted to `filesDir/media/<uuid>.enc` (recordings `.pcm.enc`). EXIF stripped from JPEG, PNG **and HEIC/HEIF** (`ExifStripper` — iPhone default format). Recorder writes straight into the encrypted `MediaSink`; no temp file; stop on `ON_STOP`; 2-minute limit; sweep orphans with `EntryRepository.sweepOrphanedMedia()`.
- **Audio playback:** decrypted into RAM, served via `MediaDataSource` (`EncryptedAudioPlayer`); override `close()`. PCM gets a WAV header in memory (`Wav.wrap`).
- **Crypto format:** 12-byte IV prefix + ciphertext, 128-bit GCM tag. `setRandomizedEncryptionRequired(false)`; decrypt reads the IV in a loop.
- **SQLCipher passphrase:** 32 random bytes, wrapped and stored as ciphertext in SharedPreferences `db_keys`. `allowBackup="false"` required.
- **Export:** encrypted with a user-chosen passphrase (PBKDF2-HMAC-SHA256 + AES-GCM). Streamed, atomic import, passphrase typed twice, work factor stored in the header. Backups must survive device loss — never wrap them with a Keystore key.

## Data invariants

- **Threading:** `EntryRepository` moves disk work to the IO dispatcher. No encryption/ContentResolver/file listing on the main thread. Keystore warmed in background.
- **Migrations:** `AppDatabase` is versioned with explicit `Migration`s. `exportSchema=false`, destructive fallback. Add the migration AND a test in `core_data/src/androidTest/.../MigrationTest.kt`.
- **Fail closed on broken media:** `openDecryptedMedia` returns null; `MediaLoader` swallows decode errors.
- **State:** `app_state` table (`AppStateRepository`) for entry-referencing state. DataStore only for non-sensitive prefs (`settings`).
- **Reorder:** `EntryRepository.reorder(idsInOrder)` needs the full active list.
- **Soft delete:** `observeAll()` filters deleted; `getById()` inspects; `restore(entry)` undoes. Slider commits on `onValueChangeFinished`.
- **Sort:** `MANUAL` keeps dragged order; `NEWEST_FIRST` by creation date. Reorder disabled in non-MANUAL or favorites filter.
- **Crisis region:** only canonical `CrisisCountry` codes. `CrisisCatalog.normalize()` maps aliases. Unrecognized region shows a warning, never falls back to the generic list.
- **Helpline data:** `HelplineCatalog.kt` is generated by `scripts/import_helplines.py`; do not edit it by hand. Extra lines live in `CrisisCatalog.kt`. Every number needs a `verifiedAt` date; Swedish lines must be human-verified before release.

## Widget & rotation

- Widget shows TEXT entries only; tappable via `Intent.setClassName(context, context.packageName + ".MainActivity")`.
- User can hide content (`settings.widgetShowContent`).
- WorkManager min periodic interval is 15 min. `WidgetRotationScheduler.schedule(context)` on interval change; >15 min uses `scheduleNext`.
- Worker and widget check `DatabaseKeyManager.isAvailable()` first. Widget updates go through WorkManager, never `AlarmManager`.

## Accessibility

- Actionable elements need content descriptions; tests enforce descriptions, ≥48 dp touch targets (Material 3 `IconToggleButton`/`IconButton` default to 40 dp layout — set `Modifier.size(48.dp)` explicitly), and 2× font scale.
- Screens that can grow must scroll (`verticalScroll`). Label shares a row with its control via `Modifier.weight(1f)`.
- Reorder via accessibility actions ("Move up"/"Move down") + `stateDescription`. Undo snackbar uses `SnackbarDuration.Long`.
- Capture field is the primary action; IME "Done" saves.

## Release

- Signed APK distribution (not Play Store). The release key lives in `keystore/` (git-ignored) and the repo secrets (`RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`). **Losing it means the app can never be updated again.**
- Tagging (`git tag -a vX.Y.Z`) triggers `.github/workflows/release.yml`. `version.properties` is the single source of the version (`versionCode = major*10000 + minor*100 + patch`); the workflow refuses a tag/`versionName` mismatch. **Tag only after the CI run for that commit is green** — a tag points at a commit, and the release workflow builds exactly that code.
- The release workflow publishes the APK, its checksum and the R8 mapping; `verify_privacy_invariants.py` checks the signature is not the debug key.

## Conventions

- Package layout mirrors the source root: `src/main/java/dev/schrammel/brilliantlist/<module>/...`.
- Document *why* in comments, especially around crash/race/security workarounds.
- `kotlin.code.style=official`; match surrounding style by hand.
