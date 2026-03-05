package dev.oneapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.oneapp.core.LocalPluginRegistry
import kotlinx.coroutines.launch

/**
 * Confirmation screen shown when user taps an external plugin deep link.
 * oneapp://install?id=com.alice.weather&dex=https://...&entry=dev.oneapp.plugins.WeatherPlugin&version=1
 *
 * Shows plugin details and lets the user confirm or cancel.
 * On confirm: calls [onConfirm] which triggers the DEX download in the caller.
 */
@Composable
fun InstallPluginScreen(
    pluginId: String,
    dexUrl: String,
    entryClass: String,
    version: Int,
    existingEntry: LocalPluginRegistry.PluginEntry?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onTrust: (() -> Unit)? = null,
) {
    val isUpdate = existingEntry != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(16.dp))

        Icon(
            imageVector = Icons.Default.Extension,
            contentDescription = null,
            modifier = Modifier.size(48.dp).align(Alignment.CenterHorizontally),
            tint = MaterialTheme.colorScheme.primary,
        )

        Text(
            text = if (isUpdate) "Update Plugin" else "Install Plugin",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        HorizontalDivider()

        DetailRow("Plugin ID", pluginId)
        DetailRow("Version", "v$version${if (isUpdate) " (installed: v${existingEntry!!.version})" else ""}")
        DetailRow("Source", dexUrl)
        DetailRow("Entry class", entryClass)

        HorizontalDivider()

        // Security notice
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Warning, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(20.dp))
                Text(
                    "Only install plugins from sources you trust. Plugins run inside this app " +
                    "and can access any permission you have granted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                Button(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                    Text(if (isUpdate) "Update" else "Install")
                }
            }
            if (onTrust != null && existingEntry?.trust == dev.oneapp.plugin.PluginTrust.EXTERNAL) {
                TextButton(
                    onClick = onTrust,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Mark as trusted (enables POST requests and dangerous permissions)",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (label == "Source" || label == "Entry class") FontFamily.Monospace else FontFamily.Default,
            maxLines = 3)
    }
}
