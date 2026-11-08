package app.awn.quran.feature.learn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.awn.quran.core.ui.components.AwnButton
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.softFill
import app.awn.quran.feature.learn.R
import app.awn.quran.feature.learn.course.CheckWithAnswer
import app.awn.quran.feature.learn.course.CourseLesson
import app.awn.quran.feature.learn.course.LearnTrack
import app.awn.quran.feature.learn.course.PracticeRun
import app.awn.quran.feature.learn.course.ScreenKind
import app.awn.quran.feature.learn.course.TrackUnit
import app.awn.quran.feature.learn.course.UnitAnswers
import app.awn.quran.feature.learn.course.UnitPage
import app.awn.quran.feature.learn.course.unitPages
import app.awn.quran.feature.learn.progress.resumePage
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Where a unit sits in its track, for the bar and the closing page. */
internal class UnitPlace(val track: LearnTrack, val number: Int, val next: TrackUnit?)

/** What the unit screen does when the user leaves it, finishes it or moves between its pages. */
internal class UnitActions(
    val onBack: () -> Unit,
    /** Called once, when the closing page first shows; [rightCount] counts right first answers. */
    val onFinished: (rightCount: Int) -> Unit,
    val onNextUnit: (TrackUnit) -> Unit,
    /** Called with the page index each time a reading or check page is shown. */
    val onPageChange: (page: Int) -> Unit = {},
    /** Called each time the closing page is shown, so a finished unit opens on its first page. */
    val onDonePageShown: () -> Unit = {},
    /** Called when the user starts the unit again; the screen itself scrolls back to page one. */
    val onStartAgain: () -> Unit = {},
)

/**
 * A unit opened from a track: its reading screens, then its checks, then a closing page, one page
 * at a time. The user swipes between pages or taps the dots; a check shows the explanation as soon
 * as an answer is picked.
 */
@Composable
internal fun UnitScreen(
    lesson: CourseLesson,
    languageCode: String,
    place: UnitPlace?,
    actions: UnitActions,
    modifier: Modifier = Modifier,
    soundsOffer: SoundsOfferState = SoundsOfferState.None,
    /** The page the user left this unit on; the unit opens there. */
    savedPage: Int = 0,
) {
    val text = lesson.text(languageCode)
    val pages = remember(text) { unitPages(text) }
    val pagerState =
        rememberPagerState(
            initialPage = resumePage(savedPage, pages.size),
            pageCount = { pages.size },
        )
    val scope = rememberCoroutineScope()
    var answers by remember(lesson.id) { mutableStateOf(UnitAnswers()) }
    // The seed is saved so that a turned screen keeps the same ten items.
    var practiceSeed by rememberSaveable(lesson.id) { mutableStateOf(Random.nextLong()) }
    var practiceRun by rememberPracticeRun(lesson.id, text, practiceSeed)
    val startAgain: () -> Unit = {
        scope.launch { pagerState.scrollToPage(0) }
        actions.onStartAgain()
    }
    val practiceAgain =
        practiceAgainAction(pages, pagerState, scope) { practiceSeed = Random.nextLong() }
    ReportPage(pagerState, pages, lesson.id, answers, text.checks, actions)
    Column(modifier = modifier.fillMaxSize()) {
        PagerBar(
            title = text.title,
            currentPage = pagerState.currentPage,
            pageCount = pages.size,
            onBack = actions.onBack,
            menu = { UnitOverflowMenu(onStartAgain = startAgain) },
        )
        PageDots(pages.size, pagerState) { index ->
            val page = pages[index]
            page is UnitPage.Check || page == UnitPage.Practice
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).testTag(UNIT_PAGER_TAG),
        ) { index ->
            UnitPageContent(
                page = pages[index],
                answers = answers,
                onAnswersChange = { answers = it },
                checks = text.checks,
                practice = PracticeState(practiceRun) { practiceRun = it },
                offer = soundsOffer.takeIf { index == 0 && text.hasClips } ?: SoundsOfferState.None,
                place = place,
                actions = actions,
                onPracticeAgain = practiceAgain,
                onContinue = { scope.launch { pagerState.animateScrollToPage(index + 1) } },
            )
        }
    }
}

/** Draws a fresh practice run and shows its page; null when the unit has no practice page. */
private fun practiceAgainAction(
    pages: List<UnitPage>,
    pagerState: PagerState,
    scope: CoroutineScope,
    drawNewRun: () -> Unit,
): (() -> Unit)? {
    val practicePage = pages.indexOf(UnitPage.Practice)
    if (practicePage < 0) return null
    return {
        drawNewRun()
        scope.launch { pagerState.animateScrollToPage(practicePage) }
    }
}

/**
 * Tells the caller which page is on screen. The closing page is reported on every visit, so a
 * unit started again ends finished too; the finished step is recorded once.
 */
@Composable
private fun ReportPage(
    pagerState: PagerState,
    pages: List<UnitPage>,
    lessonId: String,
    answers: UnitAnswers,
    checks: List<CheckWithAnswer>,
    actions: UnitActions,
) {
    var finishReported by rememberSaveable(lessonId) { mutableStateOf(false) }
    LaunchedEffect(pagerState.currentPage) {
        if (pages[pagerState.currentPage] != UnitPage.Done) {
            actions.onPageChange(pagerState.currentPage)
            return@LaunchedEffect
        }
        actions.onDonePageShown()
        if (!finishReported) {
            finishReported = true
            actions.onFinished(answers.rightCount(checks))
        }
    }
}

@Composable
private fun UnitPageContent(
    page: UnitPage,
    answers: UnitAnswers,
    onAnswersChange: (UnitAnswers) -> Unit,
    checks: List<CheckWithAnswer>,
    practice: PracticeState,
    offer: SoundsOfferState,
    place: UnitPlace?,
    actions: UnitActions,
    onPracticeAgain: (() -> Unit)?,
    onContinue: () -> Unit,
) {
    val pageModifier =
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AwnSpacing.screenMargin, vertical = AwnSpacing.medium)
    when (page) {
        is UnitPage.Teaching -> TeachingPage(page, offer, pageModifier)
        is UnitPage.Quiz -> QuizPage(page.screen.questions, onContinue, pageModifier)
        UnitPage.Practice -> PracticePage(practice.run, practice.onChange, pageModifier)
        is UnitPage.Check ->
            CheckPage(
                check = page.check,
                chosen = answers.chosen[page.index],
                onChoose = { onAnswersChange(answers.choose(page.index, it)) },
                modifier = pageModifier,
            )
        UnitPage.Done ->
            DonePage(
                rightCount = answers.rightCount(checks),
                checkCount = checks.size,
                place = place,
                actions = actions,
                onPracticeAgain = onPracticeAgain,
                modifier = pageModifier,
            )
    }
}

/** The practice run of the unit and how to change it. */
private class PracticeState(val run: PracticeRun, val onChange: (PracticeRun) -> Unit)

/**
 * A reading screen with the grids after it. The first screen titles the page; a grid keeps its own
 * heading unless it repeats that title.
 */
@Composable
private fun TeachingPage(page: UnitPage.Teaching, offer: SoundsOfferState, modifier: Modifier) {
    val pageTitle = page.screens.first().title
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AwnSpacing.medium)) {
        RichText(pageTitle, style = AwnTypography.title)
        SoundsOfferRow(offer)
        page.screens.forEach { screen ->
            if (screen.kind == ScreenKind.Grid) {
                if (screen.title != pageTitle) RichText(screen.title, style = AwnTypography.lead)
                QaidaGrid(screen.cells)
            } else {
                LessonBlocks(screen.blocks)
            }
        }
    }
}

@Composable
private fun CheckPage(
    check: CheckWithAnswer,
    chosen: Int?,
    onChoose: (Int) -> Unit,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AwnSpacing.medium)) {
        RichText(check.check.question, style = AwnTypography.title)
        check.check.ayah?.let { AyahCard(it, highlight = check.check.highlight) }
        check.check.options.forEachIndexed { index, option ->
            val state =
                when {
                    chosen == null -> AnswerRowState.Open
                    index == check.check.correctIndex -> AnswerRowState.Right
                    index == chosen -> AnswerRowState.Wrong
                    else -> AnswerRowState.Open
                }
            AnswerRow(
                markup = option,
                state = state,
                onClick = if (chosen == null) ({ onChoose(index) }) else null,
            )
        }
        if (chosen != null) {
            check.answerTitle?.let { RichText(it, style = AwnTypography.bodyStrong) }
            LessonBlocks(check.answerBlocks)
        }
    }
}

@Composable
private fun DonePage(
    rightCount: Int,
    checkCount: Int,
    place: UnitPlace?,
    actions: UnitActions,
    onPracticeAgain: (() -> Unit)?,
    modifier: Modifier,
) {
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(AwnSpacing.medium, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text =
                if (place != null) stringResource(R.string.unit_done_numbered, place.number)
                else stringResource(R.string.unit_done),
            style = AwnTypography.title,
            color = Awn.field.content,
        )
        if (checkCount > 0) {
            MutedLine(stringResource(R.string.unit_checks_right, rightCount, checkCount))
        }
        val next = place?.next
        if (next != null) {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(AwnSpacing.small))
                        .background(Awn.field.softFill)
                        .padding(AwnSpacing.medium),
            ) {
                MutedLine(stringResource(R.string.unit_next_in, trackName(place.track)))
                Text(
                    text = stringResource(R.string.unit_numbered_title, next.number, next.title),
                    style = AwnTypography.bodyStrong,
                    color = Awn.field.content,
                )
            }
            AwnButton(
                label = stringResource(R.string.unit_next),
                onClick = { actions.onNextUnit(next) },
                modifier = Modifier.fillMaxWidth().testTag(UNIT_NEXT_TAG),
            )
        }
        if (onPracticeAgain != null) {
            QuietButton(
                label = stringResource(R.string.unit_practice_again),
                onClick = onPracticeAgain,
                modifier = Modifier.testTag(UNIT_PRACTICE_AGAIN_TAG),
            )
        }
        QuietButton(
            label =
                if (place != null)
                    stringResource(R.string.unit_back_to_track, trackName(place.track))
                else stringResource(R.string.back),
            onClick = actions.onBack,
        )
    }
}

/** The name of a track as the hub and the unit pages show it. */
@Composable
internal fun trackName(track: LearnTrack): String =
    stringResource(
        when (track) {
            LearnTrack.Reading -> R.string.track_reading
            LearnTrack.Tajweed -> R.string.track_tajweed
            LearnTrack.Grammar -> R.string.track_grammar
            LearnTrack.Words -> R.string.track_words
        },
    )

internal const val UNIT_PRACTICE_AGAIN_TAG = "unit-practice-again"
internal const val UNIT_PAGER_TAG = "unit-pager"
internal const val UNIT_NEXT_TAG = "unit-next"
