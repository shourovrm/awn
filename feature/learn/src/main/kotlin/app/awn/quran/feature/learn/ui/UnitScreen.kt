package app.awn.quran.feature.learn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.awn.quran.core.ui.components.AwnButton
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.mutedContent
import app.awn.quran.core.ui.theme.softFill
import app.awn.quran.feature.learn.LearnBar
import app.awn.quran.feature.learn.R
import app.awn.quran.feature.learn.course.CheckWithAnswer
import app.awn.quran.feature.learn.course.CourseLesson
import app.awn.quran.feature.learn.course.LearnTrack
import app.awn.quran.feature.learn.course.TrackUnit
import app.awn.quran.feature.learn.course.UnitAnswers
import app.awn.quran.feature.learn.course.UnitPage
import app.awn.quran.feature.learn.course.unitPages
import kotlinx.coroutines.launch

/** Where a unit sits in its track, for the bar and the closing page. */
internal class UnitPlace(val track: LearnTrack, val number: Int, val next: TrackUnit?)

/** What the unit screen does when the user leaves it or finishes it. */
internal class UnitActions(
    val onBack: () -> Unit,
    /** Called once, when the closing page first shows; [rightCount] counts right first answers. */
    val onFinished: (rightCount: Int) -> Unit,
    val onNextUnit: (TrackUnit) -> Unit,
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
) {
    val text = lesson.text(languageCode)
    val pages = remember(text) { unitPages(text) }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    var answers by remember(lesson.id) { mutableStateOf(UnitAnswers()) }
    var finishReported by rememberSaveable(lesson.id) { mutableStateOf(false) }
    val checks = text.checks
    LaunchedEffect(pagerState.currentPage) {
        if (pages[pagerState.currentPage] == UnitPage.Done && !finishReported) {
            finishReported = true
            actions.onFinished(answers.rightCount(checks))
        }
    }
    Column(modifier = modifier.fillMaxSize()) {
        LearnBar(
            title = text.title,
            startLinkLabel = stringResource(R.string.back),
            onStartLinkClick = actions.onBack,
            actions = {
                Text(
                    text =
                        stringResource(
                            R.string.unit_page_count,
                            pagerState.currentPage + 1,
                            pages.size,
                        ),
                    style = AwnTypography.small,
                    color = Awn.field.mutedContent,
                )
            },
        )
        PageDots(pages, pagerState)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).testTag(UNIT_PAGER_TAG),
        ) { index ->
            val pageModifier =
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AwnSpacing.screenMargin, vertical = AwnSpacing.medium)
            when (val page = pages[index]) {
                is UnitPage.Reading ->
                    Column(
                        pageModifier,
                        verticalArrangement = Arrangement.spacedBy(AwnSpacing.medium),
                    ) {
                        RichText(page.screen.title, style = AwnTypography.title)
                        LessonBlocks(page.screen.blocks)
                    }
                is UnitPage.Check ->
                    CheckPage(
                        check = page.check,
                        chosen = answers.chosen[page.index],
                        onChoose = { answers = answers.choose(page.index, it) },
                        modifier = pageModifier,
                    )
                UnitPage.Done ->
                    DonePage(
                        rightCount = answers.rightCount(checks),
                        checkCount = checks.size,
                        place = place,
                        actions = actions,
                        modifier = pageModifier,
                    )
            }
        }
    }
}

/**
 * One dot per page; the current page is a wide bar and the check pages are rings, so the user sees
 * how far the reading goes and where the questions start.
 */
@Composable
private fun PageDots(pages: List<UnitPage>, pagerState: PagerState) {
    val scope = rememberCoroutineScope()
    val field = Awn.field
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = AwnSpacing.small),
        horizontalArrangement = Arrangement.Center,
    ) {
        pages.forEachIndexed { index, page ->
            val isCurrent = index == pagerState.currentPage
            val description = stringResource(R.string.unit_go_to_page, index + 1)
            Box(
                modifier =
                    Modifier.size(DOT_TOUCH_SIZE)
                        .semantics { contentDescription = description }
                        .clickable { scope.launch { pagerState.animateScrollToPage(index) } },
                contentAlignment = Alignment.Center,
            ) {
                val dotWidth = if (isCurrent) CURRENT_DOT_WIDTH else DOT_SIZE
                Box(
                    Modifier.size(width = dotWidth, height = DOT_SIZE)
                        .clip(CircleShape)
                        .background(
                            when {
                                isCurrent -> field.buttonFill
                                page is UnitPage.Check -> field.mutedContent
                                else -> field.softFill
                            },
                        ),
                )
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

private val DOT_SIZE = 8.dp
private val CURRENT_DOT_WIDTH = 20.dp

// Each dot is tappable; the touch area is wider than the dot so a row of them stays usable.
private val DOT_TOUCH_SIZE = 24.dp

internal const val UNIT_PAGER_TAG = "unit-pager"
internal const val UNIT_NEXT_TAG = "unit-next"
