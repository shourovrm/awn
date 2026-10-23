package app.awn.quran.feature.learn.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.feature.learn.LearnBar
import app.awn.quran.feature.learn.R
import app.awn.quran.feature.learn.course.LearnTrack
import app.awn.quran.feature.learn.course.PageWords
import app.awn.quran.feature.learn.course.queueNewWords
import app.awn.quran.feature.learn.words.CourseSnapshot
import app.awn.quran.feature.learn.words.LearnSets
import app.awn.quran.feature.learn.words.PageWordsSource
import app.awn.quran.feature.learn.words.WordProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The Quran words page: the hub's figures plus the sets, the pages and today's words. */
@Composable
internal fun WordsTrackRoute(
    hub: HubState,
    context: CourseScreenContext,
    hubActions: HubActions,
    modifier: Modifier,
) {
    val links = LocalLearnPageLinks.current
    val quranText = LocalLearnEnvironment.current?.quranText
    val snapshot = context.snapshot
    val state by
        produceState<WordsTrackState?>(
            null,
            hub,
            snapshot,
            links.lastReadPage,
            links.hifzTodayPage,
            links.script,
        ) {
            value =
                withContext(Dispatchers.Default) {
                    buildWordsTrackState(
                        context.runtime.lookups,
                        snapshot,
                        context.choices,
                        hub,
                        links,
                        quranText,
                    )
                }
        }
    state?.let { shown ->
        WordsTrackScreen(
            state = shown,
            actions =
                WordsTrackActions(
                    hub = hubActions,
                    onOpenSet = { context.onRouteChange(LearnRoute.SetPage(it)) },
                    onOpenPageWords = { context.onRouteChange(LearnRoute.PageWords(it)) },
                ),
            onBack = context::goHub,
            modifier = modifier,
        )
    }
}

/** The ten words of one set; a word opens its page and Back returns here. */
@Composable
internal fun SetPageRoute(number: Int, context: CourseScreenContext, modifier: Modifier) {
    val lookups = context.runtime.lookups
    val snapshot = context.snapshot
    val rows by
        produceState(emptyList<WordListRow>(), number, snapshot, lookups) {
            value = withContext(Dispatchers.Default) { buildSetRows(lookups, snapshot, number) }
        }
    Column(modifier = modifier.fillMaxSize()) {
        LearnBar(
            title = stringResource(R.string.set_page_title, number),
            startLinkLabel = stringResource(R.string.back),
            onStartLinkClick = { context.onRouteChange(LearnRoute.Track(LearnTrack.Words)) },
        )
        LazyColumn(modifier = Modifier.padding(horizontal = AwnSpacing.screenMargin)) {
            items(rows, key = { it.rank }) { row ->
                WordRow(
                    row,
                    onClick = {
                        context.onRouteChange(
                            LearnRoute.WordPage(row.rank, LearnRoute.SetPage(number)),
                        )
                    },
                )
            }
        }
    }
}

private suspend fun buildSetRows(
    lookups: CourseLookups,
    snapshot: CourseSnapshot,
    number: Int,
): List<WordListRow> =
    LearnSets.ranksOf(number)
        .filter { it <= lookups.frequency.formCount }
        .map { rank ->
            WordListRow(
                rank = rank,
                key = lookups.frequency.representative(rank),
                meaning = lookups.infoSource.info(rank)?.meaning.orEmpty(),
                status = WordProgress.statusOf(snapshot.wordCards[rank]),
            )
        }

/** Every word on one Quran page; "Open page" and "Learn new" act on that page. */
@Composable
internal fun PageWordsRoute(page: Int, context: CourseScreenContext, modifier: Modifier) {
    val links = LocalLearnPageLinks.current
    val quranText = LocalLearnEnvironment.current?.quranText ?: return
    val lookups = context.runtime.lookups
    val snapshot = context.snapshot
    val content by
        produceState<PageWordsContent?>(null, page, snapshot, lookups, links.script) {
            value =
                withContext(Dispatchers.Default) {
                    buildPageWordsContent(lookups, snapshot, page, links.script, quranText)
                }
        }
    content?.let { shown ->
        PageWordsScreen(
            content = shown,
            actions =
                PageWordsActions(
                    onOpenWord = {
                        context.onRouteChange(LearnRoute.WordPage(it, LearnRoute.PageWords(page)))
                    },
                    onOpenPage = links.onOpenPage?.let { openPage -> { openPage(page) } },
                    onLearnNew = {
                        context.scope.launch {
                            context.services.courseRepository.queueNewWords(shown.list)
                        }
                    },
                    onBack = { context.onRouteChange(LearnRoute.Track(LearnTrack.Words)) },
                ),
            modifier = modifier,
        )
    }
}

private suspend fun buildPageWordsContent(
    lookups: CourseLookups,
    snapshot: CourseSnapshot,
    page: Int,
    script: ScriptId,
    quranText: QuranTextRepository,
): PageWordsContent {
    val onPage = PageWordsSource(quranText).wordsOnPage(page, script)
    val list = PageWords.build(onPage.keys, lookups.frequency, snapshot)
    val meanings =
        list.rows.mapNotNull { row ->
            lookups.infoSource.info(row.rank)?.let { row.rank to it.meaning }
        }
    return PageWordsContent(
        page = page,
        firstAyah = onPage.ayahs.firstOrNull(),
        lastAyah = onPage.ayahs.lastOrNull(),
        list = list,
        meanings = meanings.toMap(),
    )
}
