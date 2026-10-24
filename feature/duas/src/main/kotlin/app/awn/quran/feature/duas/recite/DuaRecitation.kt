package app.awn.quran.feature.duas.recite

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.WordKey
import app.awn.quran.feature.recite.RecitationPhase
import app.awn.quran.feature.recite.ReciteController
import app.awn.quran.feature.recite.SpeechState
import app.awn.quran.feature.recite.stopMicrophone
import app.awn.quran.tracker.WordProblem
import app.awn.quran.tracker.WordState
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

    /** Loads exactly [ayahs] and waits for the microphone; the words are hidden when asked. */
    fun begin(ayahs: List<AyahRef>, hideWords: Boolean)

    fun startMicrophone()

    fun stopMicrophone()

    fun setHideWords(hide: Boolean)

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

    override fun begin(ayahs: List<AyahRef>, hideWords: Boolean) =
        controller.practiceAyahs(ayahs, hideWords)

    override fun startMicrophone() = controller.session.start()

    override fun stopMicrophone() = controller.stopMicrophone()

    override fun setHideWords(hide: Boolean) = controller.setHideWords(hide)

    override fun end() = controller.exit()
}
