# BrilliantList

*A gentle, private space to collect and revisit the small, brilliant things in life.*

## Download
**[Download the latest APK](https://github.com/i3iorn/BrilliantList/releases/latest/download/app-release.apk)** — open it on your phone and Android asks once for the permission to install apps. That is the one step a sideloaded app needs.

Every release is signed with the same key, so a new APK installs over the one you have and keeps your journal. [Obtainium](https://github.com/Obtainium/Obtainium) can install and update it for you if you would rather not download anything by hand — add this repository and give it the signing fingerprint below.

The app is not in the Play Store, and it will not need one: it has no internet permission at all, no analytics and no ads, and it never sends anything anywhere.

## What is BrilliantList?
BrilliantList is a minimal, privacy-first Android app designed to help you notice and remember moments of happiness, joy, and meaning. Whether it's a kind word, a beautiful scene, or a fleeting thought, BrilliantList makes it easy to capture and revisit what matters—especially for those who need a little more light in their day.

## Who is it for?
BrilliantList is for anyone who wants to see more happiness in their life. It is especially mindful of people who may be struggling with depression or low mood, offering a safe, emotionally gentle space to reflect on positive moments.

## Inspiration
BrilliantList is inspired by **[Every Brilliant Thing](https://everybrilliantthing.com/)**, the play by Duncan Macmillan (with Jonny Donahoe, directed by Jeremy Herrin and Duncan Macmillan). The show follows one person looking back on their life through a list of every wonderful, beautiful, and delightful thing—big, small, and everything in between—that makes life worth living. The project behind it also collects brilliant things together with [Project Healthy Minds](https://app.projecthealthyminds.com/partnerships/every-brilliant-thing), and that idea of keeping a list of the good things is what this app is built on.

BrilliantList is an independent, unofficial app. It is not affiliated with, endorsed by, or connected to the play, its writers, its producers, or the brilliant things list on that site. It is simply an attempt to put the idea of collecting brilliant things in your pocket, in a way that stays on your device.

## Key Features
- **Effortless Capture:** Write a note in one field, add a photo, or record a voice note—no multi-step flows.
- **Search and Favorites:** Find what you wrote, keep the best of it.
- **Recently Deleted:** A mistake is one tap away from being undone.
- **List Management:** View, reorder (drag & drop), and favorite your entries.
- **Rotation Engine:** Surface meaningful moments at configurable intervals, randomly or sequentially, with no repetition caching.
- **Minimal Widget:** See a single entry on your home screen—or nothing at all, if you prefer. Tap to open the app.
- **Crisis Page:** Offline, region-aware resources for when you need extra support, with the right action for every helpline: a call, a text, or just information.
- **Your Own Backup:** Export the journal as an encrypted file with a passphrase you choose, and restore it on another device.
- **Private by Default:** No ads, no analytics, no tracking, and no network calls at all—the app cannot even reach the internet.
- **Offline-First:** Works fully without internet access.
- **Accessibility:** Designed to be usable by everyone, also with a large font.

## Core Principles
- Radical simplicity—every feature must justify its existence
- Emotional safety and warmth
- No dark patterns, engagement loops, or gamification
- Strong privacy and security practices (see [SECURITY.md](SECURITY.md))

## Install from a release
1. Download the APK from the [releases](https://github.com/i3iorn/BrilliantList/releases/latest) — or use the direct link at the top of this page.
2. Open it on your phone. Android asks once for the permission to install apps from this source; that is the only step a sideloaded app needs.
3. The app opens the entries screen. That is it.

To update, download the newer APK and open it: Android replaces the installed app and keeps the journal.

The signing fingerprint of every release, so an installer can check that the app really is this one:

```
SHA-256: ba29f3639ed9f7ba5408babe422f16f2fd8ae0f0cf960a444730520070536f00
```

## Build from the source
1. Clone the repository:
   ```
   git clone https://github.com/i3iorn/BrilliantList.git
   ```
2. Open in Android Studio.
3. Build and run on your device or emulator.

A release build needs the key of the app. It is not in the repository — create it once with `python3 scripts/create_release_key.py` and put the values into the secrets of the repository (`RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`). The CI signs the release with them.

**Tech Stack:** Kotlin, Jetpack Compose, Room (with SQLCipher), WorkManager, Glance widgets, Hilt DI, Clean Architecture.

## The pipeline
- **CI** (`.github/workflows/ci.yml`): every push builds the app, runs the unit tests, builds the signed release APK and checks the privacy invariants. The instrumented tests run on a managed device, so the accessibility checks, the key rotation and the backup round trip are part of the build.
- **Release** (`.github/workflows/release.yml`): a tag (`v0.1.0`) publishes the signed APK, its checksum and the R8 mapping as a GitHub release.

## Contributing
Contributions are welcome! There are no formal guidelines yet—if you have ideas or improvements, feel free to open an issue or pull request.

## Security
BrilliantList is built with strong privacy and security practices in mind. For more details, see [SECURITY.md](SECURITY.md). No security guarantees are made.

## License
[Apache License 2.0](LICENSE) — you may use, change and redistribute it, under the conditions of the licence.
