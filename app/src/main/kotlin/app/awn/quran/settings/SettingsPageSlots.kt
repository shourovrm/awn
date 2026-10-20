package app.awn.quran.settings

import androidx.compose.runtime.Composable

/**
 * The rows of the four settings pages that other modules own. Each slot draws rows only: Settings
 * supplies the top bar and the scrolling column, so a slot must not scroll or draw a bar itself.
 *
 * The maintainer connects `MushafSettingsPage`, `ReadingSettingsPage`, `AudioSettingsPage` and
 * `ReciteSettingsPage` here after merging; [reciteModule] is followed by the "Memorised after" row,
 * which :app owns.
 */
class SettingsPageSlots(
    val mushaf: @Composable () -> Unit,
    val reading: @Composable () -> Unit,
    val audio: @Composable () -> Unit,
    val reciteModule: @Composable () -> Unit,
)
