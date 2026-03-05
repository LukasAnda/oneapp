package dev.oneapp

import android.app.Application
import dev.oneapp.core.ApkInstaller
import dev.oneapp.core.GitHubReleasesApi
import dev.oneapp.core.LocalPluginRegistry
import dev.oneapp.core.UpdateChecker
import dev.oneapp.plugin.PluginLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.io.File

class OneApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val httpClient = OkHttpClient()

    lateinit var api: GitHubReleasesApi
    lateinit var updateChecker: UpdateChecker
    lateinit var pluginLoader: PluginLoader
    lateinit var localPluginRegistry: LocalPluginRegistry
    lateinit var apkInstaller: ApkInstaller

    var manifestContent: String = ""

    override fun onCreate() {
        super.onCreate()

        val pluginsDir = File(filesDir, "plugins")

        localPluginRegistry = LocalPluginRegistry(this)
        apkInstaller = ApkInstaller(context = this, client = httpClient)

        api = GitHubReleasesApi(
            client = httpClient,
            repo = BuildConfig.GITHUB_REPO,
            token = BuildConfig.GITHUB_TOKEN,
        )

        updateChecker = UpdateChecker(
            api = api,
            pluginsDir = pluginsDir,
            client = httpClient,
            installedVersionCode = BuildConfig.VERSION_CODE,
            registry = localPluginRegistry,
        )

        pluginLoader = PluginLoader(pluginsDir = pluginsDir)

        // Note: manifest is fetched asynchronously in MainActivity on the IO thread.
        // Fetching it here on the main thread always throws NetworkOnMainThreadException.
    }
}
