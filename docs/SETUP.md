# Fork Setup Guide

Follow these steps after forking. Takes ~15 minutes.

## 1. Generate an Android Signing Key (one-time)

```bash
keytool -genkey -v -keystore oneapp.jks -alias oneapp -keyalg RSA -keysize 2048 -validity 10000
```

Convert to base64 for GitHub:
```bash
# macOS
base64 -i oneapp.jks | pbcopy

# Linux
base64 -w 0 oneapp.jks
```

Store `oneapp.jks` somewhere safe — do NOT commit it.

## 2. Add GitHub Secrets

Go to your fork → Settings → Secrets and variables → Actions → New repository secret.

| Secret name | Value |
|-------------|-------|
| `SIGNING_KEY_BASE64` | Base64-encoded keystore from step 1 |
| `SIGNING_KEY_ALIAS` | `oneapp` (or whatever alias you chose) |
| `SIGNING_KEY_STORE_PASSWORD` | Your keystore password |
| `SIGNING_KEY_PASSWORD` | Your key password |
| `ANTHROPIC_API_KEY` | Your Anthropic API key from console.anthropic.com |
| `GH_READ_TOKEN` | Fine-grained PAT — only needed for **private forks** (contents: read, this repo only) |

## 3. Build and Install

Go to **Actions → Build and Release Core APK → Run workflow**.

- Check "Publish as stable release" if you want to install this version
- Click **Run workflow**
- Wait ~5 minutes for the build

Download the APK from the **Releases** page and sideload it.

**On Android:** Settings → Apps → Special app access → Install unknown apps → allow your browser or file manager.

## 4. Start Evolving

Open a GitHub issue on your fork describing what you want. Add the `evolve` label.

The agent will:
1. Read your issue
2. Build a plugin
3. Compile it to a DEX file
4. Publish to Releases
5. Comment on your issue confirming what was built

Restart the app to pick up the new plugin.

## 5. Reverting (if something breaks)

Every release is preserved. To revert to a previous version:

1. Go to Releases
2. Find the last known-good release
3. Re-tag it as `stable` (the app's UpdateChecker only downloads `stable`-tagged releases)

Or just delete the broken DEX from the stable release assets — the app will stop loading that plugin on next launch.

## 6. Installing Plugins from Others

**Via GitHub issue:** Open an issue on your fork: `Install plugin from https://github.com/alice/oneapp-weather` with label `evolve`. The agent will handle it.

**Via deep link:** `oneapp://install?id=com.alice.weather` — tap this in your browser on your Android device.

## 7. Template Auto-Updates (optional)

OneApp's CI agent, workflows, and tooling improve over time. You can opt in to receive those improvements automatically — Claude Sonnet merges any changes into your repo every Monday, resolving conflicts without you lifting a finger.

**To enable:**

1. Repo → Settings → Secrets and variables → Actions → **Variables** tab → New repository variable
2. Name: `TEMPLATE_AUTO_UPDATE` / Value: `true`

That's it. Every Monday the workflow will:
1. Fetch the latest changes from the template repo
2. Merge them into your `main` branch
3. If there are conflicts, call Claude Sonnet to resolve them (your app code is always preserved)
4. Push directly — no PR, no manual review needed

**To disable at any time:** delete the `TEMPLATE_AUTO_UPDATE` variable or set it to `false`.

**To trigger a one-off sync:** Actions → Sync from template → Run workflow.

> Your `app/src/`, `AndroidManifest.xml`, `MANIFEST.md`, `JOURNAL.md`, and `IDENTITY.md` are always treated as yours — the merge logic preserves them. CI workflows and agent scripts are updated from the template.

## Permissions

The app pre-declares a broad set of permissions in AndroidManifest.xml. When a plugin needs a new permission that isn't declared yet, the agent will:
1. Add it to AndroidManifest.xml
2. Trigger a new core APK build automatically
3. Tell you in the issue comment that you need to reinstall the APK once

This is safe — the agent only adds permissions that your plugins explicitly request.
