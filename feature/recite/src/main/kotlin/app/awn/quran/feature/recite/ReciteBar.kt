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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.player.PlaybackState
import kotlin.math.abs
import kotlinx.coroutines.launch

const val RECITE_BAR_MIC_TAG = "recite-bar-mic"
const val RECITE_BAR_CLOSE_TAG = "recite-bar-close"
const val RECITE_BAR_STATUS_TAG = "recite-bar-status"
const val RECITE_BAR_PROGRESS_TAG = "recite-bar-progress"
const val RECITE_BAR_REVIEW_TAG = "recite-bar-review"
const val RECITE_BAR_SPEED_TAG = "recite-bar-speed"
const val RECITE_BAR_HIDE_TAG = "recite-bar-hide-words"
const val RECITE_BAR_HINT_TAG = "recite-bar-hint"
const val RECITE_BAR_REVEAL_WORD_TAG = "recite-bar-reveal-word"
const val RECITE_BAR_REVEAL_AYAH_TAG = "recite-bar-reveal-ayah"
const val RECITE_BAR_LISTEN_TAG = "recite-bar-listen"
const val RECITE_BAR_AUDIO_STOP_TAG = "recite-bar-audio-stop"
const val RECITE_BAR_AUDIO_PROGRESS_TAG = "recite-bar-audio-progress"

/** A grade button's tag is this prefix plus the grade in lower case: easy, good, hard, forgot. */
const val RECITE_BAR_GRADE_TAG_PREFIX = "recite-bar-grade-"

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
 * for close, speed, hide words, listen and the microphone. While words are hidden a second row
 * offers the first-letter hint, "show next word" and "show ayah" for silent self-testing, and
 * review (after stopping) joins it. A finished ayah brings four grade buttons. The colours follow
 * the active colour mode. [onExit] runs when the close button is tapped. Leaving the composition,
 * for example when Back removes the bar, also stops reciting and clears the word states.
 */
// One bar: its state, the controls and the review sheet belong together.
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun ReciteBar(controller: ReciteController, onExit: () -> Unit, modifier: Modifier = Modifier) {
    val session = controller.session
    val phase by session.phase.collectAsState()
    val speed by session.speed.collectAsState()
    val hideWords by session.hifzEnabled.collectAsState()
    val words by session.words.collectAsState()
    val reviews by session.reviews.collectAsState()
    val gradePrompt by session.gradePrompt.collectAsState()
    val problems by session.wordProblems.collectAsState()
    val focusedWord by controller.focusedWord.collectAsState()
    val flaggedWords = remember(reviews, problems) { flaggedReviews(reviews, problems) }
    val colors = ReciteBarColors.reciting()
    val speechState by controller.speechState.collectAsState()
    val hintEnabled by controller.settings.hintEnabled.collectAsState(initial = true)
    var reviewOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    DisposableEffect(controller) { onDispose { controller.exit() } }
    // The microphone is not available to an app in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { controller.stopMicrophone() }

    val permission = rememberMicrophonePermission()
    val access = permission.access
    val listening = phase == RecitationPhase.Listening
    val barMode = reciteBarMode(speechState)
    val canListen = barMode == ReciteBarMode.Controls && words.isNotEmpty()

    val playback by controller.audioPlayer.state.collectAsState(initial = PlaybackState.Idle)
    val audio = reciteAudioStateOf(playback)
    val isAudioActive = audio.isActive()
    // Whatever starts the audio (Listen, the ayah bar, a word), the microphone must not hear it.
    LaunchedEffect(isAudioActive) { if (isAudioActive) controller.pauseMicrophone() }

    // Starting to recite again clears the results, so the card on the old ones goes away.
    LaunchedEffect(listening) {
        if (listening) {
            reviewOpen = false
            controller.clearFocus()
        }
    }

    // The card sits above the bar in the same column, so the screen measures both and keeps the
    // page clear of them.
    Column(
        modifier
            .padding(horizontal = AwnSpacing.screenMargin)
            .padding(bottom = AwnSpacing.dockFloatGap)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
    ) {
        if (reviewOpen || focusedWord != null) {
            ReciteReviewCard(
                reviews = flaggedWords,
                focusedKey = focusedWord,
                words = words,
                onFocus = controller::focusWord,
                onListen = {
                    scope.launch { controller.listenToAyah() }
                },
                onDismiss = {
                    reviewOpen = false
                    controller.clearFocus()
                },
            )
        }
        Column(
            Modifier.fillMaxWidth()
                .clip(AwnShapes.control)
                .background(colors.background)
                .padding(AwnSpacing.small),
        ) {
            if (barMode == ReciteBarMode.Preparing) {
                PreparingRow(
                    onClose = {
                        controller.exit()
                        onExit()
                    },
                    colors = colors,
                )
            } else {
                Text(
                    text = barStatusText(controller, phase, access, audio, playback),
                    style = AwnTypography.small,
                    color = colors.content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier.padding(horizontal = AwnSpacing.small)
                            .testTag(RECITE_BAR_STATUS_TAG),
                )
                if (isAudioActive) AudioProgressLine(playback, colors)
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
                        colors = colors,
                    )
                    SpeedButton(
                        speed = speed,
                        onSpeedChange = controller::setSpeed,
                        colors = colors,
                    )
                    BarToggleButton(
                        icon = R.drawable.ic_recite_hide_words,
                        description = stringResource(R.string.recite_hide_words),
                        checked = hideWords,
                        onCheckedChange = controller::setHideWords,
                        tag = RECITE_BAR_HIDE_TAG,
                        colors = colors,
                    )
                    ListenButton(
                        audio = audio,
                        enabled = canListen || isAudioActive,
                        colors = colors,
                        onClick = {
                            when (listenActionFor(audio)) {
                                ListenAction.PlayAyah -> controller.listenOnward()
                                ListenAction.Pause -> controller.audioPlayer.pause()
                                ListenAction.Resume -> controller.audioPlayer.resume()
                                null -> Unit
                            }
                        },
                    )
                    if (isAudioActive) {
                        BarIconButton(
                            icon = R.drawable.ic_recite_stop,
                            description = stringResource(R.string.recite_listen_stop),
                            onClick = controller::stopListening,
                            tag = RECITE_BAR_AUDIO_STOP_TAG,
                            colors = colors,
                        )
                    }
                    MicrophoneButton(
                        listening = listening,
                        access = access,
                        // While the audio plays the microphone stops it and goes on reciting.
                        enabled = canListen || listening,
                        colors = colors,
                        onClick = {
                            when {
                                listening -> controller.stopMicrophone()
                                access == MicrophoneAccess.Granted && isAudioActive ->
                                    controller.resumeReciting()
                                access == MicrophoneAccess.Granted -> session.start()
                                access == MicrophoneAccess.CanAsk -> permission.requestAccess()
                                else -> permission.openAppSettings()
                            }
                        },
                    )
                }
                // Review sits here, not in the first row, which has no room for a seventh button.
                val showsReview = phase == RecitationPhase.Stopped && !isAudioActive
                if (hideWords || showsReview) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (hideWords) {
                            // The hint is for hidden words only, so it sits with the other buttons
                            // that help with them.
                            if (hintEnabled) {
                                BarIconButton(
                                    icon = R.drawable.ic_recite_hint,
                                    description = stringResource(R.string.recite_hint),
                                    onClick = session::requestHint,
                                    tag = RECITE_BAR_HINT_TAG,
                                    colors = colors,
                                )
                            }
                            BarIconButton(
                                icon = R.drawable.ic_recite_reveal_word,
                                description = stringResource(R.string.recite_reveal_word),
                                onClick = session::revealNextWord,
                                tag = RECITE_BAR_REVEAL_WORD_TAG,
                                colors = colors,
                            )
                            BarIconButton(
                                icon = R.drawable.ic_recite_reveal_ayah,
                                description = stringResource(R.string.recite_reveal_ayah),
                                onClick = session::revealAyah,
                                tag = RECITE_BAR_REVEAL_AYAH_TAG,
                                colors = colors,
                            )
                        }
                        if (showsReview) {
                            BarIconButton(
                                icon = R.drawable.ic_recite_review,
                                description = stringResource(R.string.recite_review),
                                onClick = {
                                    reviewOpen = true
                                    flaggedWords.firstOrNull()?.let { controller.focusWord(it.key) }
                                },
                                tag = RECITE_BAR_REVIEW_TAG,
                                colors = colors,
                            )
                        }
                    }
                }
                gradePrompt?.let { prompt ->
                    GradeButtons(
                        suggested = prompt.suggested,
                        onGrade = controller::grade,
                        colors = colors,
                    )
                }
            }
        }
    }
}

/**
 * What the bar shows while the speech model loads: a small progress indicator, one waiting word and
 * the close button. Nothing else can be used before the model is ready.
 */
@Composable
private fun PreparingRow(onClose: () -> Unit, colors: ReciteBarColors) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.padding(horizontal = AwnSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(AwnSpacing.large).testTag(RECITE_BAR_PROGRESS_TAG),
                color = colors.content,
                strokeWidth = 2.dp,
            )
            Text(
                text = stringResource(R.string.recite_status_preparing),
                style = AwnTypography.small,
                color = colors.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier.padding(start = AwnSpacing.small).testTag(RECITE_BAR_STATUS_TAG),
            )
        }
        BarIconButton(
            icon = R.drawable.ic_recite_close,
            description = stringResource(R.string.recite_close),
            onClick = onClose,
            tag = RECITE_BAR_CLOSE_TAG,
            colors = colors,
        )
    }
}

@Composable
private fun barStatusText(
    controller: ReciteController,
    phase: RecitationPhase,
    access: MicrophoneAccess,
    audio: ReciteAudioState,
    playback: PlaybackState,
): String {
    val session = controller.session
    val status by session.status.collectAsState()
    val hint by session.hint.collectAsState()
    val gradePrompt by session.gradePrompt.collectAsState()
    val revising by controller.isRevising.collectAsState()
    val revisionRemaining by controller.revisionRemaining.collectAsState()
    val currentStatus = status
    val currentHint = hint
    val currentPrompt = gradePrompt
    val listening = phase == RecitationPhase.Listening
    return when {
        audio.isActive() -> audioStatusText(audio, playback)
        currentHint != null -> stringResource(R.string.recite_status_hint, currentHint.letter)
        access == MicrophoneAccess.CanAsk && !listening ->
            stringResource(R.string.recite_status_allow_microphone)
        access == MicrophoneAccess.DeniedPermanently && !listening ->
            stringResource(R.string.recite_status_microphone_off)
        currentPrompt != null && !listening ->
            stringResource(R.string.recite_status_grade, currentPrompt.ayah.ayah)
        currentStatus != null -> findingText(currentStatus)
        revising && revisionRemaining > 0 && phase == RecitationPhase.Idle ->
            pluralStringResource(
                R.plurals.recite_status_revising,
                revisionRemaining,
                revisionRemaining,
            )
        else -> phaseText(phase, session)
    }
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
    colors: ReciteBarColors,
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
    // The one primary action, in the fill the colour mode gives a button on the bar.
    Box(
        modifier =
            Modifier.requiredSize(AwnSpacing.touchTarget)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .clip(AwnShapes.control)
                .background(colors.primaryFill)
                .semantics { contentDescription = description }
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .testTag(RECITE_BAR_MIC_TAG),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.onPrimary)
    }
}

@Composable
private fun SpeedButton(speed: Float, onSpeedChange: (Float) -> Unit, colors: ReciteBarColors) {
    var open by remember { mutableStateOf(false) }
    val nearestStep = speedSteps.indices.minBy { abs(speedSteps[it] - speed) }
    Box {
        BarIconButton(
            icon = R.drawable.ic_recite_speed,
            description = stringResource(R.string.recite_speed),
            onClick = { open = true },
            tag = RECITE_BAR_SPEED_TAG,
            colors = colors,
        )
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = Awn.field.background,
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
    val field = Awn.field
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
                    tint = field.content,
                )
            }
        }
        Text(
            text = label,
            style = AwnTypography.body,
            color = field.content,
            modifier = Modifier.padding(start = AwnSpacing.small),
        )
    }
}

@Composable
internal fun BarIconButton(
    icon: Int,
    description: String,
    onClick: () -> Unit,
    tag: String,
    colors: ReciteBarColors,
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
        Icon(painterResource(icon), contentDescription = null, tint = colors.content)
    }
}

/** A switch drawn as an icon: the icon takes the accent colour while it is on. */
@Composable
private fun BarToggleButton(
    icon: Int,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String,
    colors: ReciteBarColors,
) {
    val tint: Color = if (checked) colors.primaryFill else colors.content
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
