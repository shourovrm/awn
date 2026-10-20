package app.awn.quran.feature.recite

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.awn.quran.core.ui.theme.AwnColors
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import kotlin.math.abs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

const val RECITE_BAR_MIC_TAG = "recite-bar-mic"
const val RECITE_BAR_CLOSE_TAG = "recite-bar-close"
const val RECITE_BAR_STATUS_TAG = "recite-bar-status"
const val RECITE_BAR_REVIEW_TAG = "recite-bar-review"
const val RECITE_BAR_SPEED_TAG = "recite-bar-speed"
const val RECITE_BAR_HIDE_TAG = "recite-bar-hide-words"
const val RECITE_BAR_HINT_TAG = "recite-bar-hint"

private const val DISABLED_ALPHA = 0.38f

// The five speeds the popup offers, from slower to faster; the tracker takes 0 to 1.
private val speedSteps = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)

private val speedLabels =
    listOf(
        R.string.recite_speed_slowest,
        R.string.recite_slower,
        R.string.recite_speed_normal,
        R.string.recite_faster,
        R.string.recite_speed_fastest,
    )

/**
 * The recite bar for the `reciteBar` slot of the Quran screen: one status line above icon buttons
 * for close, speed, hide words, hint, review (after stopping) and the microphone. [onExit] runs
 * when the close button is tapped. Leaving the composition, for example when Back removes the bar,
 * also stops reciting and clears the word states.
 */
// One bar: its state, the six controls and the review sheet belong together.
@Suppress("LongMethod")
@Composable
fun ReciteBar(controller: ReciteController, onExit: () -> Unit, modifier: Modifier = Modifier) {
    val session = controller.session
    val phase by session.phase.collectAsState()
    val speed by session.speed.collectAsState()
    val hideWords by session.hifzEnabled.collectAsState()
    val words by session.words.collectAsState()
    val reviews by session.reviews.collectAsState()
    val speechState by controller.speechState.collectAsState()
    val hintEnabled by controller.settings.hintEnabled.collectAsState(initial = true)
    var reviewOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    DisposableEffect(controller) { onDispose { controller.exit() } }
    // The microphone is not available to an app in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { session.stop() }

    val permission = rememberMicrophonePermission()
    val access = permission.access
    val listening = phase == RecitationPhase.Listening
    val preparing = speechState == SpeechState.Preparing
    val canListen = !preparing && words.isNotEmpty()

    Column(
        modifier
            .padding(horizontal = AwnSpacing.screenMargin)
            .padding(bottom = AwnSpacing.dockFloatGap)
            .fillMaxWidth()
            .clip(AwnShapes.control)
            .background(AwnColors.green)
            .padding(AwnSpacing.small),
    ) {
        Text(
            text = barStatusText(session, phase, preparing, access),
            style = AwnTypography.small,
            color = AwnColors.paper,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier.padding(horizontal = AwnSpacing.small).testTag(RECITE_BAR_STATUS_TAG),
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarIconButton(
                icon = R.drawable.ic_recite_close,
                description = stringResource(R.string.recite_close),
                onClick = {
                    controller.exit()
                    onExit()
                },
                tag = RECITE_BAR_CLOSE_TAG,
            )
            SpeedButton(speed = speed, onSpeedChange = controller::setSpeed)
            BarToggleButton(
                icon = R.drawable.ic_recite_hide_words,
                description = stringResource(R.string.recite_hide_words),
                checked = hideWords,
                onCheckedChange = controller::setHideWords,
                tag = RECITE_BAR_HIDE_TAG,
            )
            if (hintEnabled) {
                BarIconButton(
                    icon = R.drawable.ic_recite_hint,
                    description = stringResource(R.string.recite_hint),
                    onClick = session::requestHint,
                    enabled = hideWords,
                    tag = RECITE_BAR_HINT_TAG,
                )
            }
            if (phase == RecitationPhase.Stopped) {
                BarIconButton(
                    icon = R.drawable.ic_recite_review,
                    description = stringResource(R.string.recite_review),
                    onClick = { reviewOpen = true },
                    tag = RECITE_BAR_REVIEW_TAG,
                )
            }
            MicrophoneButton(
                listening = listening,
                access = access,
                enabled = canListen || listening,
                onClick = {
                    when {
                        listening -> session.stop()
                        access == MicrophoneAccess.Granted -> session.start()
                        access == MicrophoneAccess.CanAsk -> permission.requestAccess()
                        else -> permission.openAppSettings()
                    }
                },
            )
        }
    }

    if (reviewOpen) {
        ReciteReviewSheet(
            reviews = reviews.values.flatten(),
            startKey = null,
            words = words,
            onListen = { key ->
                scope.launch {
                    playWord(controller.audioPlayer, controller.settings.reciterId.first(), key)
                }
            },
            onDismiss = { reviewOpen = false },
        )
    }
}

@Composable
private fun barStatusText(
    session: RecitationSession,
    phase: RecitationPhase,
    preparing: Boolean,
    access: MicrophoneAccess,
): String {
    val status by session.status.collectAsState()
    val hint by session.hint.collectAsState()
    val currentStatus = status
    val currentHint = hint
    val listening = phase == RecitationPhase.Listening
    return when {
        preparing -> stringResource(R.string.recite_status_preparing)
        access == MicrophoneAccess.CanAsk && !listening ->
            stringResource(R.string.recite_status_allow_microphone)
        access == MicrophoneAccess.DeniedPermanently && !listening ->
            stringResource(R.string.recite_status_microphone_off)
        currentHint != null -> stringResource(R.string.recite_status_hint, currentHint.letter)
        currentStatus is ReciteStatus.Skipped ->
            stringResource(
                R.string.recite_status_skipped,
                currentStatus.ayah,
                currentStatus.wordPosition,
            )
        currentStatus is ReciteStatus.Inserted ->
            stringResource(
                R.string.recite_status_inserted,
                currentStatus.ayah,
                currentStatus.wordPosition,
            )
        currentStatus is ReciteStatus.Anchored ->
            stringResource(
                R.string.recite_status_anchored,
                currentStatus.ayah,
                currentStatus.wordPosition,
            )
        else -> phaseText(phase, session)
    }
}

@Composable
private fun phaseText(phase: RecitationPhase, session: RecitationSession): String =
    when (phase) {
        RecitationPhase.Idle -> stringResource(R.string.recite_status_ready)
        RecitationPhase.Listening -> stringResource(R.string.recite_status_listening)
        RecitationPhase.Stopped -> stringResource(R.string.recite_status_stopped)
        RecitationPhase.Error ->
            if (session.lastFailure == null) stringResource(R.string.recite_status_no_text)
            else stringResource(R.string.recite_status_no_microphone)
    }

@Composable
private fun MicrophoneButton(
    listening: Boolean,
    access: MicrophoneAccess,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val description =
        when {
            listening -> stringResource(R.string.recite_stop)
            access == MicrophoneAccess.Granted -> stringResource(R.string.recite_start)
            access == MicrophoneAccess.CanAsk -> stringResource(R.string.recite_microphone_allow)
            else -> stringResource(R.string.recite_microphone_open_settings)
        }
    val icon = if (listening) R.drawable.ic_recite_stop else R.drawable.ic_recite_mic
    // The one primary action: ochre fill with ink, as buttons on the green field.
    Box(
        modifier =
            Modifier.requiredSize(AwnSpacing.touchTarget)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .clip(AwnShapes.control)
                .background(AwnColors.ochre)
                .semantics { contentDescription = description }
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .testTag(RECITE_BAR_MIC_TAG),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = AwnColors.ink)
    }
}

@Composable
private fun SpeedButton(speed: Float, onSpeedChange: (Float) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val nearestStep = speedSteps.indices.minBy { abs(speedSteps[it] - speed) }
    Box {
        BarIconButton(
            icon = R.drawable.ic_recite_speed,
            description = stringResource(R.string.recite_speed),
            onClick = { open = true },
            tag = RECITE_BAR_SPEED_TAG,
        )
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = AwnColors.paper,
        ) {
            // Fastest on top: the popup reads from faster down to slower as a vertical scale.
            for (step in speedSteps.indices.reversed()) {
                SpeedChoice(
                    label = stringResource(speedLabels[step]),
                    selected = step == nearestStep,
                    onClick = {
                        onSpeedChange(speedSteps[step])
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SpeedChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.defaultMinSize(minHeight = AwnSpacing.touchTarget)
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = AwnSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(AwnSpacing.large)) {
            if (selected) {
                Icon(
                    painterResource(R.drawable.ic_recite_check),
                    contentDescription = null,
                    tint = AwnColors.green,
                )
            }
        }
        Text(
            text = label,
            style = AwnTypography.body,
            color = AwnColors.ink,
            modifier = Modifier.padding(start = AwnSpacing.small),
        )
    }
}

@Composable
private fun BarIconButton(
    icon: Int,
    description: String,
    onClick: () -> Unit,
    tag: String,
    enabled: Boolean = true,
) {
    Box(
        modifier =
            Modifier.requiredSize(AwnSpacing.touchTarget)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .semantics { contentDescription = description }
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = AwnColors.paper)
    }
}

/** A switch drawn as an icon: the icon turns ochre while it is on. */
@Composable
private fun BarToggleButton(
    icon: Int,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String,
) {
    val tint: Color = if (checked) AwnColors.ochre else AwnColors.paper
    Box(
        modifier =
            Modifier.requiredSize(AwnSpacing.touchTarget)
                .semantics { contentDescription = description }
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint)
    }
}
