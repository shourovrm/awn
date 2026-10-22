package app.awn.quran.feature.recite

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography

/**
 * A message the buttons of the recite bar cannot give. Whether the microphone listens is shown by
 * the microphone button itself, so there is no message for it.
 */
internal sealed interface ReciteStripMessage {
    data object AllowMicrophone : ReciteStripMessage

    data object MicrophoneOff : ReciteStripMessage

    data class Grade(val ayah: Int) : ReciteStripMessage

    data class Finding(val status: ReciteStatus) : ReciteStripMessage

    data class Revising(val remaining: Int) : ReciteStripMessage

    data object NoText : ReciteStripMessage

    data object NoMicrophone : ReciteStripMessage
}

/** What [reciteStripMessageOf] decides on. */
internal data class ReciteStripInputs(
    val phase: RecitationPhase,
    val access: MicrophoneAccess,
    val gradeAyah: Int?,
    val status: ReciteStatus?,
    val isRevising: Boolean,
    val revisionRemaining: Int,
    val hasMicrophoneFailure: Boolean,
)

/**
 * The one message to show above the bar, or null when there is none. The order is the order of
 * need: a missing permission blocks everything, then the ayah waiting for a grade, what the last
 * run found and the revision count; a failed start comes last because the microphone button also
 * shows it.
 */
internal fun reciteStripMessageOf(inputs: ReciteStripInputs): ReciteStripMessage? =
    permissionMessage(inputs) ?: progressMessage(inputs) ?: failureMessage(inputs)

private fun permissionMessage(inputs: ReciteStripInputs): ReciteStripMessage? {
    val isListening = inputs.phase == RecitationPhase.Listening
    return when {
        isListening -> null
        inputs.access == MicrophoneAccess.CanAsk -> ReciteStripMessage.AllowMicrophone
        inputs.access == MicrophoneAccess.DeniedPermanently -> ReciteStripMessage.MicrophoneOff
        else -> null
    }
}

private fun progressMessage(inputs: ReciteStripInputs): ReciteStripMessage? {
    val isListening = inputs.phase == RecitationPhase.Listening
    val isWaitingToStart = inputs.phase == RecitationPhase.Idle
    return when {
        inputs.gradeAyah != null && !isListening -> ReciteStripMessage.Grade(inputs.gradeAyah)
        inputs.status != null -> ReciteStripMessage.Finding(inputs.status)
        inputs.isRevising && inputs.revisionRemaining > 0 && isWaitingToStart ->
            ReciteStripMessage.Revising(inputs.revisionRemaining)
        else -> null
    }
}

private fun failureMessage(inputs: ReciteStripInputs): ReciteStripMessage? =
    when {
        inputs.phase != RecitationPhase.Error -> null
        inputs.hasMicrophoneFailure -> ReciteStripMessage.NoMicrophone
        else -> ReciteStripMessage.NoText
    }

/** Collects what the message depends on, so only the strip recomposes when it changes. */
@Composable
internal fun rememberReciteStripMessage(
    controller: ReciteController,
    phase: RecitationPhase,
    access: MicrophoneAccess,
): ReciteStripMessage? {
    val session = controller.session
    val status by session.status.collectAsState()
    val gradePrompt by session.gradePrompt.collectAsState()
    val isRevising by controller.isRevising.collectAsState()
    val revisionRemaining by controller.revisionRemaining.collectAsState()
    return reciteStripMessageOf(
        ReciteStripInputs(
            phase = phase,
            access = access,
            gradeAyah = gradePrompt?.ayah?.ayah,
            status = status,
            isRevising = isRevising,
            revisionRemaining = revisionRemaining,
            hasMicrophoneFailure = session.lastFailure != null,
        ),
    )
}

/**
 * The strip above the bar: a message, and after a run the way to review the words that went wrong.
 * Draws nothing when there is neither.
 */
@Composable
internal fun ReciteStrip(
    message: ReciteStripMessage?,
    canReview: Boolean,
    onReview: () -> Unit,
    colors: ReciteBarColors,
) {
    if (message == null && !canReview) return
    Row(
        Modifier.fillMaxWidth()
            .clip(AwnShapes.control)
            .background(colors.background)
            .padding(horizontal = AwnSpacing.small),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (message != null) {
            Text(
                text = stripText(message),
                style = AwnTypography.small,
                color = colors.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).testTag(RECITE_BAR_STATUS_TAG),
            )
        }
        if (canReview) {
            Text(
                text = stringResource(R.string.recite_review),
                style = AwnTypography.bodyStrong,
                color = colors.content,
                modifier =
                    Modifier.defaultMinSize(minHeight = AwnSpacing.compactButton)
                        .clickable(role = Role.Button, onClick = onReview)
                        .wrapContentHeight(Alignment.CenterVertically)
                        .padding(horizontal = AwnSpacing.small)
                        .testTag(RECITE_BAR_REVIEW_TAG),
            )
        }
    }
}

@Composable
private fun stripText(message: ReciteStripMessage): String =
    when (message) {
        ReciteStripMessage.AllowMicrophone ->
            stringResource(R.string.recite_status_allow_microphone)
        ReciteStripMessage.MicrophoneOff -> stringResource(R.string.recite_status_microphone_off)
        is ReciteStripMessage.Grade -> stringResource(R.string.recite_status_grade, message.ayah)
        is ReciteStripMessage.Finding -> findingText(message.status)
        is ReciteStripMessage.Revising ->
            pluralStringResource(
                R.plurals.recite_status_revising,
                message.remaining,
                message.remaining,
            )
        ReciteStripMessage.NoText -> stringResource(R.string.recite_status_no_text)
        ReciteStripMessage.NoMicrophone -> stringResource(R.string.recite_status_no_microphone)
    }

@Composable
private fun findingText(status: ReciteStatus): String {
    val template =
        when (status) {
            is ReciteStatus.Skipped -> R.string.recite_status_skipped
            is ReciteStatus.Inserted -> R.string.recite_status_inserted
            is ReciteStatus.Anchored -> R.string.recite_status_anchored
        }
    return stringResource(template, status.ayah, status.wordPosition)
}
