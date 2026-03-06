package dev.oneapp.ui

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.oneapp.plugin.HomeCard
import dev.oneapp.plugin.PluginRegistry

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    onError: (String) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val cards = PluginRegistry.homeCards

    if (cards.isEmpty()) {
        EmptyState(contentPadding)
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(140.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = contentPadding.calculateTopPadding() + 16.dp,
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(cards, key = { it.pluginId }) { card ->
                PluginCard(card = card, onNavigate = onNavigate, onError = onError)
            }
        }
    }
}

@Composable
private fun PluginCard(card: HomeCard, onNavigate: (String) -> Unit, onError: (String) -> Unit) {
    ElevatedCard(
        onClick = {
            runCatching { card.route?.let(onNavigate) ?: card.onClick() }
                .onFailure { e ->
                    Log.e("HomeScreen", "Plugin navigation failed for '${card.pluginId}'", e)
                    onError("Plugin '${card.label}' failed: ${e.message}")
                }
        },
        modifier = Modifier.aspectRatio(1f),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(12.dp),
        ) {
            Icon(imageVector = card.icon, contentDescription = card.label, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(8.dp))
            Text(card.label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
            if (card.subtitle.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(card.subtitle, style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
