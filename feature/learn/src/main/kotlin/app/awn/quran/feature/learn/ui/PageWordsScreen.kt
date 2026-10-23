package app.awn.quran.feature.learn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.ui.components.AwnButton
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.mutedContent
import app.awn.quran.core.ui.theme.softFill
import app.awn.quran.feature.learn.LearnBar
import app.awn.quran.feature.learn.R
import app.awn.quran.feature.learn.course.PageWordFilter
import app.awn.quran.feature.learn.course.PageWordList
import app.awn.quran.feature.learn.course.PageWordRow
import app.awn.quran.feature.learn.words.WordStatus

/** The words of one Quran page with their meanings, ready to draw. */
internal data class PageWordsContent(
    val page: Int,
    val firstAyah: AyahRef?,
    val lastAyah: AyahRef?,
    val list: PageWordList,
    /** The meaning of each form by rank; a form without one has no entry. */
    val meanings: Map<Int, String>,
)

/** What the buttons and rows of the page do; [onOpenPage] is null when the host cannot open it. */
internal class PageWordsActions(
    val onOpenWord: (Int) -> Unit,
    val onOpenPage: (() -> Unit)?,
    val onLearnNew: () -> Unit,
    val onBack: () -> Unit,
)

private val CHIP_BORDER = 1.dp

/**
 * Every different word on a Quran page, once, with its meaning, how often it occurs in the Quran
 * and whether the user knows it. A word opens its own page. "Learn new" queues the new words so
 * they come first in the next sets.
 */
@Composable
internal fun PageWordsScreen(
    content: PageWordsContent,
    actions: PageWordsActions,
    modifier: Modifier = Modifier,
) {
    var filter by remember { mutableStateOf(PageWordFilter.All) }
    val shown = content.list.rowsFor(filter)
    Column(modifier = modifier.fillMaxSize()) {
        LearnBar(
            title = stringResource(R.string.page_words_title, content.page),
            startLinkLabel = stringResource(R.string.back),
            onStartLinkClick = actions.onBack,
        )
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = AwnSpacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
        ) {
            item { Subtitle(content) }
            item { FilterChips(content.list, filter, onSelect = { filter = it }) }
            if (shown.isEmpty()) {
                item { EmptyLine() }
            }
            items(shown, key = { it.rank }) { row ->
                PageWordRowView(row, content.meanings[row.rank], actions.onOpenWord)
            }
        }
        BottomButtons(content.list, actions)
    }
}

@Composable
private fun Subtitle(content: PageWordsContent) {
    val list = content.list
    val knownCount = list.countOf(PageWordFilter.Known)
    val range = rangeText(content.firstAyah, content.lastAyah)
    val text =
        if (range == null) {
            pluralStringResource(
                R.plurals.words_page_detail,
                list.rows.size,
                list.rows.size,
                knownCount,
            )
        } else {
            pluralStringResource(
                R.plurals.page_words_subtitle,
                list.rows.size,
                range,
                list.rows.size,
                knownCount,
            )
        }
    Text(text = text, style = AwnTypography.body, color = Awn.field.mutedContent)
}

/** "Al-Baqarah 157–163", or both surahs when the page crosses a surah boundary. */
@Composable
private fun rangeText(first: AyahRef?, last: AyahRef?): String? {
    val environment = LocalLearnEnvironment.current ?: return null
    if (first == null || last == null) return null
    return if (first.surah == last.surah) {
        stringResource(
            R.string.page_words_range,
            environment.surahName(first.surah),
            first.ayah,
            last.ayah,
        )
    } else {
        stringResource(
            R.string.page_words_range_across,
            environment.surahName(first.surah),
            first.ayah,
            environment.surahName(last.surah),
            last.ayah,
        )
    }
}

@Composable
private fun FilterChips(
    list: PageWordList,
    selected: PageWordFilter,
    onSelect: (PageWordFilter) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small),
    ) {
        PageWordFilter.entries.forEach { filter ->
            val field = Awn.field
            val isSelected = filter == selected
            Box(
                modifier =
                    Modifier.heightIn(min = AwnSpacing.touchTarget)
                        .clip(AwnShapes.control)
                        .background(if (isSelected) field.buttonFill else Color.Transparent)
                        .border(CHIP_BORDER, field.softFill, AwnShapes.control)
                        .clickable(role = Role.Tab) { onSelect(filter) }
                        .padding(horizontal = AwnSpacing.medium),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = chipLabel(filter, list.countOf(filter)),
                    style = AwnTypography.small,
                    color = if (isSelected) field.onButton else field.content,
                )
            }
        }
    }
}

@Composable
private fun chipLabel(filter: PageWordFilter, count: Int): String =
    stringResource(
        when (filter) {
            PageWordFilter.All -> R.string.page_words_filter_all
            PageWordFilter.New -> R.string.page_words_filter_new
            PageWordFilter.Learning -> R.string.page_words_filter_learning
            PageWordFilter.Known -> R.string.page_words_filter_known
        },
        count,
    )

@Composable
private fun EmptyLine() {
    Text(
        text = stringResource(R.string.page_words_empty),
        style = AwnTypography.body,
        color = Awn.field.mutedContent,
    )
}

@Composable
private fun PageWordRowView(row: PageWordRow, meaning: String?, onOpen: (Int) -> Unit) {
    val field = Awn.field
    Row(
        modifier =
            Modifier.fillMaxWidth().heightIn(min = AwnSpacing.settingsRowTall).clickable(
                role = Role.Button,
            ) {
                onOpen(row.rank)
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AwnSpacing.medium),
    ) {
        ArabicWord(
            key = row.key,
            style = AwnTypography.lead,
            modifier = Modifier.width(AwnSpacing.giant * 2),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = meaning.orEmpty(), style = AwnTypography.body, color = field.content)
            Text(
                text =
                    pluralStringResource(
                        R.plurals.page_word_times,
                        row.occurrences,
                        row.occurrences,
                    ),
                style = AwnTypography.small,
                color = field.mutedContent,
            )
        }
        StatusChip(row.status)
    }
}

@Composable
private fun StatusChip(status: WordStatus) {
    val field = Awn.field
    val label =
        stringResource(
            when (status) {
                WordStatus.NotMet -> R.string.page_word_state_new
                WordStatus.Learning -> R.string.word_state_learning
                WordStatus.Known -> R.string.word_state_known
            },
        )
    val isKnown = status == WordStatus.Known
    Text(
        text = label,
        style = AwnTypography.small,
        color = if (isKnown) field.onButton else field.content,
        modifier =
            Modifier.clip(AwnShapes.control)
                .background(
                    when (status) {
                        WordStatus.Known -> field.buttonFill
                        WordStatus.Learning -> field.softFill
                        WordStatus.NotMet -> Color.Transparent
                    },
                )
                .border(CHIP_BORDER, field.softFill, AwnShapes.control)
                .padding(horizontal = AwnSpacing.small, vertical = AwnSpacing.small / 2),
    )
}

@Composable
private fun BottomButtons(list: PageWordList, actions: PageWordsActions) {
    val field = Awn.field
    val toQueue = list.ranksToQueue.size
    val hasNewWords = list.countOf(PageWordFilter.New) > 0
    Row(
        modifier = Modifier.fillMaxWidth().padding(AwnSpacing.screenMargin),
        horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small),
    ) {
        actions.onOpenPage?.let { openPage ->
            AwnButton(
                label = stringResource(R.string.page_words_open),
                onClick = openPage,
                modifier = Modifier.weight(1f),
                containerColor = field.softFill,
                contentColor = field.content,
            )
        }
        if (hasNewWords) {
            AwnButton(
                label =
                    if (toQueue > 0) {
                        pluralStringResource(R.plurals.page_words_learn_new, toQueue, toQueue)
                    } else {
                        stringResource(R.string.word_added)
                    },
                onClick = actions.onLearnNew,
                modifier = Modifier.weight(1f),
                enabled = toQueue > 0,
            )
        }
    }
}
