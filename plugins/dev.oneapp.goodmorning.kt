package dev.oneapp.plugins

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import dev.oneapp.plugin.Plugin
import dev.oneapp.plugin.PluginHost
import android.Manifest
import java.util.Calendar

// ── constants shared by plugin + receivers ──────────────────────────────────
private const val PREF_KEY_ENABLED   = "alarm_enabled"
private const val CHANNEL_ID         = "daily_greeting"
private const val ACTION_ALARM       = "dev.oneapp.goodmorning.ALARM"
private const val ACTION_BOOT        = "android.intent.action.BOOT_COMPLETED"
private const val REQUEST_CODE       = 0x474D // "GM"
private const val NOTIF_ID           = 1001

// ── helpers ─────────────────────────────────────────────────────────────────

private fun ensureNotificationChannel(ctx: Context) {
    val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (nm.getNotificationChannel(CHANNEL_ID) == null) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Daily Greeting",
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "Good morning daily alarm notifications" }
        nm.createNotificationChannel(channel)
    }
}

private fun postGoodMorningNotification(ctx: Context) {
    ensureNotificationChannel(ctx)
    val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("Good morning! ☀️")
        .setContentText("Have a great day ahead.")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .build()
    nm.notify(NOTIF_ID, notif)
}

/** Returns a Calendar set to 08:00:00 today. */
private fun todayAt8(): Calendar = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 8)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}

/**
 * Returns the next 08:00 trigger time:
 * - today at 08:00 if the current time is before 08:00
 * - tomorrow at 08:00 otherwise
 */
private fun nextAlarmMillis(): Long {
    val t = todayAt8()
    if (System.currentTimeMillis() >= t.timeInMillis) {
        t.add(Calendar.DAY_OF_YEAR, 1)
    }
    return t.timeInMillis
}

private fun alarmPendingIntent(ctx: Context): PendingIntent {
    val intent = Intent(ACTION_ALARM).apply { setPackage(ctx.packageName) }
    return PendingIntent.getBroadcast(
        ctx, REQUEST_CODE, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

private fun scheduleNext(ctx: Context) {
    val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    am.setExactAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        nextAlarmMillis(),
        alarmPendingIntent(ctx)
    )
}

private fun cancelAlarm(ctx: Context) {
    val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    am.cancel(alarmPendingIntent(ctx))
}

private fun isEnabledInPrefs(ctx: Context): Boolean =
    ctx.getSharedPreferences("dev.oneapp.goodmorning", Context.MODE_PRIVATE)
        .getBoolean(PREF_KEY_ENABLED, false)

private fun setEnabledInPrefs(ctx: Context, enabled: Boolean) =
    ctx.getSharedPreferences("dev.oneapp.goodmorning", Context.MODE_PRIVATE)
        .edit().putBoolean(PREF_KEY_ENABLED, enabled).apply()

// ── BroadcastReceiver: fires at 08:00 ───────────────────────────────────────

class GoodMorningAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ALARM) return
        if (!isEnabledInPrefs(context)) return
        postGoodMorningNotification(context)
        // Reschedule for the next day
        scheduleNext(context)
    }
}

// ── BroadcastReceiver: device reboot ────────────────────────────────────────

class GoodMorningBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_BOOT) return
        if (!isEnabledInPrefs(context)) return
        scheduleNext(context)
    }
}

// ── Plugin ──────────────────────────────────────────────────────────────────

class GoodMorningPlugin : Plugin {

    override val id          = "dev.oneapp.goodmorning"
    override val version     = 1
    override val permissions = listOf(
        Manifest.permission.POST_NOTIFICATIONS
    )

    override fun register(host: PluginHost) {
        val ctx = host.context

        // Ensure notification channel exists
        ensureNotificationChannel(ctx)

        // Register receivers dynamically so they survive config changes
        val alarmFilter = IntentFilter(ACTION_ALARM)
        ctx.registerReceiver(GoodMorningAlarmReceiver(), alarmFilter,
            Context.RECEIVER_NOT_EXPORTED)

        val bootFilter = IntentFilter(ACTION_BOOT)
        ctx.registerReceiver(GoodMorningBootReceiver(), bootFilter,
            Context.RECEIVER_NOT_EXPORTED)

        // Restore alarm if it was enabled before
        if (isEnabledInPrefs(ctx)) {
            scheduleNext(ctx)
        }

        // ── Home card ────────────────────────────────────────────────────────
        host.addHomeCard(
            config = buildCardConfig(isEnabledInPrefs(ctx)),
            icon   = Icons.Default.WbSunny,
            onClick = {}
        )

        // ── Full-screen UI ───────────────────────────────────────────────────
        host.addFullScreen("goodmorning_main") {
            val prefs  = host.getPrefs()
            var enabled by remember {
                mutableStateOf(prefs.getBoolean(PREF_KEY_ENABLED, false))
            }

            fun toggle(newValue: Boolean) {
                enabled = newValue
                prefs.edit().putBoolean(PREF_KEY_ENABLED, newValue).apply()
                if (newValue) {
                    // Also request POST_NOTIFICATIONS at runtime
                    host.requestPermission(Manifest.permission.POST_NOTIFICATIONS) {}
                    scheduleNext(ctx)
                } else {
                    cancelAlarm(ctx)
                }
            }

            val subtitle = when {
                !enabled -> "Disabled"
                else -> {
                    val now    = System.currentTimeMillis()
                    val today8 = todayAt8().timeInMillis
                    if (now < today8) "Today at 08:00" else "Tomorrow at 08:00"
                }
            }

            Surface(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.WbSunny,
                        contentDescription = null,
                        tint   = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Good Morning Alarm",
                        style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(32.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            if (enabled) "Enabled" else "Disabled",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Switch(
                            checked  = enabled,
                            onCheckedChange = { toggle(it) }
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Daily alarm fires at 08:00 every morning.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Request POST_NOTIFICATIONS if enabled at boot
        if (isEnabledInPrefs(ctx)) {
            host.requestPermission(Manifest.permission.POST_NOTIFICATIONS) {}
        }
    }

    // Build JSON config for the home card
    private fun buildCardConfig(enabled: Boolean): String {
        val subtitle = when {
            !enabled -> "Disabled"
            System.currentTimeMillis() < todayAt8().timeInMillis -> "Today at 08:00"
            else -> "Tomorrow at 08:00"
        }
        return """{"label":"Good Morning Alarm","subtitle":"Next alarm: $subtitle"}"""
    }
}