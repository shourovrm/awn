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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.read.R
import app.awn.quran.feature.read.ReadIconButton
import app.awn.quran.feature.read.settings.PageView
import app.awn.quran.feature.read.settings.TextScale

private const val PERCENT = 100
private const val UNANCHORED_TOP_SHARE = 0.3f

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

/**
 * The bar at the top of the page: index, a two-line title, the view button, day and night, text
 * size and the overflow menu. It floats over the page and never changes its size.
 */
@Suppress("LongMethod") // One row of buttons.
@Composable
internal fun QuranTopBar(
    state: QuranUiState,
    surahName: String,
    isNight: Boolean,
    actions: TopBarActions,
    modifier: Modifier = Modifier,
) {
    val field = Awn.field
    Row(
        modifier = modifier.fillMaxWidth().background(field.buttonFill),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReadIconButton(
            iconRes = R.drawable.ic_read_index,
            description = stringResource(R.string.bar_index),
            onClick = actions.onOpenIndex,
            tint = field.onButton,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = surahName,
                style = AwnTypography.bodyStrong,
                color = field.onButton,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.bar_title_subtitle, state.currentPage, state.juz),
                style = AwnTypography.caption,
                color = field.onButton,
                maxLines = 1,
            )
        }
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

/** What the buttons of the floating bar over a selected ayah do. */
internal class AyahBarActions(
    val onPlay: () -> Unit,
    val onRecite: () -> Unit,
    val onBookmark: () -> Unit,
    val onTranslation: () -> Unit,
    val onCopy: () -> Unit,
    val onShare: () -> Unit,
)

/**
 * The bar of six icon buttons above a selected ayah. [anchor] is the ayah's bounds in the
 * coordinates of the parent; the bar sits above it, or below it when there is no room above, and
 * stays inside the parent. A null anchor puts the bar a third of the way down.
 */
@Suppress("LongMethod") // Placement plus six buttons.
@Composable
internal fun AyahActionBar(
    anchor: Rect?,
    isBookmarked: Boolean,
    actions: AyahBarActions,
    modifier: Modifier = Modifier,
) {
    val field = Awn.field
    Box(
        modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
            val gap = AwnSpacing.small.roundToPx()
            layout(constraints.maxWidth, constraints.maxHeight) {
                val centerX = anchor?.center?.x ?: (constraints.maxWidth / 2f)
                val x =
                    (centerX - placeable.width / 2f)
                        .toInt()
                        .coerceIn(0, (constraints.maxWidth - placeable.width).coerceAtLeast(0))
                val above = anchor?.let { it.top - placeable.height - gap }
                val y =
                    when {
                        anchor == null -> (constraints.maxHeight * UNANCHORED_TOP_SHARE).toInt()
                        above != null && above >= 0f -> above.toInt()
                        else -> (anchor.bottom + gap).toInt()
                    }
                placeable.place(IntOffset(x, y))
            }
        },
    ) {
        Row(
            modifier =
                Modifier.shadow(AwnSpacing.small, AwnShapes.control)
                    .background(field.buttonFill, AwnShapes.control),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReadIconButton(
                R.drawable.ic_read_play,
                stringResource(R.string.ayah_play),
                actions.onPlay,
                tint = field.onButton,
            )
            ReadIconButton(
                R.drawable.ic_read_recite,
                stringResource(R.string.ayah_recite),
                actions.onRecite,
                tint = field.onButton,
            )
            ReadIconButton(
                iconRes =
                    if (isBookmarked) R.drawable.ic_read_bookmark_filled
                    else R.drawable.ic_read_bookmark,
                description =
                    stringResource(
                        if (isBookmarked) R.string.ayah_remove_bookmark else R.string.ayah_bookmark,
                    ),
                onClick = actions.onBookmark,
                tint = field.onButton,
            )
            ReadIconButton(
                R.drawable.ic_read_translate,
                stringResource(R.string.ayah_translation),
                actions.onTranslation,
                tint = field.onButton,
            )
            ReadIconButton(
                R.drawable.ic_read_copy,
                stringResource(R.string.ayah_copy),
                actions.onCopy,
                tint = field.onButton,
            )
            ReadIconButton(
                R.drawable.ic_read_share,
                stringResource(R.string.ayah_share),
                actions.onShare,
                tint = field.onButton,
            )
        }
    }
}
