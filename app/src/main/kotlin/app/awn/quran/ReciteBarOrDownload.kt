package app.awn.quran

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.awn.quran.core.data.models.SpeechModelState
import app.awn.quran.feature.read.quran.SpeechModelCard
import app.awn.quran.feature.read.quran.SpeechModelCardActions
import app.awn.quran.feature.recite.ReciteBar
import app.awn.quran.notice.NoticeCard
import app.awn.quran.services.AwnServices
import kotlinx.coroutines.launch

/** What the first recite shows, in order: the notice, the one-time download, then the bar. */
internal enum class ReciteEntryStep {
    Notice,
    Download,
    Bar,
}

/** The notice comes first, so nobody downloads or recites before reading it. */
internal fun reciteEntryStep(noticeAccepted: Boolean, isModelInstalled: Boolean): ReciteEntryStep =
    when {
        !noticeAccepted -> ReciteEntryStep.Notice
        !isModelInstalled -> ReciteEntryStep.Download
        else -> ReciteEntryStep.Bar
    }

/**
 * The recite bar once the notice is accepted and the speech files are on the phone. Before that the
 * user sees the notice, then the card that offers the one-time download; when the download finishes
 * the bar takes its place and the speech files are prepared again, because the first attempt,
 * started by the recite button, found nothing to load.
 */
@Composable
internal fun ReciteBarOrDownload(
    services: AwnServices,
    onExit: () -> Unit,
    // The Hifz practice bar passes itself here: it needs the same speech files and the same card.
    bar: @Composable () -> Unit = { ReciteBar(services.recite, onExit) },
) {
    val modelState by services.speechModel.state.collectAsStateWithLifecycle()
    // null until DataStore answers, so the notice does not flash for users who accepted it.
    val noticeAccepted by
        services.preferences.noticeAccepted.collectAsStateWithLifecycle(initialValue = null)
    val coroutineScope = rememberCoroutineScope()
    val isInstalled = modelState is SpeechModelState.Installed
    LaunchedEffect(isInstalled) {
        if (isInstalled) services.speechPreparation.prepare()
    }
    val accepted = noticeAccepted ?: return
    when (reciteEntryStep(accepted, isInstalled)) {
        ReciteEntryStep.Notice ->
            NoticeCard(onAccept = { coroutineScope.launch { services.preferences.acceptNotice() } })
        ReciteEntryStep.Download -> ReciteDownloadOffer(services, modelState, onExit)
        ReciteEntryStep.Bar -> bar()
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
