package dev.oneapp

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import dev.oneapp.core.LocalPluginRegistry
import dev.oneapp.core.PendingApkUpdate
import dev.oneapp.core.PermissionBroker
import dev.oneapp.plugin.PluginHostImpl
import dev.oneapp.plugin.PluginRegistry
import dev.oneapp.plugin.PluginTrust
import dev.oneapp.ui.HomeScreen
import dev.oneapp.ui.InstallPluginScreen
import dev.oneapp.ui.theme.OneAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
                // Navigation 3 back stack — drives all navigation in the app.
                // Plugin full-screens are resolved dynamically from PluginRegistry at render time,
                // so they work even if plugins load after the back stack entry was pushed.
                val backStack = remember { NavBackStack<AppNavKey>(HomeKey) }
                var pendingApkUpdate by remember { mutableStateOf<PendingApkUpdate?>(null) }

                fun showSnackbar(msg: String) {
                    lifecycleScope.launch { snackbarHostState.showSnackbar(msg) }
                }

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
                                showSnackbar("Update failed — check your connection")
                            }
                        }
                    }
                    pendingApkUpdate = null
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    containerColor = MaterialTheme.colorScheme.background,
                ) { paddingValues ->
                    NavDisplay(
                        backStack = backStack,
                        modifier = Modifier.fillMaxSize(),
                        onBack = { if (backStack.size > 1) backStack.removeLast() },
                        entryProvider = entryProvider<AppNavKey> {
                            // Home screen — the plugin card grid
                            entry<HomeKey> { _ ->
                                HomeScreen(
                                    onNavigate = { route -> backStack.add(PluginScreenKey(route)) },
                                    onError = { msg -> showSnackbar(msg) },
                                    contentPadding = paddingValues,
                                )
                            }

                            // Plugin full-screens — resolved dynamically from PluginRegistry.
                            // Since fullScreens is a mutableStateListOf, this composable reacts to
                            // plugins loading and shows the correct screen once available.
                            entry<PluginScreenKey> { key ->
                                val screen = PluginRegistry.fullScreens.find { it.route == key.route }
                                // Assumes full-screen route matches the card's route string.
                                // Plugins without a matching card entry use the default theme.
                                val pluginId = PluginRegistry.cardEntries.find { it.route == key.route }?.pluginId
                                val seedColor = pluginId?.let { PluginRegistry.pluginThemes[it] }

                                if (screen != null) {
                                    if (seedColor != null) {
                                        val darkTheme = isSystemInDarkTheme()
                                        val base = if (darkTheme) darkColorScheme() else lightColorScheme()
                                        // Simplified seed theming: only primary is overridden.
                                        // onPrimary assumes a dark-enough seed — full tonal palette
                                        // generation (material-color-utilities) is a future improvement.
                                        MaterialTheme(colorScheme = base.copy(primary = Color(seedColor), onPrimary = Color.White)) {
                                            screen.content()
                                        }
                                    } else {
                                        screen.content()
                                    }
                                } else {
                                    Column(
                                        modifier = Modifier.fillMaxSize().padding(24.dp),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text(
                                            "Plugin screen '${key.route}' is not available.",
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                        Spacer(Modifier.height(16.dp))
                                        Button(onClick = { backStack.removeLastOrNull() }) {
                                            Text("Go back")
                                        }
                                    }
                                }
                            }

                            // External plugin install confirmation screen
                            entry<InstallPluginKey> { key ->
                                InstallPluginScreen(
                                    pluginId = key.pluginId,
                                    dexUrl = key.dexUrl,
                                    entryClass = key.entryClass,
                                    version = key.version,
                                    existingEntry = app.localPluginRegistry.get(key.pluginId),
                                    onConfirm = {
                                        lifecycleScope.launch {
                                            installExternalPlugin(
                                                app = app,
                                                pluginId = key.pluginId,
                                                dexUrl = key.dexUrl,
                                                entryClass = key.entryClass,
                                                version = key.version,
                                                snackbarHostState = snackbarHostState,
                                                hash = key.hash,
                                            )
                                            backStack.removeLastOrNull()
                                        }
                                    },
                                    onCancel = { backStack.removeLastOrNull() },
                                    onTrust = {
                                        app.localPluginRegistry.setTrust(key.pluginId, PluginTrust.TRUSTED)
                                        backStack.removeLastOrNull()
                                    },
                                )
                            }
                        },
                    )
                }

                // Run update check + plugin load on first composition
                LaunchedEffect(Unit) {
                    lifecycleScope.launch {
                        // Fetch manifest on IO thread first — must happen before download/load
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

                        // Load all plugins — fullScreens populated here triggers NavDisplay recompose
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
                        backStack.add(
                            InstallPluginKey(
                                pluginId = params.pluginId,
                                dexUrl = params.dexUrl,
                                entryClass = params.entryClass,
                                version = params.version,
                                hash = params.hash,
                            )
                        )
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
        if (expectedHash == null) return true
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
