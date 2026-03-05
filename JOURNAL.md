# Evolution Journal

Append-only log of every evolution session. Most recent entry at the top.

---

## Evolution #1 — Issue #1: Good Morning Daily Notification at 8 AM
**Date:** 2025-01-01
**Plugin:** `dev.oneapp.goodmorning` v1
**Entry class:** `dev.oneapp.plugins.GoodMorningPlugin`

### What was built
- **GoodMorningPlugin** — a single-file plugin that schedules a daily exact alarm at 08:00 local time.
- Uses `AlarmManager.setExactAndAllowWhileIdle` for reliable exact alarms on API 23+; after each fire the alarm is rescheduled for the next day manually (no inexact `setRepeating`).
- **GoodMorningAlarmReceiver** — fires at 08:00, posts the notification, then reschedules for tomorrow.
- **GoodMorningBootReceiver** — listens for `BOOT_COMPLETED`; re-schedules the alarm if it was enabled.
- **NotificationChannel** `daily_greeting` (importance HIGH) created on plugin registration.
- Home card shows "Good Morning Alarm" with dynamic subtitle (Today / Tomorrow / Disabled) and routes to a full-screen toggle UI.
- Enabled/disabled state persisted in SharedPreferences key `alarm_enabled`; alarm restored on app restart.
- On first enable: schedules today at 08:00 if current time < 08:00, otherwise tomorrow.

### Manifest changes
- Added `<uses-permission android:name="android.permission.USE_EXACT_ALARM" />` — **triggers APK rebuild**.

### Permissions
| Permission | Reason |
|---|---|
| `POST_NOTIFICATIONS` | Required on Android 13+ to post notifications; requested at runtime via `host.requestPermission` |
| `USE_EXACT_ALARM` | Required to call `setExactAndAllowWhileIdle` reliably on API 31+ |
| `RECEIVE_BOOT_COMPLETED` | Reschedule alarm after device reboot |

### Files changed
- `plugins/dev.oneapp.goodmorning.kt` — created
- `app/src/main/AndroidManifest.xml` — added `USE_EXACT_ALARM`
- `MANIFEST.md` — plugin row added
- `JOURNAL.md` — this entry

---