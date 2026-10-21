package app.awn.quran.feature.read.quran

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.read.R
import app.awn.quran.feature.read.page.SelectionBounds
import app.awn.quran.feature.read.settings.ScreenOrientation
import app.awn.quran.feature.read.settings.requestedOrientation

/**
 * Turns the activity to [quranOrientation] while the Quran screen is shown and back to
 * [appOrientation] when the screen leaves, so the Quran pages and the rest of the app can differ.
 */
@Composable
internal fun QuranOrientation(
    quranOrientation: ScreenOrientation,
    appOrientation: ScreenOrientation,
) {
    val activity = LocalActivity.current
    val latestAppOrientation by rememberUpdatedState(appOrientation)
    LaunchedEffect(activity, quranOrientation) {
        activity?.requestedOrientation = quranOrientation.requestedOrientation()
    }
    DisposableEffect(activity) {
        onDispose {
            // A turn of the screen recreates the activity; restoring the app default then would
            // turn it back and forth, so only a real exit from the screen restores it.
            if (activity?.isChangingConfigurations == false) {
                activity.requestedOrientation = latestAppOrientation.requestedOrientation()
            }
        }
    }
}

/** The top bar's buttons wired to the view model and the host. */
@Composable
internal fun topBarActions(viewModel: QuranViewModel, slots: QuranScreenSlots): TopBarActions {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    return TopBarActions(
        onOpenIndex = slots.onOpenIndex,
        onSwitchView = viewModel::switchView,
        onOpenLayers = { viewModel.openPopup(Popup.Layers) },
        onToggleLayer = viewModel::toggleLayer,
        onToggleTranslation = viewModel::toggleTranslation,
        onToggleNight = slots.onToggleNight,
        onOpenTextSize = { viewModel.openPopup(Popup.TextSize) },
        onOpenOverflow = { viewModel.openPopup(Popup.Overflow) },
        onDismissPopup = viewModel::closePopup,
        onTextSizeChange = viewModel::changeTextSize,
        onMushafSizeChange = viewModel::changeMushafSize,
        onSearch = {
            viewModel.closePopup()
            slots.onOpenSearch()
        },
        onGoToPage = { viewModel.openPopup(Popup.GoToPage) },
        onBookmarkPage = {
            viewModel.closePopup()
            viewModel.toggleBookmarkOfPage()
        },
        onRotate = {
            viewModel.closePopup()
            viewModel.rotate(isLandscapeNow = isLandscape)
        },
        onLearn = {
            viewModel.closePopup()
            slots.onOpenLearn()
        },
        onSettings = slots.onOpenSettings,
    )
}

/**
 * A small card with the tapped word, its meaning and its transliteration. It sits above the word
 * ([anchor], in the coordinates of the screen), below it when there is no room above or when the
 * word is under the top bar ([topClearancePx]), and at the bottom when the word's place is unknown.
 * It takes the colours of the bars, which are readable in every mode.
 */
@Composable
internal fun WordPopupCard(
    popup: WordPopup,
    anchor: Rect?,
    topClearancePx: Float,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val field = Awn.field
    Box(
        modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
            val gap = AwnSpacing.small.roundToPx()
            val screenSize = IntSize(constraints.maxWidth, constraints.maxHeight)
            layout(screenSize.width, screenSize.height) {
                val position =
                    if (anchor == null) {
                        val dockClearance = (AwnSpacing.dockHeight + AwnSpacing.medium).roundToPx()
                        IntOffset(
                            (screenSize.width - placeable.width) / 2,
                            screenSize.height - placeable.height - dockClearance,
                        )
                    } else {
                        placeSelectionBar(
                            bounds = SelectionBounds(firstLine = anchor, all = anchor),
                            barSize = IntSize(placeable.width, placeable.height),
                            screenSize = screenSize,
                            gapPx = gap,
                            flipBelowTopPx = topClearancePx + placeable.height + gap,
                        )
                    }
                placeable.place(position)
            }
        },
    ) {
        Column(
            Modifier.shadow(AwnSpacing.small, AwnShapes.control)
                .background(field.barBackground, AwnShapes.control)
                .clickable(
                    onClickLabel = stringResource(R.string.panel_close),
                    role = Role.Button,
                    onClick = onDismiss,
                )
                .padding(AwnSpacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
        ) {
            Text(text = popup.word.arabic, style = AwnTypography.lead, color = field.onBar)
            popup.word.meaning?.let {
                Text(text = it, style = AwnTypography.body.ltr(), color = field.onBar)
            }
            popup.word.transliteration?.let {
                Text(text = it, style = AwnTypography.small.ltr(), color = field.onBar)
            }
        }
    }
}

/** Latin and Bangla glosses read left to right even inside right-to-left Arabic surroundings. */
internal fun TextStyle.ltr(): TextStyle = copy(textDirection = TextDirection.Ltr)

/** The space under the status bar and, while the bars show, the top bar. */
@Composable
internal fun topBarClearancePx(areBarsVisible: Boolean): Float {
    val density = LocalDensity.current
    val statusBarPx = WindowInsets.statusBars.getTop(density)
    val topBarPx = if (areBarsVisible) with(density) { AwnSpacing.topBarHeight.toPx() } else 0f
    return statusBarPx + topBarPx
}

internal fun copyToClipboard(context: Context, ayah: AyahRef, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText(ayah.toString(), text))
}

internal fun shareText(context: Context, text: String) {
    val send =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
    val chooser =
        Intent.createChooser(send, context.getString(R.string.ayah_share))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}
