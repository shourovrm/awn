package app.awn.quran.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/** Keeps the screen lit while this is in the composition and [enabled] is true. */
@Composable
fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        // Back to the default on leaving, so no other screen inherits the flag.
        onDispose { view.keepScreenOn = false }
    }
}
