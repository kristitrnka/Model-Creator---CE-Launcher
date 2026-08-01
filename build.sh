#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
BUILD="$ROOT/build"
rm -rf "$BUILD"
mkdir -p "$BUILD/classes" "$BUILD/jar/payload"

javac -source 8 -target 8 -encoding UTF-8 -d "$BUILD/classes" "$ROOT/src/ce/launcher/Main.java"
cp -R "$BUILD/classes"/* "$BUILD/jar/"
cp "$ROOT/MANIFEST.MF" "$BUILD/jar/META-INF.MF"

if [ -f "$ROOT/payload/editor.jar" ]; then
  cp "$ROOT/payload/editor.jar" "$BUILD/jar/payload/editor.jar"
else
  echo "Missing payload/editor.jar" >&2
  exit 1
fi

jar cfm "$ROOT/Model-Creator-CE-Launcher-1.3.6.jar" "$ROOT/MANIFEST.MF" -C "$BUILD/jar" .
echo "Built: $ROOT/Model-Creator-CE-Launcher-1.3.6.jar"
