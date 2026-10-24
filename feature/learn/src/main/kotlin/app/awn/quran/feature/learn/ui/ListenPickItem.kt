package app.awn.quran.feature.learn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.mutedContent
import app.awn.quran.feature.learn.R
import app.awn.quran.feature.learn.course.LessonCheck
import app.awn.quran.feature.learn.course.LessonText
import app.awn.quran.feature.learn.course.ListenPick
import app.awn.quran.feature.learn.course.PracticeDraw
import app.awn.quran.feature.learn.course.PracticeRun
import app.awn.quran.feature.learn.sound.LocalUnitSounds
import kotlinx.coroutines.launch

private val PLAY_BUTTON_SIZE = AwnSpacing.listenButtonSize
private val PLAY_ICON_SIZE = AwnSpacing.huge
private const val OPTIONS_PER_ROW = 2

/**
 * Listen, then pick the Arabic: a large play button, four options in two rows and "I don't know".
 * The sound plays when the question appears and again after the answer, so a wrong pick is heard
 * next to the right Arabic.
 */
@Composable
internal fun ListenPickItem(check: LessonCheck, pick: Int?, onPick: (Int) -> Unit) {
    val sounds = LocalUnitSounds.current
    val scope = rememberCoroutineScope()
    PlayOnShowAndAfterAnswer(check, pick)
    Column(
        verticalArrangement = Arrangement.spacedBy(AwnSpacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        PlayButton { scope.launch { sounds.playClips(check.clipIds) } }
        Text(
            text = stringResource(R.string.practice_tap_to_hear),
            style = AwnTypography.small,
            color = Awn.field.mutedContent,
        )
        ListenOptions(check, pick, onPick)
        if (pick == null) {
            Text(
                text = stringResource(R.string.question_dont_know),
                style = AwnTypography.bodyStrong,
                color = Awn.field.content,
                modifier =
                    Modifier.clickable(role = Role.Button) { onPick(ListenPick.NO_ANSWER) }
                        .padding(AwnSpacing.medium)
                        .testTag(PRACTICE_DONT_KNOW_TAG),
            )
        }
    }
}

/**
 * Plays the sound of a listen question as it appears and again once its answer is in; leaving the
 * screen stops it.
 */
@Composable
private fun PlayOnShowAndAfterAnswer(item: LessonCheck, pick: Int?) {
    val sounds = LocalUnitSounds.current
    LaunchedEffect(item, pick) { sounds.playClips(item.clipIds) }
    DisposableEffect(item) { onDispose { sounds.stop() } }
}

@Composable
private fun PlayButton(onClick: () -> Unit) {
    Box(
        modifier =
            Modifier.size(PLAY_BUTTON_SIZE)
                .clip(CircleShape)
                .background(Awn.field.buttonFill)
                .clickable(role = Role.Button, onClick = onClick)
                .testTag(PRACTICE_LISTEN_TAG),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_learn_play),
            contentDescription = stringResource(R.string.practice_listen),
            tint = Awn.field.onButton,
            modifier = Modifier.size(PLAY_ICON_SIZE),
        )
    }
}

@Composable
private fun ListenOptions(check: LessonCheck, pick: Int?, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
        check.options.withIndex().chunked(OPTIONS_PER_ROW).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
                row.forEach { (index, option) ->
                    OptionTile(
                        markup = option,
                        state = optionState(check, pick, index),
                        onClick = if (pick == null) ({ onPick(index) }) else null,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * The ten items of one visit to a unit's practice. Listen questions need the recorded sounds;
 * without them the pool keeps its read-aloud items, and the run is drawn again once they are found.
 */
@Composable
internal fun rememberPracticeRun(
    lessonId: String,
    text: LessonText,
    seed: Long,
): MutableState<PracticeRun> {
    val sounds = LocalUnitSounds.current
    val canListen by
        produceState(false, text, sounds) {
            value = sounds.hasClips(ListenPick.materialOf(text).flatMap { it.clipIds })
        }
    return remember(lessonId, seed, canListen) {
        val pool = if (canListen) ListenPick.poolWithQuestions(text, seed) else text.practiceItems
        mutableStateOf(PracticeRun(PracticeDraw.draw(pool, PracticeDraw.ITEMS_PER_VISIT, seed)))
    }
}

internal const val PRACTICE_DONT_KNOW_TAG = "practice-dont-know"
