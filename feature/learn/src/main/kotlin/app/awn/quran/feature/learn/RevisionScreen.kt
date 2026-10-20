package app.awn.quran.feature.learn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.awn.quran.core.ui.components.AwnButton
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.learn.fsrs.FsrsRating

/**
 * Cards due today, one ayah at a time. The user recites from memory, shows the text to check, then
 * rates the recital. Until the recite feature can judge a recital, the rating is the user's own:
 * Again or Good.
 */
@Composable
fun RevisionScreen(
    state: RevisionUiState,
    onShowTextClick: () -> Unit,
    onRate: (FsrsRating) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AwnTheme(field = LearnField) {
        Column(modifier.fillMaxSize()) {
            LearnBar(
                title = stringResource(R.string.revision_title),
                startLinkLabel = stringResource(R.string.back),
                onStartLinkClick = onBackClick,
            )
            val card = state.current
            if (card == null) {
                EmptyRevision()
            } else {
                RevisionCardContent(card, state, onShowTextClick, onRate)
            }
        }
    }
}

@Composable
private fun EmptyRevision() {
    Text(
        text = stringResource(R.string.revision_empty),
        style = AwnTypography.title,
        modifier = Modifier.padding(AwnSpacing.screenMargin),
    )
}

@Composable
private fun ColumnScope.RevisionCardContent(
    card: RevisionCard,
    state: RevisionUiState,
    onShowTextClick: () -> Unit,
    onRate: (FsrsRating) -> Unit,
) {
    Column(
        modifier =
            Modifier.weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AwnSpacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(AwnSpacing.medium),
    ) {
        Text(
            text =
                stringResource(
                    R.string.revision_position,
                    formatCount(state.total - state.remaining + 1),
                    formatCount(state.total),
                ),
            style = AwnTypography.small,
        )
        Text(text = card.label, style = AwnTypography.title)
        val text = card.text
        if (state.isTextShown && text != null) {
            ArabicText(text = text)
        } else {
            Text(text = stringResource(R.string.revision_prompt), style = AwnTypography.body)
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(AwnSpacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
    ) {
        if (state.isTextShown || card.text == null) {
            AwnButton(
                label = stringResource(R.string.revision_good),
                onClick = { onRate(FsrsRating.Good) },
                modifier = Modifier.fillMaxWidth(),
            )
            TextLink(
                label = stringResource(R.string.revision_again),
                onClick = { onRate(FsrsRating.Again) },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            AwnButton(
                label = stringResource(R.string.revision_show),
                onClick = onShowTextClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
