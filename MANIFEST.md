# App Manifest
core_version: 1
plugin_api_version: 1
last_evolved: 2026-03-05T20:36:56.939194+00:00
github_repo: REPLACE_WITH_YOUR_REPO

## Installed Plugins
| id | version | permissions | entry_class | description |
|----|---------|-------------|-------------|-------------|
| dev.oneapp.goodmorning | 1 | POST_NOTIFICATIONS, USE_EXACT_ALARM, RECEIVE_BOOT_COMPLETED | dev.oneapp.plugins.GoodMorningPlugin | Daily 8 AM Good Morning notification with home card toggle |
| dev.oneapp.hello | 1 | NONE | dev.oneapp.plugins.HelloPlugin | Smoke test - greeting screen |

## External Plugins
| id | source_repo | version | registry |
|----|-------------|---------|----------|

## PluginHost API Surface (v1)
- addHomeCard(config, icon, onClick)  // config is JSON: {"label":"...", "subtitle":"..."} — subtitle optional
- addFullScreen(route, content)
- requestPermission(permission, onResult)
- httpGet(url, headers)
- httpPost(url, body, headers)
- getPrefs()  // no argument — storage is automatically scoped to this plugin's ID
- readFile(name)
- writeFile(name, data)
- context (read-only)
- coroutineScope
