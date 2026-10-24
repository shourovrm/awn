package app.awn.quran.feature.read.page

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.ui.components.AwnButton
import app.awn.quran.core.ui.components.AwnConfirmDialog
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.mutedContent
import app.awn.quran.core.ui.theme.softFill
import app.awn.quran.feature.read.R
import app.awn.quran.feature.read.ReadDependencies
import app.awn.quran.feature.read.quran.megabytesText
import app.awn.quran.feature.read.settings.failureMessage
import app.awn.quran.feature.read.settings.sizeText
import kotlinx.coroutines.flow.first

private const val PERCENT = 100
private const val DISABLED_ALPHA = 0.4f

/**
 * The Prints screen of Settings, Mushaf: one print per screen in a pager, with the print's name,
 * page dots and the buttons that fit its state (see [printControlsOf]). It opens on the print in
 * use. Downloads, deletion and the choice of print go through [MushafSettingsViewModel], the same
 * code that the first-launch card and the Downloads list use.
 */
@Composable
fun PrintsPage(dependencies: ReadDependencies, modifier: Modifier = Modifier) {
    val viewModel: MushafSettingsViewModel =
        viewModel(
            factory = viewModelFactory { initializer { MushafSettingsViewModel(dependencies) } },
        )
    // The pager must open on the stored print, not on the default the view model starts with.
    val scriptInUseAtOpening by
        produceState<ScriptId?>(initialValue = null, dependencies) {
            value = dependencies.settingsStore.settings.first().script
        }
    val openingScript = scriptInUseAtOpening ?: return
    val pagerState =
        rememberPagerState(
            initialPage = pageTypeScripts.indexOf(openingScript).coerceAtLeast(0),
            pageCount = { pageTypeScripts.size },
        )
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val statuses by viewModel.statuses.collectAsStateWithLifecycle()

    Column(modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { pageIndex ->
            PrintPreview(pageTypeScripts[pageIndex])
        }
        PageDots(pagerState)
        val script = pageTypeScripts[pagerState.currentPage]
        val status = statuses.getValue(script)
        PrintButtons(
            script = script,
            status = status,
            controls = printControlsOf(status, isInUse = script == settings.script),
            viewModel = viewModel,
        )
    }
}

@Composable
private fun PrintButtons(
    script: ScriptId,
    status: FontPackStatus,
    controls: PrintControls,
    viewModel: MushafSettingsViewModel,
) {
    var isDeleteAsked by remember(script) { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = AwnSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
    ) {
        when (controls) {
            is PrintControls.Download -> DownloadButton(script, status, controls, viewModel)
            is PrintControls.Downloading -> DownloadingControls(script, controls, viewModel)
            PrintControls.DeleteOrUse ->
                DeleteAndUse(
                    onDelete = { isDeleteAsked = true },
                    onUse = { viewModel.use(script) },
                )
            PrintControls.InUse -> InUseControls()
        }
    }
    if (isDeleteAsked) {
        DeleteDialog(script, status, onDismiss = { isDeleteAsked = false }) {
            isDeleteAsked = false
            viewModel.remove(script)
        }
    }
}

@Composable
private fun DownloadButton(
    script: ScriptId,
    status: FontPackStatus,
    controls: PrintControls.Download,
    viewModel: MushafSettingsViewModel,
) {
    status.failure?.let { failure ->
        Text(
            text = failureMessage(failure),
            style = AwnTypography.small,
            color = Awn.field.wrongWord,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    val label =
        if (controls.downloadBytes == null) {
            stringResource(R.string.print_offer_download)
        } else {
            stringResource(R.string.print_download_sized, megabytesText(controls.downloadBytes))
        }
    AwnButton(
        label = label,
        onClick = { viewModel.install(script) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DownloadingControls(
    script: ScriptId,
    controls: PrintControls.Downloading,
    viewModel: MushafSettingsViewModel,
) {
    val field = Awn.field
    LinearProgressIndicator(
        progress = { controls.progress },
        modifier = Modifier.fillMaxWidth(),
        color = field.buttonFill,
        trackColor = field.softFill,
    )
    Text(
        text = stringResource(R.string.catalogue_status_downloading, percentOf(controls)),
        style = AwnTypography.small,
        color = field.mutedContent,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    QuietButton(
        label = stringResource(R.string.catalogue_cancel),
        onClick = { viewModel.cancel(script) },
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun percentOf(controls: PrintControls.Downloading): Int =
    (controls.progress * PERCENT).toInt()

@Composable
private fun DeleteAndUse(onDelete: () -> Unit, onUse: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
        QuietButton(
            label = stringResource(R.string.print_delete),
            onClick = onDelete,
            modifier = Modifier.weight(1f),
        )
        AwnButton(
            label = stringResource(R.string.print_use),
            onClick = onUse,
            modifier = Modifier.weight(1f),
        )
    }
}

/** The print in use is marked and cannot be deleted, so Delete stays on screen but disabled. */
@Composable
private fun InUseControls() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QuietButton(
            label = stringResource(R.string.print_delete),
            onClick = {},
            modifier = Modifier.weight(1f),
            enabled = false,
        )
        Text(
            text = stringResource(R.string.print_in_use),
            style = AwnTypography.bodyStrong,
            color = Awn.field.mutedContent,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DeleteDialog(
    script: ScriptId,
    status: FontPackStatus,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val context = LocalContext.current
    val freedBytes = status.installedBytes ?: status.downloadBytes ?: 0L
    AwnConfirmDialog(
        title =
            stringResource(R.string.print_delete_title, stringResource(pageTypeText(script).name)),
        message = stringResource(R.string.catalogue_remove_frees, sizeText(context, freedBytes)),
        confirmLabel = stringResource(R.string.print_delete),
        dismissLabel = stringResource(R.string.catalogue_dialog_cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** An outlined button for the action that is not the main one of the screen. */
@Composable
private fun QuietButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val field = Awn.field
    Box(
        modifier =
            modifier
                .defaultMinSize(minHeight = AwnSpacing.buttonHeight)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .clip(AwnShapes.control)
                .border(AwnSpacing.switchKnobRing, field.softFill, AwnShapes.control)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = AwnSpacing.medium),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = AwnTypography.bodyStrong, color = field.content)
    }
}
