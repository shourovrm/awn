package app.awn.quran.feature.duas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import app.awn.quran.core.data.MyWords
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.mutedContent
import app.awn.quran.core.ui.theme.raisedSurface
import app.awn.quran.core.ui.theme.softFill
import app.awn.quran.feature.duas.R
import app.awn.quran.feature.duas.card.DuaCardData
import app.awn.quran.feature.duas.card.DuaWordTile

internal const val OPEN_MUSHAF_TAG = "dua-open-mushaf"
internal const val SHOW_DUA_ONLY_TAG = "dua-show-dua-only"

/** What decides how one card looks. */
internal class CardPageState(
    val mode: DuaCardMode,
    val showDuaOnly: Boolean,
    /** True while reciting with the words hidden, when the translation would give them away. */
    val isTranslationHidden: Boolean,
    val highlights: CardHighlights,
)

/** What the controls on a card do. */
internal class CardPageActions(
    val onToggleDuaOnly: () -> Unit,
    val onOpenMushaf: () -> Unit,
    val onPlayWord: (WordKey) -> Unit,
    val onFlaggedWordClick: (WordKey) -> Unit,
)

/** One card of the pager: the dua's reference, its words or word tiles, translation and links. */
@Composable
internal fun DuaCardPage(
    card: DuaCardData?,
    wordTiles: List<DuaWordTile>,
    state: CardPageState,
    myWords: MyWords?,
    actions: CardPageActions,
    modifier: Modifier = Modifier,
) {
    // The texts are read in the background; the card shows nothing until they are there.
    if (card == null) return
    val field = Awn.field
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AwnSpacing.screenMargin, vertical = AwnSpacing.small),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .clip(AwnShapes.control)
                .background(field.raisedSurface)
                .padding(AwnSpacing.cardInnerPadding),
            verticalArrangement = Arrangement.spacedBy(AwnSpacing.medium),
        ) {
            CardHeader(card)
            if (state.mode == DuaCardMode.WordByWord) {
                WordTileGrid(wordTiles, myWords, actions.onPlayWord)
            } else {
                val textStyle: TextStyle =
                    if (card.hasWordsOutsideDua) AwnTypography.quranLead else AwnTypography.quran
                CardWordFlow(
                    words = card.words,
                    showDuaOnly = state.showDuaOnly,
                    highlights = state.highlights,
                    onFlaggedWordClick = actions.onFlaggedWordClick,
                    textStyle = textStyle,
                    modifier = Modifier.fillMaxWidth(),
                )
                CardTranslation(card.translation, state.isTranslationHidden)
            }
            CardFooter(card, state, actions)
        }
    }
}

@Composable
private fun CardHeader(card: DuaCardData) {
    val field = Awn.field
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small),
    ) {
        Text(
            text = card.reference,
            style = AwnTypography.bodyStrong,
            color = field.content,
            modifier = Modifier.weight(1f, fill = false),
        )
        openingLabelResource(card.dua.opening)?.let { label ->
            Text(
                text = stringResource(label),
                style = AwnTypography.small,
                color = field.content,
                modifier =
                    Modifier.clip(AwnShapes.wordBlock)
                        .background(field.softFill)
                        .padding(horizontal = AwnSpacing.small),
            )
        }
    }
}

@Composable
private fun CardTranslation(translation: String?, isHidden: Boolean) {
    val field = Awn.field
    when {
        isHidden ->
            Text(
                text = stringResource(R.string.duas_translation_hidden),
                style = AwnTypography.small,
                color = field.mutedContent,
            )
        translation != null ->
            Text(text = translation, style = AwnTypography.body, color = field.content)
    }
}

@Composable
private fun CardFooter(card: DuaCardData, state: CardPageState, actions: CardPageActions) {
    val field = Awn.field
    val hasToggle = card.hasWordsOutsideDua && state.mode != DuaCardMode.WordByWord
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (hasToggle) Arrangement.SpaceBetween else Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasToggle) {
            Text(
                text =
                    stringResource(
                        if (state.showDuaOnly) R.string.duas_show_whole_ayah
                        else R.string.duas_show_dua_only,
                    ),
                style = AwnTypography.bodyStrong,
                color = field.buttonFill,
                modifier =
                    Modifier.heightIn(min = AwnSpacing.touchTarget)
                        .clickable(role = Role.Button, onClick = actions.onToggleDuaOnly)
                        .testTag(SHOW_DUA_ONLY_TAG)
                        .padding(vertical = AwnSpacing.small),
            )
        }
        Text(
            text = stringResource(R.string.duas_open_mushaf),
            style = AwnTypography.bodyStrong,
            color = field.buttonFill,
            modifier =
                Modifier.heightIn(min = AwnSpacing.touchTarget)
                    .clickable(role = Role.Button, onClick = actions.onOpenMushaf)
                    .testTag(OPEN_MUSHAF_TAG)
                    .padding(vertical = AwnSpacing.small),
        )
    }
}
