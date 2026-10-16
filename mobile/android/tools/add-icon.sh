#!/bin/bash
# Adds a Material Symbols Rounded icon to :core:designsystem as
# res/drawable/ic_<name>[_fill].xml. Map SF Symbols to names in
# core/designsystem/ICONS.md; browse names at https://fonts.google.com/icons.
#
#   tools/add-icon.sh chevron_right
#   tools/add-icon.sh play_circle fill
set -euo pipefail

name="${1:?usage: tools/add-icon.sh <material-symbol-name> [fill]}"
variant="${2:-}"
case "$variant" in
  "") file="${name}_24px.xml"; suffix="" ;;
  fill) file="${name}_fill1_24px.xml"; suffix="_fill" ;;
  *) echo "usage: tools/add-icon.sh <material-symbol-name> [fill]" >&2; exit 2 ;;
esac

cd "$(dirname "$0")/.."
url="https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/$name/materialsymbolsrounded/$file"
out="core/designsystem/src/main/res/drawable/ic_${name}${suffix}.xml"

tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT
if ! curl -fsSL "$url" -o "$tmp"; then
  echo "No Material Symbol '$name'${suffix:+ (filled)} at $url" >&2
  exit 1
fi
grep -q '<vector' "$tmp" || { echo "Not a vector drawable: $url" >&2; exit 1; }

# The tint names an AppCompat attribute this module doesn't have; Compose's Icon tints.
# Path coordinates are rounded to whole units of the 960-unit viewport (1/40 dp at
# 24 dp), which keeps long paths under lint's VectorPath limit with no visible change.
perl -0pe '
  s/\s*android:tint="[^"]*"//g;
  s{(android:pathData=")([^"]*)}{my ($attr, $path) = ($1, $2); $path =~ s/(\d+\.\d+)/sprintf("%.0f", $1)/ge; $attr . $path}ge;
' "$tmp" > "$out"

length=$(perl -ne 'while (/pathData="([^"]*)"/g) { $m = length($1) if length($1) > $m } END { print $m + 0 }' "$out")
echo "Added $out (longest path: $length characters)"
if [ "$length" -gt 2000 ]; then
  echo "Warning: lint's VectorPath check may reject a path this long; prefer a simpler symbol." >&2
fi
