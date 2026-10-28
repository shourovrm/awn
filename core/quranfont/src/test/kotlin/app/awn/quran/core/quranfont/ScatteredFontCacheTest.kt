package app.awn.quran.core.quranfont

import androidx.compose.ui.text.font.FontFamily
import app.awn.quran.core.data.PackRepository
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.fakes.FakePackRepository
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScatteredFontCacheTest {
    private val loadedFiles = mutableListOf<File>()

    /** Like the installed prints: every page has a font file of its own. */
    private val packs: PackRepository =
        object : PackRepository by FakePackRepository() {
            override fun fontFile(script: ScriptId, page: Int): File = File("p$page.ttf")
        }

    private fun cache(capacity: Int = ScatteredFontCache.DEFAULT_CAPACITY) =
        ScatteredFontCache(
            packs,
            capacity = capacity,
            createFamily = { file ->
                loadedFiles += file
                FontFamily.Serif
            },
            nightFileFor = { file -> File("night/${file.name}") },
        )

    @Test
    fun aPageIsLoadedOnceWhileItStaysInTheCache() {
        val cache = cache()

        cache.familyFor(ScriptId.V2, 5)
        cache.familyFor(ScriptId.V2, 5)

        assertEquals(listOf(File("p5.ttf")), loadedFiles)
    }

    @Test
    fun theCacheNeverHoldsMoreThanItsCapacityOfPages() {
        val cache = cache(capacity = 12)

        (1..40).forEach { page -> cache.familyFor(ScriptId.V2, page) }

        assertEquals(12, cache.cachedPageCount)
        assertNull(cache.cachedFamily(ScriptId.V2, 1))
        assertNotNull(cache.cachedFamily(ScriptId.V2, 40))
    }

    @Test
    fun aPageReadRecentlyIsKeptWhenAnotherPageIsAdded() {
        val cache = cache(capacity = 12)
        (1..12).forEach { page -> cache.familyFor(ScriptId.V2, page) }
        cache.familyFor(ScriptId.V2, 1)
        loadedFiles.clear()

        cache.familyFor(ScriptId.V2, 13)
        cache.familyFor(ScriptId.V2, 1)

        assertEquals(listOf(File("p13.ttf")), loadedFiles)
    }

    @Test
    fun theLeastRecentlyUsedPageIsLoadedAgainOnceTwelveNewPagesHaveArrived() {
        val cache = cache(capacity = 12)
        (1..12).forEach { page -> cache.familyFor(ScriptId.V2, page) }
        loadedFiles.clear()

        (13..24).forEach { page -> cache.familyFor(ScriptId.V2, page) }
        assertNull(cache.cachedFamily(ScriptId.V2, 1))
        cache.familyFor(ScriptId.V2, 1)

        assertEquals(13, loadedFiles.size)
        assertEquals(File("p1.ttf"), loadedFiles.last())
    }

    @Test
    fun aMissingFontIsNotCached() {
        val cache = ScatteredFontCache(FakePackRepository(), createFamily = { FontFamily.Serif })

        assertNull(cache.familyFor(ScriptId.V2, 5))
        assertEquals(0, cache.cachedPageCount)
    }

    @Test
    fun aNightFamilyIsKeptApartFromTheDayFamilyOfTheSamePage() {
        val cache = cache()

        cache.familyFor(ScriptId.V4Tajweed, 7, isNight = false)
        cache.familyFor(ScriptId.V4Tajweed, 7, isNight = true)

        assertEquals(2, cache.cachedPageCount)
    }
}
