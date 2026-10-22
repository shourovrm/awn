@file:Suppress("MatchingDeclarationName", "TooManyFunctions")

package app.awn.quran.feature.listen

import android.text.format.Formatter
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnColors
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.player.PlayStep
import kotlinx.coroutines.launch

/** What the buttons of the audio bar do. */
@Suppress("LongParameterList") // One lambda per button.
internal class AudioBarActions(
    val onPlay: () -> Unit,
    val onPause: () -> Unit,
    val onStop: () -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onRepeat: () -> Unit,
    val onSpeed: () -> Unit,
    val onCancelDownload: () -> Unit,
    val onOpenReciters: () -> Unit,
    val onExpand: () -> Unit,
    val onChooseStart: (StartChoice) -> Unit,
    val onDismissStartChoices: () -> Unit,
    val onDownload: () -> Unit,
    val onStream: () -> Unit,
    /** Starts reciting; null when the screen has no recite mode, which hides the microphone. */
    val onRecite: (() -> Unit)? = null,
)

/**
 * The audio bar for the `audioBar` slot of the Quran screen: idle (play, reciter, expand), playing
 * (stop, previous, pause, next, repeat, reciter) or downloading (cancel, progress, size). The idle
 * and playing bars end with a microphone that calls [onRecite] with the ayah to recite from: the
 * one playing, else the first of [page].
 *
 * [page] is the page on screen; Play starts at its first ayah. The reciter name and the reciter
 * button open the reciter sheet. Expand calls [onExpand]; when that is null the bar opens the play
 * options itself in a bottom sheet.
 */
@Composable
fun AudioBar(
    feature: AudioFeature,
    page: Int,
    modifier: Modifier = Modifier,
    onExpand: (() -> Unit)? = null,
    onRecite: ((AyahRef) -> Unit)? = null,
) {
    val viewModel: AudioBarViewModel = viewModel(factory = feature.viewModelFactory)
    LaunchedEffect(Unit) { viewModel.warmUp() }
    LaunchedEffect(page) { viewModel.showPage(page) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val startChoices by viewModel.startChoices.collectAsStateWithLifecycle()
    val pageStart by viewModel.pageStart.collectAsStateWithLifecycle()
    var reciterSheetOpen by rememberSaveable { mutableStateOf(false) }
    var optionsSheetOpen by rememberSaveable { mutableStateOf(false) }

    val currentOnExpand by rememberUpdatedState(onExpand)
    val currentOnRecite by rememberUpdatedState(onRecite)
    val scope = rememberCoroutineScope()
    val canRecite = onRecite != null
    // One instance for the life of the view model, so a state change does not rebuild every button.
    val actions =
        remember(viewModel, canRecite) {
            AudioBarActions(
                onPlay = viewModel::play,
                onPause = viewModel::pause,
                onStop = viewModel::stop,
                onPrevious = viewModel::previous,
                onNext = viewModel::next,
                onRepeat = viewModel::cycleRepeat,
                onSpeed = viewModel::cycleSpeed,
                onCancelDownload = viewModel::stop,
                onOpenReciters = { reciterSheetOpen = true },
                onExpand = { currentOnExpand?.invoke() ?: run { optionsSheetOpen = true } },
                onChooseStart = viewModel::choose,
                onDismissStartChoices = viewModel::dismissStartChoices,
                onDownload = { viewModel.answerDownload(true) },
                onStream = { viewModel.answerDownload(false) },
                onRecite =
                    if (canRecite) {
                        {
                            scope.launch {
                                viewModel.reciteStart()?.let { ayah ->
                                    currentOnRecite?.invoke(ayah)
                                }
                            }
                        }
                    } else {
                        null
                    },
            )
        }
    AudioBarContent(state, startChoices, actions, modifier)

    if (reciterSheetOpen) {
        ReciterSheet(feature, page, onDismiss = { reciterSheetOpen = false })
    }
    val optionsStart = pageStart
    if (optionsSheetOpen && optionsStart != null) {
        PlayOptionsSheet(feature, optionsStart, page, onDismiss = { optionsSheetOpen = false })
    }
}

@Composable
internal fun AudioBarContent(
    state: AudioBarState,
    startChoices: List<StartChoice>,
    actions: AudioBarActions,
    modifier: Modifier = Modifier,
) {
    val field = Awn.field
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(AwnShapes.control)
                .background(field.barBackground)
                .padding(horizontal = AwnSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (state) {
            is AudioBarState.Idle -> IdleBar(state, startChoices, actions)
            is AudioBarState.Active -> ActiveBar(state, actions)
            is AudioBarState.Downloading -> DownloadingBar(state, actions)
            is AudioBarState.Confirm -> ConfirmBar(state, actions)
        }
    }
}

@Composable
private fun RowScope.IdleBar(
    state: AudioBarState.Idle,
    choices: List<StartChoice>,
    actions: AudioBarActions,
) {
    val tint = Awn.field.onBar
    Box {
        IconAction(
            R.drawable.ic_listen_play,
            stringResource(R.string.audio_play),
            actions.onPlay,
            tint = tint,
        )
        StartFromMenu(choices, actions)
    }
    Text(
        text = idleText(state),
        style = AwnTypography.bodyStrong,
        color = tint,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            Modifier.weight(1f)
                .heightIn(min = AwnSpacing.touchTarget)
                .clickable(role = Role.Button, onClick = actions.onOpenReciters)
                .wrapContentHeight(Alignment.CenterVertically)
                .padding(horizontal = AwnSpacing.small),
    )
    IconAction(
        R.drawable.ic_listen_reciter,
        stringResource(R.string.audio_choose_reciter),
        actions.onOpenReciters,
        tint = tint,
    )
    IconAction(
        R.drawable.ic_listen_expand,
        stringResource(R.string.audio_expand),
        actions.onExpand,
        tint = tint,
    )
    ReciteAction(actions)
}

/**
 * The microphone at the end of the bar; absent when the screen gives no recite action. The touch
 * target is a full cell like the other buttons; the ochre square inside is smaller, so it sits in
 * the bar with the same room around it as the other buttons' icons and is not cut by its edge.
 */
@Composable
private fun ReciteAction(actions: AudioBarActions, modifier: Modifier = Modifier) {
    val onRecite = actions.onRecite ?: return
    Box(
        modifier =
            modifier
                .defaultMinSize(AwnSpacing.touchTarget, AwnSpacing.touchTarget)
                .clickable(role = Role.Button, onClick = onRecite),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier.size(AwnSpacing.touchTarget - AwnSpacing.small)
                    .clip(AwnShapes.control)
                    .background(AwnColors.ochre),
            contentAlignment = Alignment.Center,
        ) {
            ListenIcon(
                icon = R.drawable.ic_listen_mic,
                description = stringResource(R.string.audio_recite),
                tint = AwnColors.ink,
            )
        }
    }
}

@Composable
private fun idleText(state: AudioBarState.Idle): String =
    when {
        state.problem != null -> problemText(state.problem)
        state.hasError -> stringResource(R.string.audio_error)
        else -> state.reciterName
    }

/** "Download 3.2 MB?" with a Download and a Stream action, in place of the buttons. */
@Composable
private fun RowScope.ConfirmBar(state: AudioBarState.Confirm, actions: AudioBarActions) {
    val tint = Awn.field.onBar
    IconAction(
        R.drawable.ic_listen_close,
        stringResource(R.string.audio_cancel),
        actions.onStop,
        tint = tint,
    )
    val size = Formatter.formatShortFileSize(LocalContext.current, state.sizeBytes)
    Text(
        text = stringResource(R.string.audio_download_question, size),
        style = AwnTypography.bodyStrong,
        color = tint,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f).padding(horizontal = AwnSpacing.small),
    )
    BarTextAction(stringResource(R.string.audio_stream_action), actions.onStream, tint)
    BarTextAction(stringResource(R.string.audio_download_action), actions.onDownload, tint)
}

@Composable
private fun BarTextAction(
    label: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color,
) {
    Text(
        text = label,
        style = AwnTypography.bodyStrong,
        color = tint,
        modifier =
            Modifier.heightIn(min = AwnSpacing.touchTarget)
                .clickable(role = Role.Button, onClick = onClick)
                .wrapContentHeight(Alignment.CenterVertically)
                .padding(horizontal = AwnSpacing.small),
    )
}

@Composable
private fun StartFromMenu(choices: List<StartChoice>, actions: AudioBarActions) {
    DropdownMenu(
        expanded = choices.isNotEmpty(),
        onDismissRequest = actions.onDismissStartChoices,
    ) {
        Text(
            text = stringResource(R.string.audio_start_from),
            style = AwnTypography.small,
            modifier = Modifier.padding(horizontal = AwnSpacing.medium),
        )
        for (choice in choices) {
            DropdownMenuItem(
                text = {
                    Text(
                        text =
                            stringResource(
                                R.string.audio_start_choice,
                                choice.surahName,
                                choice.ayah.ayah,
                            ),
                        style = AwnTypography.body,
                    )
                },
                onClick = { actions.onChooseStart(choice) },
            )
        }
    }
}

@Composable
private fun RowScope.ActiveBar(state: AudioBarState.Active, actions: AudioBarActions) {
    val tint = Awn.field.onBar
    val evenly = Modifier.weight(1f)
    IconAction(
        R.drawable.ic_listen_stop,
        stringResource(R.string.audio_stop),
        actions.onStop,
        evenly,
        tint,
    )
    IconAction(
        R.drawable.ic_listen_previous,
        stringResource(R.string.audio_previous),
        actions.onPrevious,
        evenly,
        tint,
    )
    if (state.isPlaying) {
        IconAction(
            R.drawable.ic_listen_pause,
            stringResource(R.string.audio_pause),
            actions.onPause,
            evenly,
            tint,
        )
    } else {
        IconAction(
            R.drawable.ic_listen_play,
            stringResource(R.string.audio_play),
            actions.onPlay,
            evenly,
            tint,
        )
    }
    IconAction(
        R.drawable.ic_listen_next,
        stringResource(R.string.audio_next),
        actions.onNext,
        evenly,
        tint,
    )
    RepeatAndReciterButtons(state, actions)
    state.step?.let { StepCounter(it, tint) }
    ReciteAction(actions, evenly)
}

/**
 * The buttons that change how and with whom the audio plays, as evenly weighted as the rest. The
 * speed button is not here: seven buttons fill 360 dp, and the speed is in the play options.
 */
@Composable
private fun RowScope.RepeatAndReciterButtons(
    state: AudioBarState.Active,
    actions: AudioBarActions,
) {
    val tint = Awn.field.onBar
    val evenly = Modifier.weight(1f)
    ValueButton(
        icon = R.drawable.ic_listen_repeat,
        plainDescription = stringResource(R.string.audio_repeat),
        valueText = state.repeatCount?.let { repeatLabel(it) },
        valueDescriptionRes = R.string.audio_repeat_value,
        onClick = actions.onRepeat,
        modifier = evenly,
    )
    IconAction(
        R.drawable.ic_listen_reciter,
        stringResource(R.string.audio_choose_reciter),
        actions.onOpenReciters,
        evenly,
        tint,
    )
}

/** "3/7": where the cumulative drill is, read aloud as "Step 3 of 7". */
@Composable
private fun StepCounter(step: PlayStep, tint: androidx.compose.ui.graphics.Color) {
    val description = stringResource(R.string.audio_step_description, step.number, step.total)
    Text(
        text = stringResource(R.string.audio_step, step.number, step.total),
        style = AwnTypography.bodyStrong,
        color = tint,
        modifier =
            Modifier.padding(horizontal = AwnSpacing.small).semantics {
                contentDescription = description
            },
    )
}

/** A bar button that shows a small [valueText] over its icon when the value is not the default. */
@Composable
private fun ValueButton(
    @DrawableRes icon: Int,
    plainDescription: String,
    valueText: String?,
    @StringRes valueDescriptionRes: Int,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    IconAction(
        icon = icon,
        description =
            if (valueText == null) plainDescription
            else stringResource(valueDescriptionRes, valueText),
        onClick = onClick,
        modifier = modifier,
        tint = Awn.field.onBar,
        badge = valueText,
    )
}

@Composable
private fun RowScope.DownloadingBar(state: AudioBarState.Downloading, actions: AudioBarActions) {
    val tint = Awn.field.onBar
    IconAction(
        R.drawable.ic_listen_close,
        stringResource(R.string.audio_cancel),
        actions.onCancelDownload,
        tint = tint,
    )
    Column(
        modifier =
            Modifier.weight(1f).padding(horizontal = AwnSpacing.small, vertical = AwnSpacing.small),
        verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
    ) {
        Text(
            text = stringResource(R.string.audio_downloading),
            style = AwnTypography.bodyStrong,
            color = tint,
        )
        val fraction = state.totalBytes?.takeIf { it > 0L }?.let { state.doneBytes.toFloat() / it }
        ProgressTrack(fraction?.coerceIn(0f, 1f), tint)
        val total = state.totalBytes
        if (total != null) {
            val (done, all) = progressSizes(LocalContext.current, state.doneBytes, total)
            Text(
                text = stringResource(R.string.downloads_size_progress, done, all),
                style = AwnTypography.small,
                color = tint,
            )
        }
    }
}

/** The play options in a bottom sheet, for a Quran screen that does not host them itself. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayOptionsSheet(
    feature: AudioFeature,
    start: app.awn.quran.core.data.AyahRef,
    page: Int,
    onDismiss: () -> Unit,
) {
    val field = Awn.field
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = field.background,
        contentColor = field.content,
    ) {
        PlayRangePanel(feature, start, page)
    }
}
