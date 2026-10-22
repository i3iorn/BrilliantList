#!/usr/bin/env bash
# Runs the instrumented tests on the emulator of the runner.
set -euo pipefail

ADB="${ANDROID_HOME}/platform-tools/adb"

# The key of the app is bound to the unlocked device: the keystore can only
# create such a key when the device has a screen lock. The emulator of the
# runner has none, so one is set here.
#
# The app lock of the app is switched off by the tests themselves, so the
# screen lock does not get in the way of them.
echo "Setting a screen lock on the emulator."
"$ADB" shell locksettings set-pin 1234 || true
# Setting the pin may leave the keyguard in front of the screen.
"$ADB" shell input keyevent 82 || true
"$ADB" shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER || true

./gradlew \
  :app:connectedDebugAndroidTest \
  :core_data:connectedDebugAndroidTest \
  :core_encryption:connectedDebugAndroidTest \
  :feature_entry:connectedDebugAndroidTest \
  --stacktrace

# The release build is the one that is published. It is shrunk with R8, so it
# has to start somewhere - nothing else in the pipeline would notice a
# missing keep rule.
.github/check-release-build.sh
