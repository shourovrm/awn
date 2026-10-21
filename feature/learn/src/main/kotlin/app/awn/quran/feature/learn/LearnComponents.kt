package app.awn.quran.feature.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import app.awn.quran.core.ui.R as UiR
import app.awn.quran.core.ui.components.AwnIconButton
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnField
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.LocalColourMode
import app.awn.quran.core.ui.theme.awnFieldColors
import java.text.NumberFormat

// Three 64 dp steps keep a tile roughly square at 360 dp and let it grow with large text.
private const val TILE_MINIMUM_HEIGHT_STEPS = 3

/** Formats a count with the digits of the user's language. */
@Composable
internal fun formatCount(count: Int): String =
    remember(count) { NumberFormat.getIntegerInstance().format(count) }

/** A bar with the screen name or a back link on the left and an optional link on the right. */
@Composable
internal fun LearnBar(
    title: String,
    modifier: Modifier = Modifier,
    startLinkLabel: String? = null,
    onStartLinkClick: () -> Unit = {},
    endLinkLabel: String? = null,
    onEndLinkClick: () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = AwnSpacing.touchTarget)
                .padding(horizontal = AwnSpacing.screenMargin),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (startLinkLabel != null) {
                AwnIconButton(
                    icon = UiR.drawable.ic_ui_arrow_back,
                    contentDescription = startLinkLabel,
                    onClick = onStartLinkClick,
                )
            }
            Text(
                text = title,
                style = AwnTypography.bodyStrong,
                color = Awn.field.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (endLinkLabel != null) {
            TextLink(label = endLinkLabel, onClick = onEndLinkClick)
        }
        actions()
    }
}

/** A text-only action; the 48 dp height is the touch target. */
@Composable
internal fun TextLink(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .sizeIn(minHeight = AwnSpacing.touchTarget, minWidth = AwnSpacing.touchTarget)
                .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = AwnTypography.bodyStrong, color = Awn.field.content)
    }
}

/** One big number on a field of its own, with a caption: the poster size from the design system. */
@Composable
internal fun FigureTile(
    field: AwnField,
    figure: Int,
    caption: String,
    modifier: Modifier = Modifier,
) {
    // In Awn mode the tiles keep the green and night fields; other modes use their own colours.
    val colors = awnFieldColors(field, LocalColourMode.current)
    Column(
        modifier =
            modifier
                .defaultMinSize(minHeight = AwnSpacing.giant * TILE_MINIMUM_HEIGHT_STEPS)
                .background(colors.background)
                .padding(AwnSpacing.medium),
    ) {
        Text(text = formatCount(figure), style = AwnTypography.poster, color = colors.content)
        Text(
            text = caption,
            style = AwnTypography.body,
            color = colors.content,
            modifier = Modifier.padding(top = AwnSpacing.small),
        )
    }
}

/** A lesson as a poster line with a one-line detail. */
@Composable
internal fun LessonLine(lesson: LessonSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val detailResource =
        if (lesson.isDraft) R.plurals.lesson_detail_draft else R.plurals.lesson_detail_reviewed
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = AwnSpacing.touchTarget)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = AwnSpacing.small),
    ) {
        Text(text = lesson.title, style = AwnTypography.title)
        Text(
            text = pluralStringResource(detailResource, lesson.exampleCount, lesson.exampleCount),
            style = AwnTypography.small,
        )
    }
}

/** Arabic text laid out right to left, in the Quran type style. */
@Composable
internal fun ArabicText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = AwnTypography.quran.copy(textDirection = TextDirection.Rtl),
        textAlign = TextAlign.Start,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
internal fun DraftLabel(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.lesson_draft),
        style = AwnTypography.small,
        modifier = modifier,
    )
}
