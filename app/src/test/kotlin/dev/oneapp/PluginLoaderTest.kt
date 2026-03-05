package dev.oneapp

import dev.oneapp.plugin.PluginLoader
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PluginLoaderTest {

    @Test
    fun `entryClassNameFromManifest extracts class from manifest table`() {
        val manifest = """
            ## Installed Plugins
            | id | version | permissions | entry_class | description |
            |----|---------|-------------|-------------|-------------|
            | com.user.notes | 1 | NONE | dev.oneapp.plugins.NotesPlugin | Simple note-taking |
            | com.user.camera | 1 | CAMERA | dev.oneapp.plugins.CameraPlugin | Photo capture |
        """.trimIndent()

        val loader = PluginLoader(pluginsDir = File("/tmp"), codeCacheDir = File("/tmp"))
        assertEquals("dev.oneapp.plugins.NotesPlugin", loader.entryClassNameFromManifest(manifest, "com.user.notes"))
        assertEquals("dev.oneapp.plugins.CameraPlugin", loader.entryClassNameFromManifest(manifest, "com.user.camera"))
    }

    @Test
    fun `entryClassNameFromManifest returns null for unknown plugin id`() {
        val loader = PluginLoader(pluginsDir = File("/tmp"), codeCacheDir = File("/tmp"))
        assertNull(loader.entryClassNameFromManifest("", "com.unknown.plugin"))
    }

    @Test
    fun `entryClassNameFromManifest ignores header row`() {
        val manifest = """
            | id | version | permissions | entry_class | description |
            |----|---------|-------------|-------------|-------------|
        """.trimIndent()
        val loader = PluginLoader(pluginsDir = File("/tmp"), codeCacheDir = File("/tmp"))
        assertNull(loader.entryClassNameFromManifest(manifest, "id"))
    }
}
