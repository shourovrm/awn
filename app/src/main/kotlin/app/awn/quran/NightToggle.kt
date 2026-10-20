package app.awn.quran

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.awn.quran.core.ui.theme.ColourSettings
import kotlinx.coroutines.launch

/**
 * The state and action behind the Quran screen's day/night button: [isNight] is whether a night
 * palette is on screen now, and [toggle] flips it and stops following the device. Pass them to
 * `QuranScreenSlots.isNight` and `QuranScreenSlots.onToggleNight`.
 */
class NightToggle(val isNight: Boolean, val toggle: () -> Unit)

@Composable
fun rememberNightToggle(preferences: AppPreferences): NightToggle {
    val settings by preferences.colourSettings.collectAsStateWithLifecycle(ColourSettings())
    val deviceIsDark = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()
    return NightToggle(
        isNight = settings.isNight(deviceIsDark),
        toggle = {
            scope.launch { preferences.updateColourSettings { it.toggledNight(deviceIsDark) } }
        },
    )
}
