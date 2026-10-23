package app.awn.quran.feature.learn.course

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.MyWordMark
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.Word
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.data.fakes.FakeQuranTextRepository
import app.awn.quran.feature.learn.data.LearnDatabase
import app.awn.quran.feature.learn.session.FakeFrequency
import app.awn.quran.feature.learn.words.PageWordsSource
import app.awn.quran.feature.learn.words.WordCourseRepository
import app.awn.quran.feature.learn.words.WordStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** FakeFrequency gives a word the rank of its position, so position 3 is form 3. */
@RunWith(RobolectricTestRunner::class)
class PageWordsTest {

    private fun key(position: Int, ayah: Int = 1) = WordKey(2, ayah, position)

    private val knownCard = wordCard(rank = 1, stability = 10.0, state = 2)
    private val learningCard = wordCard(rank = 2, stability = 1.0, state = 1)

    // Form 3 is written twice on the page; form 1 is known, 2 is being learned, 3 and 4 are new.
    private val pageKeys = listOf(key(1), key(2), key(3), key(4), key(3, ayah = 2))

    private val snapshot = snapshotOf(cards = listOf(knownCard, learningCard), queued = listOf(4))

    private val list = PageWords.build(pageKeys, FakeFrequency(), snapshot)

    @Test
    fun aFormThatOccursTwiceIsListedOnceAtItsFirstPlace() {
        assertEquals(listOf(1, 2, 3, 4), list.rows.map { it.rank })
        assertEquals(key(3), list.rows[2].key)
    }

    @Test
    fun theFilterCountsSplitTheRowsByState() {
        assertEquals(4, list.countOf(PageWordFilter.All))
        assertEquals(2, list.countOf(PageWordFilter.New))
        assertEquals(1, list.countOf(PageWordFilter.Learning))
        assertEquals(1, list.countOf(PageWordFilter.Known))
        assertEquals(listOf(WordStatus.Known), list.rowsFor(PageWordFilter.Known).map { it.status })
    }

    @Test
    fun aRowCarriesHowOftenTheFormOccursInTheQuran() {
        assertTrue(list.rows.all { it.occurrences == 3 })
    }

    @Test
    fun onlyNewFormsThatAreNotQueuedYetAreLeftToQueue() {
        assertEquals(listOf(3), list.ranksToQueue)
    }

    @Test
    fun theQuranPageMarksKnownAndMetFormsAndLeavesOthersOut() {
        val marks = PageWords.marks(pageKeys, FakeFrequency(), snapshot)

        assertEquals(MyWordMark.Known, marks[key(1)])
        assertEquals(MyWordMark.Learning, marks[key(2)])
        assertNull(marks[key(3)])
        assertNull(marks[key(4)])
        assertEquals(2, marks.size)
    }

    @Test
    fun aWordWithTheFormOfAKnownWordIsMarkedKnownWhereverItIsWritten() {
        val marks = PageWords.marks(listOf(key(1, ayah = 7)), FakeFrequency(), snapshot)

        assertEquals(MyWordMark.Known, marks[key(1, ayah = 7)])
    }

    @Test
    fun learnNewQueuesExactlyTheNewFormsOfThePage() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database =
            Room.inMemoryDatabaseBuilder(context, LearnDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        try {
            val repository = WordCourseRepository(database)
            repository.meet(1)
            val pageList =
                PageWords.build(
                    listOf(key(1), key(2), key(3)),
                    FakeFrequency(),
                    repository.snapshot(),
                )

            val queuedCount = repository.queueNewWords(pageList)

            assertEquals(2, queuedCount)
            assertEquals(listOf(2, 3), repository.snapshot().queuedRanks.sorted())
        } finally {
            database.close()
        }
    }

    @Test
    fun aPrintWhoseLayoutsAreMissingFallsBackToTheDefaultPrint() = runBlocking {
        val ayah = AyahRef(2, 1)
        val fake =
            FakeQuranTextRepository(
                wordsByAyah = mutableMapOf(ayah to listOf(Word(key(1), "x", null, null))),
                pageByAyah = mutableMapOf(ayah to 5),
            )
        val missingIndoPak =
            object : QuranTextRepository by fake {
                override suspend fun ayahsOnPage(page: Int, script: ScriptId): List<AyahRef> {
                    if (script == ScriptId.IndoPak15) {
                        throw UnsupportedOperationException(
                            "The IndoPak layouts are not installed.",
                        )
                    }
                    return fake.ayahsOnPage(page, script)
                }
            }

        val found = PageWordsSource(missingIndoPak).wordsOnPage(5, ScriptId.IndoPak15)

        assertEquals(listOf(ayah), found.ayahs)
        assertEquals(listOf(key(1)), found.keys)
    }

    @Test
    fun aPageNoPrintHasGivesAnEmptyPage() = runBlocking {
        val outOfRange =
            object : QuranTextRepository by FakeQuranTextRepository() {
                override suspend fun ayahsOnPage(page: Int, script: ScriptId): List<AyahRef> =
                    throw IllegalArgumentException("Page must be 1..604 but was $page.")
            }

        val found = PageWordsSource(outOfRange).wordsOnPage(700, ScriptId.V2)

        assertTrue(found.keys.isEmpty())
    }
}
