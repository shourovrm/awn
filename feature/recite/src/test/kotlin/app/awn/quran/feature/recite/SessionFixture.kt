package app.awn.quran.feature.recite

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Word
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.data.fakes.FakeProgressEvents
import app.awn.quran.core.data.fakes.FakeQuranTextRepository
import app.awn.quran.tracker.PhonemeEvent
import app.awn.quran.tracker.PhonemeSource
import app.awn.quran.tracker.ReferenceWord
import app.awn.quran.tracker.TrackerEvent
import app.awn.quran.tracker.TrackerInput
import app.awn.quran.tracker.WordKeyLike
import app.awn.quran.tracker.WordState
import app.awn.quran.tracker.fakes.FakeReferencePhonemes
import app.awn.quran.tracker.fakes.FakeTracker
import app.awn.quran.tracker.fakes.FakeTrackerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/** Gives out [blockCount] blocks of silence and then reports the end of the input. */
class ScriptedInput(private var blockCount: Int) : TrackerInput {
    override fun nextBlock(): FloatArray? = if (blockCount-- > 0) FloatArray(1) else null
}

/** Reports one phoneme per block so the tracker is called once per block. */
class OnePhonemePerBlockSource : PhonemeSource {
    override fun accept(block: FloatArray): List<PhonemeEvent> = listOf(PhonemeEvent("a", 0f, 1f))

    override fun finish(): List<PhonemeEvent> = emptyList()

    override fun close() = Unit
}

/** Surah 1 with ayah 1 of two words and ayah 2 of one word. */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionFixture(inputBlocks: Int = 1) {
    val first = WordKey(1, 1, 1)
    val second = WordKey(1, 1, 2)
    val third = WordKey(1, 2, 1)
    val range = ReciteRange(surah = 1, firstAyah = 1, lastAyah = 2)

    val progress = FakeProgressEvents()
    val graded = mutableListOf<Pair<AyahRef, AyahGrade>>()
    val trackerFactory = FakeTrackerFactory()
    val tracker: FakeTracker
        get() = trackerFactory.created.last()

    private val dispatcher = UnconfinedTestDispatcher(TestCoroutineScheduler())
    var blocksPerStart = inputBlocks

    val session: RecitationSession

    init {
        val text =
            FakeQuranTextRepository(
                wordsByAyah =
                    mutableMapOf(
                        AyahRef(1, 1) to listOf(word(first, "بِسْمِ"), word(second, "ٱللَّهِ")),
                        AyahRef(1, 2) to listOf(word(third, "ٱلْحَمْدُ")),
                    ),
            )
        val reference =
            FakeReferencePhonemes(
                mutableMapOf(
                    (1 to 1) to listOf(reference(first), reference(second)),
                    (1 to 2) to listOf(reference(third)),
                ),
            )
        session =
            RecitationSession(
                quranText = text,
                referencePhonemes = reference,
                trackerFactory = trackerFactory,
                progressEvents = progress,
                phonemeSourceFactory = { OnePhonemePerBlockSource() },
                inputFactory = { ScriptedInput(blocksPerStart) },
                scope = CoroutineScope(dispatcher),
                recognitionDispatcher = dispatcher,
                gradeSink = { ayah, grade -> graded += ayah to grade },
            )
    }

    /** Queues what the tracker reports for the next block. */
    fun script(vararg events: TrackerEvent) {
        tracker.scriptedBatches.addLast(events.toList())
    }

    fun confirmed(index: Int) = TrackerEvent.WordChanged(index, WordState.Confirmed)

    private fun word(key: WordKey, text: String) = Word(key, text, null, null, null)

    private fun reference(key: WordKey) =
        ReferenceWord(WordKeyLike(key.surah, key.ayah, key.wordPosition), listOf("a"))
}
