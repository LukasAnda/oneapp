package dev.oneapp.core

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

private const val TAG = "ApkInstaller"

class ApkInstaller(
    private val context: Context,
    private val client: OkHttpClient,
) {
    /**
     * Downloads the APK from [url] and triggers the system package installer.
     * The user sees a single "Install" / "Update" system dialog — no manual file handling.
     * Requires REQUEST_INSTALL_PACKAGES permission + FileProvider configured in manifest.
     */
    suspend fun downloadAndInstall(url: String, filename: String) = withContext(Dispatchers.IO) {
        val updatesDir = File(context.filesDir, "updates").also { it.mkdirs() }
        val apkFile = File(updatesDir, filename)

        Log.i(TAG, "Downloading APK: $url")
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "APK download failed: ${response.code}" }
            response.body!!.byteStream().use { input ->
                apkFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        Log.i(TAG, "APK downloaded: ${apkFile.absolutePath}")

        // Hand off to system installer via FileProvider (required on Android 7+)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )
        // ACTION_INSTALL_PACKAGE is deprecated at API 29 in favour of PackageInstaller, but
        // PackageInstaller requires far more boilerplate for the same user-facing install dialog.
        // For a sideload-oriented app this remains the correct choice.
        @Suppress("DEPRECATION")
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            putExtra(Intent.EXTRA_RETURN_RESULT, false)
        }
        context.startActivity(intent)
    }
}
