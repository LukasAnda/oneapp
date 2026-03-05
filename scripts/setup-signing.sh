#!/usr/bin/env bash
# Generates an Android signing keystore for OneApp and wires it into local.properties.
# Safe to re-run — skips key generation if oneapp.jks already exists.
#
# After running, copy the printed values into your GitHub repo secrets.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KEYSTORE="$REPO_ROOT/oneapp.jks"
ALIAS="oneapp"
LOCAL_PROPS="$REPO_ROOT/local.properties"

# ── Check dependencies ──────────────────────────────────────────────────────
if ! command -v keytool &>/dev/null; then
  echo "Error: keytool not found. Install a JDK (e.g. brew install openjdk)." >&2
  exit 1
fi

# ── Generate keystore if missing ────────────────────────────────────────────
if [ -f "$KEYSTORE" ]; then
  echo "Keystore already exists at $KEYSTORE — skipping generation."
  # Read passwords back from local.properties
  STORE_PASS=$(grep "^signing.keystore.password=" "$LOCAL_PROPS" 2>/dev/null | cut -d= -f2 || true)
  KEY_PASS=$(grep "^signing.key.password=" "$LOCAL_PROPS" 2>/dev/null | cut -d= -f2 || true)
  if [ -z "$STORE_PASS" ]; then
    echo "Error: oneapp.jks exists but signing passwords not found in local.properties." >&2
    echo "Delete oneapp.jks and re-run to start fresh." >&2
    exit 1
  fi
else
  echo "Generating Android signing key..."

  # Generate a random 32-char hex password
  STORE_PASS=$(python3 -c "import secrets; print(secrets.token_hex(16))")
  KEY_PASS=$(python3 -c "import secrets; print(secrets.token_hex(16))")

  keytool -genkey -v \
    -keystore "$KEYSTORE" \
    -alias "$ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -storepass "$STORE_PASS" \
    -keypass "$KEY_PASS" \
    -dname "CN=OneApp Owner, OU=OneApp, O=OneApp, L=Unknown, S=Unknown, C=US" \
    2>/dev/null

  echo "Keystore generated: $KEYSTORE"

  # Write signing config into local.properties
  # Preserve existing lines, add/update signing.* keys
  touch "$LOCAL_PROPS"

  # Remove any existing signing.* lines
  TMP=$(mktemp)
  grep -v "^signing\." "$LOCAL_PROPS" > "$TMP" || true
  mv "$TMP" "$LOCAL_PROPS"

  {
    echo "signing.keystore.path=$KEYSTORE"
    echo "signing.keystore.alias=$ALIAS"
    echo "signing.keystore.password=$STORE_PASS"
    echo "signing.key.password=$KEY_PASS"
  } >> "$LOCAL_PROPS"

  echo "Signing config written to local.properties."
fi

# ── Encode keystore for GitHub ──────────────────────────────────────────────
if [[ "$OSTYPE" == "darwin"* ]]; then
  B64=$(base64 -i "$KEYSTORE")
else
  B64=$(base64 -w 0 "$KEYSTORE")
fi

# ── Print GitHub Secrets ────────────────────────────────────────────────────
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  GitHub Secrets — add these at:"
echo "  your repo → Settings → Secrets and variables → Actions"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "  SIGNING_KEY_BASE64"
echo "$B64"
echo ""
echo "  SIGNING_KEY_ALIAS"
echo "$ALIAS"
echo ""
echo "  SIGNING_KEY_STORE_PASSWORD"
echo "$STORE_PASS"
echo ""
echo "  SIGNING_KEY_PASSWORD"
echo "$KEY_PASS"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Two more secrets to add manually:"
echo ""
echo "  ANTHROPIC_API_KEY      → https://console.anthropic.com"
echo "  GH_READ_TOKEN          → only needed for private repos"
echo "                           (fine-grained PAT, contents:read)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "Local builds (debug + release) are now signed with oneapp.jks."
echo "Keep oneapp.jks safe — it is gitignored and must not be committed."
