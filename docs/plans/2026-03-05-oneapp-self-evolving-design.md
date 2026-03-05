# OneApp — Self-Evolving Android Shell: Design Document

**Date:** 2026-03-05
**Status:** Approved

---

## Vision

A self-evolving Android shell app where each person forks the repository and owns their own instance. The app ships as an empty container. Functionality is requested via GitHub issues, built by an AI agent, and delivered as hot-loaded DEX plugins — no Play Store, no manual coding required from the user. Plugins developed by anyone can later be shared or sold through a decentralized registry.

---

## Core Concepts

### The Fork Model
- User forks this repo (public or private)
- GitHub Actions bakes their GitHub identity (`GITHUB_REPOSITORY`, `GH_READ_TOKEN`) into the APK at build time via `BuildConfig`
- User sideloads the APK once — no further manual installs unless the core changes
- From that point, everything is driven through GitHub issues

### The Evolution Loop
1. User files a GitHub issue describing what they want
2. User labels the issue `evolve`
3. GitHub Actions triggers the evolution workflow
4. Claude agent reads `MANIFEST.md` (fast path), writes/modifies plugin Kotlin source
5. Plugin is compiled to `.dex`, published to GitHub Releases
6. Agent updates `MANIFEST.md` and appends to `JOURNAL.md`
7. Agent comments on the issue confirming what was built and any permissions requested
8. App downloads the new DEX on next launch and loads it

### Revert Strategy
Every GitHub Release is preserved. A `stable` tag marks the last known-good release. Rolling back = retagging a previous release as `stable`. The app's `UpdateChecker` only downloads releases tagged `stable`.

---

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                  GITHUB REPO (fork)                  │
│                                                      │
│  MANIFEST.md        ← agent's working memory        │
│  JOURNAL.md         ← append-only evolution history  │
│  IDENTITY.md        ← immutable: who this app is    │
│  core/              ← stable Android shell source    │
│  plugins/           ← AI-generated plugin sources    │
│  .github/workflows/ ← evolve.yml, build.yml          │
└────────────┬────────────────────────┬────────────────┘
             │ issue labeled `evolve` │
             ▼                        ▼
┌─────────────────────┐    ┌──────────────────────────┐
│   GitHub Actions    │    │     GitHub Releases       │
│                     │    │                           │
│  1. Read MANIFEST   │───▶│  core-v1.apk  (stable)   │
│  2. Write plugin    │    │  plugin-notes.dex         │
│  3. Compile DEX     │    │  plugin-camera.dex        │
│  4. Update MANIFEST │    │  plugin-gps.dex           │
│  5. Write JOURNAL   │    └──────────────┬────────────┘
│  6. Comment issue   │                   │ polled on launch
└─────────────────────┘                   ▼
                              ┌───────────────────────┐
                              │   Android Device       │
                              │                        │
                              │  Core APK (stable)     │
                              │  ├─ UpdateChecker      │
                              │  ├─ PluginLoader       │
                              │  ├─ PermissionBroker   │
                              │  └─ Plugin interface   │
                              │                        │
                              │  filesDir/plugins/     │
                              │  ├─ notes.dex          │
                              │  └─ camera.dex         │
                              └───────────────────────┘
```

---

## Core APK Components

The core APK is a stable shell that rarely changes. It exposes four subsystems:

### 1. UpdateChecker
- On launch, calls GitHub Releases API using `BuildConfig.GITHUB_REPO` and `BuildConfig.GITHUB_TOKEN`
- Compares installed plugin versions (stored in SharedPreferences) against latest releases tagged `stable`
- Downloads new/updated `.dex` files into `context.filesDir/plugins/` (private, no root needed)
- Copies to `context.codeCacheDir/` before loading (required for Android 10+ W^X compliance)
- Also checks for core APK updates — shows a notification if a new core APK is available (requires manual reinstall)

### 2. PluginLoader
- On launch (after UpdateChecker), scans `filesDir/plugins/*.dex`
- Loads each via `DexClassLoader(dexPath, codeCacheDir, null, classLoader)`
- Instantiates the plugin's main class (resolved from manifest metadata)
- Calls `plugin.register(host)` — plugins self-register their UI and capabilities
- Failed plugin loads are logged and skipped; they never crash the core

### 3. PermissionBroker
- Plugins declare required permissions via `PluginHost.requestPermission()`
- Broker checks current grant status, requests dangerous permissions at runtime
- If a plugin needs a permission not declared in the core APK manifest: shows a clear in-app message explaining a core update is needed, does not crash
- Tracks per-plugin permission grants in SharedPreferences

### 4. HomeScreen
- Renders whatever plugins have registered via `host.addHomeCard()`
- Empty state on first launch: shows a single card pointing to GitHub with instructions
- No hardcoded navigation — entirely driven by plugin registrations
- Uses Jetpack Compose; plugins provide `@Composable` lambdas via the `PluginHost` interface

---

## Plugin Interface Contract

Every plugin implements two interfaces defined in the core:

```kotlin
interface Plugin {
    val id: String           // e.g. "com.user.notes" — stable, unique
    val version: Int         // bumped on each evolution
    val permissions: List<String>  // Android permission strings needed at runtime
    fun register(host: PluginHost)
}

interface PluginHost {
    // UI registration
    fun addHomeCard(label: String, icon: ImageVector, onClick: () -> Unit)
    fun addFullScreen(route: String, content: @Composable () -> Unit)

    // Runtime capabilities
    fun requestPermission(permission: String, onResult: (Boolean) -> Unit)
    fun httpGet(url: String, headers: Map<String, String> = emptyMap()): String
    fun httpPost(url: String, body: String, headers: Map<String, String> = emptyMap()): String

    // Storage (sandboxed per plugin)
    fun getPrefs(pluginId: String): SharedPreferences
    fun readFile(name: String): ByteArray?
    fun writeFile(name: String, data: ByteArray)

    // Context (read-only)
    val context: Context
    val coroutineScope: CoroutineScope
}
```

The `PluginHost` interface is versioned. `MANIFEST.md` records the current API surface. The agent always writes plugins against the documented API — it never invents methods that don't exist. When a plugin needs a capability the `PluginHost` doesn't expose, the agent flags it as a core evolution request and triggers a core APK rebuild.

---

## MANIFEST.md Structure

```markdown
# App Manifest
core_version: 1
plugin_api_version: 1
last_evolved: 2026-03-05T00:00:00Z
github_repo: user/oneapp

## Installed Plugins
| id | version | permissions | entry_class | description |
|----|---------|-------------|-------------|-------------|
| com.user.notes | 1 | NONE | NotesPlugin | Simple note-taking |
| com.user.camera | 1 | CAMERA | CameraPlugin | Photo capture |

## External Plugins
| id | source_repo | version | registry |
|----|-------------|---------|----------|
| com.alice.weather | alice/oneapp-weather | 3 | official |

## PluginHost API Surface (v1)
addHomeCard, addFullScreen, requestPermission,
httpGet, httpPost, getPrefs, readFile, writeFile

## Permission Budget (declared in core AndroidManifest.xml)
INTERNET, CAMERA, ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION,
RECORD_AUDIO, READ_CONTACTS, WRITE_CONTACTS,
READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE,
VIBRATE, RECEIVE_BOOT_COMPLETED, USE_BIOMETRIC
```

**Agent rules for the manifest:**
- Always read it first before any evolution
- Always update it after any evolution
- Never leave it out of sync with actual plugin source
- If full source is needed, read `plugins/<id>.kt` directly from the repo

---

## Evolution Workflow (evolve.yml)

Triggers on: issue labeled `evolve`

```
1.  Read MANIFEST.md
2.  Read the issue — classify: new plugin / modify existing / core change
3.  If existing plugin: read plugins/<id>.kt
4.  If ambiguous: read full plugin source listing
5.  Write or modify Kotlin source in plugins/
6.  Compile to DEX:
      kotlinc plugins/<id>.kt -cp core-stubs.jar -d <id>.jar
      d8 <id>.jar --output <id>.dex
7.  Run basic lint check on generated Kotlin
8.  If lint passes: publish DEX to GitHub Releases (tagged stable)
9.  If lint fails: attempt one fix round; if still failing, comment on issue with error, do not publish
10. Update MANIFEST.md (version bump, permissions, API surface)
11. Append entry to JOURNAL.md
12. Comment on issue: what was built, permissions it will request, how to get it (next app launch)
13. If permission outside budget OR new PluginHost method needed:
      → also trigger core APK rebuild workflow
      → comment on issue explaining the reinstall will be required
```

---

## Private Fork Support

For users who keep their fork private:

- Create a fine-grained GitHub PAT (permissions: `contents: read`, scoped to this repo only)
- Add as repo secret: `GH_READ_TOKEN`
- Build pipeline injects it: `buildConfigField("String", "GITHUB_TOKEN", "\"${System.getenv("GH_READ_TOKEN")}\"")`
- App uses this token for all GitHub API calls
- External plugins from public repos download without auth — no token needed for those
- Worst-case token exposure: read-only access to one private repo — acceptable for a personal app

---

## Plugin Marketplace

Plugins are inherently portable across all forks — any fork running the same `plugin_api_version` can install any compatible plugin.

### Registry Format
A `registry.json` hosted at a stable URL (initially a GitHub repo, later a web service):

```json
{
  "api_version": 1,
  "plugins": [
    {
      "id": "com.alice.weather",
      "name": "Weather",
      "author": "alice",
      "description": "Current weather and 7-day forecast",
      "source": "https://github.com/alice/oneapp-weather",
      "dex_url": "https://github.com/alice/oneapp-weather/releases/latest/download/plugin.dex",
      "plugin_api_version": 1,
      "permissions": ["INTERNET"],
      "price_usd": 0,
      "paid": false
    }
  ]
}
```

### Installing External Plugins

**Via GitHub issue (consistent with the evolution model):**
User files: _"Install plugin from https://github.com/alice/oneapp-weather"_ with label `evolve`.
Agent fetches plugin metadata, checks API version compatibility, source is public, adds to `MANIFEST.md` under External Plugins. App downloads DEX on next launch.

**Via deep link (for marketplace browsing):**
`oneapp://install?id=com.alice.weather`
Opens app to an install confirmation screen showing name, author, permissions, price. One tap to install.

### Paid Plugin Flow
1. Author lists plugin on Lemon Squeezy, sets price
2. Registry entry includes `"paid": true, "ls_store": "alice", "ls_product_id": "12345"`
3. User buys on Lemon Squeezy → receives license key
4. User enters license key in app's plugin browser
5. App calls `api.lemonsqueezy.com/v1/licenses/validate` — if valid, downloads DEX
6. License key stored locally; not checked again unless plugin updates

Author needs: a Lemon Squeezy account. No infrastructure, no custom backend.

---

## Permission Growth Model

The core APK pre-declares a broad permission budget. Plugins within the budget deliver as DEX only. Plugins outside the budget trigger a core APK rebuild.

| Permission | Dangerous? | In initial budget? |
|------------|------------|-------------------|
| INTERNET | No | Yes |
| CAMERA | Yes | Yes |
| ACCESS_FINE_LOCATION | Yes | Yes |
| RECORD_AUDIO | Yes | Yes |
| READ_CONTACTS | Yes | Yes |
| USE_BIOMETRIC | Yes | Yes |
| VIBRATE | No | Yes |
| BODY_SENSORS | Yes | No — triggers core rebuild |
| BLUETOOTH_CONNECT | Yes | No — triggers core rebuild |

Over time the budget expands as the core evolves. The agent tracks the budget in MANIFEST.md and reasons about it before writing any plugin.

---

## Technology Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose
- **Min SDK:** 26 (Android 8.0) — covers 95%+ of active devices
- **Plugin compilation:** `kotlinc` + `d8` (Android build tools) in GitHub Actions
- **DEX loading:** `DexClassLoader` + copy-to-`codeCacheDir` pattern (Android 10+ compliant)
- **Networking:** OkHttp (in core, exposed via PluginHost)
- **CI:** GitHub Actions (Linux runners — fast, cheap)
- **Releases:** GitHub Releases API
- **Payment:** Lemon Squeezy license key validation

---

## What Ships Day One (Core Only)

- Empty home screen with GitHub onboarding hint
- UpdateChecker (polls releases on launch)
- PluginLoader (DEX loading machinery)
- PermissionBroker (runtime permission management)
- Plugin + PluginHost interface definitions
- `evolve.yml` GitHub Actions workflow
- `build.yml` — builds and publishes core APK
- `MANIFEST.md`, `JOURNAL.md`, `IDENTITY.md` templates
- `core-stubs.jar` — compile-time stub of PluginHost for agent to compile plugins against

Zero plugins installed. Zero features. Just the machinery.
