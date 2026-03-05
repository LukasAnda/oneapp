# OneApp

**A self-evolving Android shell. Fork it. It becomes yours.**

OneApp is an empty Android app that grows through GitHub issues. File an issue, label it `evolve`, and an AI agent builds a plugin, compiles it, and delivers it to your device on next launch. No coding required.

## How it works

```
You file a GitHub issue  →  agent reads it  →  writes a Kotlin plugin
→  compiles to DEX  →  publishes to GitHub Releases  →  app downloads it on next launch
```

Every plugin is a self-contained `.dex` file hot-loaded at startup. No Play Store. No reinstall (usually). Just restart the app.

## Getting started

1. **Use this template** — click "Use this template" on GitHub (not "Fork")
2. Follow [`docs/SETUP.md`](docs/SETUP.md) — add 5 secrets, run one workflow, sideload the APK
3. Open an issue describing what you want, label it `evolve`

## Plugin system

The app is built entirely from plugins. Each plugin implements a simple interface and registers UI cards and full screens via `PluginHost`. Plugins can use:

- Network (HTTP GET/POST)
- Camera, GPS, microphone, contacts — any Android permission
- Sandboxed file storage and SharedPreferences
- Full Jetpack Compose UI

## Installing plugins from others

File an issue: `Install plugin from https://github.com/alice/oneapp-weather` — the agent handles the rest.

Or tap a deep link: `oneapp://install?id=com.alice.weather`

## Reverting

Every GitHub Release is preserved. Re-tag any previous release as `stable` to roll back. The app only downloads `stable`-tagged releases.

## For developers

- [`docs/plans/2026-03-05-oneapp-self-evolving-design.md`](docs/plans/2026-03-05-oneapp-self-evolving-design.md) — full architecture design
- [`IDENTITY.md`](IDENTITY.md) — the agent's behavioral constitution
- [`MANIFEST.md`](MANIFEST.md) — current installed plugins and API surface
- [`JOURNAL.md`](JOURNAL.md) — evolution history
