package app.awn.quran.feature.duas

import androidx.compose.runtime.Composable
import app.awn.quran.core.data.CatalogueItem
import app.awn.quran.core.data.InstalledPack
import app.awn.quran.core.data.MyWords
import app.awn.quran.core.data.PackRepository
import app.awn.quran.core.data.PackSize
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.ReciterRepository
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.TranslationId
import app.awn.quran.core.data.TranslationRepository
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.data.packs.PackEntry
import app.awn.quran.feature.duas.content.DuaCatalogue
import app.awn.quran.feature.duas.content.TopicCatalogue
import app.awn.quran.feature.duas.data.DuaProgressStore
import app.awn.quran.feature.duas.recite.DuaRecitation
import app.awn.quran.feature.read.settings.ReadSettings
import app.awn.quran.player.AudioPlayer
import java.io.File
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
    /**
     * The Quran screen's settings. The cards draw their words with the script chosen there, so a
     * dua looks as it does in the Mushaf.
     */
    val readSettings: Flow<ReadSettings> = flowOf(ReadSettings(script = ScriptId.QpcHafs)),
    /** Where the Mushaf's page fonts are installed. */
    val packs: PackRepository = NoPacks,
)

/** Stands in for the pack store where there is none; no font is installed. */
internal object NoPacks : PackRepository {
    override fun installed(): Flow<List<InstalledPack>> = flowOf(emptyList())

    override suspend fun manifest(kind: String): List<PackEntry> = emptyList()

    override suspend fun sizeOf(id: String): PackSize? = null

    override suspend fun catalogue(): List<CatalogueItem> = emptyList()

    override fun freeBytes(): Long = 0L

    override suspend fun install(entry: PackEntry, onProgress: (Float) -> Unit) = Unit

    override suspend fun remove(id: String) = Unit

    override fun fontFile(script: ScriptId, page: Int): File? = null

    override fun learnAudioFile(clipId: String): File? = null
}

/** Where the word-by-word pronunciation download stands. */
enum class PronunciationState {
    Installed,
    Downloading,
    Missing,
}
