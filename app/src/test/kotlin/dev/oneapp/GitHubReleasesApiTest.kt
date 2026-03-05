package dev.oneapp

import dev.oneapp.core.GitHubReleasesApi
import dev.oneapp.core.ReleaseAsset
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitHubReleasesApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: GitHubReleasesApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = GitHubReleasesApi(
            client = OkHttpClient(),
            baseUrl = server.url("/").toString(),
            repo = "user/oneapp",
            token = "test-token",
        )
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `fetchLatestStableAssets returns parsed assets from stable release`() {
        server.enqueue(MockResponse().setBody("""
            [
              {
                "tag_name": "stable",
                "assets": [
                  {"name": "plugin-notes.dex", "browser_download_url": "https://example.com/notes.dex", "size": 1024},
                  {"name": "core-1.0.apk", "browser_download_url": "https://example.com/core.apk", "size": 2048}
                ]
              }
            ]
        """.trimIndent()).setResponseCode(200))

        val assets = api.fetchLatestStableAssets()

        assertEquals(2, assets.size)
        assertEquals("plugin-notes.dex", assets[0].name)
        assertEquals("https://example.com/notes.dex", assets[0].downloadUrl)
        assertEquals(1024L, assets[0].size)
    }

    @Test
    fun `fetchLatestStableAssets returns empty list on non-200`() {
        server.enqueue(MockResponse().setResponseCode(404))
        val assets = api.fetchLatestStableAssets()
        assertTrue(assets.isEmpty())
    }

    @Test
    fun `fetchManifest returns raw manifest content`() {
        server.enqueue(MockResponse().setBody("# App Manifest\ncore_version: 1").setResponseCode(200))
        val content = api.fetchManifest()
        assertTrue(content!!.contains("core_version: 1"))
    }
}
