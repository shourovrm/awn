package app.awn.quran.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import app.awn.quran.R
import app.awn.quran.core.ui.components.AwnButton
import app.awn.quran.core.ui.theme.AwnField
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.awnFieldColors
import app.awn.quran.feature.learn.content.LearnLanguage
import app.awn.quran.feature.learn.content.Snippet
import java.text.NumberFormat

private const val SNIPPET_ARABIC_LINES = 2
private const val SNIPPET_RENDERING_LINES = 2

/** The actions Home can start; the bands are the navigation, there is no tab bar. */
class HomeActions(
    val onSettings: () -> Unit,
    val onRead: () -> Unit,
    val onListen: () -> Unit,
    val onRecite: () -> Unit,
    val onLearn: () -> Unit,
    val onContinueReading: () -> Unit,
)

/**
 * Wordmark and Settings, one poster band per mode with its live detail, two figure tiles, the day's
 * snippet and "Continue reading" as the one primary action.
 */
@Composable
fun HomeScreen(state: HomeState, actions: HomeActions) {
    AwnTheme(field = AwnField.Paper) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            HomeBar(onSettingsClick = actions.onSettings)
            ModeBand(AwnField.Paper, R.string.mode_read, state.lastPageDetail(), actions.onRead)
            ModeBand(AwnField.Green, R.string.mode_listen, state.reciterName, actions.onListen)
            ModeBand(AwnField.Night, R.string.mode_recite, state.hifzDetail(), actions.onRecite)
            ModeBand(AwnField.Ochre, R.string.mode_learn, state.dueDetail(), actions.onLearn)
            Row(Modifier.fillMaxWidth()) {
                FigureTile(
                    field = AwnField.Green,
                    figure = state.streakDays,
                    caption = stringResource(R.string.home_streak_caption),
                    modifier = Modifier.weight(1f),
                )
                FigureTile(
                    field = AwnField.Night,
                    figure = state.pagesThisWeek,
                    caption = stringResource(R.string.home_pages_caption),
                    modifier = Modifier.weight(1f),
                )
            }
            state.snippet?.let { SnippetBlock(it) }
            AwnButton(
                label = stringResource(R.string.home_continue),
                onClick = actions.onContinueReading,
                modifier = Modifier.fillMaxWidth().padding(AwnSpacing.screenMargin),
            )
        }
    }
}

@Composable
private fun HomeBar(onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AwnSpacing.screenMargin),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = stringResource(R.string.app_name), style = AwnTypography.lead)
        Box(
            modifier =
                Modifier.heightIn(min = AwnSpacing.touchTarget)
                    .clickable(role = Role.Button, onClick = onSettingsClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = stringResource(R.string.home_settings), style = AwnTypography.body)
        }
    }
}

@Composable
private fun HomeState.lastPageDetail(): String? = lastPage?.let {
    stringResource(R.string.home_detail_page, it)
}

@Composable
private fun HomeState.hifzDetail(): String? =
    if (isHifzOn) stringResource(R.string.home_detail_hifz) else null

@Composable
private fun HomeState.dueDetail(): String? =
    if (ayahsDue > 0) pluralStringResource(R.plurals.home_detail_due, ayahsDue, ayahsDue) else null

@Composable
private fun ModeBand(field: AwnField, nameResource: Int, detail: String?, onClick: () -> Unit) {
    val colors = awnFieldColors(field)
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = AwnSpacing.posterBandHeight)
                .background(colors.background)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = AwnSpacing.screenMargin),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(nameResource),
            style = AwnTypography.title,
            color = colors.content,
        )
        if (detail != null) {
            Text(
                text = detail,
                style = AwnTypography.small,
                color = colors.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).padding(start = AwnSpacing.medium),
            )
        }
    }
}

/** One big number on a field of its own, with a caption: the poster size from the design system. */
@Composable
private fun FigureTile(field: AwnField, figure: Int, caption: String, modifier: Modifier) {
    val colors = awnFieldColors(field)
    Column(modifier.background(colors.background).padding(AwnSpacing.medium)) {
        Text(
            text = NumberFormat.getIntegerInstance().format(figure),
            style = AwnTypography.poster,
            color = colors.content,
        )
        Text(text = caption, style = AwnTypography.body, color = colors.content)
    }
}

@Composable
private fun SnippetBlock(snippet: Snippet) {
    val language = LearnLanguage.of(LocalConfiguration.current.locales[0])
    Column(
        modifier = Modifier.fillMaxWidth().padding(AwnSpacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
    ) {
        Text(
            text = snippet.arabic,
            style = AwnTypography.quran.copy(textDirection = TextDirection.Rtl),
            maxLines = SNIPPET_ARABIC_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = snippet.rendering(language),
            style = AwnTypography.body,
            maxLines = SNIPPET_RENDERING_LINES,
            overflow = TextOverflow.Ellipsis,
        )
        Text(text = snippet.reference, style = AwnTypography.small)
    }
}
