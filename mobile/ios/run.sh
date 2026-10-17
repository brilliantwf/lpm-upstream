#!/bin/bash
set -euo pipefail

BUNDLE_ID="cx.lpm.mobile"
SCHEME="LpmMobile"
CONFIG="Debug"
DERIVED="build/DerivedData"

cd "$(dirname "$0")"

source ./team.sh "Apple Development"

# First connected physical device (its UDID), else a paired one devicectl can
# reach over the network, unless overridden. The `|| true` guards matter: grep
# exits 1 on no match, and under `set -euo pipefail` that would kill the script
# right here, before the friendly message below ever prints.
UDID_RE='[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}'
DEVICES="$(xcrun devicectl list devices 2>/dev/null || true)"
DEVICE_ID="${DEVICE_ID:-$(printf '%s\n' "$DEVICES" \
  | grep -i connected | grep -oiE "$UDID_RE" | head -1 || true)}"
if [ -z "$DEVICE_ID" ]; then
  DEVICE_ID=$(printf '%s\n' "$DEVICES" \
    | grep -i 'available' | grep -oiE "$UDID_RE" | head -1 || true)
fi
if [ -z "$DEVICE_ID" ]; then
  echo "No iPhone found. Plug one in (or pair it over Wi-Fi), unlock it, and trust this Mac." >&2
  exit 1
fi

echo "▸ Regenerating project (picks up new sources)…"
xcodegen generate

echo "▸ Building $SCHEME for device $DEVICE_ID (team $TEAM)…"
xcodebuild \
  -project LpmMobile.xcodeproj \
  -scheme "$SCHEME" \
  -configuration "$CONFIG" \
  -destination "id=$DEVICE_ID" \
  -derivedDataPath "$DERIVED" \
  -allowProvisioningUpdates \
  DEVELOPMENT_TEAM="$TEAM" \
  build

APP_PATH="$DERIVED/Build/Products/$CONFIG-iphoneos/$SCHEME.app"

echo "▸ Installing to device…"
xcrun devicectl device install app --device "$DEVICE_ID" "$APP_PATH"

echo "▸ Launching…"
xcrun devicectl device process launch --device "$DEVICE_ID" "$BUNDLE_ID"
