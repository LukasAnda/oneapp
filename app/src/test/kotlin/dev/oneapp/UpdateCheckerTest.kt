package dev.oneapp

import dev.oneapp.core.GitHubReleasesApi
import dev.oneapp.core.ReleaseAsset
import dev.oneapp.core.UpdateChecker
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpdateCheckerTest {

    private val api = mockk<GitHubReleasesApi>()

    @Test
    fun `filterDexAssets returns only dex files`() {
        val assets = listOf(
            ReleaseAsset("plugin-notes.dex", "https://example.com/notes.dex", 100),
            ReleaseAsset("core-1.0.apk", "https://example.com/core.apk", 5000),
            ReleaseAsset("plugin-camera.dex", "https://example.com/camera.dex", 200),
        )
        val checker = UpdateChecker(api = api, pluginsDir = File("/tmp"), codeCacheDir = File("/tmp"))
        val dexAssets = checker.filterDexAssets(assets)

        assertEquals(2, dexAssets.size)
        assertTrue(dexAssets.all { it.name.endsWith(".dex") })
        assertTrue(dexAssets.all { it.name.startsWith("plugin-") })
    }

    @Test
    fun `pluginIdFromFilename strips prefix and suffix correctly`() {
        val checker = UpdateChecker(api = api, pluginsDir = File("/tmp"), codeCacheDir = File("/tmp"))
        assertEquals("com.alice.weather", checker.pluginIdFromFilename("plugin-com.alice.weather.dex"))
        assertEquals("dev.oneapp.hello", checker.pluginIdFromFilename("plugin-dev.oneapp.hello.dex"))
    }
}
