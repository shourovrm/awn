package app.awn.quran.feature.read.quran

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration

private const val LONG_PRESS_MS = 500L

/**
 * The platform view configuration with the 500 ms long press the ayah selection is specified for.
 */
private class FixedLongPress(private val base: ViewConfiguration) : ViewConfiguration by base {
    override val longPressTimeoutMillis: Long = LONG_PRESS_MS
}

/**
 * The [FixedLongPress] for this screen, one object for as long as the platform's configuration
 * stays. Compose restarts every touch handler whose view configuration changes, so a new object on
 * each recomposition cancelled any drag in progress whenever a page loaded.
 */
@Composable
internal fun rememberFixedLongPress(): ViewConfiguration {
    val base = LocalViewConfiguration.current
    return remember(base) { FixedLongPress(base) }
}
