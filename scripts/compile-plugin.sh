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
CLASSPATH_FILE="app/build/plugin-classpath.txt"
OUT_DIR="out"

mkdir -p "$OUT_DIR"

echo "Compiling $PLUGIN_SRC -> plugin-${PLUGIN_ID}.dex"

# Build the classpath: stubs (plugin interface) + full app compile classpath (Android SDK, Compose, etc.)
if [ -f "$CLASSPATH_FILE" ]; then
    RAW_CP="$STUBS_JAR:$(cat "$CLASSPATH_FILE")"
else
    echo "Warning: $CLASSPATH_FILE not found — falling back to stubs only"
    RAW_CP="$STUBS_JAR"
fi

# kotlinc only accepts JARs, not AARs. Extract classes.jar from each .aar entry.
TEMP_DIR=$(mktemp -d)
trap 'rm -rf "$TEMP_DIR"' EXIT

COMPILE_CP=""
IFS=':' read -ra ENTRIES <<< "$RAW_CP"
for entry in "${ENTRIES[@]}"; do
    if [[ "$entry" == *.aar ]]; then
        JAR="$TEMP_DIR/$(basename "${entry%.aar}").jar"
        unzip -p "$entry" classes.jar > "$JAR" 2>/dev/null && COMPILE_CP="$COMPILE_CP:$JAR" || true
    elif [[ "$entry" == *.jar ]]; then
        COMPILE_CP="$COMPILE_CP:$entry"
    fi
done
COMPILE_CP="${COMPILE_CP#:}"  # strip leading colon

# Step 1: Kotlin source → JAR
kotlinc "$PLUGIN_SRC" \
    -cp "$COMPILE_CP" \
    -d "$OUT_DIR/${PLUGIN_ID}.jar"

# Step 2: JAR → DEX using d8
D8_PATH="${ANDROID_HOME}/build-tools/$(ls "${ANDROID_HOME}/build-tools" | sort -V | tail -1)/d8"
"$D8_PATH" "$OUT_DIR/${PLUGIN_ID}.jar" --output "$OUT_DIR/${PLUGIN_ID}/"

# Step 3: Rename classes.dex → plugin-<id>.dex
mv "$OUT_DIR/${PLUGIN_ID}/classes.dex" "$OUT_DIR/plugin-${PLUGIN_ID}.dex"
rm -rf "$OUT_DIR/${PLUGIN_ID}" "$OUT_DIR/${PLUGIN_ID}.jar"

echo "Done: $OUT_DIR/plugin-${PLUGIN_ID}.dex"
