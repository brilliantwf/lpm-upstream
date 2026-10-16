#!/bin/sh
# Regenerates src/test/resources/ios-parity/{diffs,expected}.json: the diffs in
# cases.py, parsed and highlighted by the iOS ParsedDiff and DiffHighlighter
# (mobile/ios/Sources/LpmMobile/Diff*.swift) compiled for macOS. DiffParityTest then
# checks the Android port renders the same text in the same colors. Run it after
# changing the iOS diff code. Needs swiftc (Xcode) and python3.
set -eu
here=$(cd "$(dirname "$0")" && pwd)
ios=${IOS_SOURCES:-"$here/../../../../ios/Sources/LpmMobile"}
out="$here/../src/test/resources/ios-parity"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

cp "$ios/DiffHighlighter.swift" "$ios/DiffRendering.swift" "$work/"
python3 "$here/patch.py" "$work"
cp "$here/Stubs.swift" "$work/Stubs.swift"
cp "$here/Harness.swift" "$work/main.swift"
swiftc -O "$work"/*.swift -o "$work/diff-harness"

mkdir -p "$out"
python3 "$here/cases.py" > "$out/diffs.json"
"$work/diff-harness" "$out/diffs.json" > "$out/expected.json"
echo "$(grep -c '"name"' "$out/expected.json") iOS cases in $out/expected.json"
