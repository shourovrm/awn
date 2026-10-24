package app.awn.quran.feature.read.page

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.mutedContent
import app.awn.quran.core.ui.theme.softFill
import app.awn.quran.feature.read.R

/** The picture of page 50 in [script] with the print's name under it. */
@Composable
internal fun PrintPreview(script: ScriptId) {
    val text = pageTypeText(script)
    val name = stringResource(text.name)
    Column(
        modifier = Modifier.fillMaxSize().padding(top = AwnSpacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (text.preview != null) {
                Image(
                    painter = painterResource(text.preview),
                    contentDescription =
                        stringResource(R.string.mushaf_preview_description, name, PREVIEW_PAGE),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                PreviewPlaceholder(name)
            }
        }
        Text(
            text = name,
            style = AwnTypography.lead,
            color = Awn.field.content,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = AwnSpacing.medium),
        )
    }
}

/** Stands in for a print that has no preview picture: a plain page shape with the print's name. */
@Composable
private fun PreviewPlaceholder(name: String) {
    Box(
        modifier =
            Modifier.fillMaxSize()
                .aspectRatio(PREVIEW_ASPECT_RATIO, matchHeightConstraintsFirst = true)
                .clip(AwnShapes.control)
                .background(Awn.field.softFill),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = name, style = AwnTypography.title, color = Awn.field.mutedContent)
    }
}

@Composable
internal fun PageDots(pagerState: PagerState) {
    val field = Awn.field
    val position =
        stringResource(R.string.print_position, pagerState.currentPage + 1, pagerState.pageCount)
    Row(
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = position },
        horizontalArrangement = Arrangement.spacedBy(DOT_GAP, Alignment.CenterHorizontally),
    ) {
        repeat(pagerState.pageCount) { index ->
            val colour = if (index == pagerState.currentPage) field.buttonFill else field.softFill
            Box(Modifier.size(DOT_SIZE).background(colour, CircleShape))
        }
    }
}

private const val PREVIEW_ASPECT_RATIO = 0.62f
private val DOT_SIZE = AwnSpacing.small
private val DOT_GAP = AwnSpacing.small
