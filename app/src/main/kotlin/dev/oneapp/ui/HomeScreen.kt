package dev.oneapp.ui

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.oneapp.plugin.CardEntry
import dev.oneapp.plugin.PluginRegistry

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    onError: (String) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val entries = PluginRegistry.cardEntries

    if (entries.isEmpty()) {
        EmptyState(contentPadding)
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(140.dp),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = contentPadding.calculateTopPadding() + 16.dp,
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(entries, key = { it.pluginId }) { entry ->
                PluginCard(entry = entry, onNavigate = onNavigate, onError = onError)
            }
        }
    }
}

@Composable
private fun PluginCard(entry: CardEntry, onNavigate: (String) -> Unit, onError: (String) -> Unit) {
    val content by entry.content.collectAsState()
    val isUpdating = entry.pluginId in PluginRegistry.updatingPluginIds

    // Tint from registered seed color, fallback to theme primary
    val seedColor = PluginRegistry.pluginThemes[entry.pluginId]
    val iconTint = if (seedColor != null) Color(seedColor) else MaterialTheme.colorScheme.primary

    ElevatedCard(
        onClick = {
            runCatching { entry.route?.let(onNavigate) ?: entry.onClick() }
                .onFailure { e ->
                    Log.e("HomeScreen", "Plugin navigation failed for '${entry.pluginId}'", e)
                    onError("Plugin '${content.label}' failed: ${e.message}")
                }
        },
        modifier = Modifier.aspectRatio(1f),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize().padding(12.dp),
            ) {
                val icon = content.icon ?: Icons.Default.Extension
                Icon(
                    imageVector = icon,
                    contentDescription = content.label,
                    modifier = Modifier.size(40.dp),
                    tint = iconTint,
                )
                Spacer(Modifier.height(8.dp))
                Text(content.label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                if (content.subtitle.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        content.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (isUpdating) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(16.dp)
                        .align(Alignment.TopEnd),
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(contentPadding: PaddingValues = PaddingValues(0.dp)) {
    Box(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Default.Extension, contentDescription = null,
                modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(16.dp))
            Text("No plugins yet", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Open a GitHub issue on your fork and label it 'evolve' to request your first feature.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
