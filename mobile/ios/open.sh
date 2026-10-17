#!/bin/bash
set -euo pipefail

cd "$(dirname "$0")"

source ./team.sh "Apple Development"

echo "▸ Regenerating project (team $TEAM)…"
xcodegen generate

open LpmMobile.xcodeproj
