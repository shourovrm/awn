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
import app.awn.quran.feature.learn.CourseTodayLine
import app.awn.quran.feature.learn.learnCourseTodayLine
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
    /**
     * Opens the Quran screen at [AyahRef] in recite mode with the words hidden, for a revision run
     * of at most the given number of ayahs; null means every due ayah.
     */
    val reviseAt: (AyahRef, Int?) -> Unit,
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

/** The Learn row once the word course has started: its time, new words and words to revise. */
@Composable
private fun courseTodayItem(line: CourseTodayLine, navigation: TodayNavigation): TodayItem {
    val parts = buildList {
        if (line.newWords > 0) {
            add(pluralStringResource(R.plurals.today_course_new, line.newWords, line.newWords))
        }
        if (line.reviews > 0) {
            add(pluralStringResource(R.plurals.today_course_reviews, line.reviews, line.reviews))
        }
    }
    return TodayItem(
        title = stringResource(R.string.today_course_start, line.minutes),
        detail = parts.joinToString(" · "),
        icon = R.drawable.ic_app_school,
        onOpen = navigation.openLearnTab,
    )
}

/**
 * The Learn row: the word course's day once the course has started, otherwise the ayahs due for
 * revision, capped at [LEARN_TODAY_CAP].
 */
object LearnTodayProvider : TodayProvider {
    @Suppress("ReturnCount") // The course row, no row, or the revise row.
    @Composable
    override fun item(navigation: TodayNavigation): TodayItem? {
        val context = LocalContext.current
        val dueCounts = remember(context) { learnDueAyahCount(context) }
        val dueCount by dueCounts.collectAsStateWithLifecycle(initialValue = 0)
        val scope = rememberCoroutineScope()
        val courseLines = remember(context) { learnCourseTodayLine(context) }
        val courseLine by courseLines.collectAsStateWithLifecycle(initialValue = null)
        courseLine?.let {
            return courseTodayItem(it, navigation)
        }
        val count = learnTodayCount(dueCount) ?: return null
        return TodayItem(
            title = stringResource(R.string.tab_learn),
            detail = pluralStringResource(R.plurals.today_learn_revise, count, count),
            icon = R.drawable.ic_app_school,
            onOpen = {
                scope.launch {
                    val firstDue = learnFirstDueAyah(context)
                    if (firstDue != null) navigation.reviseAt(firstDue, LEARN_TODAY_CAP)
                    else navigation.openLearnTab()
                }
            },
        )
    }
}
