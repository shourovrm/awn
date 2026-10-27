package app.awn.quran.feature.duas.recite

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.WordKey
import app.awn.quran.feature.recite.RecitationPhase
import app.awn.quran.feature.recite.ReciteController
import app.awn.quran.feature.recite.ReciteHint
import app.awn.quran.feature.recite.SpeechState
import app.awn.quran.feature.recite.stopMicrophone
import app.awn.quran.tracker.WordProblem
import app.awn.quran.tracker.WordState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * What a dua card needs from recitation checking. The real implementation wraps the controller of
 * the Quran screen; a test passes its own because the real one needs the speech files.
 */
interface DuaRecitation {
    val wordStates: StateFlow<Map<WordKey, WordState>>
    val wordProblems: StateFlow<Map<WordKey, WordProblem>>
    val phase: StateFlow<RecitationPhase>
    val speechState: StateFlow<SpeechState>

    /** The first letter shown for a hidden word, or null when no letter is shown. */
    val hint: StateFlow<ReciteHint?>

    /** The hidden word the user is on: the next one to say, or to reveal. */
    val currentWord: StateFlow<WordKey?>

    /** The saved first-letter setting: when on, the first reveal tap shows only the letter. */
    val hintEnabled: Flow<Boolean>

    /** Loads exactly [ayahs] and waits for the microphone; the words are hidden when asked. */
    fun begin(ayahs: List<AyahRef>, hideWords: Boolean)

    fun startMicrophone()

    fun stopMicrophone()

    fun setHideWords(hide: Boolean)

    /** Shows the next hidden word without the microphone; see [RecitationSession]. */
    fun revealNextWord(showsFirstLetterFirst: Boolean)

    /** Shows every hidden word of the ayah being recited; see [RecitationSession.revealAyah]. */
    fun revealAyah()

    /** Stops the microphone and forgets the loaded words. */
    fun end()
}

/** Checks a dua with the same [ReciteController] that checks the Quran screen. */
class ControllerDuaRecitation(private val controller: ReciteController) : DuaRecitation {
    override val wordStates: StateFlow<Map<WordKey, WordState>> = controller.session.wordStates
    override val wordProblems: StateFlow<Map<WordKey, WordProblem>> =
        controller.session.wordProblems
    override val phase: StateFlow<RecitationPhase> = controller.session.phase
    override val speechState: StateFlow<SpeechState> = controller.speechState
    override val hint: StateFlow<ReciteHint?> = controller.session.hint
    override val currentWord: StateFlow<WordKey?> = controller.session.currentWord
    override val hintEnabled: Flow<Boolean> = controller.settings.hintEnabled

    override fun begin(ayahs: List<AyahRef>, hideWords: Boolean) =
        controller.practiceAyahs(ayahs, hideWords)

    override fun startMicrophone() = controller.session.start()

    override fun stopMicrophone() = controller.stopMicrophone()

    override fun setHideWords(hide: Boolean) = controller.setHideWords(hide)

    override fun revealNextWord(showsFirstLetterFirst: Boolean) =
        controller.session.revealNextWord(showsFirstLetterFirst)

    override fun revealAyah() = controller.session.revealAyah()

    override fun end() = controller.exit()
}
