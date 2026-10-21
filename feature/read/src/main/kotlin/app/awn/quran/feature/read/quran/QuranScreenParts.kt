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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.read.R
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

/** A small card with the tapped word, its meaning and its transliteration. */
@Composable
internal fun WordPopupCard(popup: WordPopup, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val field = Awn.field
    Column(
        modifier
            .padding(bottom = AwnSpacing.dockHeight + AwnSpacing.medium)
            .shadow(AwnSpacing.small, AwnShapes.control)
            .background(field.buttonFill, AwnShapes.control)
            .clickable(
                onClickLabel = stringResource(R.string.panel_close),
                role = Role.Button,
                onClick = onDismiss,
            )
            .padding(AwnSpacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
    ) {
        Text(text = popup.word.arabic, style = AwnTypography.lead, color = field.onButton)
        popup.word.meaning?.let {
            Text(text = it, style = AwnTypography.body, color = field.onButton)
        }
        popup.word.transliteration?.let {
            Text(text = it, style = AwnTypography.small, color = field.onButton)
        }
    }
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
