package app.awn.quran.feature.learn.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.awn.quran.core.ui.components.AwnButton
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.softFill
import app.awn.quran.feature.learn.R
import app.awn.quran.feature.learn.course.LessonCheck
import app.awn.quran.feature.learn.course.PracticeItem
import app.awn.quran.feature.learn.course.PracticeRun
import app.awn.quran.feature.learn.sound.LocalUnitSounds
import kotlinx.coroutines.launch

/**
 * The practice set of a unit, one item per screen with a progress line above it. After the answer
 * the right and the wrong option are marked, the explanation shows and the item's sound plays: the
 * clip, or the highlighted word with the user's word reciter.
 */
@Composable
internal fun PracticePage(
    run: PracticeRun,
    onRunChange: (PracticeRun) -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = run.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AwnSpacing.medium)) {
        if (item == null) {
            PracticeResult(run)
        } else {
            MutedLine(
                stringResource(R.string.practice_progress, run.position + 1, run.items.size),
                Modifier.testTag(PRACTICE_PROGRESS_TAG),
            )
            ProgressTrack(run.position.toFloat() / run.items.size)
            when (item) {
                is PracticeItem.Question ->
                    QuestionItem(
                        item.check,
                        run.currentPick,
                        onPick = { onRunChange(run.pick(it)) },
                    )
                is PracticeItem.ListenPick ->
                    ListenPickItem(
                        item.check,
                        run.currentPick,
                        onPick = { onRunChange(run.pick(it)) },
                    )
                is PracticeItem.ReadAloud -> ReadAloudItem(item)
            }
            val isReadAloud = item is PracticeItem.ReadAloud
            if (run.currentPick != null || isReadAloud) {
                AwnButton(
                    label = stringResource(R.string.practice_next),
                    onClick = {
                        onRunChange(if (isReadAloud) run.finishReading().next() else run.next())
                    },
                    modifier = Modifier.fillMaxWidth().testTag(PRACTICE_NEXT_TAG),
                )
            }
        }
    }
}

@Composable
private fun PracticeResult(run: PracticeRun) {
    RichText(stringResource(R.string.practice_done), style = AwnTypography.title)
    if (run.questionCount > 0) {
        MutedLine(stringResource(R.string.unit_checks_right, run.rightCount, run.questionCount))
    }
}

@Composable
private fun QuestionItem(item: LessonCheck, pick: Int?, onPick: (Int) -> Unit) {
    val sounds = LocalUnitSounds.current
    PlayAfterAnswer(item, pick)
    Column(verticalArrangement = Arrangement.spacedBy(AwnSpacing.medium)) {
        RichText(item.question, style = AwnTypography.lead)
        item.ayah?.let { AyahCard(it, highlight = item.highlight) }
        // Only a listening question, whose options are tiles, may be heard before the answer.
        if (item.clipIds.isNotEmpty() && pick == null && item.options.all(::isTileOption)) {
            ListenButton(item)
        }
        if (item.options.all(::isTileOption)) {
            OptionTiles(item, pick, onPick)
        } else {
            item.options.forEachIndexed { index, option ->
                AnswerRow(
                    markup = option,
                    state = optionState(item, pick, index),
                    onClick = if (pick == null) ({ onPick(index) }) else null,
                )
            }
        }
        if (pick != null) item.explanation?.let { RichText(it) }
    }
}

@Composable
private fun ListenButton(item: LessonCheck) {
    val sounds = LocalUnitSounds.current
    val scope = rememberCoroutineScope()
    AwnButton(
        label = stringResource(R.string.practice_listen),
        onClick = { scope.launch { sounds.playClips(item.clipIds) } },
        modifier = Modifier.fillMaxWidth().testTag(PRACTICE_LISTEN_TAG),
    )
}

/**
 * A row to read aloud: the Arabic large with no transliteration, a button that plays the clip and
 * one that plays it slower.
 */
@Composable
private fun ReadAloudItem(item: PracticeItem.ReadAloud) {
    val sounds = LocalUnitSounds.current
    val scope = rememberCoroutineScope()
    DisposableEffect(item) { onDispose { sounds.stop() } }
    Column(verticalArrangement = Arrangement.spacedBy(AwnSpacing.medium)) {
        RichText(stringResource(R.string.practice_read_aloud), style = AwnTypography.lead)
        RichText(
            text = item.read,
            style = AwnTypography.title,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = AwnSpacing.large),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
            AwnButton(
                label = stringResource(R.string.practice_listen),
                onClick = { scope.launch { sounds.playClips(item.clipIds) } },
                modifier = Modifier.weight(1f).testTag(PRACTICE_LISTEN_TAG),
            )
            AwnButton(
                label = stringResource(R.string.practice_listen_slowly),
                onClick = { scope.launch { sounds.playClips(item.clipIds, SLOW_SPEED) } },
                modifier = Modifier.weight(1f).testTag(PRACTICE_SLOW_TAG),
            )
        }
    }
}

/** Plays the item's sound once its answer is in; leaving the screen stops it. */
@Composable
private fun PlayAfterAnswer(item: LessonCheck, pick: Int?) {
    val sounds = LocalUnitSounds.current
    LaunchedEffect(item, pick) {
        if (pick == null) return@LaunchedEffect
        val highlight = item.highlight
        when {
            item.clipIds.isNotEmpty() -> sounds.playClips(item.clipIds)
            highlight != null -> sounds.playWord(highlight)
        }
    }
    DisposableEffect(item) { onDispose { sounds.stop() } }
}

/** Two tiles to a row for options that are single Arabic fragments such as [[بَ]]. */
@Composable
private fun OptionTiles(item: LessonCheck, pick: Int?, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
        item.options.withIndex().chunked(TILES_PER_ROW).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
                row.forEach { (index, option) ->
                    OptionTile(
                        markup = option,
                        state = optionState(item, pick, index),
                        onClick = if (pick == null) ({ onPick(index) }) else null,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size < TILES_PER_ROW) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun OptionTile(
    markup: String,
    state: AnswerRowState,
    onClick: (() -> Unit)?,
    modifier: Modifier,
) {
    val field = Awn.field
    val isRight = state == AnswerRowState.Right
    Box(
        modifier =
            modifier
                .heightIn(min = AwnSpacing.practiceTileHeight)
                .clip(AwnShapes.control)
                .background(if (isRight) field.buttonFill else field.softFill)
                .then(
                    if (state == AnswerRowState.Wrong)
                        Modifier.border(BorderStroke(2.dp, field.wrongWord), AwnShapes.control)
                    else Modifier,
                )
                .then(
                    if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick)
                    else Modifier,
                )
                .padding(AwnSpacing.small),
        contentAlignment = Alignment.Center,
    ) {
        RichText(
            text = markup,
            style = AwnTypography.title,
            color = if (isRight) field.onButton else field.content,
            textAlign = TextAlign.Center,
        )
    }
}

internal fun optionState(item: LessonCheck, pick: Int?, index: Int): AnswerRowState =
    when {
        pick == null -> AnswerRowState.Open
        index == item.correctIndex -> AnswerRowState.Right
        index == pick -> AnswerRowState.Wrong
        else -> AnswerRowState.Open
    }

/** True for an option that is one typed Arabic fragment, which practice draws as a tile. */
internal fun isTileOption(markup: String): Boolean = TILE_OPTION.matches(markup.trim())

private val TILE_OPTION = Regex("""^\[\[[^\]]+]]$""")
private const val TILES_PER_ROW = 2
private const val SLOW_SPEED = 0.75f

internal const val PRACTICE_PROGRESS_TAG = "practice-progress"
internal const val PRACTICE_NEXT_TAG = "practice-next"
internal const val PRACTICE_LISTEN_TAG = "practice-listen"
internal const val PRACTICE_SLOW_TAG = "practice-slow"
