package app.awn.quran.feature.recite

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.player.PlaybackState

const val RECITE_BAR_MIC_TAG = "recite-bar-mic"
const val RECITE_BAR_CLOSE_TAG = "recite-bar-close"
const val RECITE_BAR_STATUS_TAG = "recite-bar-status"
const val RECITE_BAR_PROGRESS_TAG = "recite-bar-progress"
const val RECITE_BAR_REVIEW_TAG = "recite-bar-review"
const val RECITE_BAR_SPEED_TAG = "recite-bar-speed"
const val RECITE_BAR_HIDE_TAG = "recite-bar-hide-words"
const val RECITE_BAR_REVEAL_WORD_TAG = "recite-bar-reveal-word"
const val RECITE_BAR_REVEAL_AYAH_TAG = "recite-bar-reveal-ayah"
const val RECITE_BAR_LISTEN_TAG = "recite-bar-listen"

/** A grade button's tag is this prefix plus the grade in lower case: easy, good, hard, forgot. */
const val RECITE_BAR_GRADE_TAG_PREFIX = "recite-bar-grade-"

/**
 * The recite bar for the `reciteBar` slot of the Quran screen: one row of buttons
 * ([ReciteBarControls]) with a strip above it that shows a message or the way to review only while
 * there is one. A finished ayah brings four grade buttons under the row. The colours follow the
 * active colour mode. [onExit] runs when the close button is tapped. Leaving the composition, for
 * example when Back removes the bar, also stops reciting and clears the word states.
 */
@Composable
fun ReciteBar(controller: ReciteController, onExit: () -> Unit, modifier: Modifier = Modifier) {
    val session = controller.session
    val phase by session.phase.collectAsState()
    val words by session.words.collectAsState()
    val reviews by session.reviews.collectAsState()
    val problems by session.wordProblems.collectAsState()
    val focusedWord by controller.focusedWord.collectAsState()
    val flaggedWords = remember(reviews, problems) { flaggedReviews(reviews, problems) }
    val colors = ReciteBarColors.reciting()
    val speechState by controller.speechState.collectAsState()
    var reviewOpen by remember { mutableStateOf(false) }

    DisposableEffect(controller) { onDispose { controller.exit() } }
    // The microphone is not available to an app in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { controller.stopMicrophone() }

    val permission = rememberMicrophonePermission()
    val barMode = reciteBarMode(speechState)
    val canListen = barMode == ReciteBarMode.Controls && words.isNotEmpty()
    val playback by controller.audioPlayer.state.collectAsState(initial = PlaybackState.Idle)
    val isAudioActive = reciteAudioStateOf(playback).isActive()

    // Starting to recite again clears the results, so the card on the old ones goes away.
    LaunchedEffect(phase == RecitationPhase.Listening) {
        if (phase == RecitationPhase.Listening) {
            reviewOpen = false
            controller.clearFocus()
        }
    }

    // The card and the strip sit above the bar in the same column, so the screen measures all of
    // them and keeps the page clear.
    Column(
        modifier
            .padding(horizontal = AwnSpacing.screenMargin)
            .padding(bottom = AwnSpacing.dockFloatGap)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
    ) {
        if (reviewOpen || focusedWord != null) {
            ReciteReviewCardWithWordActions(
                controller = controller,
                reviews = flaggedWords,
                focusedKey = focusedWord,
                isAudioActive = isAudioActive,
                onDismiss = {
                    reviewOpen = false
                    controller.clearFocus()
                },
            )
        }
        if (barMode == ReciteBarMode.Controls) {
            ReciteStrip(
                message = rememberReciteStripMessage(controller, phase, permission.access),
                canReview =
                    phase == RecitationPhase.Stopped && !isAudioActive && flaggedWords.isNotEmpty(),
                onReview = {
                    reviewOpen = true
                    flaggedWords.firstOrNull()?.let { controller.focusWord(it.key) }
                },
                colors = colors,
            )
        }
        ReciteBarSurface(controller, colors, barMode, permission, canListen, onExit)
    }
}

/** The rounded surface of the bar: its one row of buttons and the grade buttons under it. */
@Composable
private fun ReciteBarSurface(
    controller: ReciteController,
    colors: ReciteBarColors,
    barMode: ReciteBarMode,
    permission: MicrophonePermissionState,
    canListen: Boolean,
    onExit: () -> Unit,
) {
    val gradePrompt by controller.session.gradePrompt.collectAsState()
    Column(
        Modifier.fillMaxWidth()
            .clip(AwnShapes.control)
            .background(colors.background)
            .padding(AwnSpacing.small),
    ) {
        val closeBar = {
            controller.exit()
            onExit()
        }
        if (barMode == ReciteBarMode.Preparing) {
            PreparingRow(onClose = closeBar, colors = colors)
        } else {
            ReciteBarControls(controller, colors, permission, canListen, closeBar)
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
