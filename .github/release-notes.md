BrilliantList is a private journal: everything stays on the device, encrypted.

**Install**

1. Download `app-release.apk` from the assets below.
2. Open it. Android asks once for the permission to install apps from this
   source - that is the one step a sideloaded app needs, there is no way
   around it.
3. The app opens the entries screen. That is it.

**Updates**

Download the newer `app-release.apk` and open it: Android replaces the
installed app and keeps the journal. Every release is signed with the same
key, so an update always installs over the previous one.

[Obtainium](https://github.com/Obtainium/Obtainium) can do that for you:
add this repository, and it installs and updates the app on its own. Give it
the signing fingerprint below, so it can check that the app it installs is
the one from this project.

**The signing key**

```
SHA-256: ba29f3639ed9f7ba5408babe422f16f2fd8ae0f0cf960a444730520070536f00
```

The key is the same for every release. If it is ever lost, the app can no
longer be updated over an installed one - everyone has to uninstall it
first, and the journal goes with it.

**Privacy**

The app has no internet permission at all: it cannot send anything
anywhere. The journal is encrypted with a key in the keystore of the
device, and the photos lose their location data on the way in.
