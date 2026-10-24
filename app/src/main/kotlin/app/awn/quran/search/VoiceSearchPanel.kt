package app.awn.quran.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    Column(Modifier.fillMaxWidth()) {
        VoiceStatusRow(state, actions)
        state.rows.forEach { row -> ResultRow(row) { onOpen(row.page, row.hit.ayah) } }
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
                text = stringResource(R.string.search_voice_listening),
                buttonLabel = stringResource(R.string.search_voice_stop),
                onButton = actions.onStop,
            )
        VoicePhase.Finished ->
            VoiceMessage(
                text =
                    if (state.rows.isEmpty()) stringResource(R.string.search_voice_no_match)
                    else null,
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
