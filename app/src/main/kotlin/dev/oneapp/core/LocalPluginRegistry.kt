package dev.oneapp.core

import android.content.Context
import android.util.Log
import dev.oneapp.plugin.PluginTrust
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private const val TAG = "LocalPluginRegistry"
private const val REGISTRY_FILE = "plugin_registry.json"

class LocalPluginRegistry(context: Context) {

    private val file = File(context.filesDir, REGISTRY_FILE)

    data class PluginEntry(
        val id: String,
        val entryClass: String,
        val version: Int,
        val source: String,
        val dexUrl: String = "",
        val trust: PluginTrust = PluginTrust.OWN,
    )

    fun all(): List<PluginEntry> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            PluginEntry(
                id = obj.getString("id"),
                entryClass = obj.getString("entryClass"),
                version = obj.getInt("version"),
                source = obj.optString("source", "own"),
                dexUrl = obj.optString("dexUrl", ""),
                trust = runCatching {
                    PluginTrust.valueOf(obj.optString("trust", "OWN"))
                }.getOrDefault(PluginTrust.OWN),
            )
        }
    }.getOrElse {
        Log.e(TAG, "Failed to read registry", it)
        emptyList()
    }

    fun get(pluginId: String): PluginEntry? = all().firstOrNull { it.id == pluginId }

    fun register(entry: PluginEntry) {
        val entries = all().filter { it.id != entry.id }.toMutableList()
        entries.add(entry)
        save(entries)
        Log.i(TAG, "Registered plugin: ${entry.id} v${entry.version} trust=${entry.trust}")
    }

    fun setTrust(pluginId: String, trust: PluginTrust) {
        val entry = get(pluginId) ?: return
        register(entry.copy(trust = trust))
        Log.i(TAG, "Updated trust for $pluginId: $trust")
    }

    fun unregister(pluginId: String) {
        save(all().filter { it.id != pluginId })
    }

    private fun save(entries: List<PluginEntry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(JSONObject().apply {
                put("id", e.id)
                put("entryClass", e.entryClass)
                put("version", e.version)
                put("source", e.source)
                put("dexUrl", e.dexUrl)
                put("trust", e.trust.name)
            })
        }
        file.writeText(arr.toString(2))
    }
}
