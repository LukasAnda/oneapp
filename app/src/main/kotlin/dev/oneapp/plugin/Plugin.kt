package dev.oneapp.plugin

/**
 * Every plugin DEX must contain exactly one class implementing this interface.
 * The implementing class must have a no-arg constructor.
 *
 * Plugin API version: 1
 * Breaking changes here require a core APK rebuild AND all existing plugins to be recompiled.
 */
interface Plugin {
    /** Stable, unique reverse-domain identifier. e.g. "com.alice.weather" */
    val id: String

    /** Increment on every evolution. Used by UpdateChecker to detect new versions. */
    val version: Int

    /** Android permission strings this plugin needs. PermissionBroker checks these at load time. */
    val permissions: List<String>

    /**
     * Called once after the plugin is loaded and permissions are checked.
     * Plugin registers all its UI and capabilities here via [host].
     * Must not block — use [PluginHost.coroutineScope] for async work.
     */
    fun register(host: PluginHost)
}
