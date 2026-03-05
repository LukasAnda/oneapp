package dev.oneapp.core

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

private const val TAG = "UpdateChecker"
private const val DEX_PREFIX = "plugin-"
private const val DEX_SUFFIX = ".dex"

data class PendingApkUpdate(val downloadUrl: String, val filename: String)

class UpdateChecker(
    private val api: GitHubReleasesApi,
    private val pluginsDir: File,
    private val client: OkHttpClient = OkHttpClient(),
    private val installedVersionCode: Int = 0,
    private val registry: LocalPluginRegistry? = null,
) {
    /**
     * Checks GitHub Releases for new/updated DEX plugins and downloads them.
     * Also registers each downloaded plugin into LocalPluginRegistry if provided.
     * Returns list of plugin IDs that were updated.
     */
    suspend fun checkAndDownload(manifestContent: String = ""): List<String> = withContext(Dispatchers.IO) {
        pluginsDir.mkdirs()

        val assets = api.fetchLatestStableAssets()
        val dexAssets = filterDexAssets(assets)

        dexAssets.mapNotNull { asset ->
            runCatching {
                val destFile = File(pluginsDir, asset.name)
                val needsDownload = !destFile.exists() || destFile.length() != asset.size

                if (needsDownload) {
                    downloadFile(asset.downloadUrl, destFile)
                    Log.i(TAG, "Downloaded ${asset.name}")
                } else {
                    Log.d(TAG, "Skip download ${asset.name} — already up to date")
                }

                val pluginId = pluginIdFromFilename(asset.name)

                // Always (re)register entry class — manifest may have been empty on prior launch
                if (registry != null && manifestContent.isNotEmpty()) {
                    val entryClass = entryClassFromManifest(manifestContent, pluginId)
                    if (entryClass != null) {
                        registry.register(
                            LocalPluginRegistry.PluginEntry(
                                id = pluginId,
                                entryClass = entryClass,
                                version = 1,
                                source = "own",
                            )
                        )
                    }
                }

                if (needsDownload) pluginId else null
            }.getOrElse { e ->
                Log.e(TAG, "Failed to process ${asset.name}", e)
                null
            }
        }
    }

    /**
     * Checks whether a newer core APK is available in the stable release.
     * Returns a [PendingApkUpdate] if an update exists, null otherwise.
     */
    suspend fun checkForApkUpdate(): PendingApkUpdate? = withContext(Dispatchers.IO) {
        runCatching {
            val (releaseVersionCode, apkUrl) = api.fetchCoreVersion() ?: return@withContext null
            if (releaseVersionCode > installedVersionCode) {
                val filename = apkUrl.substringAfterLast("/")
                Log.i(TAG, "APK update available: versionCode $releaseVersionCode > $installedVersionCode")
                PendingApkUpdate(downloadUrl = apkUrl, filename = filename)
            } else {
                Log.d(TAG, "APK up to date (versionCode $installedVersionCode)")
                null
            }
        }.getOrElse { e ->
            Log.e(TAG, "APK update check failed", e)
            null
        }
    }

    fun filterDexAssets(assets: List<ReleaseAsset>): List<ReleaseAsset> =
        assets.filter { it.name.startsWith(DEX_PREFIX) && it.name.endsWith(DEX_SUFFIX) }

    fun pluginIdFromFilename(filename: String): String =
        filename.removePrefix(DEX_PREFIX).removeSuffix(DEX_SUFFIX)

    private fun entryClassFromManifest(manifestContent: String, pluginId: String): String? {
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

    private fun downloadFile(url: String, dest: File) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Download failed: ${response.code}" }
            response.body!!.byteStream().use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }
}
