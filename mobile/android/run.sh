#!/bin/bash
set -euo pipefail

APP_ID="cx.lpm.mobile"

cd "$(dirname "$0")"

ADB="$(command -v adb || echo "${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb")"
if ! "$ADB" get-state >/dev/null 2>&1; then
  echo "No Android device found. Plug one in with USB debugging on (or start an emulator)." >&2
  exit 1
fi

echo "▸ Building and installing debug build…"
./gradlew installDebug

echo "▸ Launching…"
"$ADB" shell am start -n "$APP_ID/.MainActivity"
