# AGENTS.md

## Build & verify

```bash
./gradlew assembleDebug :core_model:test :core_data:testDebugUnitTest :feature_entry:testDebugUnitTest   # CI build command
./gradlew :feature_entry:assembleDebug   # faster, but only compiles core_model/core_data/core_encryption transitively
python3 scripts/verify_privacy_invariants.py   # run after assembleDebug
```

- CI (JDK 21) runs `assembleDebug :core_model:test :core_data:testDebugUnitTest :feature_entry:testDebugUnitTest`, builds the signed release APK and runs the privacy script. A second job runs instrumented tests on an emulator.
- Instrumented vs JVM: anything touching `AndroidKeyStore`, SQLCipher or `MediaPlayer` must stay in `src/androidTest` (device required). Pure logic belongs in `core_model` (plain JVM module).
- Lint needs network on first run (pulls KMP JVM/desktop artifacts). It fails offline on a pristine checkout — not a code problem.
- Toolchain: Gradle 9.4.1, AGP 9.2.0, Kotlin 2.2.10, KSP 2.3.12, Hilt 2.60.1, compileSdk 36, JDK 21. JDK auto-downloads via foojay.
- Room codegen runs through KSP (including `kspAndroidTest` in `core_data` for the migration test).

## Architecture & modules

| Module | Owns |
|---|---|
| `core_model` | Pure Kotlin data classes + enums, no Android imports. Crisis/helpline catalog lives here (JVM-testable). |
| `core_encryption` | `CryptoManager` — AndroidKeyStore AES-GCM, versioned key aliases. |
| `core_data` | Room (`AppDatabase`, SQLCipher), `DatabaseKeyManager`, `EntryRepository`, `SettingsRepository`, media pipeline, backups. |
| `core_ui` | Shared Compose theme/components. |
| `feature_entry` | Capture & edit screen. Largest feature module. |
| `feature_list` | Main journal list, favourites, trash. |
| `feature_crisis` | Crisis screen; pulls from `core_model` catalog. |
| `feature_settings` | Preferences (DataStore Preferences, key `settings`). |
| `feature_widget` | Glance widget + `WidgetRotationWorker`. |

- Every inter-module dependency is `implementation`, never `api` — types are not visible transitively; add the direct dependency you actually use.
- Hilt DI: modules in `core_data` (`di/DataModule`), app-level in `app/di`. ViewModels use `@HiltViewModel` + `hiltViewModel()`.
- Room schema exports to `core_data/schemas/`; bump `version` in `AppDatabase` and write a `Migration` when the entity changes.

## Security & privacy invariants

- **Never add network permissions.** The app is offline by design; `scripts/verify_privacy_invariants.py` enforces no `INTERNET`/`ACCESS_NETWORK_STATE` in the release manifest and blocks debug-signed release builds.
- **Native libs must run on 16 KB page-size devices** (Android 15+). The script checks ELF segment alignment. Do not swap in a native dependency without verifying 16 KB alignment.
- **Database is SQLCipher-encrypted.** Passphrase comes from `DatabaseKeyManager.getOrCreatePassphrase()`: random 32-byte value, encrypted by an AndroidKeyStore key (AES-GCM, IV prepended), ciphertext stored in SharedPreferences `db_keys` (never plaintext).
- **Keystore key is versioned** (`KEY_VERSION` in `CryptoManager`). Changing the spec must bump the version, rotate via `rotateKey`, and never break `KeyRotationTest`. Losing the key means the database is unrecoverable — there is no recovery path.
- **Keys are device-bound** (`setUnlockedDeviceRequired(true)`): the master key (media) is always. The passphrase wrap key is an auth key (`setUserAuthenticationRequired(true)`) only when the app lock is on — the choice follows the setting via the `AUTH_BOUND_PREF` mirror in `db_keys`, because the widget/worker path needs a synchronous check. A device without a screen lock never binds auth.
- **The passphrase wrap key and the media key are separate.** Never recreate the media key (alias `activeAlias()`); existing media has no per-file key version, so recreating it makes every photo/recording unreadable.
- **Media files (photos/audio) are individually encrypted** with the master key, stored as `media/<uuid>.enc`. EXIF metadata is stripped on ingest (`ExifStripper`); JPEG, PNG and HEIC/HEIF are covered — keep stripping for any new format.
- **Backups are user-passphrase encrypted** (`EntryRepository.exportTo/importFrom`), never by the Keystore key — they must survive a device change. Keep the backup format versioned and backward-compatible.
- **Flag Secure**: journal screens set `FLAG_SECURE` to prevent screenshots. New screens that show entry content must do the same.
- **App lock**: lock the journal behind a user-facing lock, never behind a simple timeout that unlocks on resume.

## Data-layer rules

- **Soft delete:** `observeAll()` filters deleted. Use `getById()` to inspect. `restore(entry)` undoes.
- **Settings:** DataStore Preferences (`settings`). Slider commits on `onValueChangeFinished`.
- **Sort:** `MANUAL` keeps dragged order; `NEWEST_FIRST` by creation date. Reorder disabled in non-MANUAL or favorites filter.
- **Crisis region:** only canonical `CrisisCountry` codes. `CrisisCatalog.normalize()` maps aliases. Unrecognized region must show the warning, never fallback to the generic list.
- **Helpline data:** `HelplineCatalog.kt` is generated by `scripts/import_helplines.py`; do not edit it by hand. Extra lines live in `CrisisCatalog.kt`. Every new number needs a `verifiedAt` date. Swedish lines are already in the catalog; verify their numbers before release.

## Widget & rotation

- Widget renders entries without launching the app; toggle content via `widgetShowContent`.
- `RotationMode.RANDOM` re-seeds on widget refresh and app start; `SEQUENTIAL` advances once per refresh.
- Widget updates go through `WorkManager` — do not switch to `AlarmManager` (battery, background limits).

## Accessibility

- The capture toolbar (`EntryToolbarAccessibilityTest`) enforces: content descriptions on all icon buttons, ≥ 48dp touch targets, and layout survival at 2× font scale. Strings are asserted literally — changing wording means updating the test.
- Entry dialog has a label and auto-focus.
- Capture field is primary action; IME "Done" saves.

## Conventions

- Package layout mirrors source root: `src/main/java/dev/schrammel/brilliantlist/<module>/...`.
- Document *why* in comments, especially crash/race/security workarounds.
- `kotlin.code.style=official`; match surrounding style.
