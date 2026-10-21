package app.awn.quran

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.awn.quran.core.data.models.SpeechModelState
import app.awn.quran.feature.read.quran.SpeechModelCard
import app.awn.quran.feature.read.quran.SpeechModelCardActions
import app.awn.quran.feature.recite.ReciteBar
import app.awn.quran.services.AwnServices

/**
 * The recite bar once the speech files are on the phone. Before that the user sees the card that
 * offers the one-time download; when the download finishes the bar takes its place and the speech
 * files are prepared again, because the first attempt, started by the recite button, found nothing
 * to load.
 */
@Composable
internal fun ReciteBarOrDownload(services: AwnServices, onExit: () -> Unit) {
    val modelState by services.speechModel.state.collectAsStateWithLifecycle()
    val isInstalled = modelState is SpeechModelState.Installed
    LaunchedEffect(isInstalled) {
        if (isInstalled) services.speechPreparation.prepare()
    }
    if (isInstalled) {
        ReciteBar(services.recite, onExit)
    } else {
        ReciteDownloadOffer(services, modelState, onExit)
    }
}

@Composable
private fun ReciteDownloadOffer(
    services: AwnServices,
    modelState: SpeechModelState,
    onExit: () -> Unit,
) {
    // The recite bar ends the recitation when it leaves the screen; the card does the same when
    // it goes away without a download (Not now, or Back), so the page returns to normal reading.
    DisposableEffect(Unit) {
        onDispose { if (!services.speechModel.isInstalled()) services.recite.exit() }
    }
    SpeechModelCard(
        state = modelState,
        totalBytes = services.speechModel.totalBytes,
        actions =
            SpeechModelCardActions(
                onDownload = { services.speechModel.download() },
                onNotNow = onExit,
                onCancel = services.speechModel::cancel,
            ),
    )
}
