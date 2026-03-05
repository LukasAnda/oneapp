package dev.oneapp.plugin

import android.util.Log
import dalvik.system.DexClassLoader
import dev.oneapp.core.LocalPluginRegistry
import java.io.File

private const val TAG = "PluginLoader"

class PluginLoader(
    private val pluginsDir: File,
    private val codeCacheDir: File,
) {
    /**
     * Loads all DEX plugins from codeCacheDir.
     * [hostFactory] receives (pluginId, trust) and returns a scoped PluginHostImpl.
     * Skips plugins that fail to load — never crashes the core.
     */
    fun loadAll(
        manifestContent: String,
        hostFactory: (pluginId: String, trust: PluginTrust) -> PluginHost,
        registry: LocalPluginRegistry? = null,
    ): List<Plugin> {
        val dexFiles = codeCacheDir.listFiles { f -> f.name.endsWith(".dex") } ?: return emptyList()
        return dexFiles.mapNotNull { dexFile ->
            runCatching { loadOne(dexFile, manifestContent, hostFactory, registry) }
                .getOrElse { e ->
                    Log.e(TAG, "Failed to load ${dexFile.name}", e)
                    null
                }
        }
    }

    private fun loadOne(
        dexFile: File,
        manifestContent: String,
        hostFactory: (pluginId: String, trust: PluginTrust) -> PluginHost,
        registry: LocalPluginRegistry?,
    ): Plugin {
        val pluginId = dexFile.name.removePrefix("plugin-").removeSuffix(".dex")
        val registryEntry = registry?.get(pluginId)
        val trust = registryEntry?.trust ?: PluginTrust.OWN

        val entryClass = registryEntry?.entryClass
            ?: entryClassNameFromManifest(manifestContent, pluginId)
            ?: error("No entry_class found for plugin $pluginId")

        val classLoader = DexClassLoader(
            dexFile.absolutePath,
            codeCacheDir.absolutePath,
            null,
            Plugin::class.java.classLoader,
        )

        val host = hostFactory(pluginId, trust)
        val pluginClass = classLoader.loadClass(entryClass)
        val plugin = pluginClass.getDeclaredConstructor().newInstance() as Plugin
        plugin.register(host)
        Log.i(TAG, "Loaded plugin: $pluginId v${plugin.version} trust=$trust")
        return plugin
    }

    fun entryClassNameFromManifest(manifestContent: String, pluginId: String): String? {
        return manifestContent.lines()
            .firstOrNull { line ->
                val trimmed = line.trim()
                trimmed.startsWith("|") &&
                trimmed.contains(pluginId) &&
                !trimmed.contains("entry_class") &&
                !trimmed.startsWith("|---")
            }
            ?.split("|")?.map { it.trim() }
            ?.getOrNull(4)?.takeIf { it.isNotBlank() }
    }
}
