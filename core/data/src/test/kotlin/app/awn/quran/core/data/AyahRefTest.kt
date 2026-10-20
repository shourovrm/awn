package app.awn.quran.core.data

import app.awn.quran.core.data.fakes.FakeBookmarkRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AyahRefTest {

    @Test
    fun ordersBySurahThenAyah() {
        val sorted = listOf(AyahRef(2, 1), AyahRef(1, 7), AyahRef(1, 2)).sorted()
        assertEquals(listOf(AyahRef(1, 2), AyahRef(1, 7), AyahRef(2, 1)), sorted)
    }

    @Test
    fun fakeBookmarkToggleAddsThenRemoves() = runBlocking {
        val repository = FakeBookmarkRepository(clock = { 5L })
        repository.toggleBookmark(10)
        assertEquals(listOf(Bookmark(10, 5L)), repository.bookmarks().first())
        repository.toggleBookmark(10)
        assertTrue(repository.bookmarks().first().isEmpty())
    }
}
