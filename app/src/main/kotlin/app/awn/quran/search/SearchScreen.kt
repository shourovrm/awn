package app.awn.quran.search

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
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

private const val MAX_RESULT_LINES = 3

/** One search field over the Arabic and the installed translations, with the matches marked. */
@Composable
fun SearchScreen(
    typedQuery: String,
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onOpen: (page: Int, ayah: AyahRef?) -> Unit,
    modifier: Modifier = Modifier,
) {
    AwnTheme(field = AwnField.Paper, barBehindStatusBar = true) {
        Column(modifier.fillMaxSize()) {
            SearchBar(typedQuery, onQueryChange, onBack)
            val rows = state.rows
            if (rows != null) {
                Text(
                    text =
                        pluralStringResource(R.plurals.search_result_count, rows.size, rows.size),
                    style = AwnTypography.small,
                    color = Awn.field.content,
                    modifier = Modifier.padding(AwnSpacing.screenMargin),
                )
                LazyColumn(Modifier.fillMaxSize()) {
                    items(rows.size) { index ->
                        ResultRow(rows[index]) { onOpen(rows[index].page, rows[index].hit.ayah) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, onBack: () -> Unit) {
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
        }
    }
}

private const val HINT_ALPHA = 0.6f

@Composable
private fun ResultRow(row: SearchRow, onClick: () -> Unit) {
    val field = Awn.field
    val isArabic = row.hit.source == SearchSource.Arabic
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = AwnSpacing.huge)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = AwnSpacing.screenMargin, vertical = AwnSpacing.small),
    ) {
        Text(
            text = markMatches(row, field.selectedAyahFill),
            style =
                if (isArabic) {
                    AwnTypography.body.copy(
                        fontFamily = AwnTypography.quran.fontFamily,
                        textDirection = TextDirection.Rtl,
                    )
                } else {
                    AwnTypography.body
                },
            color = field.content,
            maxLines = MAX_RESULT_LINES,
            overflow = TextOverflow.Ellipsis,
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
            style = AwnTypography.small,
            color = field.buttonFill,
        )
    }
}

private fun markMatches(row: SearchRow, markColor: Color): AnnotatedString = buildAnnotatedString {
    append(row.hit.text)
    row.hit.matches.forEach { range ->
        addStyle(
            SpanStyle(background = markColor, fontWeight = FontWeight.SemiBold),
            range.first,
            range.last + 1,
        )
    }
}
