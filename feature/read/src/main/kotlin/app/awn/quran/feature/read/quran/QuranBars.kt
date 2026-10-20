package app.awn.quran.feature.read.quran

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.read.R
import app.awn.quran.feature.read.ReadIconButton
import app.awn.quran.feature.read.settings.PageView
import app.awn.quran.feature.read.settings.TextScale

private const val PERCENT = 100

/** What the top bar's buttons do. Each one is a single user action. */
@Suppress("LongParameterList") // One callback per button and menu item.
internal class TopBarActions(
    val onOpenIndex: () -> Unit,
    val onCycleView: () -> Unit,
    val onToggleNight: () -> Unit,
    val onOpenTextSize: () -> Unit,
    val onOpenOverflow: () -> Unit,
    val onDismissPopup: () -> Unit,
    val onTextSizeChange: (Int) -> Unit,
    val onSearch: () -> Unit,
    val onGoToPage: () -> Unit,
    val onBookmarkPage: () -> Unit,
    val onLearn: () -> Unit,
    val onSettings: () -> Unit,
)

/** Above this font scale the title and the buttons no longer share a row. */
internal const val LARGE_TEXT_SCALE = 1.3f

/**
 * The bar at the top of the page: index, a two-line title, the view button, day and night, text
 * size and the overflow menu. It floats over the page and never changes its width. At a large
 * system font scale the buttons move to a second row under the title, so the title has the whole
 * width and nothing is cut off; the bar then grows taller.
 */
@Composable
internal fun QuranTopBar(
    state: QuranUiState,
    surahName: String,
    isNight: Boolean,
    actions: TopBarActions,
    modifier: Modifier = Modifier,
) {
    val field = Awn.field
    val isLargeText = LocalDensity.current.fontScale > LARGE_TEXT_SCALE
    if (isLargeText) {
        Column(modifier.fillMaxWidth().background(field.buttonFill)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IndexButton(actions)
                TopBarTitle(
                    state,
                    surahName,
                    maxLines = LARGE_TEXT_TITLE_LINES,
                    Modifier.weight(1f),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TopBarButtons(state, isNight, actions)
            }
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth().background(field.buttonFill),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IndexButton(actions)
            TopBarTitle(state, surahName, maxLines = 1, Modifier.weight(1f))
            TopBarButtons(state, isNight, actions)
        }
    }
}

private const val LARGE_TEXT_TITLE_LINES = 2

@Composable
private fun IndexButton(actions: TopBarActions) {
    ReadIconButton(
        iconRes = R.drawable.ic_read_index,
        description = stringResource(R.string.bar_index),
        onClick = actions.onOpenIndex,
        tint = Awn.field.onButton,
    )
}

@Composable
private fun TopBarTitle(
    state: QuranUiState,
    surahName: String,
    maxLines: Int,
    modifier: Modifier = Modifier,
) {
    val field = Awn.field
    Column(modifier) {
        Text(
            text = surahName,
            style = AwnTypography.bodyStrong,
            color = field.onButton,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(R.string.bar_title_subtitle, state.currentPage, state.juz),
            style = AwnTypography.caption,
            color = field.onButton,
            maxLines = maxLines,
        )
    }
}

@Suppress("LongMethod") // One row of buttons.
@Composable
private fun TopBarButtons(state: QuranUiState, isNight: Boolean, actions: TopBarActions) {
    val field = Awn.field
    ReadIconButton(
        iconRes = viewIcon(state.settings.pageView.next()),
        description = stringResource(viewDescription(state.settings.pageView.next())),
        onClick = actions.onCycleView,
        tint = field.onButton,
    )
    ReadIconButton(
        iconRes = if (isNight) R.drawable.ic_read_light else R.drawable.ic_read_dark,
        description =
            stringResource(if (isNight) R.string.bar_day_mode else R.string.bar_night_mode),
        onClick = actions.onToggleNight,
        tint = field.onButton,
    )
    Box {
        ReadIconButton(
            iconRes = R.drawable.ic_read_text_size,
            description = stringResource(R.string.bar_text_size),
            onClick = actions.onOpenTextSize,
            tint = field.onButton,
        )
        DropdownMenu(
            expanded = state.popup == Popup.TextSize,
            onDismissRequest = actions.onDismissPopup,
        ) {
            TextSizeControls(state, actions.onTextSizeChange)
        }
    }
    ReadIconButton(
        iconRes = R.drawable.ic_read_settings,
        description = stringResource(R.string.menu_settings),
        onClick = actions.onSettings,
        tint = field.onButton,
    )
    Box {
        ReadIconButton(
            iconRes = R.drawable.ic_read_more,
            description = stringResource(R.string.bar_more),
            onClick = actions.onOpenOverflow,
            tint = field.onButton,
        )
        DropdownMenu(
            expanded = state.popup == Popup.Overflow,
            onDismissRequest = actions.onDismissPopup,
        ) {
            OverflowItems(state, actions)
        }
    }
}

private fun viewIcon(view: PageView): Int =
    when (view) {
        PageView.Mushaf -> R.drawable.ic_read_view_mushaf
        PageView.Translation -> R.drawable.ic_read_translate
        PageView.WordByWord -> R.drawable.ic_read_word_by_word
    }

/** The button describes the view it switches to, not the one that is showing. */
private fun viewDescription(view: PageView): Int =
    when (view) {
        PageView.Mushaf -> R.string.bar_view_mushaf
        PageView.Translation -> R.string.bar_view_translation
        PageView.WordByWord -> R.string.bar_view_word_by_word
    }

@Composable
private fun TextSizeControls(state: QuranUiState, onChange: (Int) -> Unit) {
    val step = state.settings.arabicSizeStep
    Row(verticalAlignment = Alignment.CenterVertically) {
        ReadIconButton(
            iconRes = R.drawable.ic_read_minus,
            description = stringResource(R.string.action_smaller),
            onClick = { onChange(-1) },
            isEnabled = step > 0,
        )
        Text(
            text =
                stringResource(
                    R.string.size_percent,
                    (state.settings.arabicScale * PERCENT).toInt(),
                ),
            style = AwnTypography.body,
        )
        ReadIconButton(
            iconRes = R.drawable.ic_read_plus,
            description = stringResource(R.string.action_larger),
            onClick = { onChange(1) },
            isEnabled = step < TextScale.STEP_COUNT - 1,
        )
    }
}

@Composable
private fun OverflowItems(state: QuranUiState, actions: TopBarActions) {
    MenuItem(R.string.menu_search, R.drawable.ic_read_search, actions.onSearch)
    MenuItem(R.string.menu_go_to_page, R.drawable.ic_read_go_to_page, actions.onGoToPage)
    MenuItem(
        label =
            if (state.isPageBookmarked) R.string.menu_remove_page_bookmark
            else R.string.menu_bookmark_page,
        icon =
            if (state.isPageBookmarked) R.drawable.ic_read_bookmark_filled
            else R.drawable.ic_read_bookmark,
        onClick = actions.onBookmarkPage,
    )
    MenuItem(R.string.menu_learn, R.drawable.ic_read_learn, actions.onLearn)
}

@Composable
private fun MenuItem(label: Int, icon: Int, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text = stringResource(label), style = AwnTypography.body) },
        leadingIcon = {
            Icon(painter = painterResource(icon), contentDescription = null)
        },
        onClick = onClick,
    )
}
