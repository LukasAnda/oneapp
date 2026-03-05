package dev.oneapp.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.vector.ImageVector

data class HomeCard(
    val pluginId: String,
    val label: String,
    val subtitle: String = "",
    val icon: ImageVector,
    val route: String?,
    val onClick: () -> Unit,
)

data class FullScreen(
    val route: String,
    val content: @Composable () -> Unit,
)

/**
 * Holds UI registrations from all loaded plugins.
 *
 * Plugins interact only via addCard/addFullScreen — they cannot enumerate,
 * modify, or clear other plugins' registrations.
 */
object PluginRegistry {
    private val _homeCards = mutableStateListOf<HomeCard>()
    private val _fullScreens = mutableStateListOf<FullScreen>()

    // Read-only views for HomeScreen
    val homeCards: List<HomeCard> get() = _homeCards
    val fullScreens: List<FullScreen> get() = _fullScreens

    // Called by PluginHostImpl only — not exposed to Plugin implementations
    internal fun addCard(card: HomeCard) {
        _homeCards.removeAll { it.pluginId == card.pluginId && it.label == card.label }
        _homeCards.add(card)
    }

    internal fun addFullScreen(screen: FullScreen) {
        _fullScreens.removeAll { it.route == screen.route }
        _fullScreens.add(screen)
    }

    // Called only by the core on app restart
    internal fun clear() {
        _homeCards.clear()
        _fullScreens.clear()
    }
}
