package app.awn.quran.feature.learn.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.awn.quran.core.data.AyahRef
import app.awn.quran.feature.learn.LearnRouteContent
import app.awn.quran.feature.learn.LearnUiMapper
import app.awn.quran.feature.learn.OpenPageAction
import app.awn.quran.feature.learn.ReadingProgressActions
import app.awn.quran.feature.learn.ReadingProgressScreen
import app.awn.quran.feature.learn.ReviseActions
import app.awn.quran.feature.learn.ReviseAyahsScreen
import app.awn.quran.feature.learn.words.WordProgress
import java.time.LocalDate
import java.time.ZoneId

/** The Revise screen of the hub's "Revise ayahs" row, with each button tied to a destination. */
@Composable
internal fun ReviseAyahsRoute(
    content: LearnRouteContent,
    onReviseAt: ((AyahRef) -> Unit)?,
    context: CourseScreenContext,
    modifier: Modifier,
) {
    val snapshot = content.snapshot
    val zone = ZoneId.systemDefault()
    val state =
        LearnUiMapper.reviseState(
            snapshot = snapshot,
            surahName = content.surahName,
            today = LocalDate.now(zone),
            zone = zone,
            locale = content.locale,
        )
    val pageLinks = LocalLearnPageLinks.current
    val hifzPage = pageLinks.hifzTodayPage
    val openPage = pageLinks.onOpenPage
    // Hifz has a page to open only once the user has a lesson for today.
    val memorise =
        if (hifzPage != null && openPage != null) OpenPageAction(hifzPage) { openPage(hifzPage) }
        else null
    ReviseAyahsScreen(
        state = state,
        actions =
            ReviseActions(
                onStartClick = { startRevising(snapshot.dueToday, onReviseAt, context) },
                onReviseEarlyClick = { startRevising(snapshot.nextDueAyahs, onReviseAt, context) },
                memorise = memorise,
            ),
        onBackClick = context::goHub,
        modifier = modifier,
    )
}

/** The Reading progress screen: the reading figures and the word figures of the course. */
@Composable
internal fun ReadingProgressRoute(
    content: LearnRouteContent,
    context: CourseScreenContext,
    modifier: Modifier,
) {
    val courseSnapshot = context.snapshot
    val frequency = context.runtime.lookups.frequency
    val wordShare =
        remember(courseSnapshot.knownRanks, frequency) {
            WordProgress.coverage(courseSnapshot.knownRanks, frequency)
        }
    val zone = ZoneId.systemDefault()
    val state =
        LearnUiMapper.progressState(
            snapshot = content.snapshot,
            wordsKnown = courseSnapshot.wordsKnown,
            quranWordsShare = wordShare,
            today = LocalDate.now(zone),
            zone = zone,
            locale = content.locale,
        )
    val pageLinks = LocalLearnPageLinks.current
    val openPage = pageLinks.onOpenPage
    // A user who has read nothing starts on page 1, the first page of the Mushaf.
    val readPage = pageLinks.lastReadPage ?: FIRST_PAGE
    ReadingProgressScreen(
        state = state,
        actions =
            ReadingProgressActions(
                onNextRevisionClick = { context.onRouteChange(LearnRoute.ReviseAyahs) },
                read = openPage?.let { open -> OpenPageAction(readPage) { open(readPage) } },
                hasReadBefore = pageLinks.lastReadPage != null,
            ),
        onBackClick = context::goHub,
        modifier = modifier,
    )
}

private const val FIRST_PAGE = 1

/**
 * Opens the Quran screen at the first of [ayahs] in recite mode when :app gave [onReviseAt];
 * otherwise runs the revision session of this tab over them.
 */
private fun startRevising(
    ayahs: List<AyahRef>,
    onReviseAt: ((AyahRef) -> Unit)?,
    context: CourseScreenContext,
) {
    val first = ayahs.minOrNull()
    if (onReviseAt != null && first != null) onReviseAt(first)
    else context.onRouteChange(LearnRoute.Revision)
}
