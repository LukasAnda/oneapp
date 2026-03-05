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

# Find the Compose compiler plugin (non-embeddable, compatible with standalone kotlinc).
# The -embeddable variant uses shaded IntelliJ class names not present in standalone kotlin-compiler.jar.
# Priority: 1) COMPOSE_COMPILER_PLUGIN env var (set by CI), 2) kotlinc/lib/ bundled, 3) none.
COMPOSE_PLUGIN="${COMPOSE_COMPILER_PLUGIN:-}"
if [ -z "$COMPOSE_PLUGIN" ] || [ ! -f "$COMPOSE_PLUGIN" ]; then
    KOTLINC_LIB="$(dirname "$(command -v kotlinc)")/../lib"
    if [ -f "$KOTLINC_LIB/compose-compiler-plugin.jar" ]; then
        COMPOSE_PLUGIN="$KOTLINC_LIB/compose-compiler-plugin.jar"
    fi
fi

PLUGIN_ARGS=""
if [ -n "$COMPOSE_PLUGIN" ] && [ -f "$COMPOSE_PLUGIN" ]; then
    echo "Using Compose compiler plugin: $COMPOSE_PLUGIN"
    PLUGIN_ARGS="-Xplugin=$COMPOSE_PLUGIN"
else
    echo "Warning: Compose compiler plugin not found — @Composable inline functions may fail" >&2
fi

# Step 1: Kotlin source → JAR
# shellcheck disable=SC2086
kotlinc "$PLUGIN_SRC" \
    -cp "$COMPILE_CP" \
    $PLUGIN_ARGS \
    -d "$OUT_DIR/${PLUGIN_ID}.jar"

# Step 2: JAR → DEX using d8
D8_PATH="${ANDROID_HOME}/build-tools/$(ls "${ANDROID_HOME}/build-tools" | sort -V | tail -1)/d8"
mkdir -p "$OUT_DIR/${PLUGIN_ID}"
"$D8_PATH" "$OUT_DIR/${PLUGIN_ID}.jar" --output "$OUT_DIR/${PLUGIN_ID}/"

# Step 3: Rename classes.dex → plugin-<id>.dex
mv "$OUT_DIR/${PLUGIN_ID}/classes.dex" "$OUT_DIR/plugin-${PLUGIN_ID}.dex"
rm -rf "$OUT_DIR/${PLUGIN_ID}" "$OUT_DIR/${PLUGIN_ID}.jar"

echo "Done: $OUT_DIR/plugin-${PLUGIN_ID}.dex"
