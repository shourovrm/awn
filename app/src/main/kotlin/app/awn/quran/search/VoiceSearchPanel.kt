package app.awn.quran.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.awn.quran.R
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.models.SpeechModelState
import app.awn.quran.core.ui.components.AwnButton
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.read.quran.SpeechModelCard
import app.awn.quran.feature.read.quran.SpeechModelCardActions
import app.awn.quran.feature.recite.MicrophoneAccess
import app.awn.quran.feature.recite.R as ReciteR
import app.awn.quran.feature.recite.rememberMicrophonePermission

/** What the voice panel can ask of the screen. */
class VoiceSearchActions(
    val onStart: () -> Unit,
    val onStop: () -> Unit,
    val onClose: () -> Unit,
    val onDownloadModel: () -> Unit,
    val onCancelDownload: () -> Unit,
)

/** The speech files' download, which voice search needs before it can listen. */
class SpeechModelOffer(val state: SpeechModelState, val totalBytes: Long)

/**
 * Takes the place of the typed results while voice search is open: first the speech files and the
 * microphone permission, then a status line with the matching ayahs below it.
 */
@Composable
fun VoiceSearchPanel(
    state: VoiceSearchUiState,
    speechModel: SpeechModelOffer,
    actions: VoiceSearchActions,
    onOpen: (page: Int, ayah: AyahRef?) -> Unit,
) {
    if (state.phase == VoicePhase.Asking) {
        VoiceSearchSetup(speechModel, actions)
        return
    }
    VoiceResultList(state, actions, onOpen)
}

/**
 * The status row, then the matching ayahs one page at a time. A new result list starts again at the
 * first page and at the top, so a stale scroll position never hides the new best matches.
 */
@Composable
private fun VoiceResultList(
    state: VoiceSearchUiState,
    actions: VoiceSearchActions,
    onOpen: (page: Int, ayah: AyahRef?) -> Unit,
) {
    val listState = rememberLazyListState()
    val pageCount = remember(state.rows) { mutableIntStateOf(1) }
    LaunchedEffect(state.rows) { listState.scrollToItem(0) }
    val visibleRows = visibleVoiceRows(state.rows, pageCount.intValue)
    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        item { VoiceStatusRow(state, actions) }
        if (state.rows.size > VOICE_RESULT_PAGE_SIZE) {
            item { VoiceMessage(text = stringResource(R.string.voice_search_many_matches)) }
        }
        items(visibleRows.size) { index ->
            val row = visibleRows[index]
            ResultRow(row) { onOpen(row.page, row.hit.ayah) }
        }
        if (visibleRows.size < state.rows.size) {
            // A new key per shown count gives each page a fresh sentinel that loads the next page.
            item(key = "more-${visibleRows.size}") {
                LaunchedEffect(Unit) { pageCount.intValue++ }
            }
        }
    }
}

@Composable
private fun VoiceSearchSetup(speechModel: SpeechModelOffer, actions: VoiceSearchActions) {
    if (speechModel.state !is SpeechModelState.Installed) {
        SpeechModelCard(
            state = speechModel.state,
            totalBytes = speechModel.totalBytes,
            actions =
                SpeechModelCardActions(
                    onDownload = actions.onDownloadModel,
                    onNotNow = actions.onClose,
                    onCancel = actions.onCancelDownload,
                ),
        )
        return
    }
    val permission = rememberMicrophonePermission()
    when (permission.access) {
        MicrophoneAccess.Granted -> LaunchedEffect(Unit) { actions.onStart() }
        MicrophoneAccess.CanAsk ->
            VoiceMessage(
                text = stringResource(ReciteR.string.recite_status_allow_microphone),
                buttonLabel = stringResource(ReciteR.string.recite_microphone_allow),
                onButton = permission::requestAccess,
            )
        MicrophoneAccess.DeniedPermanently ->
            VoiceMessage(
                text = stringResource(ReciteR.string.recite_status_microphone_off),
                buttonLabel = stringResource(ReciteR.string.recite_microphone_open_settings),
                onButton = permission::openAppSettings,
            )
    }
}

@Composable
private fun VoiceStatusRow(state: VoiceSearchUiState, actions: VoiceSearchActions) {
    when (state.phase) {
        VoicePhase.GettingReady ->
            VoiceMessage(text = stringResource(R.string.search_voice_getting_ready))
        VoicePhase.Listening ->
            VoiceMessage(
                text = listeningText(state),
                buttonLabel = stringResource(R.string.search_voice_stop),
                onButton = actions.onStop,
            )
        VoicePhase.Finished ->
            VoiceMessage(
                text = finishedText(state),
                buttonLabel = stringResource(R.string.search_voice_again),
                onButton = actions.onStart,
            )
        VoicePhase.Failed ->
            VoiceMessage(
                text = stringResource(ReciteR.string.recite_status_no_microphone),
                buttonLabel = stringResource(R.string.search_voice_again),
                onButton = actions.onStart,
            )
        VoicePhase.Off,
        VoicePhase.Asking -> Unit
    }
}

@Composable
private fun listeningText(state: VoiceSearchUiState): String =
    if (state.heardTooLittle) {
        stringResource(R.string.voice_search_too_short)
    } else {
        stringResource(R.string.search_voice_listening)
    }

@Composable
private fun finishedText(state: VoiceSearchUiState): String? =
    when {
        state.heardTooLittle -> stringResource(R.string.voice_search_too_short)
        state.rows.isEmpty() -> stringResource(R.string.search_voice_no_match)
        else -> null
    }

@Composable
private fun VoiceMessage(
    text: String?,
    buttonLabel: String? = null,
    onButton: () -> Unit = {},
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = AwnSpacing.screenMargin, vertical = AwnSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AwnSpacing.medium),
    ) {
        Text(
            text = text.orEmpty(),
            style = AwnTypography.body,
            color = Awn.field.content,
            modifier = Modifier.weight(1f),
        )
        if (buttonLabel != null) AwnButton(label = buttonLabel, onClick = onButton)
    }
}
