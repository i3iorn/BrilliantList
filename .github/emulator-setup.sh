#!/usr/bin/env bash
# Runs before the emulator is started.
#
# The system image of the runner asks for more space for the user data
# partition than the runner has, so the emulator refuses to start:
#
#   FATAL | Not enough space to create userdata partition.
#   Available: 6619.82 MB, need 7372.80 MB.
#
# The journal of the app needs a few megabytes, not six gigabytes, so the
# partition is made smaller. The last value in the file wins, so it does not
# matter whether the image already has one.
set -euo pipefail

AVD_CONFIG="${HOME}/.android/avd/${AVD_NAME:-test}.avd/config.ini"

if [ -f "$AVD_CONFIG" ]; then
  echo "Making the user data partition of the emulator smaller."
  echo "disk.dataPartition.size=2048" >> "$AVD_CONFIG"
  grep "disk.dataPartition.size" "$AVD_CONFIG" || true
else
  echo "No emulator configuration at $AVD_CONFIG - nothing to do."
fi
