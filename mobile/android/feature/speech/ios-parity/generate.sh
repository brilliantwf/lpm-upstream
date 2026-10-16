#!/bin/sh
# Regenerates src/test/resources/ios-parity/expected.jsonl: every document in
# cases.json read by the iOS SpeechText (mobile/ios/Sources/LpmMobile/SpeechText.swift
# with MarkdownText.swift's block parser) compiled for macOS. SpeechTextIosParityTest
# checks that the Android port speaks the same segments. Run it after changing either
# Swift file or the cases. Needs swiftc (Xcode).
set -eu
here=$(cd "$(dirname "$0")" && pwd)
ios=${IOS_SOURCES:-"$here/../../../../ios/Sources/LpmMobile"}
out="$here/../src/test/resources/ios-parity"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

cp "$ios/SpeechText.swift" "$work/"
# Only the block parser: the rest of MarkdownText.swift is SwiftUI for iOS.
sed -n '/^\/\/ MARK: - Blocks/,/^\/\/ MARK: - Inline spans/p' "$ios/MarkdownText.swift" \
    | sed '$d' > "$work/MarkdownBlock.swift"
cp "$here/Harness.swift" "$work/main.swift"
swiftc -O "$work"/*.swift -o "$work/speech-harness"

LANG=en_US.UTF-8 "$work/speech-harness" "$out/cases.json" > "$out/expected.jsonl"
echo "$(wc -l < "$out/expected.jsonl") iOS readings in $out/expected.jsonl"
