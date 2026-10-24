package app.awn.quran.feature.duas

import androidx.compose.runtime.Composable
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.MyWords
import app.awn.quran.core.data.Reciter
import app.awn.quran.core.data.RemovedMyWord
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.TranslationId
import app.awn.quran.core.data.TranslationText
import app.awn.quran.core.data.Word
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.data.fakes.FakeQuranTextRepository
import app.awn.quran.core.data.fakes.FakeReciterRepository
import app.awn.quran.core.data.fakes.FakeTranslationRepository
import app.awn.quran.feature.duas.content.AyahPassage
import app.awn.quran.feature.duas.content.Dua
import app.awn.quran.feature.duas.content.DuaCatalogue
import app.awn.quran.feature.duas.content.DuaGroup
import app.awn.quran.feature.duas.content.DuaOpening
import app.awn.quran.feature.duas.content.LocalName
import app.awn.quran.feature.duas.content.Topic
import app.awn.quran.feature.duas.content.TopicCatalogue
import app.awn.quran.feature.duas.content.TopicCategory
import app.awn.quran.feature.duas.content.TopicPart
import app.awn.quran.feature.duas.data.CardLayers
import app.awn.quran.feature.duas.data.DuaProgress
import app.awn.quran.feature.duas.data.DuaProgressStore
import app.awn.quran.feature.duas.recite.DuaRecitation
import app.awn.quran.feature.recite.RecitationPhase
import app.awn.quran.feature.recite.SpeechState
import app.awn.quran.player.fakes.FakeAudioPlayer
import app.awn.quran.tracker.WordProblem
import app.awn.quran.tracker.WordState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * A small collection for the tests: two groups and three duas.
 *
 * - `d1` is the last three words of 2:201 (the ayah starts with three words that are not the dua).
 * - `d2` runs over 20:25 and 20:26 and is a prophet's dua.
 * - `d3` is the whole of 14:41.
 */
object DuasFixtures {
    /** One category, one topic with two parts over ayahs the fake Quran text holds. */
    val topics =
        TopicCatalogue(
            categories = listOf(TopicCategory("family", LocalName("Family", "পরিবার"))),
            topics =
                listOf(
                    Topic(
                        key = "parents",
                        categoryKey = "family",
                        name = LocalName("Parents", "মা-বাবা"),
                        parts =
                            listOf(
                                TopicPart(
                                    "kindness",
                                    LocalName("Be good to your parents", "সদাচরণ"),
                                    listOf(AyahPassage(14, 41, 41)),
                                ),
                                TopicPart(
                                    "world",
                                    LocalName("Good in both worlds", "দুই জগতের কল্যাণ"),
                                    listOf(AyahPassage(2, 201, 201)),
                                ),
                            ),
                        duasGroupKey = "parents",
                    ),
                ),
        )

    val englishTranslation = TranslationId("en_saheeh")

    val catalogue =
        DuaCatalogue(
            groups =
                listOf(
                    DuaGroup(
                        "good",
                        LocalName("Good in this world and the next", "দুনিয়া ও আখিরাতের ভালো"),
                    ),
                    DuaGroup("parents", LocalName("Parents and family", "মা-বাবা ও পরিবার")),
                ),
            duas =
                listOf(
                    Dua(
                        "d1",
                        WordKey(2, 201, 4),
                        WordKey(2, 201, 6),
                        "good",
                        DuaOpening.Rabbana,
                        prophetKey = null,
                        isStarter = true,
                    ),
                    Dua(
                        "d2",
                        WordKey(20, 25, 1),
                        WordKey(20, 26, 2),
                        "good",
                        DuaOpening.Rabbi,
                        prophetKey = "musa",
                        isStarter = false,
                    ),
                    Dua(
                        "d3",
                        WordKey(14, 41, 1),
                        WordKey(14, 41, 3),
                        "parents",
                        DuaOpening.Rabbana,
                        prophetKey = "ibrahim",
                        isStarter = false,
                    ),
                ),
            prophets =
                mapOf(
                    "musa" to LocalName("Musa", "মুসা"),
                    "ibrahim" to LocalName("Ibrahim", "ইব্রাহিম"),
                ),
        )

    val d1: Dua = catalogue.duas[0]
    val d2: Dua = catalogue.duas[1]
    val d3: Dua = catalogue.duas[2]

    private val arabicByAyah =
        mapOf(
            AyahRef(2, 201) to
                listOf("وَمِنْهُم", "مَّن", "يَقُولُ", "رَبَّنَآ", "ءَاتِنَا", "فِى"),
            AyahRef(20, 25) to listOf("رَبِّ", "ٱشْرَحْ"),
            AyahRef(20, 26) to listOf("لِى", "صَدْرِى"),
            AyahRef(14, 41) to listOf("رَبَّنَا", "ٱغْفِرْ", "لِى"),
        )

    private val englishByAyah =
        mapOf(
            AyahRef(2, 201) to "Our Lord, give us good in this world.",
            AyahRef(20, 25) to "He said, My Lord, expand for me my chest.",
            AyahRef(20, 26) to "And ease for me my task.",
            AyahRef(14, 41) to "Our Lord, forgive me and my parents.",
        )

    private fun chapter(number: Int, name: String) =
        Chapter(
            number,
            "س$number",
            name,
            nameBangla = null,
            versesCount = 286,
            revelationPlace = "madinah",
            bismillahPre = true,
        )

    fun quranText() =
        FakeQuranTextRepository(
            chapterList =
                listOf(chapter(2, "Al-Baqarah"), chapter(14, "Ibrahim"), chapter(20, "Taha")),
            wordsByAyah =
                arabicByAyah
                    .mapValues { (ayah, texts) ->
                        texts.mapIndexed { index, text ->
                            Word(
                                WordKey(ayah.surah, ayah.ayah, index + 1),
                                text,
                                codeV1 = null,
                                codeV2 = null,
                            )
                        }
                    }
                    .toMutableMap(),
            pageByAyah =
                mutableMapOf(AyahRef(2, 201) to 31, AyahRef(20, 25) to 313, AyahRef(14, 41) to 262),
        )

    fun translations(): FakeTranslationRepository {
        val repository = FakeTranslationRepository()
        repository.texts[englishTranslation] =
            englishByAyah
                .mapValues { (ayah, text) -> TranslationText(ayah, ayah.ayah, ayah.ayah, text) }
                .toMutableMap()
        repository.wordMeanings["en" to AyahRef(2, 201)] =
            listOf("and among them", "who", "says", "Our Lord", "give us", "in")
        repository.wordTransliterations[AyahRef(2, 201)] =
            listOf("wa-minhum", "man", "yaqulu", "rabbana", "atina", "fi")
        return repository
    }

    fun reciters() =
        FakeReciterRepository(
            reciterList =
                mutableListOf(
                    Reciter(
                        "alafasy",
                        "Mishary Alafasy",
                        "{SSS}.mp3",
                        "{SSS}{AAA}.mp3",
                        true,
                        null,
                    ),
                ),
        )
}

/** Saved ids and clean-recitation counts kept in memory. */
class InMemoryDuaProgressStore(initial: DuaProgress = DuaProgress()) : DuaProgressStore {
    private val current = MutableStateFlow(initial)

    override val progress: Flow<DuaProgress> = current

    override suspend fun toggleSaved(duaId: String) {
        current.update { state ->
            val saved =
                if (duaId in state.savedIds) state.savedIds - duaId else state.savedIds + duaId
            state.copy(savedIds = saved)
        }
    }

    override suspend fun setLayers(layers: CardLayers) {
        current.update { state -> state.copy(layers = layers) }
    }

    override suspend fun recordCleanRecitation(duaId: String) {
        current.update { state ->
            state.copy(
                cleanRecitations = state.cleanRecitations + (duaId to state.cleanCount(duaId) + 1),
            )
        }
    }
}

/** A recitation that records what the card asks and lets a test set what the checker reports. */
class FakeDuaRecitation : DuaRecitation {
    override val wordStates = MutableStateFlow<Map<WordKey, WordState>>(emptyMap())
    override val wordProblems = MutableStateFlow<Map<WordKey, WordProblem>>(emptyMap())
    override val phase = MutableStateFlow(RecitationPhase.Idle)
    override val speechState: MutableStateFlow<SpeechState> = MutableStateFlow(SpeechState.Ready)

    val begun = mutableListOf<Pair<List<AyahRef>, Boolean>>()
    val hideCalls = mutableListOf<Boolean>()
    var startCount = 0
    var stopCount = 0
    var endCount = 0

    override fun begin(ayahs: List<AyahRef>, hideWords: Boolean) {
        begun += ayahs to hideWords
    }

    override fun startMicrophone() {
        startCount++
        phase.value = RecitationPhase.Listening
    }

    override fun stopMicrophone() {
        stopCount++
        phase.value = RecitationPhase.Stopped
    }

    override fun setHideWords(hide: Boolean) {
        hideCalls += hide
    }

    override fun end() {
        endCount++
    }
}

/** The user's word list in memory. */
class FakeMyWords : MyWords {
    private val added = MutableStateFlow<Set<WordKey>>(emptySet())

    val addedWords: Set<WordKey>
        get() = added.value

    override fun isAdded(word: WordKey): Flow<Boolean> = added.map { word in it }

    override suspend fun add(word: WordKey) {
        added.update { it + word }
    }

    override suspend fun remove(word: WordKey): RemovedMyWord {
        added.update { it - word }
        return RemovedMyWord { added.update { it + word } }
    }
}

/** Everything a Duas screen needs, with the fakes kept in fields so a test can look at them. */
class DuasTestEnvironment(
    translationsTicked: List<TranslationId> = listOf(DuasFixtures.englishTranslation),
    initialProgress: DuaProgress = DuaProgress(),
) {
    val audioPlayer = FakeAudioPlayer()
    val recitation = FakeDuaRecitation()
    val myWords = FakeMyWords()
    val progressStore = InMemoryDuaProgressStore(initialProgress)
    val playedWords = mutableListOf<WordKey>()
    val quranText = DuasFixtures.quranText()
    val translations = DuasFixtures.translations()
    val enabledTranslations = MutableStateFlow(translationsTicked)

    /** Replaces the wrapper around the recite controls; by default the controls show directly. */
    var reciteGate: @Composable (onCancel: () -> Unit, content: @Composable () -> Unit) -> Unit =
        { _, content ->
            content()
        }

    val pronunciation = MutableStateFlow(PronunciationState.Installed)
    var pronunciationRequests = 0

    fun dependencies(): DuasDependencies =
        DuasDependencies(
            loadCatalogue = { DuasFixtures.catalogue },
            progress = progressStore,
            quranText = quranText,
            translations = translations,
            enabledTranslations = enabledTranslations,
            pageScript = flowOf(ScriptId.V2),
            audioPlayer = audioPlayer,
            reciters = DuasFixtures.reciters(),
            selectedReciterId = flowOf("alafasy"),
            recitation = recitation,
            playWord = { playedWords += it },
            myWords = myWords,
            reciteGate = { onCancel, content -> reciteGate(onCancel, content) },
            reciterPicker = { androidx.compose.material3.Text(RECITER_PICKER_TEXT) },
            pronunciation = pronunciation,
            requestPronunciation = { pronunciationRequests++ },
            loadTopics = { DuasFixtures.topics },
        )
}

/** What the test reciter picker shows, standing in for the app's reciter sheet. */
const val RECITER_PICKER_TEXT = "Reciter sheet"
