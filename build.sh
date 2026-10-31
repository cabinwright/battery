#!/bin/sh
# Builds the cabinwright.battery NEMESIS package: the Battery app, on the Cabinwright SDK (sdk/,
# a submodule, or CABINWRIGHT_SDK). The store installs it from the index and writes its
# NEMESIS.data itself.
set -e
ROOT=$(cd "$(dirname "$0")" && pwd)
CABINWRIGHT_SDK=${CABINWRIGHT_SDK:-$ROOT/sdk}
STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT
cp -R "$ROOT/package/." "$STAGE/"
mkdir -p "$ROOT/dist"
CABINWRIGHT_MAP=$ROOT/dist/cabinwright.battery.map \
    sh "$CABINWRIGHT_SDK/api/tools/build-app.sh" --ui --broker "$ROOT/app" "$STAGE/battery.apk" >/dev/null
find "$STAGE" -name .DS_Store -delete
rm -f "$ROOT/dist/cabinwright.battery.zip"
(cd "$STAGE" && zip -qrX "$ROOT/dist/cabinwright.battery.zip" .)
echo "$ROOT/dist/cabinwright.battery.zip"
