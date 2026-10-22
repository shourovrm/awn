package app.awn.quran.feature.learn.words

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.awn.quran.feature.learn.data.LearnDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Removing a saved word from the course and putting it back with Undo. */
@RunWith(RobolectricTestRunner::class)
class RemoveWordTest {
    private lateinit var database: LearnDatabase
    private lateinit var repository: WordCourseRepository

    private val rank = 7

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room.inMemoryDatabaseBuilder(context, LearnDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository = WordCourseRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun aQueuedWordIsRemovedAndUndoPutsTheSameRowBack() = runBlocking {
        repository.queueWord(rank)
        val queuedBefore = database.wordDao().findQueuedWord(rank)

        val removed = repository.removeWord(rank)

        assertNull(database.wordDao().findQueuedWord(rank))
        assertNull(removed.card)

        repository.restoreWord(removed)

        assertEquals(queuedBefore, database.wordDao().findQueuedWord(rank))
        assertNull(database.wordDao().findWordCard(rank))
    }

    @Test
    fun aMetWordLosesItsCardAndUndoRestoresTheExactCard() = runBlocking {
        repository.meet(rank)
        val cardBefore = database.wordDao().findWordCard(rank)
        assertNotNull(cardBefore)

        val removed = repository.removeWord(rank)

        assertNull(database.wordDao().findWordCard(rank))
        assertFalse(repository.snapshot().wordCards.containsKey(rank))

        repository.restoreWord(removed)

        assertEquals(cardBefore, database.wordDao().findWordCard(rank))
        assertTrue(repository.snapshot().wordCards.containsKey(rank))
    }

    @Test
    fun removingAWordTheCourseNeverHeldChangesNothing() = runBlocking {
        val removed = repository.removeWord(rank)

        assertNull(removed.queued)
        assertNull(removed.card)

        repository.restoreWord(removed)

        assertNull(database.wordDao().findQueuedWord(rank))
        assertNull(database.wordDao().findWordCard(rank))
    }

    @Test
    fun otherWordsStayWhenOneIsRemoved() = runBlocking {
        repository.meet(rank)
        repository.meet(rank + 1)

        repository.removeWord(rank)

        assertNotNull(database.wordDao().findWordCard(rank + 1))
    }
}
