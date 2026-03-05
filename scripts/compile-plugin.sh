#!/usr/bin/env bash
# Compiles a single Kotlin plugin source file to a DEX file.
# Usage: ./scripts/compile-plugin.sh plugins/notes.kt com.user.notes
# Output: out/plugin-com.user.notes.dex
#
# Requires: kotlinc on PATH, d8 (from ANDROID_HOME/build-tools), build-stubs.jar already built
set -euo pipefail

PLUGIN_SRC="${1:?Usage: compile-plugin.sh <source.kt> <plugin-id>}"
PLUGIN_ID="${2:?Usage: compile-plugin.sh <source.kt> <plugin-id>}"
STUBS_JAR="build-stubs/build/libs/build-stubs.jar"
OUT_DIR="out"

mkdir -p "$OUT_DIR"

echo "Compiling $PLUGIN_SRC -> plugin-${PLUGIN_ID}.dex"

# Step 1: Kotlin source → JAR
kotlinc "$PLUGIN_SRC" \
    -cp "$STUBS_JAR" \
    -d "$OUT_DIR/${PLUGIN_ID}.jar"

# Step 2: JAR → DEX using d8
D8_PATH="${ANDROID_HOME}/build-tools/$(ls "${ANDROID_HOME}/build-tools" | sort -V | tail -1)/d8"
"$D8_PATH" "$OUT_DIR/${PLUGIN_ID}.jar" --output "$OUT_DIR/${PLUGIN_ID}/"

# Step 3: Rename classes.dex → plugin-<id>.dex
mv "$OUT_DIR/${PLUGIN_ID}/classes.dex" "$OUT_DIR/plugin-${PLUGIN_ID}.dex"
rm -rf "$OUT_DIR/${PLUGIN_ID}" "$OUT_DIR/${PLUGIN_ID}.jar"

echo "Done: $OUT_DIR/plugin-${PLUGIN_ID}.dex"
