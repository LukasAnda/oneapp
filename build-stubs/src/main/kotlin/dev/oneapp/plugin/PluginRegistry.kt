package dev.oneapp.plugin

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

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

data class FullScreen(
    val route: String,
    val content: @Composable () -> Unit,
)

/** Stub — plugins do not read from the registry directly. */
object PluginRegistry
