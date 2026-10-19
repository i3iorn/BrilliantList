# AGENTS.md

## Project Overview

- **App:** "BrilliantList" — a privacy-first Android journaling/capture app.
- **Stack:** Kotlin, Jetpack Compose (UI + Glance widgets), Clean Architecture.
- **Persistence:** Room + SQLCipher (database at rest), Android Keystore (AES-GCM for media).
- **DI:** Dagger Hilt.
- **Async:** Kotlin Coroutines + Flow, WorkManager for scheduling (widget rotation).

## Architectural Boundaries

- **Multi-module structure:** Follow clean architecture strictly.
  - `core_model`: Pure Kotlin data classes (no Android dependencies).
  - `core_data`: Room, DAOs, Repositories, SQLCipher integration. Features must *not* depend on this directly for logic, only via `core_data` repositories.
  - `core_encryption`: Crypto primitives (Keystore helpers, AES-GCM wrappers). Do not implement crypto logic in feature modules.
  - `feature_*`: UI and feature-specific logic. Features should depend on `core_data` for data access and `core_model` for entities.
- **Data Flow:** UI (Compose) -> ViewModel -> Repository (core_data) -> DAO -> Room/SQLCipher.

## Security & Encryption (Critical)

- **NEVER store unencrypted data.** All database entries go through `SQLCipher` (`SupportFactory` in `AppDatabase`).
- **NEVER bypass `CryptoManager`.** File-based media (images/audio) must be encrypted using `CryptoManager` (AES/GCM).
- **Key Management:** Keys are derived from the Android Keystore. Do not store keys in `SharedPreferences` or code.
- **Schema:** See `ENCRYPTION_MEDIA_PIPELINE.md` in the root for the media encryption pipeline design (IV-prefix strategy).
- **Context Injection:** When injecting `android.content.Context` into Hilt-injected classes (e.g., Repositories, Workers), **always** use the `@ApplicationContext` qualifier from `dagger.hilt.android.qualifiers`. Bare `Context` will fail compilation with `Dagger/MissingBinding`.
- **SQLCipher init:** `AppDatabase.build()` must call `SQLiteDatabase.loadLibs(context)` before constructing `SupportFactory`, otherwise the first DB open crashes with `UnsatisfiedLinkError`.
- **Backups:** `android:allowBackup="false"` in the app manifest. Backing up the ciphertext DB without the (non-exportable) Keystore key makes it undecryptable after restore — and startup then crashes.

## Conventions & Gotchas

- **Database Migrations:** The database is encrypted. Be extremely careful with schema migrations in Room; verify decryption keys don't conflict with SQLCipher initialization.
- **Reactive Data:** Use Kotlin `Flow` for all database and settings access. `SettingsRepository` uses DataStore, not SharedPreferences.
- **Rotation Engine:** The widget rotation logic (`feature_widget` / `WidgetRotationWorker`) depends on `SettingsRepository` and `EntryRepository`. Do not poll the database directly from the widget; rely on WorkManager.
- **WorkManager init:** `MainApp` implements `Configuration.Provider` (Hilt worker factory). The default `WorkManagerInitializer` MUST be removed from the app manifest (via `tools:node="remove"` on the `androidx.startup` provider) — otherwise eager default initialization wins and Hilt worker injection fails.
- **Manifest permissions:** Any permission requested at runtime (`RECORD_AUDIO` for audio capture) must be declared in the app manifest, or `MediaRecorder` setup crashes with `SecurityException`.
- **DataStore writes:** Don't write settings on every UI event (e.g., slider drag steps). Use local Compose state and commit on `onValueChangeFinished` / settled values to avoid flooding disk I/O.
- **Glance widgets:** Data must be fetched before `provideContent {}` (its lambda isn't a coroutine scope). Widget text fallbacks must only use TEXT entries — image/audio entries have null `textContent`.

## Building & Testing

- **Gradle Wrapper:** Use `./gradlew` (or `gradlew.bat`).
- **Unit Tests:** `./gradlew :core_data:testDebugUnitTest` for database tests (requires Robolectric or JVM environment).
- **Instrumented Tests:** `./gradlew :core_data:connectedAndroidTest` for Room/SQLCipher device tests.
- **Lint:** `./gradlew lint`.
