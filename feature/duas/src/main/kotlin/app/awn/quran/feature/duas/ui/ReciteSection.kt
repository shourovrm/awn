package app.awn.quran.feature.duas.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.feature.duas.DuasDependencies
import app.awn.quran.feature.duas.card.DuaCardData
import app.awn.quran.feature.duas.content.Dua
import app.awn.quran.feature.recite.RecitationPhase
import kotlinx.coroutines.launch

/** What the Recite section reports back to the screen. */
internal class ReciteSectionActions(
    val onToggleHideWords: () -> Unit,
    val onReviewedWordChange: (WordKey?) -> Unit,
    val onCleanRecitation: () -> Unit,
    /** The user left the download offer without downloading. */
    val onCancel: () -> Unit,
)

/**
 * The Recite controls of one dua. While it is on screen the dua's ayahs (and no others) are loaded
 * for checking; leaving the screen or the dua ends the recitation. The app's gate shows the
 * one-time download offer in place of the controls until the speech files are on the phone.
 */
@Composable
internal fun ReciteSection(
    dua: Dua,
    card: DuaCardData?,
    dependencies: DuasDependencies,
    cleanCount: Int,
    isHidingWords: Boolean,
    reviewedWord: WordKey?,
    actions: ReciteSectionActions,
    modifier: Modifier = Modifier,
) {
    dependencies.reciteGate(actions.onCancel) {
        ReciteControls(
            dua = dua,
            card = card,
            dependencies = dependencies,
            cleanCount = cleanCount,
            isHidingWords = isHidingWords,
            reviewedWord = reviewedWord,
            actions = actions,
            modifier = modifier,
        )
    }
}

@Composable
private fun ReciteControls(
    dua: Dua,
    card: DuaCardData?,
    dependencies: DuasDependencies,
    cleanCount: Int,
    isHidingWords: Boolean,
    reviewedWord: WordKey?,
    actions: ReciteSectionActions,
    modifier: Modifier,
) {
    val recitation = dependencies.recitation
    val scope = rememberCoroutineScope()
    val wordStates by recitation.wordStates.collectAsState()
    val wordProblems by recitation.wordProblems.collectAsState()
    val phase by recitation.phase.collectAsState()
    val speechState by recitation.speechState.collectAsState()
    val duaWordKeys =
        remember(card) {
            card?.words.orEmpty().filter { it.isDuaWord }.map { it.word.key }.toSet()
        }
    val isClean = isCleanRecitation(duaWordKeys, wordStates)

    // The words start hidden when the user had them hidden on the dua before.
    DisposableEffect(dua.id) {
        recitation.begin(dua.ayahs, isHidingWords)
        onDispose { recitation.end() }
    }
    LaunchedEffect(isClean, dua.id) {
        if (isClean) {
            recitation.stopMicrophone()
            actions.onCleanRecitation()
        }
    }
    // A new try clears the card; a stopped one opens the first flagged word.
    LaunchedEffect(phase) {
        when (phase) {
            RecitationPhase.Listening -> actions.onReviewedWordChange(null)
            RecitationPhase.Stopped ->
                actions.onReviewedWordChange(wordProblems.keys.firstOrNull { it in duaWordKeys })
            else -> Unit
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
        val reviewedProblem = reviewedWord?.let { wordProblems[it] }
        val reviewedArabic = card?.words?.firstOrNull { it.word.key == reviewedWord }?.word
        if (reviewedWord != null && reviewedProblem != null && reviewedArabic != null) {
            WordReviewCard(
                arabic = reviewedArabic.textUthmani,
                problem = reviewedProblem,
                onPlayWord = { scope.launch { dependencies.playWord(reviewedWord) } },
                onClose = { actions.onReviewedWordChange(null) },
            )
        }
        RecitePanel(
            state = RecitePanelState(speechState, phase, isClean, cleanCount, isHidingWords),
            actions =
                RecitePanelActions(
                    onToggleHideWords = actions.onToggleHideWords,
                    onStartMicrophone = recitation::startMicrophone,
                    onStopMicrophone = recitation::stopMicrophone,
                ),
        )
    }
}
