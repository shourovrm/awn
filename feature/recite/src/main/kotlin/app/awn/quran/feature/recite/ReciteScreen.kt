package app.awn.quran.feature.recite

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.components.AwnButton
import app.awn.quran.core.ui.components.AwnSwitch
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnColors
import app.awn.quran.core.ui.theme.AwnField
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.player.AudioPlayer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The field colour that tells the user they are in Recite mode. */
val ReciteField = AwnField.Night

/** The test tag of the one primary action, which reads Start or Stop. */
const val RECITE_PRIMARY_TAG = "recite-primary"

/**
 * The Recite screen for [range]. [session] must outlive the screen (hold it in :app, for example in
 * a ViewModel) so the results survive a rotation; the screen stops listening when it leaves.
 */
@Composable
fun ReciteScreen(
    range: ReciteRange,
    session: RecitationSession,
    settings: ReciteSettings,
    audioPlayer: AudioPlayer,
    modifier: Modifier = Modifier,
) {
    AwnTheme(field = ReciteField) { ReciteContent(range, session, settings, audioPlayer, modifier) }
}

@Composable
@Suppress("LongMethod") // One screen: top bar, words, controls and two floating panels.
private fun ReciteContent(
    initialRange: ReciteRange,
    session: RecitationSession,
    settings: ReciteSettings,
    audioPlayer: AudioPlayer,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    var range by remember(initialRange) { mutableStateOf(initialRange) }
    var pickerOpen by remember { mutableStateOf(false) }
    var reviewStart by remember { mutableStateOf<Pair<Int, WordKey>?>(null) }

    val chapters by produceState(emptyList<Chapter>(), session) { value = session.chapters() }
    val phase by session.phase.collectAsState()
    val words by session.words.collectAsState()
    val wordStates by session.wordStates.collectAsState()
    val skippedWords by session.skippedWords.collectAsState()
    val hint by session.hint.collectAsState()
    val reviews by session.reviews.collectAsState()
    val speed by session.speed.collectAsState()
    val hifzEnabled by session.hifzEnabled.collectAsState()
    val hintEnabled by settings.hintEnabled.collectAsState(initial = true)

    LaunchedEffect(session) {
        session.setSpeed(settings.speed.first())
        session.setHifzEnabled(settings.hifzEnabled.first())
    }
    LaunchedEffect(session, range) { session.load(range) }
    DisposableEffect(session) { onDispose { session.stop() } }

    Column(modifier.fillMaxSize().padding(AwnSpacing.screenMargin)) {
        TopBar(
            title = rangeTitle(range, chapters),
            hifzEnabled = hifzEnabled,
            onTitleClick = { pickerOpen = true },
            onHifzChange = { enabled ->
                session.setHifzEnabled(enabled)
                scope.launch { settings.setHifzEnabled(enabled) }
            },
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            chapters.firstOrNull { it.number == range.surah }?.let { SurahBand(it) }
            ReciteWords(
                words = words,
                wordStates = wordStates,
                skippedWords = skippedWords,
                hint = hint,
                reviews = reviews,
                onFlaggedWordClick = { key -> reviewStart = key.ayah to key },
                onReviewClick = { ayah ->
                    reviews[ayah]?.firstOrNull()?.let { reviewStart = ayah to it.key }
                },
            )
        }
        Controls(
            session = session,
            phase = phase,
            speed = speed,
            showHint = hifzEnabled && hintEnabled,
            onSpeedFinished = { scope.launch { settings.setSpeed(session.speed.value) } },
            onReset = { scope.launch { session.reset() } },
        )
    }

    if (pickerOpen) {
        ReciteRangePicker(
            chapters = chapters,
            current = range,
            onConfirm = {
                range = it
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false },
        )
    }
    reviewStart?.let { (ayah, startKey) ->
        ReciteReviewSheet(
            reviews = reviews[ayah].orEmpty(),
            startKey = startKey,
            words = words,
            onListen = { key ->
                scope.launch { playWord(audioPlayer, settings.reciterId.first(), key) }
            },
            onDismiss = { reviewStart = null },
        )
    }
}

@Composable
private fun rangeTitle(range: ReciteRange, chapters: List<Chapter>): String {
    val name =
        chapters.firstOrNull { it.number == range.surah }?.nameSimple ?: range.surah.toString()
    return if (range.firstAyah == range.lastAyah) {
        stringResource(R.string.recite_range_single, name, range.firstAyah)
    } else {
        stringResource(R.string.recite_range_span, name, range.firstAyah, range.lastAyah)
    }
}

@Composable
private fun TopBar(
    title: String,
    hifzEnabled: Boolean,
    onTitleClick: () -> Unit,
    onHifzChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = AwnTypography.body,
            modifier =
                Modifier.defaultMinSize(minHeight = AwnSpacing.touchTarget)
                    .clickable(onClick = onTitleClick)
                    .padding(vertical = AwnSpacing.small),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.recite_hifz), style = AwnTypography.body)
            AwnSwitch(checked = hifzEnabled, onCheckedChange = onHifzChange)
        }
    }
}

@Composable
private fun SurahBand(chapter: Chapter) {
    Box(
        Modifier.fillMaxWidth().background(AwnColors.ochre).padding(AwnSpacing.small),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = chapter.nameArabic, style = AwnTypography.lead, color = AwnColors.ink)
    }
}

@Composable
private fun Controls(
    session: RecitationSession,
    phase: RecitationPhase,
    speed: Float,
    showHint: Boolean,
    onSpeedFinished: () -> Unit,
    onReset: () -> Unit,
) {
    val permission = rememberMicrophonePermission()
    val access = permission.access
    val status by session.status.collectAsState()

    Column(Modifier.fillMaxWidth().padding(top = AwnSpacing.medium)) {
        Text(text = statusText(status, phase, session), style = AwnTypography.body)
        if (access != MicrophoneAccess.Granted) {
            val message =
                if (access == MicrophoneAccess.CanAsk) R.string.recite_microphone_rationale
                else R.string.recite_microphone_denied
            Text(
                text = stringResource(message),
                style = AwnTypography.small,
                color = Awn.field.tentativeWord,
            )
        }
        Legend()
        SpeedSlider(
            value = speed,
            onValueChange = session::setSpeed,
            onValueChangeFinished = onSpeedFinished,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showHint) TextAction(stringResource(R.string.recite_hint), session::requestHint)
            if (phase != RecitationPhase.Listening && phase != RecitationPhase.Idle) {
                TextAction(stringResource(R.string.recite_reset), onReset)
            }
        }
        val listening = phase == RecitationPhase.Listening
        val primaryLabel =
            when {
                listening -> R.string.recite_stop
                access == MicrophoneAccess.Granted -> R.string.recite_start
                access == MicrophoneAccess.CanAsk -> R.string.recite_microphone_allow
                else -> R.string.recite_microphone_open_settings
            }
        AwnButton(
            label = stringResource(primaryLabel),
            onClick = {
                when {
                    listening -> session.stop()
                    access == MicrophoneAccess.Granted -> session.start()
                    access == MicrophoneAccess.CanAsk -> permission.requestAccess()
                    else -> permission.openAppSettings()
                }
            },
            modifier = Modifier.fillMaxWidth().testTag(RECITE_PRIMARY_TAG),
        )
    }
}

/** Four coloured dots with one-word labels: what each colour on the page means. */
@Composable
private fun Legend() {
    val field = Awn.field
    Row(
        Modifier.fillMaxWidth().padding(vertical = AwnSpacing.small),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        LegendEntry(field.confirmedWord, stringResource(R.string.recite_word_confirmed))
        LegendEntry(field.tentativeWord, stringResource(R.string.recite_legend_mistake))
        LegendEntry(field.wrongWord, stringResource(R.string.recite_verdict_skipped))
        LegendEntry(field.hiddenWordBlock, stringResource(R.string.recite_word_hidden))
    }
}

@Composable
private fun LegendEntry(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(AwnSpacing.small).clip(CircleShape).background(color))
        Text(
            text = label,
            style = AwnTypography.small,
            modifier = Modifier.padding(start = AwnSpacing.small / 2),
        )
    }
}

@Composable
private fun TextAction(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = AwnTypography.bodyStrong,
        modifier =
            Modifier.defaultMinSize(minHeight = AwnSpacing.touchTarget)
                .clickable(onClick = onClick)
                .padding(vertical = AwnSpacing.small),
    )
}

@Composable
private fun statusText(
    status: ReciteStatus?,
    phase: RecitationPhase,
    session: RecitationSession,
): String =
    when (status) {
        is ReciteStatus.Skipped ->
            stringResource(R.string.recite_status_skipped, status.ayah, status.wordPosition)
        is ReciteStatus.Inserted ->
            stringResource(R.string.recite_status_inserted, status.ayah, status.wordPosition)
        is ReciteStatus.Anchored ->
            stringResource(R.string.recite_status_anchored, status.ayah, status.wordPosition)
        null ->
            when (phase) {
                RecitationPhase.Idle -> stringResource(R.string.recite_status_ready)
                RecitationPhase.Listening -> stringResource(R.string.recite_status_listening)
                RecitationPhase.Stopped -> stringResource(R.string.recite_status_stopped)
                RecitationPhase.Error ->
                    if (session.lastFailure == null) stringResource(R.string.recite_status_no_text)
                    else stringResource(R.string.recite_status_no_microphone)
            }
    }
