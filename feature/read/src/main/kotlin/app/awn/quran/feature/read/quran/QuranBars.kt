@file:Suppress("MatchingDeclarationName") // TopBarActions is the contract; the bar follows it.

package app.awn.quran.feature.read.quran

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import app.awn.quran.core.data.TranslationId
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.read.R
import app.awn.quran.feature.read.ReadIconButton
import app.awn.quran.feature.read.settings.PageView
import app.awn.quran.feature.read.settings.TextLayer

/** What the top bar's buttons do. Each one is a single user action. */
@Suppress("LongParameterList") // One callback per button and menu item.
internal class TopBarActions(
    val onOpenIndex: () -> Unit,
    val onSwitchView: () -> Unit,
    val onOpenLayers: () -> Unit,
    val onToggleLayer: (TextLayer) -> Unit,
    val onToggleTranslation: (TranslationId) -> Unit,
    val onToggleNight: () -> Unit,
    val onOpenTextSize: () -> Unit,
    val onOpenOverflow: () -> Unit,
    val onDismissPopup: () -> Unit,
    val onTextSizeChange: (TextLayer, Int) -> Unit,
    val onMushafSizeChange: (Int) -> Unit,
    val onSearch: () -> Unit,
    val onGoToPage: () -> Unit,
    val onBookmarkPage: () -> Unit,
    val onRotate: () -> Unit,
    val onLearn: () -> Unit,
    val onSettings: () -> Unit,
    /** Opens the words of the page in the Learn tab; null hides the menu item. */
    val onWordsOnPage: (() -> Unit)? = null,
    /** Turns "Show my words" on or off; null hides the menu item. */
    val onToggleMyWords: (() -> Unit)? = null,
)

/** Above this font scale the title and the buttons no longer share a row. */
internal const val LARGE_TEXT_SCALE = 1.3f

/**
 * The bar at the top of the page: index, a two-line title, the view button, the layers button (in
 * the line-by-line view), day and night, text size, settings and the overflow menu. It floats over
 * the page and never changes its width. At a large system font scale the buttons move to a second
 * row under the title, so the title has the whole width and nothing is cut off; the bar then grows
 * taller.
 */
@Composable
internal fun QuranTopBar(
    state: QuranUiState,
    surahName: String,
    isNight: Boolean,
    actions: TopBarActions,
    modifier: Modifier = Modifier,
) {
    val isLargeText = LocalDensity.current.fontScale > LARGE_TEXT_SCALE
    val buttonCount =
        if (state.settings.pageView == PageView.LineByLine) LINE_VIEW_BUTTONS else MUSHAF_BUTTONS
    // The bar's own width decides, so a split screen or a rotation is handled as well.
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val settingsInMenu = !isLargeText && shouldMoveSettingsToOverflow(maxWidth, buttonCount)
        TopBarContent(state, surahName, isNight, actions, isLargeText, settingsInMenu)
    }
}

@Composable
private fun TopBarContent(
    state: QuranUiState,
    surahName: String,
    isNight: Boolean,
    actions: TopBarActions,
    isLargeText: Boolean,
    settingsInMenu: Boolean,
) {
    val field = Awn.field
    if (isLargeText) {
        Column(Modifier.fillMaxWidth().background(field.barBackground)) {
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
                TopBarButtons(state, isNight, actions, settingsInMenu = false)
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().background(field.barBackground),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IndexButton(actions)
            TopBarTitle(state, surahName, maxLines = 1, Modifier.weight(1f))
            TopBarButtons(state, isNight, actions, settingsInMenu)
        }
    }
}

private const val LARGE_TEXT_TITLE_LINES = 2

/** Buttons right of the title, settings included: the line-by-line view adds the layers one. */
private const val MUSHAF_BUTTONS = 5
private const val LINE_VIEW_BUTTONS = 6

@Composable
private fun IndexButton(actions: TopBarActions) {
    ReadIconButton(
        iconRes = R.drawable.ic_read_index,
        description = stringResource(R.string.bar_index),
        onClick = actions.onOpenIndex,
        tint = Awn.field.onBar,
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
            color = field.onBar,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(R.string.bar_title_subtitle, state.currentPage, state.juz),
            style = AwnTypography.caption,
            color = field.onBar,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TopBarButtons(
    state: QuranUiState,
    isNight: Boolean,
    actions: TopBarActions,
    settingsInMenu: Boolean,
) {
    val field = Awn.field
    val view = state.settings.pageView
    ReadIconButton(
        iconRes = viewIcon(view.other()),
        description = stringResource(viewDescription(view.other())),
        onClick = actions.onSwitchView,
        tint = field.onBar,
    )
    if (view == PageView.LineByLine) {
        MenuButton(R.drawable.ic_read_layers, R.string.bar_layers, actions.onOpenLayers) {
            DropdownMenu(
                expanded = state.popup == Popup.Layers,
                onDismissRequest = actions.onDismissPopup,
            ) {
                LayerTicks(state, actions)
            }
        }
    }
    ReadIconButton(
        iconRes = if (isNight) R.drawable.ic_read_light else R.drawable.ic_read_dark,
        description =
            stringResource(if (isNight) R.string.bar_day_mode else R.string.bar_night_mode),
        onClick = actions.onToggleNight,
        tint = field.onBar,
    )
    MenuButton(R.drawable.ic_read_text_size, R.string.bar_text_size, actions.onOpenTextSize) {
        DropdownMenu(
            expanded = state.popup == Popup.TextSize,
            onDismissRequest = actions.onDismissPopup,
        ) {
            TextSizeRows(state, actions)
        }
    }
    if (!settingsInMenu) {
        ReadIconButton(
            iconRes = R.drawable.ic_read_settings,
            description = stringResource(R.string.menu_settings),
            onClick = actions.onSettings,
            tint = field.onBar,
        )
    }
    MenuButton(R.drawable.ic_read_more, R.string.bar_more, actions.onOpenOverflow) {
        DropdownMenu(
            expanded = state.popup == Popup.Overflow,
            onDismissRequest = actions.onDismissPopup,
        ) {
            OverflowItems(state, actions, includesSettings = settingsInMenu)
        }
    }
}

/** A top-bar icon button with the menu it opens, anchored under it. */
@Composable
private fun MenuButton(
    @DrawableRes iconRes: Int,
    @StringRes descriptionRes: Int,
    onClick: () -> Unit,
    menu: @Composable () -> Unit,
) {
    Box {
        ReadIconButton(
            iconRes = iconRes,
            description = stringResource(descriptionRes),
            onClick = onClick,
            tint = Awn.field.onBar,
        )
        menu()
    }
}

private fun viewIcon(view: PageView): Int =
    when (view) {
        PageView.Mushaf -> R.drawable.ic_read_view_mushaf
        PageView.LineByLine -> R.drawable.ic_read_translate
    }

/** The button describes the view it switches to, not the one that is showing. */
private fun viewDescription(view: PageView): Int =
    when (view) {
        PageView.Mushaf -> R.string.bar_view_mushaf
        PageView.LineByLine -> R.string.bar_view_line_by_line
    }
