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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
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
import app.awn.quran.feature.learn.sound.LocalUnitSounds
import app.awn.quran.feature.learn.sound.WordSoundStatus
import app.awn.quran.feature.learn.words.WordStatus
import kotlinx.coroutines.launch

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

/**
 * One word of the word page: the word to tap and hear, its meaning and its example ayahs. The
 * "Appears" line opens every ayah the word is in ([onOpenAyahs]).
 */
@Composable
internal fun WordPageBody(
    details: WordDetails,
    onReviseNow: () -> Unit,
    onAdd: () -> Unit,
    onOpenAyahs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isMet = details.status != WordStatus.NotMet
    BodyWithButton(
        modifier = modifier,
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
        TappableWord(details.key)
        Text(
            text = details.meaning,
            style = AwnTypography.lead,
            color = Awn.field.content,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        InfoLine(
            stringResource(R.string.word_appears),
            pluralStringResource(
                R.plurals.word_appears_times,
                details.occurrences,
                details.occurrences,
            ),
            onClick = onOpenAyahs,
        )
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

/**
 * The word in large Arabic; a tap plays it. While the word reciter's timings are not on the phone
 * the tap does nothing and the download is offered under the word.
 */
@Composable
private fun TappableWord(key: WordKey) {
    val sounds = LocalUnitSounds.current
    val scope = rememberCoroutineScope()
    var downloadRequests by remember { mutableIntStateOf(0) }
    val status by rememberWordSoundStatus(key, sounds, downloadRequests)
    val canPlay = status == WordSoundStatus.Ready
    ArabicWord(
        key = key,
        style = AwnTypography.poster,
        modifier =
            Modifier.clickable(enabled = canPlay, role = Role.Button) {
                scope.launch { sounds.playWord(key) }
            },
    )
    val missing = status as? WordSoundStatus.Missing
    if (missing != null) {
        WordSoundOffer(
            status = missing,
            onDownload = {
                sounds.downloadWordSound(missing.packId)
                downloadRequests++
            },
        )
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
private fun InfoLine(label: String, value: String, onClick: (() -> Unit)? = null) {
    val tappable =
        if (onClick == null) {
            Modifier
        } else {
            Modifier.heightIn(min = AwnSpacing.touchTarget).clickable(role = Role.Button) {
                onClick()
            }
        }
    Row(
        modifier = Modifier.fillMaxWidth().then(tappable),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = AwnTypography.body, color = Awn.field.content)
        Text(
            text = if (onClick == null) value else "$value $LINK_MARK",
            style = AwnTypography.bodyStrong,
            color = if (onClick == null) Awn.field.content else Awn.field.buttonFill,
        )
    }
}

private const val LINK_MARK = "›"
