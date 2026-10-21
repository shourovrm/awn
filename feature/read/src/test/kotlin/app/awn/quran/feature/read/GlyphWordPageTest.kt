package app.awn.quran.feature.read

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.LineItem
import app.awn.quran.core.data.PageLayout
import app.awn.quran.core.data.PageLine
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.Word
import app.awn.quran.core.data.WordKey
import app.awn.quran.feature.read.quran.PageContent
import app.awn.quran.feature.read.quran.PageContentLoader
import app.awn.quran.feature.read.quran.PageItem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A glyph code works only with the font of the page its word is printed on, so the line-by-line
 * view must know the page of every word, including the words of an ayah that runs over a page.
 */
class GlyphWordPageTest {
    private val fixture = ReadFixture()
    private val loader = PageContentLoader(fixture.text, fixture.translations, wordLanguage = "en")
    private val splitAyah = AyahRef(2, 6)
    private val chapters = listOf(ReadFixture.baqarah)

    private fun glyphWord(ayah: AyahRef, position: Int) =
        Word(
            key = WordKey(ayah.surah, ayah.ayah, position),
            textUthmani = "w$position",
            codeV1 = null,
            codeV2 = "g$position",
            transliteration = null,
        )

    private fun layoutOf(page: Int, words: List<Word>) =
        PageLayout(
            page,
            ScriptId.V4Tajweed,
            listOf(PageLine(1, words.map { LineItem.WordItem(it) })),
        )

    private fun setUpSplitAyah() {
        val first = (1..2).map { glyphWord(splitAyah, it) }
        val second = (3..4).map { glyphWord(splitAyah, it) }
        fixture.text.wordsByAyah[splitAyah] = first + second
        fixture.text.pageByAyah[splitAyah] = 3
        fixture.text.pages[3] = layoutOf(3, first)
        fixture.text.pages[4] = layoutOf(4, second)
    }

    private fun blockOf(content: PageContent) =
        content.items.filterIsInstance<PageItem.Ayah>().single().block

    @Test
    fun theWordsOfAnAyahThatRunsOverAPageBreakKeepTheirOwnPage() = runTest {
        setUpSplitAyah()

        val content = loader.load(3, ScriptId.V4Tajweed, emptyList(), chapters)

        val words = blockOf(content).words
        assertEquals(listOf(3, 3, 4, 4), words.map { it.glyphs?.page })
        assertEquals("g1", words.first().glyphs?.word?.codeV2)
    }

    @Test
    fun anAyahThatFitsOnItsPageNeedsOnlyThatPagesLayout() = runTest {
        val ayah = AyahRef(2, 1)
        val words = (1..3).map { glyphWord(ayah, it) }
        fixture.text.wordsByAyah[ayah] = words
        fixture.text.pageByAyah[ayah] = 2
        fixture.text.pages[2] = layoutOf(2, words)

        loader.load(2, ScriptId.V4Tajweed, emptyList(), chapters)

        assertEquals(listOf(2 to ScriptId.V4Tajweed), fixture.text.layoutRequests)
    }

    @Test
    fun unicodePrintsHaveNoPageFonts() = runTest {
        val ayah = AyahRef(2, 1)
        fixture.text.wordsByAyah[ayah] = listOf(glyphWord(ayah, 1))
        fixture.text.pageByAyah[ayah] = 2

        val content = loader.load(2, ScriptId.QpcHafs, emptyList(), chapters)

        assertNull(blockOf(content).words.single().glyphs)
        assertEquals(emptyList<Pair<Int, ScriptId>>(), fixture.text.layoutRequests)
    }

    @Test
    fun aWordWithoutAGlyphCodeInThePrintIsDrawnAsText() = runTest {
        val ayah = AyahRef(2, 1)
        val withCode = glyphWord(ayah, 1)
        val withoutCode = glyphWord(ayah, 2).copy(codeV2 = null)
        fixture.text.wordsByAyah[ayah] = listOf(withCode, withoutCode)
        fixture.text.pageByAyah[ayah] = 2
        fixture.text.pages[2] = layoutOf(2, listOf(withCode, withoutCode))

        val content = loader.load(2, ScriptId.V4Tajweed, emptyList(), chapters)

        val words = blockOf(content).words
        assertNotNull(words[0].glyphs)
        assertNull(words[1].glyphs)
    }
}
