package dev.oneapp.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for dynamic home card content. Add new fields with default implementations
 * to stay binary-compatible with existing compiled plugins.
 */
interface HomeCardContent {
    val label: String
    val subtitle: String get() = ""
    val icon: ImageVector? get() = null
}

/**
 * Convenience data class implementing HomeCardContent.
 * Use this in plugins to get copy() for free.
 */
data class HomeCardData(
    override val label: String,
    override val subtitle: String = "",
    override val icon: ImageVector? = null,
) : HomeCardContent

/** Internal representation of a registered home card. Not part of the plugin API. */
internal data class CardEntry(
    val pluginId: String,
    val content: StateFlow<HomeCardContent>,
    val route: String?,
    val onClick: () -> Unit,
)

data class FullScreen(
    val route: String,
    val content: @Composable () -> Unit,
)

object PluginRegistry {
    private val _cardEntries = mutableStateListOf<CardEntry>()
    private val _fullScreens = mutableStateListOf<FullScreen>()

    // pluginId → ARGB seed color as Long
    private val _pluginThemes = mutableStateMapOf<String, Long>()

    // pluginIds currently being downloaded — HomeScreen shows badge for these
    private val _updatingPluginIds = mutableStateListOf<String>()

    val cardEntries: List<CardEntry> get() = _cardEntries
    val fullScreens: List<FullScreen> get() = _fullScreens
    val pluginThemes: Map<String, Long> get() = _pluginThemes
    val updatingPluginIds: List<String> get() = _updatingPluginIds

    internal fun addCard(entry: CardEntry) {
        _cardEntries.removeAll { it.pluginId == entry.pluginId }
        _cardEntries.add(entry)
    }

    internal fun addFullScreen(screen: FullScreen) {
        _fullScreens.removeAll { it.route == screen.route }
        _fullScreens.add(screen)
    }

    internal fun setTheme(pluginId: String, seedColor: Long) {
        _pluginThemes[pluginId] = seedColor
    }

    internal fun markUpdating(pluginId: String) {
        if (pluginId !in _updatingPluginIds) _updatingPluginIds.add(pluginId)
    }

    internal fun unmarkUpdating(pluginId: String) {
        _updatingPluginIds.remove(pluginId)
    }

    // Called only by the core on plugin reload
    internal fun clear() {
        _cardEntries.clear()
        _fullScreens.clear()
        _pluginThemes.clear()
        // intentionally NOT clearing updatingPluginIds — managed by download progress
    }
}
