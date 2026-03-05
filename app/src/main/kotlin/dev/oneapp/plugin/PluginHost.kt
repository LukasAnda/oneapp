package dev.oneapp.plugin

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.CoroutineScope

/**
 * The API surface the core exposes to all plugins.
 * Plugins may ONLY interact with Android via this interface.
 *
 * Capabilities are enforced per trust level by PluginHostImpl:
 * - EXTERNAL plugins: no dangerous permissions, no POST, no custom headers, own storage only
 * - OWN/TRUSTED plugins: full access
 *
 * Plugin API version: 1
 */
interface PluginHost {

    fun addHomeCard(label: String, icon: ImageVector, onClick: () -> Unit)
    fun addFullScreen(route: String, content: @Composable () -> Unit)

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
