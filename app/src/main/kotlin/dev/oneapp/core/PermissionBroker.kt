package dev.oneapp.core

import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Handles runtime permission requests for plugins via PluginHost.requestPermission().
 *
 * No permission budget enforcement here — the evolution agent adds new permissions
 * to AndroidManifest.xml as needed, which triggers a core APK rebuild automatically.
 * PermissionBroker simply checks grant status and requests at runtime.
 */
class PermissionBroker(private val activity: ComponentActivity) {

    private var pendingCallback: ((Boolean) -> Unit)? = null

    private val launcher: ActivityResultLauncher<String> =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            pendingCallback?.invoke(granted)
            pendingCallback = null
        }

    fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED

    fun request(permission: String, onResult: (Boolean) -> Unit) {
        if (isGranted(permission)) {
            onResult(true)
            return
        }
        pendingCallback = onResult
        launcher.launch(permission)
    }
}
