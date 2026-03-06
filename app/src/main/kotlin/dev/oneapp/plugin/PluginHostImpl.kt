package dev.oneapp.plugin

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import dev.oneapp.core.PermissionBroker
import dev.oneapp.plugin.PluginTrust
import kotlinx.coroutines.CoroutineScope
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

// Dangerous permissions blocked for EXTERNAL plugins
private val DANGEROUS_PERMISSIONS = setOf(
    android.Manifest.permission.CAMERA,
    android.Manifest.permission.RECORD_AUDIO,
    android.Manifest.permission.ACCESS_FINE_LOCATION,
    android.Manifest.permission.ACCESS_COARSE_LOCATION,
    android.Manifest.permission.READ_CONTACTS,
    android.Manifest.permission.WRITE_CONTACTS,
    android.Manifest.permission.READ_MEDIA_IMAGES,
    android.Manifest.permission.READ_MEDIA_VIDEO,
    android.Manifest.permission.READ_MEDIA_AUDIO,
    android.Manifest.permission.USE_BIOMETRIC,
    android.Manifest.permission.CALL_PHONE,
    android.Manifest.permission.SEND_SMS,
    android.Manifest.permission.READ_SMS,
    android.Manifest.permission.READ_CALL_LOG,
)

private const val TAG = "PluginHostImpl"

class PluginHostImpl(
    override val context: Context,
    override val coroutineScope: CoroutineScope,
    private val permissionBroker: PermissionBroker,
    private val client: OkHttpClient,
    private val pluginDataDir: File,
    private val pluginId: String,
    private val trust: PluginTrust = PluginTrust.OWN,
) : PluginHost {

    override fun addHomeCard(config: String, icon: ImageVector, onClick: () -> Unit) {
        val json = org.json.JSONObject(config)
        val label = json.getString("label")
        val subtitle = json.optString("subtitle", "")
        // Only set a route if explicitly specified in config — prevents navigation to non-existent routes.
        // If no "route" key, onClick is used directly.
        val route = json.optString("route", "").ifEmpty { null }
        PluginRegistry.addCard(
            HomeCard(pluginId = pluginId, label = label, subtitle = subtitle, icon = icon, route = route, onClick = onClick)
        )
    }

    override fun addFullScreen(route: String, content: @Composable () -> Unit) {
        PluginRegistry.addFullScreen(FullScreen(route = route, content = content))
    }

    override fun requestPermission(permission: String, onResult: (Boolean) -> Unit) {
        if (trust == PluginTrust.EXTERNAL && permission in DANGEROUS_PERMISSIONS) {
            Log.w(TAG, "EXTERNAL plugin '$pluginId' denied dangerous permission: $permission")
            onResult(false)
            return
        }
        permissionBroker.request(permission, onResult)
    }

    override fun httpGet(url: String, headers: Map<String, String>): String {
        // EXTERNAL plugins: strip all custom headers to prevent credential smuggling
        val safeHeaders = if (trust == PluginTrust.EXTERNAL) emptyMap() else headers
        val req = Request.Builder().url(url)
            .apply { safeHeaders.forEach { (k, v) -> addHeader(k, v) } }
            .build()
        return client.newCall(req).execute().use { it.body?.string() ?: "" }
    }

    override fun httpPost(url: String, body: String, headers: Map<String, String>): String {
        if (trust == PluginTrust.EXTERNAL) {
            throw SecurityException("EXTERNAL plugin '$pluginId' is not allowed to make POST requests. " +
                "Ask the user to mark this plugin as TRUSTED to enable POST.")
        }
        val reqBody = body.toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url(url).post(reqBody)
            .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
            .build()
        return client.newCall(req).execute().use { it.body?.string() ?: "" }
    }

    // Storage is always scoped to this plugin's own ID — no parameter, no cross-plugin access
    override fun getPrefs(): SharedPreferences =
        context.getSharedPreferences("plugin_$pluginId", Context.MODE_PRIVATE)

    override fun readFile(name: String): ByteArray? {
        val file = File(File(pluginDataDir, pluginId), name)
        return if (file.exists()) file.readBytes() else null
    }

    override fun writeFile(name: String, data: ByteArray) {
        val dir = File(pluginDataDir, pluginId).also { it.mkdirs() }
        File(dir, name).writeBytes(data)
    }
}
