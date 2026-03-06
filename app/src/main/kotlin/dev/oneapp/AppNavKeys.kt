package dev.oneapp

import androidx.navigation3.runtime.NavKey

/** Sealed hierarchy of all navigation destinations in the core app. */
sealed interface AppNavKey : NavKey

/** Home screen — the plugin card grid. */
data object HomeKey : AppNavKey

/**
 * A plugin-registered full-screen.
 * The [route] string matches what the plugin passed to [PluginHost.addFullScreen].
 */
data class PluginScreenKey(val route: String) : AppNavKey

/** External plugin install confirmation screen. */
data class InstallPluginKey(
    val pluginId: String,
    val dexUrl: String,
    val entryClass: String,
    val version: Int,
    val hash: String? = null,
) : AppNavKey
