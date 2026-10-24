package app.awn.quran.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import app.awn.quran.R
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.search.SearchSource
import app.awn.quran.core.ui.R as UiR
import app.awn.quran.core.ui.components.AwnIconButton
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnField
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.searchMatchFill

private const val MAX_RESULT_LINES = 3

// Arabic lines are 2.2 times the font size tall to leave room for the marks, so a block of 0.7 of
// the line is about the height of the letters with their marks; Latin lines are 1.5 times.
private const val ARABIC_BLOCK_HEIGHT = 0.7f
private const val LATIN_BLOCK_HEIGHT = 0.85f

/** One search field over the Arabic and the installed translations, with the matches marked. */
@Composable
fun SearchScreen(
    typedQuery: String,
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onOpen: (page: Int, ayah: AyahRef?) -> Unit,
    onOpenSurah: (surah: Int) -> Unit,
    voice: VoiceSearchSlot,
    modifier: Modifier = Modifier,
) {
    val isVoiceOpen = voice.state.phase != VoicePhase.Off
    BackHandler(enabled = isVoiceOpen, onBack = voice.actions.onClose)
    AwnTheme(field = AwnField.Paper, barBehindStatusBar = true) {
        Column(modifier.fillMaxSize()) {
            val keyboard = LocalSoftwareKeyboardController.current
            SearchBar(
                query = typedQuery,
                onQueryChange = { text ->
                    if (isVoiceOpen) voice.actions.onClose()
                    onQueryChange(text)
                },
                onBack = onBack,
                onVoice = {
                    keyboard?.hide()
                    voice.onOpen()
                },
            )
            if (isVoiceOpen) {
                VoiceSearchPanel(voice.state, voice.speechModel, voice.actions, onOpen)
                return@Column
            }
            val rows = state.rows
            if (rows != null || state.surahs.isNotEmpty()) {
                if (rows != null) {
                    Text(
                        text =
                            pluralStringResource(
                                R.plurals.search_result_count,
                                rows.size,
                                rows.size,
                            ),
                        style = AwnTypography.small,
                        color = Awn.field.content,
                        modifier = Modifier.padding(AwnSpacing.screenMargin),
                    )
                }
                val ayahRows = rows.orEmpty()
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.surahs.size) { index ->
                        val chapter = state.surahs[index]
                        SurahResultRow(chapter) { onOpenSurah(chapter.number) }
                    }
                    items(ayahRows.size) { index ->
                        ResultRow(ayahRows[index]) {
                            onOpen(ayahRows[index].page, ayahRows[index].hit.ayah)
                        }
                    }
                }
            }
        }
    }
}

/** Voice search's state and actions, handed through the search screen to its panel. */
class VoiceSearchSlot(
    val state: VoiceSearchUiState,
    val speechModel: SpeechModelOffer,
    val actions: VoiceSearchActions,
    val onOpen: () -> Unit,
)

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onVoice: () -> Unit,
) {
    val field = Awn.field
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .background(field.barBackground)
                .windowInsetsPadding(WindowInsets.statusBars)
                .heightIn(min = AwnSpacing.topBarHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AwnIconButton(
            icon = UiR.drawable.ic_ui_arrow_back,
            contentDescription = stringResource(R.string.navigate_back),
            onClick = onBack,
            tint = field.onBar,
        )
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle =
                AwnTypography.body.copy(color = field.onBar, textDirection = TextDirection.Content),
            cursorBrush = SolidColor(field.onBar),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
            decorationBox = { innerTextField ->
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.search_hint),
                        style = AwnTypography.body,
                        color = field.onBar.copy(alpha = HINT_ALPHA),
                    )
                }
                innerTextField()
            },
        )
        if (query.isNotEmpty()) {
            AwnIconButton(
                icon = UiR.drawable.ic_ui_close,
                contentDescription = stringResource(R.string.search_clear),
                onClick = { onQueryChange("") },
                tint = field.onBar,
            )
        } else {
            AwnIconButton(
                icon = R.drawable.ic_app_recite_person,
                contentDescription = stringResource(R.string.search_voice_start),
                onClick = onVoice,
                tint = field.onBar,
            )
        }
    }
}

private const val HINT_ALPHA = 0.6f

@Composable
internal fun ResultRow(row: SearchRow, onClick: () -> Unit) {
    val field = Awn.field
    val isArabic = row.hit.source == SearchSource.Arabic
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = AwnSpacing.huge)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = AwnSpacing.screenMargin, vertical = AwnSpacing.small),
    ) {
        MatchHighlightedText(
            text = row.hit.text,
            matches = row.hit.matches,
            blockColor = field.searchMatchFill,
            blockHeightFraction = if (isArabic) ARABIC_BLOCK_HEIGHT else LATIN_BLOCK_HEIGHT,
            style =
                if (isArabic) {
                    AwnTypography.searchResultArabic.copy(textDirection = TextDirection.Rtl)
                } else {
                    AwnTypography.searchResultTranslation
                },
            color = field.content,
            maxLines = MAX_RESULT_LINES,
        )
        Text(
            text =
                stringResource(
                    R.string.search_result_reference,
                    row.surahName,
                    row.hit.ayah.surah,
                    row.hit.ayah.ayah,
                    row.page,
                ),
            style = AwnTypography.searchResultReference,
            color = field.buttonFill,
        )
    }
}
