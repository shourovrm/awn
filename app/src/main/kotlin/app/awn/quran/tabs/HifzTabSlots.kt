package app.awn.quran.tabs

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.awn.quran.R
import app.awn.quran.core.ui.components.AwnIconButton
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnField
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.hifz.HifzTab
import app.awn.quran.feature.hifz.HifzTodayLine
import app.awn.quran.feature.hifz.rememberHifzTodayLine
import app.awn.quran.services.AwnServices

/** The slots with the real Hifz tab and the Today providers of Hifz and Learn. */
fun defaultTabSlots(services: AwnServices): TabSlots =
    TabSlots(
        hifzTab = { actions ->
            HifzTabContent(
                services = services,
                actions = actions,
            )
        },
        hifzToday =
            HifzTodayProvider(
                line = { rememberHifzTodayLine(services.hifz.controller) },
                startNextPart = {
                    services.hifz.controller.startNextPart()?.let(services::startHifzPractice)
                },
            ),
        learnToday = LearnTodayProvider,
    )

@Composable
private fun HifzTabContent(services: AwnServices, actions: TabActions) {
    AwnTheme(field = AwnField.Paper, barBehindStatusBar = true) {
        HifzTab(
            controller = services.hifz.controller,
            quranText = services.coreData.quranText,
            onStartPractice = services::startHifzPractice,
            // The Quran screen opens at the ayah; the page argument is ignored when an ayah is
            // given.
            onOpenAyah = { ayah -> actions.openQuran(FIRST_PAGE, ayah) },
            topBarActions = {
                AwnIconButton(
                    icon = R.drawable.ic_app_settings,
                    contentDescription = stringResource(R.string.index_settings),
                    onClick = actions.openSettings,
                    tint = Awn.field.onBar,
                )
            },
        )
    }
}

private const val FIRST_PAGE = 1

/**
 * The Hifz tile of the Quran tab: [line] is today's one line from the Hifz feature (null when
 * there is no plan or nothing to do). A tap shows the Hifz tab, so Back from the practice returns
 * there, then [startNextPart] opens the next part when one is left.
 */
class HifzTodayProvider(
    private val line: @Composable () -> HifzTodayLine?,
    private val startNextPart: () -> Unit,
) : TodayProvider {
    @Composable
    override fun item(navigation: TodayNavigation): TodayItem? {
        val today = line() ?: return null
        return TodayItem(
            title = today.title,
            detail = today.detail,
            icon = R.drawable.ic_app_psychology,
            onOpen = {
                navigation.openHifzTab()
                startNextPart()
            },
        )
    }
}
