package app.awn.quran.feature.hifz

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.awn.quran.feature.hifz.method.HifzMethodId
import app.awn.quran.feature.hifz.method.PartKind

/**
 * What is left of today for the one line on the Quran tab. [lessonPage] is the page of a new lesson
 * not yet done; [revisionPages] counts the pages of revision not yet done.
 */
data class HifzTodaySummary(
    val lessonPage: Int?,
    val revisionPages: Int,
    val isSabaq: Boolean,
    val nextPart: PartKind?,
)

/** The text for the Quran tab's Today section; [title] is "Hifz" and [detail] is one line. */
data class HifzTodayLine(val title: String, val detail: String)

object HifzToday {

    /** The summary of today, null when there is no plan or the method assigns nothing. */
    // Guards for no plan and nothing assigned come first.
    @Suppress("ReturnCount")
    fun summaryOf(state: HifzState): HifzTodaySummary? {
        if (state.plan.method == HifzMethodId.TrackOnly) return null
        val assignment = state.assignment
        val lesson = assignment.newLesson?.takeIf { !it.isDone }
        val recentLeft = assignment.recent?.takeIf { !it.isDone }?.pages?.size ?: 0
        val oldLeft = assignment.old?.takeIf { !it.isDone }?.pages?.size ?: 0
        val nothingAssigned =
            assignment.newLesson == null && assignment.recent == null && assignment.old == null
        if (nothingAssigned) return null
        return HifzTodaySummary(
            lessonPage = lesson?.part?.firstPage,
            revisionPages = recentLeft + oldLeft,
            isSabaq = state.plan.method == HifzMethodId.SabaqSabqiManzil,
            nextPart = assignment.nextPart(),
        )
    }
}

/** The Today line of Hifz for the Quran tab, or null while there is nothing to show. */
@Suppress("ReturnCount") // Guards: not ready, nothing today.
@Composable
fun rememberHifzTodayLine(controller: HifzController): HifzTodayLine? {
    val screenState by controller.screenState.collectAsStateWithLifecycle()
    val ready = screenState as? HifzScreenState.Ready ?: return null
    val summary = HifzToday.summaryOf(ready.state) ?: return null
    val pieces = buildList {
        summary.lessonPage?.let { page ->
            add(
                stringResource(
                    if (summary.isSabaq) R.string.today_line_sabaq else R.string.today_line_lesson,
                    page,
                ),
            )
        }
        if (summary.revisionPages > 0) {
            add(
                pluralStringResource(
                    R.plurals.today_line_revise,
                    summary.revisionPages,
                    summary.revisionPages,
                ),
            )
        }
    }
    val detail = pieces.joinToString(", ").ifEmpty { stringResource(R.string.today_line_done) }
    return HifzTodayLine(title = stringResource(R.string.today_line_title), detail = detail)
}
