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

/**
 * One of the two Today tiles on the Quran tab. [detail] states today's load ("Revise 12 ayahs"),
 * never a backlog; [onOpen] starts it.
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
 * A feature's offer for its Today tile. The Learn and Hifz features each have one, held in
 * [TabSlots]. [item] runs in composition, so the strings follow the app language, and returns null
 * when the feature has nothing to do today; the tile then stays with a neutral line.
 */
fun interface TodayProvider {
    @Composable fun item(navigation: TodayNavigation): TodayItem?
}

/** The Hifz and Learn tiles of the Quran tab. */
class TodayTiles(val hifz: TodayItem, val learn: TodayItem)

/**
 * The two tiles. A provider with nothing to offer gives a tile that names its tab, says nothing is
 * due, and opens that tab, so the row of tiles always has the same four places.
 */
@Composable
fun rememberTodayTiles(
    hifzProvider: TodayProvider,
    learnProvider: TodayProvider,
    navigation: TodayNavigation,
): TodayTiles {
    val idleDetail = stringResource(R.string.today_tile_idle)
    val hifz =
        hifzProvider.item(navigation)
            ?: TodayItem(
                title = stringResource(R.string.tab_hifz),
                detail = idleDetail,
                icon = R.drawable.ic_app_psychology,
                onOpen = navigation.openHifzTab,
            )
    val learn =
        learnProvider.item(navigation)
            ?: TodayItem(
                title = stringResource(R.string.tab_learn),
                detail = idleDetail,
                icon = R.drawable.ic_app_school,
                onOpen = navigation.openLearnTab,
            )
    return TodayTiles(hifz, learn)
}

/**
 * The most ayahs the Learn tile offers in one day. A user who skipped a week has hundreds due, and
 * a row that says so reads as a debt; the tile offers a day's worth.
 */
const val LEARN_TODAY_CAP = 10

/** The ayah count the Learn tile shows for [dueCount] due ayahs, or null when none is due. */
fun learnTodayCount(dueCount: Int): Int? = dueCount.takeIf { it > 0 }?.coerceAtMost(LEARN_TODAY_CAP)

/** The Learn tile once the word course has started: its name and the minutes of today's work. */
@Composable
private fun courseTodayItem(line: CourseTodayLine, navigation: TodayNavigation): TodayItem =
    TodayItem(
        title = stringResource(R.string.today_words_title),
        detail = stringResource(R.string.today_course_minutes, line.minutes),
        icon = R.drawable.ic_app_school,
        onOpen = navigation.openLearnTab,
    )

/**
 * The Learn tile: the word course's day once the course has started, otherwise the ayahs due for
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
