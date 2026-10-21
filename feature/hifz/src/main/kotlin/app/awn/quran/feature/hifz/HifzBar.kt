package app.awn.quran.feature.hifz

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnColors
import app.awn.quran.core.ui.theme.AwnFieldColors
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.hifz.method.HifzProgress
import app.awn.quran.feature.hifz.method.PartGrade
import app.awn.quran.feature.hifz.method.PartKind
import app.awn.quran.feature.hifz.ui.pagesText
import app.awn.quran.feature.hifz.ui.partTitle
import app.awn.quran.feature.recite.MicrophoneAccess
import app.awn.quran.feature.recite.RecitationPhase
import app.awn.quran.feature.recite.ReciteBarMode
import app.awn.quran.feature.recite.ReciteController
import app.awn.quran.feature.recite.reciteBarMode
import app.awn.quran.feature.recite.rememberMicrophonePermission
import app.awn.quran.player.PlaybackState

const val HIFZ_BAR_TAG = "hifz-bar"
const val HIFZ_BAR_HIDE_TAG = "hifz-bar-hide-words"
const val HIFZ_BAR_PLAY_TAG = "hifz-bar-play"
const val HIFZ_BAR_REPEAT_TAG = "hifz-bar-repeat"
const val HIFZ_BAR_MIC_TAG = "hifz-bar-mic"
const val HIFZ_BAR_FINISH_TAG = "hifz-bar-finish"
const val HIFZ_BAR_CLOSE_TAG = "hifz-bar-close"

/** A grade button's tag is this prefix plus the grade name in lower case. */
const val HIFZ_BAR_GRADE_TAG_PREFIX = "hifz-bar-grade-"

private const val DISABLED_ALPHA = 0.38f
private val repeatChoices = listOf(1, 3, 5, 10, 20)

/** The colours of the bar; in Awn mode the main button is the ochre of the mushaf's rosettes. */
private data class HifzBarColors(
    val background: Color,
    val content: Color,
    val primaryFill: Color,
    val onPrimary: Color,
) {
    companion object {
        fun of(field: AwnFieldColors): HifzBarColors {
            // In Awn mode the button colour is the bar's own green, so a button in it would vanish.
            val buttonVanishes = field.buttonFill == field.barBackground
            return HifzBarColors(
                background = field.barBackground,
                content = field.onBar,
                primaryFill = if (buttonVanishes) AwnColors.ochre else field.buttonFill,
                onPrimary = if (buttonVanishes) AwnColors.ink else field.onButton,
            )
        }
    }
}

/**
 * The bar of the `hifzBar` slot of the Quran screen while a part is practised: show or hide words,
 * play the part with repeat, recite to check, and finish with a grade. Words and microphone are the
 * recite controller's own; the page, the word fills and following come from the Quran screen's
 * recite mode, which :app turns on together with the practice.
 *
 * [playRange] plays the ayahs with the chosen reciter; [onExit] leaves recite mode on the screen.
 * Leaving the composition stops reciting, restores the repeat setting of the player and drops the
 * practice if it was not finished.
 */
// One bar: its state, the two rows and the grade step belong together.
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun HifzBar(
    controller: HifzController,
    recite: ReciteController,
    playRange: (first: AyahRef, last: AyahRef) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val request by controller.practice.collectAsState()
    val current = request ?: return
    val session = recite.session
    val phase by session.phase.collectAsState()
    val hideWords by session.hifzEnabled.collectAsState()
    val currentWord by session.currentWord.collectAsState()
    val speechState by recite.speechState.collectAsState()
    val colors = HifzBarColors.of(Awn.field)
    val permission = rememberMicrophonePermission()
    val playback by recite.audioPlayer.state.collectAsState(initial = PlaybackState.Idle)
    val isAudioActive = playback !is PlaybackState.Idle && playback !is PlaybackState.Error
    var repeatCount by remember(current) { mutableIntStateOf(current.repeatCount) }
    var isFinishing by remember(current) { mutableStateOf(false) }

    // The player's repeat setting belongs to the user's listening; practice borrows it.
    DisposableEffect(recite) {
        val before = recite.audioPlayer.options.value
        onDispose {
            recite.audioPlayer.setOptions(
                recite.audioPlayer.options.value.copy(repeatEachAyah = before.repeatEachAyah),
            )
            recite.exit()
            controller.cancelPractice()
        }
    }
    // The microphone is not available to an app in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { session.stop() }
    // A stopped recitation leaves its mistakes behind for the Weak spots list.
    LaunchedEffect(phase) {
        if (phase == RecitationPhase.Stopped) {
            controller.recordMistakes(
                PracticeMistakes.recordsOf(session.reviews.value, System.currentTimeMillis()),
            )
        }
    }

    val listening = phase == RecitationPhase.Listening
    val barMode = reciteBarMode(speechState)
    val words by session.words.collectAsState()
    val canListen = barMode == ReciteBarMode.Controls && words.isNotEmpty()

    Column(
        modifier
            .padding(horizontal = AwnSpacing.screenMargin)
            .padding(bottom = AwnSpacing.dockFloatGap)
            .fillMaxWidth()
            .clip(AwnShapes.control)
            .background(colors.background)
            .padding(AwnSpacing.small)
            .testTag(HIFZ_BAR_TAG),
    ) {
        if (barMode == ReciteBarMode.Preparing) {
            PreparingRow(colors) {
                controller.cancelPractice()
                onExit()
            }
            return@Column
        }
        TitleRow(
            request = current,
            currentAyah = currentWord?.let { AyahRef(it.surah, it.ayah) },
            colors = colors,
            onFinish = { isFinishing = true },
            onClose = {
                controller.cancelPractice()
                onExit()
            },
        )
        if (isFinishing) {
            val reviews = session.reviews.value
            val didRecite = phase == RecitationPhase.Stopped || phase == RecitationPhase.Listening
            val suggested =
                if (didRecite) {
                    HifzProgress.suggestedGrade(
                        PracticeMistakes.mistakenWords(
                            reviews,
                            current.firstAyah..current.lastAyah,
                        ),
                        current.ayahCount,
                    )
                } else {
                    null
                }
            GradeRow(suggested, colors) { grade ->
                controller.finishPractice(grade)
                onExit()
            }
            return@Column
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarToggle(
                icon =
                    if (hideWords) R.drawable.ic_hifz_hide_words else R.drawable.ic_hifz_show_words,
                description =
                    stringResource(
                        if (hideWords) R.string.bar_show_words else R.string.bar_hide_words,
                    ),
                checked = hideWords,
                onCheckedChange = recite::setHideWords,
                tag = HIFZ_BAR_HIDE_TAG,
                colors = colors,
            )
            BarButton(
                icon = if (isAudioActive) R.drawable.ic_hifz_stop else R.drawable.ic_hifz_play,
                description =
                    stringResource(if (isAudioActive) R.string.bar_stop else R.string.bar_play),
                onClick = {
                    if (isAudioActive) {
                        recite.audioPlayer.stop()
                    } else {
                        // The microphone would hear the reciter.
                        session.stop()
                        recite.audioPlayer.setOptions(
                            recite.audioPlayer.options.value.copy(repeatEachAyah = repeatCount),
                        )
                        playRange(current.firstAyah, current.lastAyah)
                    }
                },
                tag = HIFZ_BAR_PLAY_TAG,
                colors = colors,
            )
            RepeatButton(repeatCount, { repeatCount = it }, colors)
            MicrophoneButton(
                listening = listening,
                enabled = (canListen && !isAudioActive) || listening,
                colors = colors,
                onClick = {
                    when {
                        listening -> session.stop()
                        permission.access == MicrophoneAccess.Granted -> session.start()
                        permission.access == MicrophoneAccess.CanAsk -> permission.requestAccess()
                        else -> permission.openAppSettings()
                    }
                },
            )
        }
    }
}

@Composable
private fun TitleRow(
    request: PracticeRequest,
    currentAyah: AyahRef?,
    colors: HifzBarColors,
    onFinish: () -> Unit,
    onClose: () -> Unit,
) {
    val secondLine =
        if (request.kind == PartKind.NewLesson) {
            stringResource(R.string.bar_ayah, (currentAyah ?: request.firstAyah).ayah)
        } else {
            pagesText(request.pages)
        }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(horizontal = AwnSpacing.small)) {
            Text(
                text = partTitle(request.kind, request.method),
                style = AwnTypography.bodyStrong,
                color = colors.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = secondLine,
                style = AwnTypography.small,
                color = colors.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BarButton(
            icon = R.drawable.ic_hifz_done,
            description = stringResource(R.string.bar_finish),
            onClick = onFinish,
            tag = HIFZ_BAR_FINISH_TAG,
            colors = colors,
        )
        BarButton(
            icon = R.drawable.ic_hifz_close,
            description = stringResource(R.string.bar_close),
            onClick = onClose,
            tag = HIFZ_BAR_CLOSE_TAG,
            colors = colors,
        )
    }
}

@Composable
private fun PreparingRow(colors: HifzBarColors, onClose: () -> Unit) {
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
                modifier = Modifier.size(AwnSpacing.large),
                color = colors.content,
                strokeWidth = 2.dp,
            )
            Text(
                text = stringResource(R.string.bar_getting_ready),
                style = AwnTypography.small,
                color = colors.content,
                modifier = Modifier.padding(start = AwnSpacing.small),
            )
        }
        BarButton(
            icon = R.drawable.ic_hifz_close,
            description = stringResource(R.string.bar_close),
            onClick = onClose,
            tag = HIFZ_BAR_CLOSE_TAG,
            colors = colors,
        )
    }
}

/** Four grade buttons in a row, the suggested one filled; one tap records the part. */
@Composable
private fun GradeRow(
    suggested: PartGrade?,
    colors: HifzBarColors,
    onGrade: (PartGrade) -> Unit,
) {
    Text(
        text = stringResource(R.string.bar_pick_grade),
        style = AwnTypography.small,
        color = colors.content,
        modifier = Modifier.padding(horizontal = AwnSpacing.small),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
        listOf(PartGrade.Easy, PartGrade.Good, PartGrade.Hard, PartGrade.Forgot).forEach { grade ->
            val isSuggested = grade == suggested
            Box(
                Modifier.weight(1f)
                    .heightIn(min = AwnSpacing.touchTarget)
                    .clip(AwnShapes.control)
                    .background(if (isSuggested) colors.primaryFill else Color.Transparent)
                    .clickable(role = Role.Button) { onGrade(grade) }
                    .testTag(HIFZ_BAR_GRADE_TAG_PREFIX + grade.name.lowercase()),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = gradeLabel(grade),
                    style = AwnTypography.bodyStrong,
                    color = if (isSuggested) colors.onPrimary else colors.content,
                    modifier = Modifier.padding(horizontal = AwnSpacing.small),
                )
            }
        }
    }
}

@Composable
private fun gradeLabel(grade: PartGrade): String =
    stringResource(
        when (grade) {
            PartGrade.Easy -> R.string.grade_easy
            PartGrade.Good -> R.string.grade_good
            PartGrade.Hard -> R.string.grade_hard
            PartGrade.Forgot -> R.string.grade_forgot
        },
    )

@Composable
private fun RepeatButton(count: Int, onChange: (Int) -> Unit, colors: HifzBarColors) {
    var isOpen by remember { mutableStateOf(false) }
    val description = stringResource(R.string.bar_repeat)
    Box {
        Row(
            Modifier.defaultMinSize(
                    minWidth = AwnSpacing.touchTarget,
                    minHeight = AwnSpacing.touchTarget,
                )
                .semantics { contentDescription = description }
                .clickable(role = Role.Button) { isOpen = true }
                .testTag(HIFZ_BAR_REPEAT_TAG),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_hifz_repeat),
                contentDescription = null,
                tint = colors.content,
            )
            Text(
                text = stringResource(R.string.bar_repeat_value, count),
                style = AwnTypography.small,
                color = colors.content,
                modifier = Modifier.padding(start = 4.dp, end = AwnSpacing.small),
            )
        }
        DropdownMenu(
            expanded = isOpen,
            onDismissRequest = { isOpen = false },
            containerColor = Awn.field.background,
        ) {
            repeatChoices.forEach { choice ->
                Row(
                    Modifier.defaultMinSize(minHeight = AwnSpacing.touchTarget)
                        .fillMaxWidth()
                        .clickable(role = Role.RadioButton) {
                            onChange(choice)
                            isOpen = false
                        }
                        .padding(horizontal = AwnSpacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.bar_repeat_value, choice),
                        style =
                            if (choice == count) AwnTypography.bodyStrong else AwnTypography.body,
                        color = Awn.field.content,
                    )
                }
            }
        }
    }
}

@Composable
private fun MicrophoneButton(
    listening: Boolean,
    enabled: Boolean,
    colors: HifzBarColors,
    onClick: () -> Unit,
) {
    val description =
        stringResource(if (listening) R.string.bar_stop_reciting else R.string.bar_recite)
    Box(
        modifier =
            Modifier.requiredSize(AwnSpacing.touchTarget)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .clip(AwnShapes.control)
                .background(colors.primaryFill)
                .semantics { contentDescription = description }
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .testTag(HIFZ_BAR_MIC_TAG),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter =
                painterResource(if (listening) R.drawable.ic_hifz_stop else R.drawable.ic_hifz_mic),
            contentDescription = null,
            tint = colors.onPrimary,
        )
    }
}

@Composable
private fun BarButton(
    icon: Int,
    description: String,
    onClick: () -> Unit,
    tag: String,
    colors: HifzBarColors,
) {
    Box(
        modifier =
            Modifier.requiredSize(AwnSpacing.touchTarget)
                .semantics { contentDescription = description }
                .clickable(role = Role.Button, onClick = onClick)
                .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.content)
    }
}

/** A switch drawn as an icon; the icon takes the accent colour while it is on. */
@Composable
private fun BarToggle(
    icon: Int,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String,
    colors: HifzBarColors,
) {
    Box(
        modifier =
            Modifier.requiredSize(AwnSpacing.touchTarget)
                .semantics { contentDescription = description }
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = if (checked) colors.primaryFill else colors.content,
        )
    }
}
