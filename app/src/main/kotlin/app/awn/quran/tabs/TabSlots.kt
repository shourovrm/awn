package app.awn.quran.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.awn.quran.R
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.ui.components.AwnIconButton
import app.awn.quran.core.ui.components.AwnTopBar
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnField
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.core.ui.theme.AwnTypography

/** What a tab's content may ask the app to do. */
class TabActions(
    val openSettings: () -> Unit,
    /** Opens the Quran screen above the tabs at [page]; [ayah] is selected when given. */
    val openQuran: (page: Int, ayah: AyahRef?) -> Unit,
)

/**
 * The parts of the tabs that other features plug into. [hifzTab] draws the whole Hifz tab,
 * including its own top bar; [todayProviders] supply the rows of the Today section on the Quran
 * tab.
 */
class TabSlots(
    val hifzTab: @Composable (TabActions) -> Unit = { HifzPlaceholderTab(it) },
    val todayProviders: List<TodayProvider> = listOf(LearnTodayProvider),
)

/** Stands in for the Hifz tab until the Hifz module is wired in. */
@Composable
fun HifzPlaceholderTab(actions: TabActions) {
    AwnTheme(field = AwnField.Paper, barBehindStatusBar = true) {
        Column(Modifier.fillMaxSize()) {
            AwnTopBar(title = stringResource(R.string.tab_hifz)) {
                AwnIconButton(
                    icon = R.drawable.ic_app_settings,
                    contentDescription = stringResource(R.string.index_settings),
                    onClick = actions.openSettings,
                    tint = Awn.field.onBar,
                )
            }
            Box(Modifier.fillMaxSize().padding(AwnSpacing.screenMargin), Alignment.Center) {
                Text(
                    text = stringResource(R.string.hifz_placeholder),
                    style = AwnTypography.body,
                    color = Awn.field.content,
                )
            }
        }
    }
}
