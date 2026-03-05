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
COMPILER_PLUGINS_FILE="app/build/kotlin-compiler-plugins.txt"
OUT_DIR="out"

mkdir -p "$OUT_DIR"

echo "Compiling $PLUGIN_SRC -> plugin-${PLUGIN_ID}.dex"

# android.jar contains the Android platform API (android.app.*, android.content.*, android.os.*, etc.)
# It is NOT a Maven dependency — it ships with the SDK platform.
ANDROID_JAR="${ANDROID_HOME}/platforms/android-36/android.jar"
if [ ! -f "$ANDROID_JAR" ]; then
    echo "Error: android.jar not found at $ANDROID_JAR" >&2
    exit 1
fi

# Build the classpath: android.jar + stubs (plugin interface) + Maven deps (Compose, OkHttp, etc.)
if [ -f "$CLASSPATH_FILE" ]; then
    RAW_CP="$ANDROID_JAR:$STUBS_JAR:$(cat "$CLASSPATH_FILE")"
else
    echo "Warning: $CLASSPATH_FILE not found — falling back to android.jar + stubs only"
    RAW_CP="$ANDROID_JAR:$STUBS_JAR"
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

# Build -Xplugin args for Kotlin compiler plugins (Compose compiler, etc.)
# Without the Compose compiler plugin, inline @Composable functions fail to compile.
PLUGIN_ARGS=""
if [ -f "$COMPILER_PLUGINS_FILE" ] && [ -s "$COMPILER_PLUGINS_FILE" ]; then
    IFS=':' read -ra PLUGIN_JARS <<< "$(cat "$COMPILER_PLUGINS_FILE")"
    for jar in "${PLUGIN_JARS[@]}"; do
        [ -f "$jar" ] && PLUGIN_ARGS="$PLUGIN_ARGS -Xplugin=$jar"
    done
fi

# Step 1: Kotlin source → JAR
# shellcheck disable=SC2086
kotlinc "$PLUGIN_SRC" \
    -cp "$COMPILE_CP" \
    $PLUGIN_ARGS \
    -d "$OUT_DIR/${PLUGIN_ID}.jar"

# Step 2: JAR → DEX using d8
D8_PATH="${ANDROID_HOME}/build-tools/$(ls "${ANDROID_HOME}/build-tools" | sort -V | tail -1)/d8"
"$D8_PATH" "$OUT_DIR/${PLUGIN_ID}.jar" --output "$OUT_DIR/${PLUGIN_ID}/"

# Step 3: Rename classes.dex → plugin-<id>.dex
mv "$OUT_DIR/${PLUGIN_ID}/classes.dex" "$OUT_DIR/plugin-${PLUGIN_ID}.dex"
rm -rf "$OUT_DIR/${PLUGIN_ID}" "$OUT_DIR/${PLUGIN_ID}.jar"

echo "Done: $OUT_DIR/plugin-${PLUGIN_ID}.dex"
