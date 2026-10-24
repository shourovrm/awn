package app.awn.quran.feature.duas.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.searchMatchFill
import app.awn.quran.feature.duas.R
import app.awn.quran.feature.duas.card.DuaCardData
import app.awn.quran.feature.duas.content.Dua

private const val ARABIC_LINES_SHOWN = 2

/** One search result: where the dua is, its Arabic, and its translation with the match marked. */
@Composable
internal fun DuaSearchResultRow(
    card: DuaCardData,
    query: String,
    onClick: (Dua) -> Unit,
    modifier: Modifier = Modifier,
) {
    val field = Awn.field
    val openingLabel = openingLabelResource(card.dua.opening)?.let { stringResource(it) }
    val reference = listOfNotNull(card.reference, openingLabel).joinToString(separator = " · ")
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = AwnSpacing.touchTarget)
            .clickable(role = Role.Button) { onClick(card.dua) }
            .padding(horizontal = AwnSpacing.screenMargin, vertical = AwnSpacing.small),
    ) {
        Text(text = reference, style = AwnTypography.searchResultReference, color = field.content)
        Text(
            text = card.duaArabic,
            style = AwnTypography.searchResultArabic.copy(textDirection = TextDirection.Rtl),
            color = field.content,
            maxLines = ARABIC_LINES_SHOWN,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        card.translation?.let { translation ->
            Text(
                text = markMatch(translation, query, field.searchMatchFill),
                style = AwnTypography.searchResultTranslation,
                color = field.content,
            )
        }
    }
}

/** [text] with the first occurrence of [query], ignoring case, on a block of [fill]. */
internal fun markMatch(
    text: String,
    query: String,
    fill: Color,
): AnnotatedString {
    val matchStart = if (query.isBlank()) -1 else text.indexOf(query.trim(), ignoreCase = true)
    if (matchStart < 0) return AnnotatedString(text)
    val matchEnd = matchStart + query.trim().length
    return buildAnnotatedString {
        append(text.substring(0, matchStart))
        withStyle(SpanStyle(background = fill)) { append(text.substring(matchStart, matchEnd)) }
        append(text.substring(matchEnd))
    }
}

/** The line shown when a search finds nothing. */
@Composable
internal fun DuaSearchEmpty(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.duas_search_empty),
        style = AwnTypography.body,
        color = Awn.field.content,
        modifier = modifier.padding(AwnSpacing.screenMargin),
    )
}
