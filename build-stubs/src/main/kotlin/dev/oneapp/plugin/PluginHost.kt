package dev.oneapp.plugin

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * The API surface the core exposes to all plugins.
 * Plugins may ONLY interact with Android via this interface.
 *
 * Capabilities are enforced per trust level by PluginHostImpl:
 * - EXTERNAL plugins: no dangerous permissions, no POST, no custom headers, own storage only
 * - OWN/TRUSTED plugins: full access
 *
 * Plugin API version: 2
 */
interface PluginHost {

    /**
     * Registers a reactive home card. The card content (label, subtitle, optional icon)
     * is driven by [content] — HomeScreen recomposes whenever the flow emits.
     * @param content StateFlow of HomeCardContent owned and updated by the plugin.
     * @param route   Optional navigation route. If non-null, tapping navigates; onClick is ignored.
     * @param onClick Called on tap when route is null.
     */
    fun addHomeCard(
        content: StateFlow<HomeCardContent>,
        route: String? = null,
        onClick: () -> Unit = {},
    )
    fun addFullScreen(route: String, content: @Composable () -> Unit)

    /**
     * Sets a seed color for this plugin's theme.
     * The home card icon is tinted with this color.
     * The plugin's full screen is wrapped in a MaterialTheme generated from this seed.
     * @param seedColor ARGB color as Long, e.g. 0xFFFFA000L for amber.
     */
    fun registerTheme(seedColor: Long)

    fun requestPermission(permission: String, onResult: (Boolean) -> Unit)

    /** GET request. EXTERNAL plugins: custom headers are stripped. */
    fun httpGet(url: String, headers: Map<String, String> = emptyMap()): String

    /** POST request. NOT available to EXTERNAL plugins — throws SecurityException. */
    fun httpPost(url: String, body: String, headers: Map<String, String> = emptyMap()): String

    /** Returns SharedPreferences scoped to this plugin only. No pluginId argument — isolation enforced. */
    fun getPrefs(): SharedPreferences

    fun readFile(name: String): ByteArray?
    fun writeFile(name: String, data: ByteArray)

    val context: Context
    val coroutineScope: CoroutineScope
}
