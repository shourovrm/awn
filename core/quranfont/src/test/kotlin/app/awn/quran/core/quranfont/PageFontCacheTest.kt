package app.awn.quran.core.quranfont

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import app.awn.quran.core.data.PackRepository
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.fakes.FakePackRepository
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PageFontCacheTest {
    private val dayFile = File("p3.ttf")
    private val nightFile = File("night/p3.ttf")
    private val packs = FakePackRepository().apply { fontFiles[ScriptId.V4Tajweed] = dayFile }
    private val loadedFiles = mutableListOf<File>()

    private fun cache(
        nightFileFor: (File) -> File = { nightFile },
        prefetchRadius: Int = 2,
        radius: Int = 5,
    ) =
        PageFontCache(
            packs,
            radius = radius,
            createFamily = { file ->
                loadedFiles += file
                FontFamily(Font(file))
            },
            nightFileFor = nightFileFor,
            prefetchRadius = prefetchRadius,
            prefetchDispatcher = Dispatchers.Unconfined,
        )

    @Test
    fun dayAndNightFamiliesOfOnePageAreCachedSeparately() {
        val cache = cache()

        val day = cache.familyFor(ScriptId.V4Tajweed, 3, isNight = false)
        val night = cache.familyFor(ScriptId.V4Tajweed, 3, isNight = true)

        assertNotSame(day, night)
        assertEquals(listOf(dayFile, nightFile), loadedFiles)
        assertSame(day, cache.familyFor(ScriptId.V4Tajweed, 3, isNight = false))
        assertEquals(2, loadedFiles.size)
    }

    @Test
    fun onlyTheColourPrintLoadsAPatchedCopyAtNight() {
        packs.fontFiles[ScriptId.V1] = File("v1.ttf")
        val cache = cache(nightFileFor = { error("V1 has no night palette") })

        cache.familyFor(ScriptId.V1, 3, isNight = true)

        assertEquals(listOf(File("v1.ttf")), loadedFiles)
    }

    @Test
    fun aNightCopyThatCannotBeWrittenFallsBackToTheDayFont() {
        val cache = cache(nightFileFor = { throw IOException("disk full") })

        val family = cache.familyFor(ScriptId.V4Tajweed, 3, isNight = true)

        assertNotNull(family)
        assertEquals(listOf(dayFile), loadedFiles)
    }

    @Test
    fun retainingAPageAlsoPreparesTheNextTwoEitherWay() {
        val cache = cache()
        cache.familyFor(ScriptId.V4Tajweed, 10, isNight = true)

        cache.retainAround(10)

        assertEquals((8..12).toSet(), cache.cachedPages)
    }

    @Test
    fun preparingStopsAtThePagesOfTheMushaf() {
        val cache = cache()
        cache.familyFor(ScriptId.V4Tajweed, 1, isNight = false)
        cache.retainAround(1)
        assertEquals((1..3).toSet(), cache.cachedPages)

        cache.familyFor(ScriptId.V4Tajweed, 604, isNight = false)
        cache.retainAround(604)
        assertEquals((602..604).toSet(), cache.cachedPages)
    }

    @Test
    fun preparingFollowsTheScriptAndModeLastAskedFor() {
        val cache = cache()
        cache.familyFor(ScriptId.V4Tajweed, 10, isNight = false)
        cache.retainAround(10)
        loadedFiles.clear()

        cache.familyFor(ScriptId.V4Tajweed, 20, isNight = true)
        cache.retainAround(20)

        assertTrue(loadedFiles.all { it == nightFile })
        assertEquals((18..22).toSet(), cache.cachedPages)
    }

    @Test
    fun pagesToPrepareRunNearestFirstAndSkipTheCentre() {
        assertEquals(listOf(11, 9, 12, 8), PageFontCache.pagesToPrepare(center = 10, radius = 2))
        assertEquals(listOf(2, 3), PageFontCache.pagesToPrepare(center = 1, radius = 2))
        assertEquals(listOf(603, 602), PageFontCache.pagesToPrepare(center = 604, radius = 2))
    }

    @Test
    fun aMissingFontIsNotPreparedOrCached() {
        val cache =
            PageFontCache(
                FakePackRepository(),
                createFamily = { FontFamily.Serif },
                prefetchDispatcher = Dispatchers.Unconfined,
            )

        cache.familyFor(ScriptId.V2, 5)
        cache.retainAround(5)

        assertTrue(cache.cachedPages.isEmpty())
    }

    /** Like the installed prints: every page has a font file of its own. */
    private fun packsWithOneFontFilePerPage(): PackRepository =
        object : PackRepository by FakePackRepository() {
            override fun fontFile(script: ScriptId, page: Int): File = File("p$page.ttf")
        }

    @Test
    fun theDefaultCacheKeepsTheCurrentPageAndTwoOnEachSide() {
        val defaultCache =
            PageFontCache(
                packsWithOneFontFilePerPage(),
                createFamily = { file -> FontFamily(Font(file)) },
                prefetchDispatcher = Dispatchers.Unconfined,
            )
        (1..40).forEach { page -> defaultCache.familyFor(ScriptId.V2, page) }

        defaultCache.retainAround(20)

        assertEquals((18..22).toSet(), defaultCache.cachedPages)
    }

    @Test
    fun aFontFileThatNoCachedPageUsesIsReleased() {
        val cache =
            PageFontCache(
                packsWithOneFontFilePerPage(),
                createFamily = { file -> FontFamily(Font(file)) },
                prefetchDispatcher = Dispatchers.Unconfined,
            )
        (1..40).forEach { page -> cache.familyFor(ScriptId.V2, page) }
        assertEquals(40, cache.loadedFileCount)

        cache.retainAround(20)

        assertEquals(5, cache.loadedFileCount)
    }

    @Test
    fun pagesThatShareOneFontFileShareOneFamily() {
        val cache = cache()

        val first = cache.familyFor(ScriptId.V4Tajweed, 3)
        val second = cache.familyFor(ScriptId.V4Tajweed, 4)

        assertSame(first, second)
        assertEquals(1, loadedFiles.size)
    }
}
