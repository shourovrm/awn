package app.awn.quran.tabs

import androidx.compose.runtime.Composable
import app.awn.quran.core.data.AyahRef

/** What a tab's content may ask the app to do. */
class TabActions(
    val openSettings: () -> Unit,
    /** Opens the Quran screen above the tabs at [page]; [ayah] is selected when given. */
    val openQuran: (page: Int, ayah: AyahRef?) -> Unit,
)

/**
 * The parts of the tabs that other features plug into. [hifzTab] draws the whole Hifz tab,
 * including its own top bar; [hifzToday] and [learnToday] supply the Today tiles on the Quran
 * tab. [defaultTabSlots] builds the real ones from the app's services.
 */
class TabSlots(
    val hifzTab: @Composable (TabActions) -> Unit,
    val hifzToday: TodayProvider,
    val learnToday: TodayProvider,
)
