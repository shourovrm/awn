package app.awn.quran.feature.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.awn.quran.core.ui.components.AwnButton
import app.awn.quran.core.ui.components.SettingsLinkRow
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnColors
import app.awn.quran.core.ui.theme.AwnField
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.LocalColourMode
import app.awn.quran.core.ui.theme.awnFieldColors
import app.awn.quran.core.ui.theme.mutedContent
import app.awn.quran.core.ui.theme.softFill

private const val INACTIVE_DAY_ALPHA = 0.2f
private const val MINIMUM_BAR_FRACTION = 0.08f

// Room for the bars, the page counts above them and the weekday names below.
private val CHART_HEIGHT = AwnSpacing.giant * 2 + AwnSpacing.large

/**
 * What the buttons of the Reading progress screen do. [read] is null when the Quran screen cannot
 * be opened from here; [hasReadBefore] picks "Continue reading" over "Start reading".
 */
internal class ReadingProgressActions(
    val onNextRevisionClick: () -> Unit,
    val read: OpenPageAction?,
    val hasReadBefore: Boolean,
)

/**
 * "Reading progress" from the Learn hub: the streak with its seven days, four figures and the pages
 * read on each day of the week.
 */
@Composable
internal fun ReadingProgressScreen(
    state: ProgressUiState,
    actions: ReadingProgressActions,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LearnPage(
        title = stringResource(R.string.progress_title),
        onBackClick = onBackClick,
        modifier = modifier,
    ) {
        LearnHeroCard(
            figure = state.streakDays,
            caption = pluralStringResource(R.plurals.reading_streak_caption, state.streakDays),
            extra = { StreakDays(state.days) },
        )
        FigureGrid(state)
        LearnSectionHeading(stringResource(R.string.reading_chart_title))
        PagesChart(state.days)
        if (state.isNewUser) {
            FirstUseCard(actions)
        } else {
            NextRevisionRow(state, actions.onNextRevisionClick)
        }
    }
}

@Composable
private fun StreakDays(days: List<WeekDay>) {
    // Same field as the hero card around it.
    val colors = awnFieldColors(AwnField.Green, LocalColourMode.current)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEach { day ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                StreakDot(day, colors.content)
                Text(
                    text = day.label,
                    style = AwnTypography.caption,
                    color = colors.content,
                    maxLines = 1,
                )
            }
        }
    }
}

/** A dot for one day: ochre when the user did something, with a ring on today. */
@Composable
private fun StreakDot(day: WeekDay, inactiveColour: Color) {
    val ring = if (day.isToday) Modifier.border(2.dp, AwnColors.ochre, CircleShape) else Modifier
    val fill =
        if (day.isActive) AwnColors.ochre else inactiveColour.copy(alpha = INACTIVE_DAY_ALPHA)
    Box(
        modifier = Modifier.size(AwnSpacing.extraLarge).then(ring),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(AwnSpacing.large).clip(CircleShape).background(fill))
    }
}

@Composable
private fun FigureGrid(state: ProgressUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small),
        ) {
            FigureTile(
                figure = formatCount(state.pagesThisWeek),
                caption = pluralStringResource(R.plurals.reading_tile_pages, state.pagesThisWeek),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            FigureTile(
                figure = formatCount(state.memorisedCount),
                caption = pluralStringResource(R.plurals.reading_tile_ayahs, state.memorisedCount),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small),
        ) {
            FigureTile(
                figure = formatCount(state.wordsKnown),
                caption = pluralStringResource(R.plurals.reading_tile_words, state.wordsKnown),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            FigureTile(
                figure = stringResource(R.string.reading_percent, state.quranWordsPercent),
                caption = stringResource(R.string.reading_tile_share),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

/** A figure over a muted caption on a quiet block. */
@Composable
private fun FigureTile(figure: String, caption: String, modifier: Modifier) {
    Column(
        modifier =
            modifier
                .clip(AwnShapes.control)
                .background(Awn.field.softFill)
                .padding(AwnSpacing.medium),
    ) {
        Text(text = figure, style = AwnTypography.title, color = Awn.field.content)
        Text(text = caption, style = AwnTypography.small, color = Awn.field.mutedContent)
    }
}

@Composable
private fun PagesChart(days: List<WeekDay>) {
    val mostPages = days.maxOf { it.pages }
    LearnCard {
        Row(
            modifier = Modifier.fillMaxWidth().height(CHART_HEIGHT),
            horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small),
        ) {
            days.forEach { day -> ChartDay(day, mostPages, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun ChartDay(day: WeekDay, mostPages: Int, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // A day without pages shows no number; the short bar already says zero.
        val pagesLabel = day.pages.takeIf { it > 0 }?.let { formatCount(it) }.orEmpty()
        Text(
            text = pagesLabel,
            style = AwnTypography.caption,
            color = Awn.field.content,
            maxLines = 1,
        )
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
            Bar(pages = day.pages, mostPages = mostPages)
        }
        Text(
            text = day.label,
            style = AwnTypography.caption,
            color = Awn.field.mutedContent,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun Bar(pages: Int, mostPages: Int) {
    val shape = AwnShapes.wordBlock
    if (pages == 0) {
        Box(Modifier.fillMaxWidth().height(4.dp).clip(shape).background(Awn.field.softFill))
        return
    }
    val fraction = (pages.toFloat() / mostPages).coerceAtLeast(MINIMUM_BAR_FRACTION)
    Box(
        Modifier.fillMaxWidth()
            .fillMaxHeight(fraction)
            .clip(shape)
            .background(Awn.field.buttonFill),
    )
}

@Composable
private fun FirstUseCard(actions: ReadingProgressActions) {
    LearnCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = AwnSpacing.small),
            verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
        ) {
            Text(
                text = stringResource(R.string.reading_new_title),
                style = AwnTypography.bodyStrong,
                color = Awn.field.content,
            )
            Text(
                text = stringResource(R.string.reading_new_detail),
                style = AwnTypography.small,
                color = Awn.field.mutedContent,
            )
        }
    }
    val read = actions.read ?: return
    val label =
        if (actions.hasReadBefore) stringResource(R.string.reading_continue)
        else stringResource(R.string.reading_start)
    AwnButton(label = label, onClick = read.onClick, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun NextRevisionRow(state: ProgressUiState, onClick: () -> Unit) {
    val day = state.nextRevisionDay ?: return
    SettingsLinkRow(
        label = stringResource(R.string.reading_next_revision),
        value =
            pluralStringResource(
                R.plurals.reading_next_revision_detail,
                state.nextRevisionCount,
                day,
                formatCount(state.nextRevisionCount),
            ),
        onClick = onClick,
    )
}
