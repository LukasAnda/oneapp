package dev.oneapp.core

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val size: Long,
)

class GitHubReleasesApi(
    private val client: OkHttpClient,
    private val baseUrl: String = "https://api.github.com/",
    private val repo: String,
    private val token: String,
) {
    /** Builds a request, only adding Authorization when a token is configured.
     *  Public repos work without auth; sending a blank Bearer token causes 401. */
    private fun request(url: String, accept: String = "application/vnd.github.v3+json") =
        Request.Builder().url(url)
            .apply { if (token.isNotEmpty()) header("Authorization", "Bearer $token") }
            .header("Accept", accept)
            .build()

    /**
     * Fetches all assets from the release tagged "stable".
     * Returns empty list on any error — UpdateChecker handles retry on next launch.
     */
    fun fetchLatestStableAssets(): List<ReleaseAsset> = runCatching {
        val request = request("${baseUrl}repos/$repo/releases?per_page=10")

        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@runCatching emptyList()
            response.body?.string() ?: return@runCatching emptyList()
        }

        val releases = JSONArray(body)
        for (i in 0 until releases.length()) {
            val release = releases.getJSONObject(i)
            if (release.getString("tag_name") == "stable") {
                val assets = release.getJSONArray("assets")
                return@runCatching (0 until assets.length()).map { j ->
                    val asset = assets.getJSONObject(j)
                    ReleaseAsset(
                        name = asset.getString("name"),
                        downloadUrl = asset.getString("browser_download_url"),
                        size = asset.getLong("size"),
                    )
                }
            }
        }
        emptyList<ReleaseAsset>()
    }.getOrElse { emptyList() }

    /**
     * Fetches raw MANIFEST.md content from the default branch.
     * Returns null on any error.
     */
    fun fetchManifest(): String? = runCatching {
        val request = request(
            "${baseUrl}repos/$repo/contents/MANIFEST.md",
            accept = "application/vnd.github.v3.raw",
        )

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null
            else response.body?.string()
        }
    }.getOrNull()

    /**
     * Fetches core-version.json from the stable release.
     * Returns Pair(versionCode, downloadUrl) or null on any error.
     * The downloadUrl points to the APK asset in the same release.
     */
    fun fetchCoreVersion(): Pair<Int, String>? = runCatching {
        val request = request("${baseUrl}repos/$repo/releases?per_page=10")

        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.string() ?: return null
        }

        val releases = JSONArray(body)
        for (i in 0 until releases.length()) {
            val release = releases.getJSONObject(i)
            if (release.getString("tag_name") != "stable") continue

            val assets = release.getJSONArray("assets")
            var versionCode: Int? = null
            var apkUrl: String? = null

            for (j in 0 until assets.length()) {
                val asset = assets.getJSONObject(j)
                val name = asset.getString("name")
                when {
                    name == "core-version.json" -> {
                        // Fetch the JSON content (browser_download_url is public, no auth needed)
                        val versionRequest = request(asset.getString("browser_download_url"))
                        val versionBody = client.newCall(versionRequest).execute().use { r ->
                            if (r.isSuccessful) r.body?.string() else null
                        }
                        versionCode = versionBody?.let {
                            org.json.JSONObject(it).optInt("versionCode", -1).takeIf { v -> v > 0 }
                        }
                    }
                    name.endsWith(".apk") -> apkUrl = asset.getString("browser_download_url")
                }
            }
            if (versionCode != null && apkUrl != null) return Pair(versionCode, apkUrl)
        }
        null
    }.getOrNull()
}
