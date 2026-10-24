package app.awn.quran.feature.learn

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnField
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.LocalColourMode
import app.awn.quran.core.ui.theme.awnFieldColors
import app.awn.quran.core.ui.theme.softFill

/** Opens Quran page [page] when pressed; the Revise and Progress screens use it as a way out. */
internal class OpenPageAction(val page: Int, val onClick: () -> Unit)

/** The paper page of the Revise and Reading progress screens: a back bar over a scrolling body. */
@Composable
internal fun LearnPage(
    title: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        LearnBar(
            title = title,
            startLinkLabel = stringResource(R.string.back),
            onStartLinkClick = onBackClick,
        )
        Column(
            modifier =
                Modifier.weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AwnSpacing.screenMargin)
                    .padding(bottom = AwnSpacing.large),
            verticalArrangement = Arrangement.spacedBy(AwnSpacing.medium),
            content = content,
        )
    }
}

/** A green card with one big figure and its caption; [extra] draws below the caption. */
@Composable
internal fun LearnHeroCard(
    figure: Int,
    caption: String,
    modifier: Modifier = Modifier,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    // The green field is a raised surface in every colour mode, as it is for the figure tiles.
    val colors = awnFieldColors(AwnField.Green, LocalColourMode.current)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(AwnShapes.control)
                .background(colors.background)
                .padding(AwnSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
    ) {
        Text(text = formatCount(figure), style = AwnTypography.poster, color = colors.content)
        Text(text = caption, style = AwnTypography.body, color = colors.content)
        extra()
    }
}

@Composable
internal fun LearnSectionHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = AwnTypography.bodyStrong,
        color = Awn.field.buttonFill,
        modifier = modifier.padding(top = AwnSpacing.small),
    )
}

/** A quiet raised block on the page. */
@Composable
internal fun LearnCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(AwnShapes.control)
                .background(Awn.field.softFill)
                .padding(horizontal = AwnSpacing.medium, vertical = AwnSpacing.small),
        content = content,
    )
}
