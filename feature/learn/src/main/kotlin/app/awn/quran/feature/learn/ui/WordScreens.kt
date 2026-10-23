package app.awn.quran.feature.learn.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.components.AwnChoiceRow
import app.awn.quran.core.ui.components.AwnSearchField
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.mutedContent
import app.awn.quran.feature.learn.LearnBar
import app.awn.quran.feature.learn.R
import app.awn.quran.feature.learn.words.WordStatus

/** One row of the word list. */
internal data class WordListRow(
    val rank: Int,
    val key: WordKey,
    val meaning: String,
    val status: WordStatus,
)

/** What the list shows. */
internal enum class WordFilter {
    All,
    Known,
    Learning,
    NotMet,
}

private fun WordFilter.matches(status: WordStatus): Boolean =
    when (this) {
        WordFilter.All -> true
        WordFilter.Known -> status == WordStatus.Known
        WordFilter.Learning -> status == WordStatus.Learning
        WordFilter.NotMet -> status == WordStatus.NotMet
    }

@Composable
private fun filterLabel(filter: WordFilter): String =
    stringResource(
        when (filter) {
            WordFilter.All -> R.string.words_filter_all
            WordFilter.Known -> R.string.words_filter_known
            WordFilter.Learning -> R.string.words_filter_learning
            WordFilter.NotMet -> R.string.words_filter_not_met
        },
    )

@Composable
private fun statusLabel(status: WordStatus): String =
    stringResource(
        when (status) {
            WordStatus.Known -> R.string.word_state_known
            WordStatus.Learning -> R.string.word_state_learning
            WordStatus.NotMet -> R.string.word_state_not_met
        },
    )

/** The words the user has met and the ones coming next, with search and a filter. */
@Composable
internal fun WordListScreen(
    rows: List<WordListRow>,
    onOpen: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(WordFilter.All) }
    val shown =
        remember(rows, query, filter) {
            val needle = query.trim().lowercase()
            rows.filter {
                filter.matches(it.status) &&
                    (needle.isEmpty() || it.meaning.lowercase().contains(needle))
            }
        }
    Column(modifier = modifier.fillMaxSize()) {
        LearnBar(
            title = stringResource(R.string.words_title),
            startLinkLabel = stringResource(R.string.back),
            onStartLinkClick = onBack,
        )
        Column(modifier = Modifier.padding(horizontal = AwnSpacing.screenMargin)) {
            AwnSearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = stringResource(R.string.words_search),
                clearDescription = stringResource(R.string.words_search_clear),
            )
            AwnChoiceRow(
                label = stringResource(R.string.words_show),
                options = WordFilter.entries,
                selected = filter,
                optionLabel = { filterLabel(it) },
                onSelect = { filter = it },
            )
            Text(
                text = pluralStringResource(R.plurals.words_count, shown.size, shown.size),
                style = AwnTypography.bodyStrong,
                color = Awn.field.content,
            )
        }
        LazyColumn(modifier = Modifier.padding(horizontal = AwnSpacing.screenMargin)) {
            items(shown, key = { it.rank }) { row -> WordRow(row, onClick = { onOpen(row.rank) }) }
        }
    }
}

@Composable
internal fun WordRow(row: WordListRow, onClick: () -> Unit) {
    val muted = row.status == WordStatus.NotMet
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = AwnSpacing.settingsRowTall)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = AwnSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AwnSpacing.medium),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.meaning,
                style = AwnTypography.body,
                color = if (muted) Awn.field.mutedContent else Awn.field.content,
            )
            Text(
                text =
                    stringResource(R.string.words_status_rank, statusLabel(row.status), row.rank),
                style = AwnTypography.small,
                color = Awn.field.mutedContent,
            )
        }
        ArabicWord(
            key = row.key,
            style = AwnTypography.lead,
            modifier = Modifier.width(AwnSpacing.giant * 2).alpha(if (muted) MUTED_ALPHA else 1f),
        )
    }
}

private const val MUTED_ALPHA = 0.6f

/** Everything the word page shows. */
internal data class WordDetails(
    val rank: Int,
    val key: WordKey,
    val meaning: String,
    val status: WordStatus,
    val occurrences: Int,
    val backInDays: Int?,
    val ayahs: List<AyahRef>,
    val isQueued: Boolean,
)

@Composable
internal fun WordPageScreen(
    details: WordDetails,
    onReviseNow: () -> Unit,
    onAdd: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isMet = details.status != WordStatus.NotMet
    Column(modifier = modifier.fillMaxSize()) {
        LearnBar(
            title = stringResource(R.string.word_title),
            startLinkLabel = stringResource(R.string.back),
            onStartLinkClick = onBack,
        )
        BodyWithButton(
            buttonLabel =
                stringResource(
                    when {
                        isMet -> R.string.word_revise_now
                        details.isQueued -> R.string.word_added
                        else -> R.string.word_add
                    },
                ),
            onButtonClick = { if (isMet) onReviseNow() else if (!details.isQueued) onAdd() },
        ) {
            ArabicWord(details.key, style = AwnTypography.poster)
            Text(
                text = details.meaning,
                style = AwnTypography.lead,
                color = Awn.field.content,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            InfoLine(
                stringResource(R.string.word_appears),
                pluralStringResource(
                    R.plurals.word_appears_times,
                    details.occurrences,
                    details.occurrences,
                ),
            )
            InfoLine(stringResource(R.string.word_rank), details.rank.toString())
            InfoLine(stringResource(R.string.word_state), stateText(details))
            details.ayahs.forEach {
                AyahCard(
                    it,
                    highlight =
                        details.key.takeIf { key -> key.ayah == it.ayah && key.surah == it.surah },
                )
            }
        }
    }
}

@Composable
private fun stateText(details: WordDetails): String =
    when {
        details.status == WordStatus.Known && details.backInDays != null ->
            pluralStringResource(
                R.plurals.word_state_known_back,
                details.backInDays,
                details.backInDays,
            )
        else -> statusLabel(details.status)
    }

@Composable
private fun InfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = AwnTypography.body, color = Awn.field.content)
        Text(text = value, style = AwnTypography.bodyStrong, color = Awn.field.content)
    }
}
