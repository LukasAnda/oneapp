package dev.oneapp.plugins

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.oneapp.plugin.Plugin
import dev.oneapp.plugin.PluginHost

class HelloPlugin : Plugin {
    override val id = "dev.oneapp.hello"
    override val version = 1
    override val permissions = emptyList<String>()

    override fun register(host: PluginHost) {
        host.addHomeCard(
            config = """{"label":"Hello"}""",
            icon = Icons.Default.WavingHand,
            onClick = {},
        )
        host.addFullScreen("hello_main") {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Hello from OneApp!", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("The evolution pipeline works.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
