package app.awn.quran.feature.read

import androidx.compose.ui.text.font.FontFamily
import app.awn.quran.core.data.PageLayout
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.Word
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.data.fakes.FakePackRepository
import app.awn.quran.feature.read.page.JuzTable
import app.awn.quran.feature.read.page.PageFontCache
import app.awn.quran.feature.read.page.PageRow
import app.awn.quran.feature.read.page.buildPageRows
import app.awn.quran.feature.read.page.slotCountOf
import app.awn.quran.feature.read.page.textToDraw
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageHelpersTest {

    @Test
    fun juzBoundariesFollowTheMadaniMushaf() {
        assertEquals(1, JuzTable.juzOfPage(1))
        assertEquals(1, JuzTable.juzOfPage(21))
        assertEquals(2, JuzTable.juzOfPage(22))
        assertEquals(29, JuzTable.juzOfPage(562))
        assertEquals(30, JuzTable.juzOfPage(604))
        assertEquals(562, JuzTable.startPageOf(29))
    }

    @Test
    fun glyphScriptDrawsItsCodeOnlyWhenTheFontIsThere() {
        val word = Word(WordKey(1, 1, 1), "unicode", "v1code", "v2code")

        assertEquals("v1code", textToDraw(word, ScriptId.V1, hasGlyphFont = true))
        assertEquals("v2code", textToDraw(word, ScriptId.V2, hasGlyphFont = true))
        assertEquals("v2code", textToDraw(word, ScriptId.V4Tajweed, hasGlyphFont = true))
        assertEquals("unicode", textToDraw(word, ScriptId.V1, hasGlyphFont = false))
        assertEquals("unicode", textToDraw(word, ScriptId.QpcHafs, hasGlyphFont = true))
    }

    @Test
    fun pageOneBecomesHeaderBasmalaAndTextRows() {
        val layout: PageLayout = ReadFixture.pageOne()

        val rows = buildPageRows(layout, hasGlyphFont = false)

        assertTrue(rows[0] is PageRow.HeaderRow)
        assertTrue(rows[1] is PageRow.BasmalaRow)
        assertEquals(3, (rows[2] as PageRow.TextRow).tokens.size)
        assertEquals(3, slotCountOf(layout))
    }

    @Test
    fun fontCacheKeepsOnlyPagesWithinFiveOfTheCurrentOne() {
        val packs = FakePackRepository()
        packs.fontFiles[ScriptId.V1] = File("font.ttf")
        val cache = PageFontCache(packs, createFamily = { FontFamily.Serif })
        (1..30).forEach { cache.familyFor(ScriptId.V1, it) }

        cache.retainAround(15)

        assertEquals((10..20).toSet(), cache.cachedPages)
    }

    @Test
    fun fontCacheReturnsNullWhenTheFontIsNotInstalled() {
        val cache = PageFontCache(FakePackRepository(), createFamily = { FontFamily.Serif })

        assertNull(cache.familyFor(ScriptId.V2, 1))
        assertTrue(cache.cachedPages.isEmpty())
    }
}
