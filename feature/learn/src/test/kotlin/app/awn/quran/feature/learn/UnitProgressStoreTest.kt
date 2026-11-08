package app.awn.quran.feature.learn

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.awn.quran.feature.learn.data.LearnDatabase
import app.awn.quran.feature.learn.progress.UnitProgress
import app.awn.quran.feature.learn.progress.UnitProgressStore
import app.awn.quran.feature.learn.progress.continueUnit
import app.awn.quran.feature.learn.progress.resumePage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UnitProgressStoreTest {

    private lateinit var database: LearnDatabase
    private var now = 1_000L
    private lateinit var store: UnitProgressStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room.inMemoryDatabaseBuilder(context, LearnDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        store = UnitProgressStore(database.unitProgressDao()) { now }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a unit never opened starts on its first page`() = runBlocking {
        assertEquals(0, store.pageOf("letters-01-all-letters"))
    }

    @Test
    fun `the page a user leaves on is the page the unit opens on, and the furthest page only grows`() =
        runBlocking {
            store.save("letters-01-all-letters", 5)
            now = 2_000L
            store.save("letters-01-all-letters", 3)

            assertEquals(3, store.pageOf("letters-01-all-letters"))
            val saved = store.all().first().single()
            assertEquals(UnitProgress("letters-01-all-letters", page = 3, furthestPage = 5, updatedAt = 2_000L), saved)
        }

    @Test
    fun `finishing a unit opens it on the first page next time and keeps how far it went`() = runBlocking {
        store.save("letters-01-all-letters", 12)
        store.finish("letters-01-all-letters")

        assertEquals(0, store.pageOf("letters-01-all-letters"))
        assertEquals(12, store.all().first().single().furthestPage)
    }

    @Test
    fun `starting again clears the page and the furthest page of that unit only`() = runBlocking {
        store.save("letters-01-all-letters", 7)
        store.save("letters-02-forms", 2)

        store.restart("letters-01-all-letters")

        val byId = store.all().first().associateBy { it.lessonId }
        assertEquals(0, byId.getValue("letters-01-all-letters").page)
        assertEquals(0, byId.getValue("letters-01-all-letters").furthestPage)
        assertEquals(2, byId.getValue("letters-02-forms").page)
    }

    @Test
    fun `the database is at version three`() {
        assertEquals(3, LearnDatabase.CURRENT_VERSION)
    }

    @Test
    fun `a saved page past the end of a changed unit opens its last page`() {
        assertEquals(4, resumePage(saved = 9, pageCount = 5))
        assertEquals(2, resumePage(saved = 2, pageCount = 5))
        assertEquals(0, resumePage(saved = 3, pageCount = 0))
    }

    @Test
    fun `continue offers the unit left most recently in the middle`() {
        val progress =
            listOf(
                UnitProgress("a", page = 4, furthestPage = 4, updatedAt = 10),
                UnitProgress("b", page = 2, furthestPage = 6, updatedAt = 30),
                UnitProgress("c", page = 0, furthestPage = 9, updatedAt = 50),
            )

        assertEquals("b", continueUnit(progress)?.lessonId)
        assertNull(continueUnit(progress.filter { it.page == 0 }))
    }
}
