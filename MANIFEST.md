# App Manifest
core_version: 1
plugin_api_version: 1
last_evolved: never
github_repo: REPLACE_WITH_YOUR_REPO

## Installed Plugins
| id | version | permissions | entry_class | description |
|----|---------|-------------|-------------|-------------|
| dev.oneapp.hello | 1 | NONE | dev.oneapp.plugins.HelloPlugin | Smoke test - greeting screen |

## External Plugins
| id | source_repo | version | registry |
|----|-------------|---------|----------|

## PluginHost API Surface (v1)
- addHomeCard(label, icon, onClick)
- addFullScreen(route, content)
- requestPermission(permission, onResult)
- httpGet(url, headers)
- httpPost(url, body, headers)
- getPrefs()  // no argument — storage is automatically scoped to this plugin's ID
- readFile(name)
- writeFile(name, data)
- context (read-only)
- coroutineScope
