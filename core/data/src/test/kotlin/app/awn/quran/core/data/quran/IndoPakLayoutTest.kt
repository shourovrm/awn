package app.awn.quran.core.data.quran

import android.database.sqlite.SQLiteDatabase
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.LineItem
import app.awn.quran.core.data.PageLayout
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.realBundledPacksDirectory
import app.awn.quran.core.data.realDownloadedPacksDirectory
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Runs against the real `indopak.sqlite` that `:core:data:fetchTestPacks` places in
 * build/test-packs.
 */
@RunWith(RobolectricTestRunner::class)
class IndoPakLayoutTest {

    private val indoPakFile = File(realDownloadedPacksDirectory, "indopak.sqlite")

    private val repository =
        SqliteQuranTextRepository(
            databaseFile = { File(realBundledPacksDirectory, "quran.sqlite") },
            indoPakDatabase = {
                SQLiteDatabase.openDatabase(indoPakFile.path, null, SQLiteDatabase.OPEN_READONLY)
            },
        )

    private fun wordKeys(layout: PageLayout): List<String> =
        layout.lines.flatMap { line ->
            line.items.filterIsInstance<LineItem.WordItem>().map { it.word.key.toString() }
        }

    private fun lineOf(layout: PageLayout, number: Int) = layout.lines.first { it.line == number }

    @Test
    fun theLayoutHas610Pages() {
        assertEquals(610, repository.pageCount(ScriptId.IndoPak15))
    }

    @Test
    fun theFirstPageHasTheFatihahBannerOnLine8AndItsFirstWordOnLine9() = runBlocking {
        val layout = repository.pageLayout(1, ScriptId.IndoPak15)

        assertEquals(ScriptId.IndoPak15, layout.script)
        assertEquals((1..15).toList(), layout.lines.map { it.line })
        assertEquals(listOf(LineItem.SurahHeader(1)), lineOf(layout, 8).items)
        val firstWord = lineOf(layout, 9).items.first() as LineItem.WordItem
        assertEquals("1:1:1", firstWord.word.key.toString())
        assertEquals("بِسْمِ", firstWord.word.textNastaleeq)
        assertEquals(29, wordKeys(layout).size)
    }

    @Test
    fun aWordKeepsItsUthmaniTextFromTheQuranTextPack() = runBlocking {
        val layout = repository.pageLayout(1, ScriptId.IndoPak15)

        val firstWord = lineOf(layout, 9).items.first() as LineItem.WordItem
        val uthmani = repository.wordsOfAyah(AyahRef(1, 1)).first().textUthmani
        assertEquals(uthmani, firstWord.word.textUthmani)
        assertEquals(null, firstWord.word.codeV2)
    }

    @Test
    fun anAyahEndMarkerCarriesTheEndSignOfThePack() = runBlocking {
        val layout = repository.pageLayout(1, ScriptId.IndoPak15)

        val ayahEnd = lineOf(layout, 9).items.last() as LineItem.AyahEnd
        assertEquals(AyahRef(1, 1), ayahEnd.ayah)
        assertEquals("\u06DF\uF61E", ayahEnd.glyphCode)
    }

    @Test
    fun aSurahStartGetsItsBannerAndBismillahOnTheTwoLinesAboveTheFirstWord() = runBlocking {
        val layout = repository.pageLayout(2, ScriptId.IndoPak15)

        assertEquals(listOf(LineItem.SurahHeader(2)), lineOf(layout, 8).items)
        assertEquals(listOf(LineItem.Basmala(2)), lineOf(layout, 9).items)
        val firstWord = lineOf(layout, 10).items.first() as LineItem.WordItem
        assertEquals("2:1:1", firstWord.word.key.toString())
    }

    @Test
    fun theLastPageEndsWithTheLastWordAndTheLastAyahMarker() = runBlocking {
        val layout = repository.pageLayout(610, ScriptId.IndoPak15)

        val lastLine = layout.lines.last()
        assertEquals(10, lastLine.line)
        assertEquals(
            "114:6:3",
            (lastLine.items[lastLine.items.size - 2] as LineItem.WordItem).word.key.toString(),
        )
        assertEquals(AyahRef(114, 6), (lastLine.items.last() as LineItem.AyahEnd).ayah)
        assertEquals(43, wordKeys(layout).size)
    }

    @Test
    fun aHeaderWithNoFreeSlotAbovePageStartComesFirstAsExtraLinesZeroAndBelow() = runBlocking {
        val layout = repository.pageLayout(502, ScriptId.IndoPak15)

        assertEquals(listOf(-1, 0, 1), layout.lines.take(3).map { it.line })
        assertEquals(listOf(LineItem.SurahHeader(46)), layout.lines[0].items)
        assertEquals(listOf(LineItem.Basmala(46)), layout.lines[1].items)
        assertEquals(
            "46:1:1",
            (layout.lines[2].items.first() as LineItem.WordItem).word.key.toString(),
        )
    }

    @Test
    fun pageOfAndAyahsOnPageUseTheIndoPakPageNumbers() = runBlocking {
        assertEquals(2, repository.pageOf(AyahRef(2, 1), ScriptId.IndoPak15))
        assertEquals(610, repository.pageOf(AyahRef(114, 1), ScriptId.IndoPak15))
        assertEquals((1..7).map { AyahRef(1, it) }, repository.ayahsOnPage(1, ScriptId.IndoPak15))
    }

    @Test
    fun everyWordAndEveryAyahEndIsPrintedOnce() = runBlocking {
        for ((script, pageCount) in listOf(ScriptId.IndoPak15 to 610)) {
            val words = mutableListOf<String>()
            var ayahEnds = 0
            var banners = 0
            var bismillahs = 0
            for (page in 1..pageCount) {
                val layout = repository.pageLayout(page, script)
                words += wordKeys(layout)
                layout.lines
                    .flatMap { it.items }
                    .forEach { item ->
                        when (item) {
                            is LineItem.AyahEnd -> ayahEnds += 1
                            is LineItem.SurahHeader -> banners += 1
                            is LineItem.Basmala -> bismillahs += 1
                            is LineItem.WordItem -> Unit
                        }
                    }
            }

            assertEquals(script.name, 77_429, words.size)
            assertEquals(script.name, 77_429, words.toSet().size)
            assertEquals(script.name, 6_236, ayahEnds)
            assertEquals(script.name, 114, banners)
            assertEquals(script.name, 112, bismillahs)
        }
    }

    @Test
    fun aPageOutsideTheLayoutIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.pageLayout(611, ScriptId.IndoPak15) }
        }
    }

    @Test
    fun withoutTheLayoutPackTheIndoPakPrintsAreUnsupported() {
        val withoutPack =
            SqliteQuranTextRepository(
                databaseFile = { File(realBundledPacksDirectory, "quran.sqlite") },
            )

        val error =
            assertThrows(UnsupportedOperationException::class.java) {
                runBlocking { withoutPack.pageOf(AyahRef(1, 1), ScriptId.IndoPak15) }
            }

        assertTrue(error.message.orEmpty().contains("IndoPak"))
        assertEquals(610, withoutPack.pageCount(ScriptId.IndoPak15))
    }
}
