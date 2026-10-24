package app.awn.quran.feature.duas

import androidx.compose.runtime.Composable
import app.awn.quran.core.data.MyWords
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.ReciterRepository
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.TranslationId
import app.awn.quran.core.data.TranslationRepository
import app.awn.quran.core.data.WordKey
import app.awn.quran.feature.duas.content.DuaCatalogue
import app.awn.quran.feature.duas.content.TopicCatalogue
import app.awn.quran.feature.duas.data.DuaProgressStore
import app.awn.quran.feature.duas.recite.DuaRecitation
import app.awn.quran.player.AudioPlayer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Everything the Duas screens need from the rest of the app, handed over once by :app. */
data class DuasDependencies(
    /** Reads the bundled collection; called off the main thread. */
    val loadCatalogue: suspend () -> DuaCatalogue,
    val progress: DuaProgressStore,
    val quranText: QuranTextRepository,
    val translations: TranslationRepository,
    /** The translations ticked in the Quran screen's settings, in the order they are shown. */
    val enabledTranslations: Flow<List<TranslationId>>,
    /** The print whose page numbers "Open in the Mushaf" uses. */
    val pageScript: Flow<ScriptId>,
    val audioPlayer: AudioPlayer,
    val reciters: ReciterRepository,
    val selectedReciterId: Flow<String>,
    val recitation: DuaRecitation,
    /** Plays one Quran word in the voice of the word reciter chosen in Settings. */
    val playWord: suspend (WordKey) -> Unit,
    /** The user's own word list; null hides "Add to my words". */
    val myWords: MyWords?,
    /**
     * Wraps the recite controls: the app shows the one-time download card instead of [content]
     * while the speech files are missing, and calls [onCancel] when the user leaves that card.
     */
    val reciteGate: @Composable (onCancel: () -> Unit, content: @Composable () -> Unit) -> Unit,
    /** The app's reciter sheet; choosing a reciter there changes [selectedReciterId]. */
    val reciterPicker: @Composable (onDismiss: () -> Unit) -> Unit = {},
    /** Whether the word-by-word pronunciation download is on the phone. */
    val pronunciation: Flow<PronunciationState> = flowOf(PronunciationState.Installed),
    /** Starts the pronunciation download. */
    val requestPronunciation: () -> Unit = {},
    /** Reads the bundled Ayahs by topic list; called off the main thread. */
    val loadTopics: suspend () -> TopicCatalogue = { TopicCatalogue(emptyList(), emptyList()) },
)

/** Where the word-by-word pronunciation download stands. */
enum class PronunciationState {
    Installed,
    Downloading,
    Missing,
}
