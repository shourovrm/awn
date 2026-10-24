package app.awn.quran.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.search.SearchHit
import app.awn.quran.core.data.search.SearchSource
import app.awn.quran.feature.recite.SpeechPreparation
import app.awn.quran.tracker.AyahFinder
import app.awn.quran.tracker.AyahMatch
import app.awn.quran.tracker.TrackerInput
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MILLIS = 5_000L

/** A microphone whose [nextBlock] returns null once [stop] was called, from any thread. */
interface StoppableInput : TrackerInput {
    fun stop()
}

enum class VoicePhase {
    Off,

    /** The panel is open and waits for the microphone permission and the speech files. */
    Asking,
    GettingReady,
    Listening,
    Finished,

    /** The speech model or the microphone could not be used. */
    Failed,
}

/** [rows] are the ayahs the recitation may come from, best first. */
data class VoiceSearchUiState(
    val phase: VoicePhase = VoicePhase.Off,
    val rows: List<SearchRow> = emptyList(),
)

/**
 * Searches the Quran by recitation: the user recites a few seconds and the ayahs it may come from
 * are listed while they recite. The speech model is shared with recite, so it is only released here
 * when recite is not using it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceSearchViewModel(
    private val speech: SpeechPreparation,
    private val openMicrophone: () -> StoppableInput,
    private val quranText: QuranTextRepository,
    private val script: ScriptId,
    private val isReciteActive: () -> Boolean,
    // The microphone read blocks its thread, so listening never runs on the main thread.
    private val listeningDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val phase = MutableStateFlow(VoicePhase.Off)
    private val matches = MutableStateFlow<List<AyahMatch>>(emptyList())
    private var listeningJob: Job? = null
    @Volatile private var microphone: StoppableInput? = null

    // Built from the reference text on the first search (about 650,000 phonemes) and kept.
    @Volatile private var finder: AyahFinder? = null

    val state: StateFlow<VoiceSearchUiState> =
        combine(phase, matches.mapLatest(::rowsFor)) { currentPhase, rows ->
                VoiceSearchUiState(
                    currentPhase,
                    if (currentPhase == VoicePhase.Off) emptyList() else rows,
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                VoiceSearchUiState(),
            )

    /** Opens the panel; the screen starts listening once the microphone and the model are there. */
    fun open() {
        if (phase.value == VoicePhase.Off) phase.value = VoicePhase.Asking
    }

    fun startListening() {
        if (listeningJob?.isActive == true) return
        matches.value = emptyList()
        phase.value = VoicePhase.GettingReady
        listeningJob = viewModelScope.launch(listeningDispatcher) { listen() }
    }

    /** Ends listening now; the matches found so far stay on screen. */
    fun stopListening() {
        microphone?.stop()
    }

    fun close() {
        microphone?.stop()
        listeningJob?.cancel()
        phase.value = VoicePhase.Off
        matches.value = emptyList()
    }

    override fun onCleared() {
        microphone?.stop()
    }

    // Closing the panel while this runs moves the phase to Off; the compare-and-set calls keep a
    // late step from opening it again.
    private fun listen() {
        try {
            val prepared = speech.awaitReady()
            val ayahFinder = finder ?: AyahFinder.from(prepared.referencePhonemes)
            finder = ayahFinder
            val input = openMicrophone()
            microphone = input
            if (!phase.compareAndSet(VoicePhase.GettingReady, VoicePhase.Listening)) return
            prepared.newPhonemeSource().use { source ->
                VoiceListening(input, source, ayahFinder::find).run { found ->
                    matches.value = found
                }
            }
            phase.compareAndSet(VoicePhase.Listening, VoicePhase.Finished)
        } catch (problem: IllegalStateException) {
            // Preparing failed (no model, or it could not load) or the speech process went away.
            phase.compareAndSet(VoicePhase.GettingReady, VoicePhase.Failed)
            phase.compareAndSet(VoicePhase.Listening, VoicePhase.Failed)
            Log.w(LOG_TAG, "Voice search failed.", problem)
        } finally {
            microphone?.stop()
            microphone = null
            if (!isReciteActive()) speech.releaseAfterGrace()
        }
    }

    private suspend fun rowsFor(found: List<AyahMatch>): List<SearchRow> {
        if (found.isEmpty()) return emptyList()
        val names = quranText.chapters().associate { it.number to it.nameSimple }
        return found.map { match ->
            val ayah = AyahRef(match.surah, match.ayah)
            val text = quranText.wordsOfAyah(ayah).joinToString(" ") { it.textUthmani }
            SearchRow(
                hit = SearchHit(ayah, SearchSource.Arabic, text, matches = emptyList()),
                surahName = names[match.surah].orEmpty(),
                page = quranText.pageOf(ayah, script),
            )
        }
    }

    private companion object {
        const val LOG_TAG = "AwnVoiceSearch"
    }
}
