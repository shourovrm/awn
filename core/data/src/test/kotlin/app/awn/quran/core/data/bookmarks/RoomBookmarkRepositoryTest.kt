package app.awn.quran.core.data.bookmarks

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.database.AwnDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomBookmarkRepositoryTest {

    private lateinit var database: AwnDatabase
    private var now = 1_000L
    private lateinit var repository: RoomBookmarkRepository

    @Before
    fun openDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room.inMemoryDatabaseBuilder(context, AwnDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository = RoomBookmarkRepository(database, clock = { now })
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun toggleBookmarkAddsThenRemovesThePage() = runBlocking {
        repository.toggleBookmark(5)
        assertEquals(listOf(5), repository.bookmarks().first().map { it.page })

        repository.toggleBookmark(5)
        assertEquals(emptyList<Int>(), repository.bookmarks().first().map { it.page })
    }

    @Test
    fun bookmarksComeNewestFirst() = runBlocking {
        repository.toggleBookmark(10)
        now = 2_000L
        repository.toggleBookmark(20)

        assertEquals(listOf(20, 10), repository.bookmarks().first().map { it.page })
    }

    @Test
    fun savingAnAyahAgainReplacesItsNote() = runBlocking {
        repository.saveAyah(AyahRef(2, 255), "Ayat al-Kursi")
        now = 2_000L
        repository.saveAyah(AyahRef(2, 255), null)

        val saved = repository.savedAyahs().first()
        assertEquals(1, saved.size)
        assertNull(saved.single().note)
        assertEquals(2_000L, saved.single().createdAt)
    }

    @Test
    fun removingASavedAyahDeletesIt() = runBlocking {
        repository.saveAyah(AyahRef(1, 1), "first")
        repository.removeSavedAyah(AyahRef(1, 1))

        assertEquals(emptyList<Any>(), repository.savedAyahs().first())
    }

    @Test
    fun recentPagesKeepOneEntryPerPageMostRecentFirst() = runBlocking {
        repository.recordPageOpened(3)
        now = 2_000L
        repository.recordPageOpened(4)
        now = 3_000L
        repository.recordPageOpened(3)

        assertEquals(listOf(3, 4), repository.recentPages().first().map { it.page })
    }

    @Test
    fun recentPagesAreCappedAtFifty() = runBlocking {
        for (page in 1..60) {
            now = page * 10L
            repository.recordPageOpened(page)
        }

        val recent = repository.recentPages().first()
        assertEquals(50, recent.size)
        assertEquals(60, recent.first().page)
        assertEquals(11, recent.last().page)
    }

    @Test
    fun invalidPageIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.toggleBookmark(605) }
        }
    }

    @Test
    fun exportedJsonImportsIntoAnEmptyDatabaseUnchanged() = runBlocking {
        repository.toggleBookmark(7)
        repository.saveAyah(AyahRef(18, 10), "cave")
        repository.saveAyah(AyahRef(1, 1), null)
        repository.recordPageOpened(9)
        val exported = repository.exportJson()

        database.clearAllTables()
        repository.importJson(exported)

        assertEquals(exported, repository.exportJson())
        assertEquals(listOf(7), repository.bookmarks().first().map { it.page })
        assertEquals(2, repository.savedAyahs().first().size)
        assertEquals(listOf(9), repository.recentPages().first().map { it.page })
    }

    @Test
    fun importKeepsTheNewerSavedAyahAndTrimsRecentPagesToFifty() = runBlocking {
        now = 5_000L
        repository.saveAyah(AyahRef(2, 1), "local newer")
        repository.recordPageOpened(1)
        val document =
            """{"format_version":1,"bookmarks":[],
              |"saved_ayahs":[{"surah":2,"ayah":1,"note":"imported older","created_at":100}],
              |"recent_pages":[${(2..60).joinToString(",") { """{"page":$it,"opened_at":${it * 100}}""" }}]}"""
                .trimMargin()

        repository.importJson(document)

        assertEquals("local newer", repository.savedAyahs().first().single().note)
        assertEquals(50, repository.recentPages().first().size)
    }

    @Test
    fun malformedImportFailsWithoutChangingStoredData() = runBlocking {
        repository.toggleBookmark(3)

        val malformed =
            listOf(
                "not json",
                """{"format_version":1}""",
                """{"format_version":9,"bookmarks":[],"saved_ayahs":[],"recent_pages":[]}""",
                """{"format_version":1,"bookmarks":[{"page":999,"created_at":1}],"saved_ayahs":[],"recent_pages":[]}""",
            )
        for (document in malformed) {
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repository.importJson(document) }
            }
        }

        assertEquals(listOf(3), repository.bookmarks().first().map { it.page })
    }
}
