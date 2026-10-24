package app.awn.quran.feature.duas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import app.awn.quran.core.data.MyWords
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.mutedContent
import app.awn.quran.core.ui.theme.softFill
import app.awn.quran.feature.duas.R
import app.awn.quran.feature.duas.card.DuaWordTile
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

internal const val ADD_WORD_TAG = "dua-add-word"

/** Test tag of a word tile. */
internal fun wordTileTag(key: WordKey): String = "dua-tile-$key"

/**
 * The dua's words as tiles from right to left: Arabic, meaning, sound in Latin letters. A tap plays
 * the word and opens a strip under the tiles with "Add to my words".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WordTileGrid(
    tiles: List<DuaWordTile>,
    myWords: MyWords?,
    onPlayWord: (WordKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedKey by remember { mutableStateOf<WordKey?>(null) }
    val selectedTile = tiles.firstOrNull { it.key == selectedKey }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AwnSpacing.medium)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small),
                verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
            ) {
                tiles.forEach { tile ->
                    WordTile(
                        tile = tile,
                        isSelected = tile.key == selectedKey,
                        onClick = {
                            selectedKey = tile.key
                            onPlayWord(tile.key)
                        },
                    )
                }
            }
        }
        if (selectedTile != null) {
            WordDetailStrip(selectedTile, myWords)
        }
    }
}

@Composable
private fun WordTile(tile: DuaWordTile, isSelected: Boolean, onClick: () -> Unit) {
    val field = Awn.field
    Column(
        modifier =
            Modifier.widthIn(min = AwnSpacing.giant)
                .heightIn(min = AwnSpacing.touchTarget)
                .clip(AwnShapes.wordBlock)
                .background(if (isSelected) field.selectedAyahFill else field.softFill)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = AwnSpacing.small, vertical = AwnSpacing.small)
                .testTag(wordTileTag(tile.key)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = tile.arabic,
            style = AwnTypography.quranLead.copy(textDirection = TextDirection.Rtl),
            color = field.content,
        )
        Text(
            text = tile.meaning,
            style = AwnTypography.caption,
            color = field.content,
            textAlign = TextAlign.Center,
        )
        Text(
            text = tile.transliteration,
            style = AwnTypography.caption,
            color = field.mutedContent,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun WordDetailStrip(tile: DuaWordTile, myWords: MyWords?) {
    val field = Awn.field
    val scope = rememberCoroutineScope()
    val isAdded by
        remember(tile.key, myWords) { myWords?.isAdded(tile.key) ?: flowOf(false) }
            .collectAsState(initial = false)
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(AwnShapes.control)
                .background(field.softFill)
                .padding(AwnSpacing.medium),
    ) {
        Text(
            text = tile.arabic,
            style = AwnTypography.quranLead.copy(textDirection = TextDirection.Rtl),
            color = field.content,
        )
        Text(
            text =
                listOf(tile.transliteration, tile.meaning)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
            style = AwnTypography.small,
            color = field.content,
        )
        if (myWords != null) {
            Text(
                text =
                    stringResource(
                        if (isAdded) R.string.duas_word_added else R.string.duas_add_word,
                    ),
                style = AwnTypography.bodyStrong,
                color = field.buttonFill,
                modifier =
                    Modifier.heightIn(min = AwnSpacing.touchTarget)
                        .clickable(enabled = !isAdded, role = Role.Button) {
                            scope.launch { myWords.add(tile.key) }
                        }
                        .padding(top = AwnSpacing.small)
                        .testTag(ADD_WORD_TAG),
            )
        }
    }
}
