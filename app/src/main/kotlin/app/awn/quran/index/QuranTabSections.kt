package app.awn.quran.index

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import app.awn.quran.R
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography

// Names the animation in the layout inspector; it is never shown to the user.
private const val SNIPPET_FADE_LABEL = "snippet"

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
        label = SNIPPET_FADE_LABEL,
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
