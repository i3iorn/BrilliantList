#!/usr/bin/env bash
# Checks that the release build starts.
#
# The release build is the one that is published, and it is shrunk with R8.
# A missing keep rule would ship an app that crashes on the first screen, and
# nothing else in the pipeline would notice: the build is compiled, signed
# and published, but nobody has ever seen it run.
#
# So the release APK is installed here and started. The debug APK has to go
# first - it is signed with another key, and Android does not install one
# build over the other.
set -euo pipefail

ADB="${ANDROID_HOME}/platform-tools/adb"
APK="app/build/outputs/apk/release/app-release.apk"
PACKAGE="dev.schrammel.brilliantlist"

if [ ! -f "$APK" ]; then
  echo "There is no release APK at $APK."
  exit 1
fi

echo "Installing the release build."
"$ADB" uninstall "$PACKAGE" > /dev/null 2>&1 || true
"$ADB" install -r "$APK"

# Only the crashes of this app are of interest.
"$ADB" logcat -c

echo "Starting the release build."
"$ADB" shell am start -W -n "$PACKAGE/.MainActivity"

# The app starts the Keystore in the background and shows the journal. Give
# it a moment, then look for a crash.
sleep 15

if "$ADB" logcat -d -s AndroidRuntime:E | grep -q "$PACKAGE"; then
  echo "The release build crashed:"
  "$ADB" logcat -d -s AndroidRuntime:E
  exit 1
fi

echo "The release build starts."
