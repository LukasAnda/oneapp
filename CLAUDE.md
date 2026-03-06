# OneApp — Claude Instructions

## After Every Code Change

1. **Always push immediately after committing.** Never leave commits unpushed.
2. **Check the GitHub Actions build.** After pushing, verify the "Build and Release Core APK" workflow passes:
   ```bash
   gh run watch --repo LukasAnda/oneapp
   # or check status:
   gh run list --limit 3 --repo LukasAnda/oneapp
   ```
3. **If the build touches `app/`, `gradle/`, or `plugins/`** — the build.yml workflow will fire and produce a new release APK + compiled DEX files. Confirm it succeeds before calling the task done.
4. **Install the new APK on the emulator** after a successful build (use the release APK from GitHub, or the local debug APK if doing rapid iteration):
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

## Plugin Workflow

- Plugins live in `plugins/*.kt`. Each file compiles to a DEX via `scripts/compile-plugin.sh`.
- The build pipeline compiles all plugins and uploads them to the `stable` GitHub Release alongside the APK.
- After any plugin change, verify the compiled DEX appears in the release assets:
  ```bash
  gh release view stable --repo LukasAnda/oneapp
  ```
- To manually compile a plugin locally for quick testing:
  ```bash
  bash scripts/compile-plugin.sh plugins/dev.oneapp.goodmorning.kt dev.oneapp.goodmorning
  ```

## Evolve Workflow

- Triggered when the **repo owner comments on an open issue** OR applies the `evolve` label.
- The Python agent (`scripts/evolve_agent.py`) writes the plugin, compiles it with retry (up to 3 attempts), and commits/pushes.
- After evolve runs, check the resulting build passes and the DEX is in the release.

## Maestro Tests

- Maestro's gRPC driver on Google Play emulators requires the instrument to be pre-started:
  ```bash
  adb shell am instrument -w dev.mobile.maestro.test/androidx.test.runner.AndroidJUnitRunner &
  sleep 4
  maestro test .maestro/test_navigation.yaml
  ```
- The Maestro APKs (maestro-app.apk, maestro-server.apk) are embedded in `maestro-client-*.jar` inside the Maestro Homebrew install. Extract with `jar xf` if they need reinstalling.
- Maestro MCP is configured via `.mcp.json` — restart Claude Code to get `maestro mcp` tools.

## Architecture Quick Reference

- **Navigation**: Navigation 3 (`androidx.navigation3` 1.1.0-alpha05). `NavBackStack<AppNavKey>` + `NavDisplay`. Keys in `AppNavKeys.kt`.
- **Plugins**: Loaded from GitHub Release DEX files via `InMemoryDexClassLoader`. `PluginRegistry.fullScreens` is a `mutableStateListOf` — Navigation 3 resolves routes dynamically on recomposition.
- **No crashes policy**: Plugin errors must never crash the app. Use `runCatching` on plugin `onClick`. Show errors via snackbar.
- **System bars**: Always pass `contentPadding` (from `Scaffold`) through to scrollable content.
- **local.properties**: `github.repo=LukasAnda/oneapp`, `github.token=` (empty = public access, no auth header sent).

## Key Files

| File | Purpose |
|------|---------|
| `app/src/main/kotlin/dev/oneapp/MainActivity.kt` | Nav3 setup, plugin loading, deep links |
| `app/src/main/kotlin/dev/oneapp/AppNavKeys.kt` | Navigation key sealed hierarchy |
| `app/src/main/kotlin/dev/oneapp/plugin/PluginRegistry.kt` | Plugin card + full-screen registry |
| `app/src/main/kotlin/dev/oneapp/plugin/PluginHostImpl.kt` | Plugin SDK implementation |
| `plugins/dev.oneapp.goodmorning.kt` | Good Morning Alarm plugin source |
| `scripts/evolve_agent.py` | AI evolve agent with compile-retry loop |
| `scripts/compile-plugin.sh` | Plugin DEX compiler script |
| `.github/workflows/build.yml` | CI: builds APK + compiles all plugins |
| `.github/workflows/evolve.yml` | Evolve trigger on label or owner comment |
| `.maestro/test_navigation.yaml` | Maestro navigation test |
