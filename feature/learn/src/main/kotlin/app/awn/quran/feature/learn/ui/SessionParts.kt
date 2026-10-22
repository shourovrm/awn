package app.awn.quran.feature.learn.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.awn.quran.core.ui.R as UiR
import app.awn.quran.core.ui.components.AwnIconButton
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.mutedContent
import app.awn.quran.core.ui.theme.softFill
import app.awn.quran.feature.learn.R
import app.awn.quran.feature.learn.session.FrameLabel
import app.awn.quran.feature.learn.session.SessionState

private const val SECONDS_PER_MINUTE = 60

/** The text that names the step in the bar above a session screen. */
@Composable
internal fun frameLabelText(label: FrameLabel): String =
    when (label) {
        FrameLabel.None -> ""
        is FrameLabel.NewWord ->
            stringResource(R.string.session_label_new_word, label.number, label.total)
        is FrameLabel.Check ->
            stringResource(R.string.session_label_check, label.number, label.total)
        is FrameLabel.LessonScreen ->
            stringResource(R.string.session_label_lesson, label.number, label.total)
    }

/** The close button, the step name, the time left and a thin bar for the progress. */
@Composable
internal fun SessionBar(state: SessionState, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = AwnSpacing.screenMargin)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AwnIconButton(
                icon = UiR.drawable.ic_ui_close,
                contentDescription = stringResource(R.string.session_close),
                onClick = onClose,
            )
            Text(
                text = state.frame?.let { frameLabelText(it.label) }.orEmpty(),
                style = AwnTypography.small,
                color = Awn.field.content,
                modifier = Modifier.weight(1f).padding(horizontal = AwnSpacing.small),
            )
            Text(
                text = timeLeftText(state.secondsLeft),
                style = AwnTypography.small,
                color = Awn.field.content,
            )
        }
        ProgressTrack(state.progress)
    }
}

@Composable
private fun timeLeftText(secondsLeft: Int): String =
    if (secondsLeft < SECONDS_PER_MINUTE) {
        stringResource(R.string.session_under_a_minute)
    } else {
        stringResource(
            R.string.session_minutes_left,
            (secondsLeft + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE,
        )
    }

/** A thin track in the soft fill with the done part in the accent. */
@Composable
internal fun ProgressTrack(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(AwnSpacing.tabIndicatorHeight)
                .background(Awn.field.softFill),
    ) {
        Box(
            modifier =
                Modifier.fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(Awn.field.buttonFill),
        )
    }
}

/** How an answer row looks. */
internal enum class AnswerRowState {
    Open,
    Selected,
    Right,
    Wrong,
}

/**
 * One answer: a soft block of at least 72 dp. In the confirm flavour [showRadio] draws a ring that
 * fills when selected; a right answer takes the button colours and a tick.
 */
@Composable
internal fun AnswerRow(
    markup: String,
    state: AnswerRowState,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    showRadio: Boolean = false,
) {
    val field = Awn.field
    val isRight = state == AnswerRowState.Right
    val background = if (isRight) field.buttonFill else field.softFill
    val content = if (isRight) field.onButton else field.content
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = AwnSpacing.giant + AwnSpacing.small)
                .clip(AwnShapes.control)
                .background(background)
                .then(
                    if (state == AnswerRowState.Wrong)
                        Modifier.border(BorderStroke(2.dp, field.wrongWord), AwnShapes.control)
                    else Modifier,
                )
                .then(
                    if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick)
                    else Modifier,
                )
                .padding(horizontal = AwnSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AwnSpacing.medium),
    ) {
        if (showRadio) RadioMark(isSelected = state == AnswerRowState.Selected)
        val isArabic = isArabicAnswer(markup)
        RichText(
            text = markup,
            style = if (isArabic) AwnTypography.lead else AwnTypography.bodyStrong,
            color = content,
            modifier = Modifier.weight(1f),
            textAlign = if (isArabic) TextAlign.Center else TextAlign.Start,
        )
        if (isRight) {
            Icon(
                painter = painterResource(UiR.drawable.ic_ui_check),
                contentDescription = stringResource(R.string.answer_right),
                tint = content,
                modifier = Modifier.size(AwnSpacing.iconSize),
            )
        }
    }
}

/**
 * True when the answer is Arabic to read: a Quran word, or nothing but a typed fragment such as one
 * letter. Those are drawn large and centred; an answer in words stays at the start.
 */
internal fun isArabicAnswer(markup: String): Boolean =
    markup.contains("{{") || ARABIC_FRAGMENT_ONLY.matches(markup.trim())

private val ARABIC_FRAGMENT_ONLY = Regex("""^\[\[[^\]]+]]$""")

@Composable
internal fun RadioMark(isSelected: Boolean) {
    val field = Awn.field
    Box(
        modifier =
            Modifier.size(AwnSpacing.iconSize)
                .clip(CircleShape)
                .border(BorderStroke(2.dp, field.content), CircleShape)
                .then(if (isSelected) Modifier.background(field.buttonFill) else Modifier),
    )
}

/** A text-only button of 48 dp, for "I don't know". */
@Composable
internal fun QuietButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = AwnSpacing.touchTarget)
                .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = AwnTypography.bodyStrong, color = Awn.field.content)
    }
}

/** A muted line under a screen's main content. */
@Composable
internal fun MutedLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = AwnTypography.small,
        color = Awn.field.mutedContent,
        modifier = modifier,
    )
}
