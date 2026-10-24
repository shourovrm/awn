package app.awn.quran.feature.learn.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.components.AwnSearchField
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.learn.LearnBar
import app.awn.quran.feature.learn.R
import app.awn.quran.feature.learn.words.WordAyah
import app.awn.quran.feature.learn.words.WordAyahSearch

/**
 * Every ayah a word appears in, with the word marked and its surah and number below it. The field
 * at the top filters by surah, reference or text once [search] has loaded (null until then). A tap
 * on an ayah calls [onOpenAyah]; without it (tests, previews) the rows are not tappable.
 */
@Composable
internal fun WordAyahsScreen(
    word: WordKey,
    ayahs: List<WordAyah>,
    search: WordAyahSearch?,
    onOpenAyah: ((AyahRef) -> Unit)?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown =
        remember(ayahs, search, query) {
            val matching = search?.matching(query)?.toSet()
            if (matching == null) ayahs else ayahs.filter { it.ayah in matching }
        }
    Column(modifier.fillMaxSize()) {
        LearnBar(
            title = stringResource(R.string.word_ayahs_title),
            startLinkLabel = stringResource(R.string.back),
            onStartLinkClick = onBack,
        )
        Column(
            modifier = Modifier.padding(horizontal = AwnSpacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
        ) {
            ArabicWord(word, modifier = Modifier.fillMaxWidth())
            AwnSearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = stringResource(R.string.word_ayahs_search_hint),
                clearDescription = stringResource(R.string.word_ayahs_clear),
            )
            Text(
                text = countText(shown.size, ayahs.size),
                style = AwnTypography.small,
                color = Awn.field.content,
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
        ) {
            items(shown, key = { it.ayah.toString() }) { row -> WordAyahRow(row, onOpenAyah) }
        }
    }
}

@Composable
private fun countText(shownCount: Int, totalCount: Int): String =
    if (shownCount == totalCount) {
        pluralStringResource(R.plurals.word_ayahs_count, totalCount, totalCount)
    } else {
        pluralStringResource(R.plurals.word_ayahs_filtered, totalCount, shownCount, totalCount)
    }

// The card shows the surah name and ayah number under the ayah, so the row adds only the tap.
@Composable
private fun WordAyahRow(row: WordAyah, onOpenAyah: ((AyahRef) -> Unit)?) {
    val clickable =
        if (onOpenAyah == null) {
            Modifier
        } else {
            Modifier.clickable(role = Role.Button) { onOpenAyah(row.ayah) }
        }
    AyahCard(
        row.ayah,
        modifier =
            clickable.padding(horizontal = AwnSpacing.screenMargin, vertical = AwnSpacing.small),
        highlight = row.wordKeys.first(),
    )
}
