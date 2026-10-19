# AGENTS.md

Privacy-first Android app (Kotlin, Compose, Hilt, Room+SQLCipher) for capturing small
"brilliant things" via text/image/audio, with rotation engine and Glance widget.

## Build

- `.\gradlew.bat <task>` — Gradle 9.4.1 wrapper, JVM toolchain **21** auto-provisioned via
  `gradle/gradle-daemon-jvm.properties` (foojay resolver), so no local JDK install is needed.
- AGP 9.2.0 uses **built-in Kotlin**; `org.jetbrains.kotlin.kapt` is incompatible and will fail
  with "not compatible with built-in Kotlin support". All annotation processing (Hilt, Room)
  uses **KSP** (`libs.plugins.ksp`, version 2.3.12 — the standalone KSP releases, not the
  `<kotlin>-<ksp>` paired ones).
- Hilt must be ≥ 2.57 (older versions fail on AGP 9 with `Android BaseExtension not found`);
  the catalog pins 2.60.1. Room must be ≥ 2.7 for KSP2 support (pinned 2.8.5); Room 2.6.1
  crashes with `unexpected jvm signature V`.
- Version catalog: `gradle/libs.versions.toml`. Catalog aliases are hyphenated but referenced in
  build scripts with **dots** (`libs.hilt.android`, not `libs.hilt-android` — hyphens parse as
  Kotlin subtraction and fail with "Unresolved reference 'minus'"). Plugins applied per-module by
  `alias(libs.plugins.…)`; bare `id("com.google.devtools.ksp")` does NOT work without a version.
- Room codegen in `core_data` runs via KSP; `AppDatabase` exposes `entryDao()`.

## Modules

Single Gradle project; `settings.gradle.kts` lists 9 modules:

- `:app` — entrypoint (`MainActivity` `@AndroidEntryPoint`, `MainApp` `@HiltAndroidApp`, nav host).
  minSdk **33**.
- `:core_model` — pure Kotlin/JVM (no Android); `Entry`, `EntryType`, `Settings`, `RotationMode`,
  `PublicEntry`. **Must stay annotation-free** — Room entities live in `core_data` instead.
- `:core_data` — Room DB (`AppDatabase.build(context, passphrase)` wrapped in SQLCipher
  `SupportFactory`). Contains `EntryEntity`, `EntryDao`, `EntryRepository`, `DatabaseKeyManager`
  (keeps SQLCipher passphrase encrypted via Keystore), `mapper/EntryMappers.kt`, and
  `di/DataModule.kt` (Hilt providers). `AppDatabaseTest` is an instrumented test.
- `:core_encryption` — `CryptoManager`: AES-GCM streaming encrypt/decrypt, 12-byte IV prepended
  to ciphertext, key from Android Keystore alias `brilliantlist_master_key`.
  `CryptoManagerTest` is instrumented (Keystore is unavailable to JVM tests).
- `:core_ui` — shared Compose components/theme (`EntryCard` accepts a `Modifier`).
- `:feature_entry` — capture/list screen; the only feature module with `@HiltViewModel` + KSP.
  Drag-and-drop reorder implemented with `detectDragGesturesAfterLongPress` in `EntryScreen`.
- `:feature_widget` — Glance appwidget. Glance receivers can't use `@AndroidEntryPoint`; they
  resolve dependencies via `@EntryPoint` + `EntryPointAccessors.fromApplication`. **Data must be
  fetched before `provideContent {}`** — its lambda is not a coroutine scope. Widget receiver is
  declared in `feature_widget/src/main/AndroidManifest.xml` with provider XML
  `res/xml/brilliant_list_widget_info.xml`.
- `:feature_crisis`, `:feature_settings` — standalone Compose screens.

Library modules are minSdk 23 while `:app` is 33 — don't "fix" the mismatch without deciding intent.
Java compatibility is VERSION_11 in libraries, VERSION_21 in `:app`.

## Commands

- Build all: `.\gradlew.bat build`
- Single module JVM test: `.\gradlew.bat :core_model:test` (only module with plain JUnit;
  `:core_encryption`/`:core_data` tests are instrumented/Android tests)
- Instrumented tests: `.\gradlew.bat connectedAndroidTest` (requires emulator/device; adb is at
  `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`)
- No lint/typecheck/CI config exists in the repo.

## Non-negotiable constraints (product principles)

- Offline-first, no network calls unless explicitly enabled; no analytics/tracking/ads.
- SQLCipher for DB, AES-GCM for media, Android Keystore keys, never export keys.
- No dark patterns, engagement loops, streaks/gamification, feeds, or mood dashboards.
- Capture must stay sub-2-second / no multi-step flows; every feature must justify its existence.
- Accessibility is required, not optional.
- Future sync (if added) must be end-to-end encrypted and zero-knowledge.
