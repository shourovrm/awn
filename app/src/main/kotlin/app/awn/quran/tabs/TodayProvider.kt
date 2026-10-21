package app.awn.quran.tabs

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.awn.quran.R
import app.awn.quran.core.data.AyahRef
import app.awn.quran.feature.learn.learnDueAyahCount
import app.awn.quran.feature.learn.learnFirstDueAyah
import kotlinx.coroutines.launch

/** The most rows the Today section shows, whatever the providers supply. */
const val MAX_TODAY_ITEMS = 2

/**
 * One compact row of the Today section on the Quran tab. [detail] states today's load ("Revise 12
 * ayahs"), never a backlog; [onOpen] starts it.
 */
data class TodayItem(
    val title: String,
    val detail: String,
    @DrawableRes val icon: Int,
    val onOpen: () -> Unit,
)

/** What a provider may do when its row is tapped. */
class TodayNavigation(
    val openLearnTab: () -> Unit,
    val openHifzTab: () -> Unit,
    /** Opens the Quran screen at [AyahRef] in recite mode with the words hidden. */
    val reviseAt: (AyahRef) -> Unit,
)

/**
 * A feature's offer for the Today section. The Learn and Hifz features each have one; :app lists
 * them in [TabSlots.todayProviders]. [item] runs in composition, so the strings follow the app
 * language, and returns null when the feature has nothing to do today; the section then leaves that
 * row out, and the whole section when every provider returns null.
 */
fun interface TodayProvider {
    @Composable fun item(navigation: TodayNavigation): TodayItem?
}

/** The rows to draw: the providers' rows that exist, at most [MAX_TODAY_ITEMS], in list order. */
fun visibleTodayItems(items: List<TodayItem?>): List<TodayItem> =
    items.filterNotNull().take(MAX_TODAY_ITEMS)

@Composable
fun rememberTodayItems(
    providers: List<TodayProvider>,
    navigation: TodayNavigation,
): List<TodayItem> = visibleTodayItems(providers.map { it.item(navigation) })

/**
 * The most ayahs the Learn row offers in one day. A user who skipped a week has hundreds due, and a
 * row that says so reads as a debt; the row offers a day's worth.
 */
const val LEARN_TODAY_CAP = 10

/** The ayah count the Learn row shows for [dueCount] due ayahs, or null when none is due. */
fun learnTodayCount(dueCount: Int): Int? = dueCount.takeIf { it > 0 }?.coerceAtMost(LEARN_TODAY_CAP)

/** The Learn row: revise the ayahs due today, capped at [LEARN_TODAY_CAP]. */
object LearnTodayProvider : TodayProvider {
    @Composable
    override fun item(navigation: TodayNavigation): TodayItem? {
        val context = LocalContext.current
        val dueCounts = remember(context) { learnDueAyahCount(context) }
        val dueCount by dueCounts.collectAsStateWithLifecycle(initialValue = 0)
        val scope = rememberCoroutineScope()
        val count = learnTodayCount(dueCount) ?: return null
        return TodayItem(
            title = stringResource(R.string.tab_learn),
            detail = pluralStringResource(R.plurals.today_learn_revise, count, count),
            icon = R.drawable.ic_app_school,
            onOpen = {
                scope.launch {
                    val firstDue = learnFirstDueAyah(context)
                    if (firstDue != null) navigation.reviseAt(firstDue)
                    else navigation.openLearnTab()
                }
            },
        )
    }
}
