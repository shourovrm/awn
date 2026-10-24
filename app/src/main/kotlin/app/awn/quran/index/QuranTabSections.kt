package app.awn.quran.index

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import app.awn.quran.R
import app.awn.quran.core.ui.R as UiR
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.softFill
import app.awn.quran.tabs.TodayItem

/** The Today section: one soft row for each item. The caller leaves it out when there are none. */
@Composable
internal fun TodaySection(items: List<TodayItem>, modifier: Modifier = Modifier) {
    val field = Awn.field
    Column(modifier.fillMaxWidth().padding(top = AwnSpacing.large)) {
        Text(
            text = stringResource(R.string.today_title),
            style = AwnTypography.bodyStrong,
            color = field.buttonFill,
            modifier = Modifier.padding(bottom = AwnSpacing.small).semantics { heading() },
        )
        Column(verticalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
            items.forEach { item -> TodayRow(item) }
        }
    }
}

@Composable
private fun TodayRow(item: TodayItem) {
    val field = Awn.field
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = AwnSpacing.cardRowHeight)
                .clip(AwnShapes.control)
                .background(field.softFill)
                .clickable(role = Role.Button, onClick = item.onOpen)
                .padding(horizontal = AwnSpacing.medium, vertical = AwnSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AwnSpacing.medium),
    ) {
        Icon(
            painter = painterResource(item.icon),
            contentDescription = null,
            tint = field.buttonFill,
            modifier = Modifier.size(AwnSpacing.iconSize),
        )
        Column(Modifier.weight(1f)) {
            Text(text = item.title, style = AwnTypography.bodyStrong, color = field.content)
            Text(text = item.detail, style = AwnTypography.small, color = field.content)
        }
        Icon(
            painter = painterResource(UiR.drawable.ic_ui_chevron),
            contentDescription = null,
            tint = field.content,
            modifier = Modifier.size(AwnSpacing.iconSize),
        )
    }
}

/**
 * The day's snippet: Arabic, its rendering in the app language, and where it is from. A tap on the
 * card shows another snippet, faded in.
 */
@Composable
internal fun DailySnippet(card: DailySnippetCard, modifier: Modifier = Modifier) {
    val field = Awn.field
    Crossfade(
        targetState = card.snippet,
        animationSpec = tween(durationMillis = SNIPPET_FADE_MILLIS),
        label = "snippet",
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = AwnSpacing.large)
                .clickable(
                    onClickLabel = stringResource(R.string.snippet_show_another),
                    onClick = card.onShowAnother,
                ),
    ) { snippet ->
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = snippet.arabic,
                style = AwnTypography.quranLead.copy(textDirection = TextDirection.Rtl),
                color = field.content,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text =
                    buildAnnotatedString {
                        append(snippet.rendering(card.language))
                        append(" ")
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                            append(card.referenceOf(snippet))
                        }
                    },
                style = AwnTypography.small,
                color = field.content,
                modifier = Modifier.padding(top = AwnSpacing.small),
            )
        }
    }
}

private const val SNIPPET_FADE_MILLIS = 200
