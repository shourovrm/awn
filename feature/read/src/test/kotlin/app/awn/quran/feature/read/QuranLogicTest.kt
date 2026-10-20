package app.awn.quran.feature.read

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.LineItem
import app.awn.quran.core.data.PageLayout
import app.awn.quran.core.data.PageLine
import app.awn.quran.core.data.ScriptId
import app.awn.quran.feature.read.quran.GoToFields
import app.awn.quran.feature.read.quran.GoToPageResolver
import app.awn.quran.feature.read.quran.PageContentLoader
import app.awn.quran.feature.read.quran.PageItem
import app.awn.quran.feature.read.quran.VolumeKeyAction
import app.awn.quran.feature.read.quran.adjacentAyah
import app.awn.quran.feature.read.quran.ayahShareText
import app.awn.quran.feature.read.quran.ayahSpan
import app.awn.quran.feature.read.quran.pageToFollow
import app.awn.quran.feature.read.quran.scrollToKeepVisible
import app.awn.quran.feature.read.quran.titleSurahNumber
import app.awn.quran.feature.read.quran.volumeKeyAction
import app.awn.quran.feature.read.settings.TextScale
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuranLogicTest {

    private val tawbah = Chapter(9, "التوبة", "At-Tawbah", null, 129, "madinah", false)
    private val maryam = Chapter(19, "مريم", "Maryam", null, 98, "makkah", true)
    private val chapters = listOf(ReadFixture.fatiha, ReadFixture.baqarah, tawbah, maryam)

    @Test
    fun textScaleHasEightEvenStepsFromTheMinimumToTheMaximum() {
        assertEquals(0.85f, TextScale.scaleOf(0), 0.001f)
        assertEquals(1.6f, TextScale.scaleOf(7), 0.001f)
        assertEquals(
            TextScale.scaleOf(3) - TextScale.scaleOf(2),
            TextScale.scaleOf(5) - TextScale.scaleOf(4),
            0.001f,
        )
        assertEquals(0, TextScale.clampStep(-4))
        assertEquals(7, TextScale.clampStep(12))
    }

    @Test
    fun oldTextSizesMigrateToTheNearestStep() {
        assertEquals(1, TextScale.stepForLegacyName("Small"))
        assertEquals(3, TextScale.stepForLegacyName("Medium"))
        assertEquals(4, TextScale.stepForLegacyName("Large"))
        assertNull(TextScale.stepForLegacyName("Huge"))
        assertNull(TextScale.stepForLegacyName(null))
    }

    @Test
    fun adjacentAyahCrossesSurahBoundaries() {
        assertEquals(AyahRef(1, 2), adjacentAyah(AyahRef(1, 1), 1, chapters))
        assertEquals(AyahRef(2, 1), adjacentAyah(AyahRef(1, 7), 1, chapters))
        assertEquals(AyahRef(1, 7), adjacentAyah(AyahRef(2, 1), -1, chapters))
        assertNull(adjacentAyah(AyahRef(1, 1), -1, chapters))
    }

    @Test
    fun shareTextJoinsArabicTranslationAndReference() {
        assertEquals("ar — tr (2:255)", ayahShareText("ar", "tr", AyahRef(2, 255)))
        assertEquals("ar (2:255)", ayahShareText("ar", null, AyahRef(2, 255)))
    }

    @Test
    fun pagerFollowsPlaybackOnlyWhenTheReaderWasFollowing() {
        assertEquals(5, pageToFollow(previousPlayingPage = 4, playingPage = 5, shownPage = 4))
        assertEquals(5, pageToFollow(previousPlayingPage = null, playingPage = 5, shownPage = 1))
        assertNull(pageToFollow(previousPlayingPage = 4, playingPage = 5, shownPage = 9))
        assertNull(pageToFollow(previousPlayingPage = 5, playingPage = 5, shownPage = 5))
    }

    @Test
    fun scrollKeepsTheAyahInsideTheWindow() {
        // Window 400 high, ayah at 500..560: scroll by 560 + 20 - 400 = 180.
        assertEquals(180f, scrollToKeepVisible(500f, 560f, 400f, 20f), 0.001f)
        assertEquals(0f, scrollToKeepVisible(10f, 60f, 400f, 20f), 0.001f)
        // An ayah taller than the window keeps its top in view.
        assertEquals(480f, scrollToKeepVisible(500f, 1500f, 400f, 20f), 0.001f)
    }

    @Test
    fun volumeDownTurnsToTheNextPage() {
        assertEquals(VolumeKeyAction.NextPage, volumeKeyAction(isVolumeDown = true))
        assertEquals(VolumeKeyAction.PreviousPage, volumeKeyAction(isVolumeDown = false))
    }

    @Test
    fun pageFieldFillsSurahAndAyahAndBack() = runTest {
        val fixture = ReadFixture()
        fixture.text.pageByAyah[AyahRef(19, 96)] = 312
        val resolver = GoToPageResolver(fixture.text, ScriptId.QpcHafs, chapters)

        val fromPage = resolver.afterPageEdited(GoToFields(page = "312"))
        assertEquals("19", fromPage.surah)
        assertEquals("96", fromPage.ayah)

        val fromAyah = resolver.afterSurahOrAyahEdited(GoToFields(surah = "19", ayah = "96"))
        assertEquals("312", fromAyah.page)
        assertEquals(312, resolver.targetPage(fromAyah))
    }

    @Test
    fun invalidFieldsLeaveTheOtherFieldsAlone() = runTest {
        val fixture = ReadFixture()
        val resolver = GoToPageResolver(fixture.text, ScriptId.QpcHafs, chapters)
        val badPage = GoToFields(page = "999", surah = "2", ayah = "7")
        val badAyah = GoToFields(page = "", surah = "2", ayah = "999")

        assertEquals(badPage, resolver.afterPageEdited(badPage))
        assertEquals(badAyah, resolver.afterSurahOrAyahEdited(badAyah))
        assertNull(resolver.targetPage(badPage))
    }

    private fun layoutOf(page: Int, vararg lines: List<LineItem>) =
        PageLayout(
            page,
            ScriptId.QpcHafs,
            lines.mapIndexed { index, items -> PageLine(index + 1, items) },
        )

    private fun wordOf(surah: Int, ayah: Int) =
        LineItem.WordItem(ReadFixture.word(surah, ayah, 1, "x"))

    @Test
    fun titleNamesTheFirstSurahThatStartsOnThePage() {
        // Page 77: the end of Ali 'Imran, then An-Nisa starts.
        val page77 =
            layoutOf(
                77,
                listOf(wordOf(3, 200)),
                listOf(LineItem.SurahHeader(4)),
                listOf(wordOf(4, 1)),
            )
        assertEquals(4, titleSurahNumber(page77))

        // Page 604: 112, 113 and 114 all start; the first one wins.
        val page604 =
            layoutOf(
                604,
                listOf(LineItem.SurahHeader(112)),
                listOf(wordOf(112, 1)),
                listOf(LineItem.SurahHeader(113)),
                listOf(LineItem.SurahHeader(114)),
            )
        assertEquals(112, titleSurahNumber(page604))

        // Page 50: Ali 'Imran starts at the top.
        val page50 = layoutOf(50, listOf(LineItem.SurahHeader(3)), listOf(wordOf(3, 1)))
        assertEquals(3, titleSurahNumber(page50))
    }

    @Test
    fun ayahSpanFollowsTheSlotsOfThePage() {
        val full =
            layoutOf(
                10,
                *Array(15) { index ->
                    if (index + 1 in 3..4) listOf(wordOf(2, 5)) else listOf(wordOf(2, 4))
                },
            )
        // 15 slots of 100 px: lines 3 and 4 span 200..400.
        val span = ayahSpan(full, AyahRef(2, 5), 1500f)
        assertEquals(200f, span?.top)
        assertEquals(400f, span?.bottom)

        // 8 slots on a page 1500 px tall are centred: the block starts at (1500 - 800) / 2.
        val short = layoutOf(1, *Array(8) { listOf(wordOf(1, 1)) })
        assertEquals(350f, ayahSpan(short, AyahRef(1, 1), 1500f)?.top)
        assertNull(ayahSpan(short, AyahRef(1, 2), 1500f))
    }

    @Test
    fun titleNamesTheContinuingSurahWhenNoneStarts() {
        val page = layoutOf(51, listOf(wordOf(3, 5)), listOf(wordOf(3, 6)))
        assertEquals(3, titleSurahNumber(page))
    }

    @Test
    fun basmalahStandsBeforeEverySurahExceptTheFirstAndTheNinth() = runTest {
        val fixture = ReadFixture()
        fixture.text.chapterList = chapters
        fixture.text.pageByAyah[AyahRef(9, 1)] = 187
        val loader = PageContentLoader(fixture.text, fixture.translations, "en")

        suspend fun itemsOf(page: Int) =
            loader.load(page, ScriptId.QpcHafs, emptyList(), chapters).items

        val fatihaItems = itemsOf(1)
        assertTrue(fatihaItems[0] is PageItem.SurahBand)
        assertTrue(fatihaItems.none { it is PageItem.Basmalah })

        val baqarahItems = itemsOf(2)
        assertTrue(baqarahItems[0] is PageItem.SurahBand)
        assertEquals(PageItem.Basmalah(2), baqarahItems[1])
        assertTrue(baqarahItems[2] is PageItem.Ayah)

        val tawbahItems = itemsOf(187)
        assertTrue(tawbahItems[0] is PageItem.SurahBand)
        assertTrue(tawbahItems.none { it is PageItem.Basmalah })
    }
}
