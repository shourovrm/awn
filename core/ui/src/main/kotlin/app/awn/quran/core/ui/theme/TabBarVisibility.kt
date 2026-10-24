package app.awn.quran.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Lets a tab hide the bottom tab bar that the host draws under it. The host provides it; outside a
 * host there is no bar, so the default does nothing.
 */
val LocalTabBarHider = staticCompositionLocalOf<(Boolean) -> Unit> { {} }

/**
 * Hides the tab bar while [isOnInnerPage] is true. The bar shows only on the root page of a tab, so
 * a tab calls this with true on every page it opens above that root. Leaving the tab, or the page
 * going away, shows the bar again.
 */
@Composable
fun HideTabBarOnInnerPage(isOnInnerPage: Boolean) {
    val hideTabBar = LocalTabBarHider.current
    DisposableEffect(isOnInnerPage) {
        hideTabBar(isOnInnerPage)
        onDispose { hideTabBar(false) }
    }
}
