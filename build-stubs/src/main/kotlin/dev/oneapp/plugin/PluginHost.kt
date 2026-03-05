package dev.oneapp.plugin

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.CoroutineScope

/**
 * The API surface the core exposes to all plugins.
 * Plugins may ONLY interact with Android via this interface — never directly.
 *
 * Plugin API version: 1
 */
interface PluginHost {

    // ── UI Registration ──────────────────────────────────────────────────────

    /**
     * Adds a tappable card to the home screen.
     * @param label Short display name shown on the card
     * @param icon  Material icon vector
     * @param onClick Invoked when the user taps the card
     */
    fun addHomeCard(label: String, icon: ImageVector, onClick: () -> Unit)

    /**
     * Registers a full-screen Compose destination reachable from a home card.
     * @param route Unique route string for navigation e.g. "notes_main"
     * @param content Composable lambda that renders the screen
     */
    fun addFullScreen(route: String, content: @Composable () -> Unit)

    // ── Permissions ──────────────────────────────────────────────────────────

    /**
     * Requests a dangerous Android permission at runtime.
     * @param permission e.g. android.Manifest.permission.CAMERA
     * @param onResult   Called with true if granted, false if denied
     */
    fun requestPermission(permission: String, onResult: (Boolean) -> Unit)

    // ── Networking ───────────────────────────────────────────────────────────

    /** Synchronous GET. Must be called from a background coroutine. */
    fun httpGet(url: String, headers: Map<String, String> = emptyMap()): String

    /** Synchronous POST with string body. Must be called from a background coroutine. */
    fun httpPost(url: String, body: String, headers: Map<String, String> = emptyMap()): String

    // ── Storage (sandboxed per plugin) ───────────────────────────────────────

    /** Returns a SharedPreferences scoped to this plugin. */
    fun getPrefs(pluginId: String): SharedPreferences

    /** Reads a file from the plugin's private directory. Returns null if not found. */
    fun readFile(name: String): ByteArray?

    /** Writes a file to the plugin's private directory. */
    fun writeFile(name: String, data: ByteArray)

    // ── Context ──────────────────────────────────────────────────────────────

    /** Application context. Read-only — plugins must not start Activities directly. */
    val context: Context

    /** Coroutine scope tied to the app's lifecycle. Use for all async plugin work. */
    val coroutineScope: CoroutineScope
}
