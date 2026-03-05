package dev.oneapp

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.oneapp.core.LocalPluginRegistry
import dev.oneapp.core.PendingApkUpdate
import dev.oneapp.core.PermissionBroker
import dev.oneapp.plugin.PluginHostImpl
import dev.oneapp.plugin.PluginRegistry
import dev.oneapp.plugin.PluginTrust
import dev.oneapp.ui.HomeScreen
import dev.oneapp.ui.InstallPluginScreen
import dev.oneapp.ui.theme.OneAppTheme
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {

    private lateinit var permissionBroker: PermissionBroker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        val app = application as OneApp
        permissionBroker = PermissionBroker(this)

        // Parse deep link if launched via oneapp://install
        val deepLinkInstall = intent?.data?.takeIf { it.scheme == "oneapp" && it.host == "install" }
            ?.let { parseInstallDeepLink(it) }

        setContent {
            OneAppTheme {
                val snackbarHostState = remember { SnackbarHostState() }
                val navController = rememberNavController()
                var pendingApkUpdate by remember { mutableStateOf<PendingApkUpdate?>(null) }

                // Show snackbar when APK update is pending
                LaunchedEffect(pendingApkUpdate) {
                    val update = pendingApkUpdate ?: return@LaunchedEffect
                    val result = snackbarHostState.showSnackbar(
                        message = "App update available",
                        actionLabel = "Install",
                        duration = SnackbarDuration.Indefinite,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        lifecycleScope.launch {
                            runCatching {
                                app.apkInstaller.downloadAndInstall(update.downloadUrl, update.filename)
                            }.onFailure { e ->
                                Log.e(TAG, "APK install failed", e)
                                snackbarHostState.showSnackbar("Update failed — check your connection")
                            }
                        }
                    }
                    pendingApkUpdate = null
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    containerColor = MaterialTheme.colorScheme.background,
                ) { _ ->
                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(onNavigate = { route -> navController.navigate(route) })
                        }

                        // External plugin install confirmation screen
                        composable("install_plugin") {
                            val args = navController.currentBackStackEntry?.savedStateHandle
                            val pluginId = args?.get<String>("pluginId") ?: return@composable
                            val dexUrl = args.get<String>("dexUrl") ?: return@composable
                            val entryClass = args.get<String>("entryClass") ?: return@composable
                            val version = args.get<Int>("version") ?: 1
                            val hash = args.get<String>("hash")

                            InstallPluginScreen(
                                pluginId = pluginId,
                                dexUrl = dexUrl,
                                entryClass = entryClass,
                                version = version,
                                existingEntry = app.localPluginRegistry.get(pluginId),
                                onConfirm = {
                                    lifecycleScope.launch {
                                        installExternalPlugin(
                                            app = app,
                                            pluginId = pluginId,
                                            dexUrl = dexUrl,
                                            entryClass = entryClass,
                                            version = version,
                                            snackbarHostState = snackbarHostState,
                                            hash = hash,
                                        )
                                        navController.popBackStack()
                                    }
                                },
                                onCancel = { navController.popBackStack() },
                                onTrust = {
                                    app.localPluginRegistry.setTrust(pluginId, PluginTrust.TRUSTED)
                                    navController.popBackStack()
                                },
                            )
                        }

                        // Plugin full-screens registered by loaded plugins
                        PluginRegistry.fullScreens.forEach { screen ->
                            composable(screen.route) { screen.content() }
                        }
                    }
                }

                // Run update check + plugin load on first composition
                LaunchedEffect(Unit) {
                    lifecycleScope.launch {
                        // Fetch manifest on IO thread first — must happen before download/load
                        // because both need entry class names from the manifest.
                        // (Calling this on the main thread always throws NetworkOnMainThreadException.)
                        val freshManifest = withContext(Dispatchers.IO) {
                            runCatching { app.api.fetchManifest() }.getOrNull()
                        }
                        if (!freshManifest.isNullOrEmpty()) {
                            app.manifestContent = freshManifest
                        }

                        // Check for APK update
                        runCatching {
                            pendingApkUpdate = app.updateChecker.checkForApkUpdate()
                        }.onFailure { Log.e(TAG, "APK update check failed", it) }

                        // Download new plugin DEX files and register them
                        runCatching {
                            app.updateChecker.checkAndDownload(app.manifestContent)
                        }.onFailure { Log.e(TAG, "Plugin update check failed", it) }

                        // Load all plugins
                        PluginRegistry.clear()
                        app.pluginLoader.loadAll(
                            manifestContent = app.manifestContent,
                            hostFactory = { pluginId, trust ->
                                PluginHostImpl(
                                    context = applicationContext,
                                    coroutineScope = lifecycleScope,
                                    permissionBroker = permissionBroker,
                                    client = app.httpClient,
                                    pluginDataDir = File(filesDir, "plugin_data"),
                                    pluginId = pluginId,
                                    trust = trust,
                                )
                            },
                            registry = app.localPluginRegistry,
                        )
                    }

                    // Navigate to install screen if launched from deep link
                    deepLinkInstall?.let { params ->
                        navController.currentBackStackEntry?.savedStateHandle?.apply {
                            set("pluginId", params.pluginId)
                            set("dexUrl", params.dexUrl)
                            set("entryClass", params.entryClass)
                            set("version", params.version)
                            set("hash", params.hash)
                        }
                        navController.navigate("install_plugin")
                    }
                }
            }
        }
    }

    private fun parseInstallDeepLink(uri: Uri): DeepLinkInstallParams? {
        val pluginId = uri.getQueryParameter("id") ?: return null
        val dexUrl = uri.getQueryParameter("dex") ?: return null
        val entryClass = uri.getQueryParameter("entry") ?: return null
        val version = uri.getQueryParameter("version")?.toIntOrNull() ?: 1
        val hash = uri.getQueryParameter("hash")
        return DeepLinkInstallParams(pluginId, dexUrl, entryClass, version, hash)
    }

    private suspend fun installExternalPlugin(
        app: OneApp,
        pluginId: String,
        dexUrl: String,
        entryClass: String,
        version: Int,
        snackbarHostState: SnackbarHostState,
        hash: String? = null,
    ) {
        runCatching {
            // Download DEX to plugins dir
            val pluginsDir = File(filesDir, "plugins").also { it.mkdirs() }
            val dexFilename = "plugin-$pluginId.dex"
            val destFile = File(pluginsDir, dexFilename)

            val request = okhttp3.Request.Builder().url(dexUrl).build()
            app.httpClient.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Download failed: ${response.code}" }
                response.body!!.byteStream().use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                }
            }

            if (!verifyDexHash(destFile, hash)) {
                destFile.delete()
                snackbarHostState.showSnackbar("Install failed: plugin integrity check failed")
                return
            }

            // Register in local registry
            app.localPluginRegistry.register(
                LocalPluginRegistry.PluginEntry(
                    id = pluginId,
                    entryClass = entryClass,
                    version = version,
                    source = "external",
                    dexUrl = dexUrl,
                    trust = PluginTrust.EXTERNAL,
                )
            )
            snackbarHostState.showSnackbar("Plugin installed — restart the app to load it")
        }.onFailure { e ->
            Log.e(TAG, "External plugin install failed", e)
            snackbarHostState.showSnackbar("Install failed: ${e.message}")
        }
    }

    private fun verifyDexHash(file: File, expectedHash: String?): Boolean {
        if (expectedHash == null) return true // no hash provided — skip verification
        val algo = expectedHash.substringBefore(":")
        val expected = expectedHash.substringAfter(":")
        val digest = java.security.MessageDigest.getInstance(algo.uppercase())
        val actual = digest.digest(file.readBytes())
            .joinToString("") { "%02x".format(it) }
        val match = actual.equals(expected, ignoreCase = true)
        if (!match) Log.e(TAG, "DEX hash mismatch for ${file.name}: expected $expected got $actual")
        return match
    }
}

private data class DeepLinkInstallParams(
    val pluginId: String,
    val dexUrl: String,
    val entryClass: String,
    val version: Int,
    val hash: String? = null,
)
