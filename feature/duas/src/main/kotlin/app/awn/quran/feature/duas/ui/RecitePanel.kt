package app.awn.quran.feature.duas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.components.AwnIconButton
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.softFill
import app.awn.quran.feature.duas.R
import app.awn.quran.feature.duas.data.CLEAN_RECITATIONS_TO_LEARN
import app.awn.quran.feature.recite.MicrophoneAccess
import app.awn.quran.feature.recite.RecitationPhase
import app.awn.quran.feature.recite.SpeechState
import app.awn.quran.feature.recite.rememberMicrophonePermission
import app.awn.quran.tracker.WordState

internal const val RECITE_HIDE_TAG = "dua-recite-hide"
internal const val RECITE_MICROPHONE_TAG = "dua-recite-microphone"

/** True when every word in [duaWords] has been confirmed: a clean recitation of the dua. */
internal fun isCleanRecitation(duaWords: Set<WordKey>, states: Map<WordKey, WordState>): Boolean =
    duaWords.isNotEmpty() && duaWords.all { states[it] == WordState.Confirmed }

/** What the Recite panel shows. */
internal class RecitePanelState(
    val speechState: SpeechState,
    val phase: RecitationPhase,
    val isClean: Boolean,
    /** Clean recitations so far, as stored. */
    val cleanCount: Int,
    val isHidingWords: Boolean,
)

/** What the buttons of the Recite panel do. */
internal class RecitePanelActions(
    val onToggleHideWords: () -> Unit,
    val onStartMicrophone: () -> Unit,
    val onStopMicrophone: () -> Unit,
)

/** The Recite controls: the eye button, the state of the recitation and the microphone button. */
@Composable
internal fun RecitePanel(
    state: RecitePanelState,
    actions: RecitePanelActions,
    modifier: Modifier = Modifier,
) {
    val field = Awn.field
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(AwnShapes.control)
                .background(field.softFill)
                .padding(horizontal = AwnSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AwnIconButton(
            icon = if (state.isHidingWords) R.drawable.ic_duas_eye else R.drawable.ic_duas_eye_off,
            contentDescription =
                stringResource(
                    if (state.isHidingWords) R.string.duas_recite_show_words
                    else R.string.duas_recite_hide_words,
                ),
            onClick = actions.onToggleHideWords,
            tint = field.content,
            modifier = Modifier.testTag(RECITE_HIDE_TAG),
        )
        Column(Modifier.weight(1f).padding(horizontal = AwnSpacing.small)) {
            ReciteStatusLines(state)
        }
        MicrophoneButton(state, actions)
    }
}

@Composable
private fun ReciteStatusLines(state: RecitePanelState) {
    val field = Awn.field
    val statusText =
        when {
            state.speechState == SpeechState.Failed -> stringResource(R.string.duas_recite_failed)
            state.speechState != SpeechState.Ready -> stringResource(R.string.duas_recite_preparing)
            state.phase == RecitationPhase.Listening ->
                stringResource(R.string.duas_recite_listening)
            state.isClean && state.cleanCount >= CLEAN_RECITATIONS_TO_LEARN ->
                stringResource(R.string.duas_recite_learned)
            state.isClean -> stringResource(R.string.duas_recite_clean)
            else -> stringResource(R.string.duas_recite_idle)
        }
    Text(
        text = statusText,
        style = AwnTypography.bodyStrong,
        color = field.content,
        textAlign = TextAlign.Start,
    )
    if (state.cleanCount > 0 && state.speechState == SpeechState.Ready) {
        Text(
            text =
                stringResource(
                    R.string.duas_recite_progress,
                    state.cleanCount.coerceAtMost(CLEAN_RECITATIONS_TO_LEARN),
                    CLEAN_RECITATIONS_TO_LEARN,
                ),
            style = AwnTypography.small,
            color = field.content,
        )
    }
}

@Composable
private fun MicrophoneButton(state: RecitePanelState, actions: RecitePanelActions) {
    val field = Awn.field
    val permission = rememberMicrophonePermission()
    val isListening = state.phase == RecitationPhase.Listening
    val isReady = state.speechState == SpeechState.Ready || state.speechState == SpeechState.Failed
    Box(
        modifier =
            Modifier.size(AwnSpacing.touchTarget)
                .clip(AwnShapes.control)
                .background(if (isReady) field.buttonFill else field.softFill)
                .clickable(enabled = isReady, role = Role.Button) {
                    when {
                        isListening -> actions.onStopMicrophone()
                        permission.access == MicrophoneAccess.Granted -> actions.onStartMicrophone()
                        permission.access == MicrophoneAccess.CanAsk -> permission.requestAccess()
                        else -> permission.openAppSettings()
                    }
                }
                .testTag(RECITE_MICROPHONE_TAG),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter =
                painterResource(
                    if (isListening) R.drawable.ic_duas_stop else R.drawable.ic_duas_mic,
                ),
            contentDescription =
                stringResource(
                    if (isListening) R.string.duas_recite_stop else R.string.duas_recite_start,
                ),
            tint = field.onButton,
            modifier = Modifier.size(AwnSpacing.iconSize),
        )
    }
}
