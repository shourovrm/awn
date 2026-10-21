package app.awn.quran.tabs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import app.awn.quran.core.ui.theme.LocalNavigationBarBackground

/**
 * The three tabs over the bottom bar. A tab that is left keeps its saved state (scroll position and
 * anything held with `rememberSaveable`), so coming back finds it where it was. Back on the Learn
 * or Hifz tab goes to the Quran tab first, as the home tab; Back there leaves the app.
 */
@Composable
fun TabsHost(
    selected: AwnTab,
    onSelect: (AwnTab) -> Unit,
    quranTab: @Composable () -> Unit,
    learnTab: @Composable () -> Unit,
    hifzTab: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colours = tabBarColours()
    val stateHolder = rememberSaveableStateHolder()
    BackHandler(enabled = selected != AwnTab.Quran) { onSelect(AwnTab.Quran) }
    Column(modifier.fillMaxSize().background(colours.background)) {
        // The tab bar paints the space under the navigation bar, so the tab's own theme must not
        // pad for it a second time.
        Box(Modifier.weight(1f).consumeWindowInsets(WindowInsets.navigationBars)) {
            CompositionLocalProvider(LocalNavigationBarBackground provides colours.barBackground) {
                stateHolder.SaveableStateProvider(selected.name) {
                    when (selected) {
                        AwnTab.Quran -> quranTab()
                        AwnTab.Learn -> learnTab()
                        AwnTab.Hifz -> hifzTab()
                    }
                }
            }
        }
        AwnTabBar(selected = selected, onSelect = onSelect)
    }
}
