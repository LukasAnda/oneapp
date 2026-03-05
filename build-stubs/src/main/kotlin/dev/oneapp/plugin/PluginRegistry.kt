package dev.oneapp.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.vector.ImageVector

data class HomeCard(
    val pluginId: String,
    val label: String,
    val icon: ImageVector,
    val route: String?,
    val onClick: () -> Unit,
)

data class FullScreen(
    val route: String,
    val content: @Composable () -> Unit,
)

/**
 * Holds all UI registrations made by loaded plugins.
 * Compose state — HomeScreen observes this and recomposes when plugins load.
 */
object PluginRegistry {
    val homeCards = mutableStateListOf<HomeCard>()
    val fullScreens = mutableStateListOf<FullScreen>()

    fun clear() {
        homeCards.clear()
        fullScreens.clear()
    }
}
