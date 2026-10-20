package app.awn.quran.core.data.quran

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.LineItem
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.realBundledPacksDirectory
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Runs against the real `quran.sqlite` that fetchBundledPacks places in the module assets. */
@RunWith(RobolectricTestRunner::class)
class SqliteQuranTextRepositoryTest {

    private val repository =
        SqliteQuranTextRepository(
            databaseFile = { File(realBundledPacksDirectory, "quran.sqlite") },
        )

    private val layoutScripts = listOf(ScriptId.V1, ScriptId.V2)

    @Test
    fun readsAll114ChaptersWithTheirBismillahFlags() = runBlocking {
        val chapters = repository.chapters()

        assertEquals(114, chapters.size)
        assertEquals((1..114).toList(), chapters.map { it.number })
        assertFalse(chapters.first { it.number == 1 }.bismillahPre)
        assertFalse(chapters.first { it.number == 9 }.bismillahPre)
        assertTrue(chapters.first { it.number == 67 }.bismillahPre)
        assertEquals("Al-Fatihah", chapters.first().nameSimple)
    }

    @Test
    fun everyScriptWithALayoutHas604Pages() {
        for (script in ScriptId.entries - ScriptId.IndoPak) {
            assertEquals(script.name, 604, repository.pageCount(script))
        }
    }

    @Test
    fun pageOneHasEightLinesAndTheFatihahHeaderOnLineOne() = runBlocking {
        for (script in layoutScripts) {
            val layout = repository.pageLayout(1, script)

            assertEquals(script.name, 8, layout.lines.size)
            assertEquals(listOf(LineItem.SurahHeader(1)), layout.lines[0].items)
            val wordCount =
                layout.lines.sumOf { line -> line.items.count { it is LineItem.WordItem } }
            assertEquals(29, wordCount)
        }
    }

    @Test
    fun surahStartsGetTheBannerAndTheBismillahLineAbovePage562() = runBlocking {
        val layout = repository.pageLayout(562, ScriptId.V2)

        assertEquals(LineItem.SurahHeader(67), layout.lines[0].items.single())
        assertEquals(LineItem.Basmala(67), layout.lines[1].items.single())
        val firstWord = layout.lines[2].items.first() as LineItem.WordItem
        assertEquals("67:1:1", firstWord.word.key.toString())
    }

    @Test
    fun everySurahHasOneBannerAndOneBismillahBeforeItsFirstWordInEveryScript() = runBlocking {
        val chapters = repository.chapters()
        for (script in ScriptId.entries - ScriptId.IndoPak) {
            val banners = mutableMapOf<Int, MutableList<Pair<Int, Int>>>()
            val bismillahs = mutableMapOf<Int, MutableList<Pair<Int, Int>>>()
            val firstWords = mutableMapOf<Int, Pair<Int, Int>>()
            for (page in 1..604) {
                for (line in repository.pageLayout(page, script).lines) {
                    for (item in line.items) {
                        val place = page to line.line
                        when (item) {
                            is LineItem.SurahHeader ->
                                banners.getOrPut(item.surah) { mutableListOf() }.add(place)
                            is LineItem.Basmala ->
                                bismillahs.getOrPut(item.surah) { mutableListOf() }.add(place)
                            is LineItem.WordItem ->
                                if (item.word.key.ayah == 1 && item.word.key.wordPosition == 1) {
                                    firstWords[item.word.key.surah] = place
                                }
                            is LineItem.AyahEnd -> Unit
                        }
                    }
                }
            }
            val printOrder = compareBy<Pair<Int, Int>>({ it.first }, { it.second })
            for (chapter in chapters) {
                val label = "$script surah ${chapter.number}"
                val banner = banners[chapter.number].orEmpty()
                val bismillah = bismillahs[chapter.number].orEmpty()
                val firstWord = firstWords.getValue(chapter.number)
                assertEquals("$label banners", 1, banner.size)
                assertTrue(
                    "$label banner before first word",
                    printOrder.compare(banner[0], firstWord) < 0,
                )
                if (chapter.bismillahPre) {
                    assertEquals("$label bismillahs", 1, bismillah.size)
                    assertTrue(
                        "$label banner before bismillah",
                        printOrder.compare(banner[0], bismillah[0]) < 0,
                    )
                    assertTrue(
                        "$label bismillah before first word",
                        printOrder.compare(bismillah[0], firstWord) < 0,
                    )
                } else {
                    assertEquals("$label bismillahs", 0, bismillah.size)
                }
            }
            assertEquals(script.name, 112, bismillahs.size)
        }
    }

    @Test
    fun surahStartingOnLineTwoPutsTheBannerOnTheLastLineOfThePreviousPage() = runBlocking {
        // Surah 4 starts on line 2 of page 77 in V2: the banner is on line 15 of page 76.
        val previous = repository.pageLayout(76, ScriptId.V2)
        val page = repository.pageLayout(77, ScriptId.V2)

        assertEquals(LineItem.SurahHeader(4), previous.lines.last().items.single())
        assertEquals(15, previous.lines.last().line)
        assertEquals(LineItem.Basmala(4), page.lines[0].items.single())
        assertTrue(
            page.lines.flatMap { it.items }.none { it is LineItem.SurahHeader && it.surah == 4 },
        )
    }

    @Test
    fun surahWithoutSeparateBismillahGetsOnlyTheBanner() = runBlocking {
        val tawbahPage = repository.pageOf(AyahRef(9, 1), ScriptId.V2)
        val layout = repository.pageLayout(tawbahPage, ScriptId.V2)
        val allItems = layout.lines.flatMap { it.items }

        assertTrue(allItems.contains(LineItem.SurahHeader(9)))
        assertFalse(allItems.contains(LineItem.Basmala(9)))
    }

    @Test
    fun everySlotOfEveryPageHoldsSomethingAndWordsKeepReadingOrder() = runBlocking {
        for (script in layoutScripts) {
            var wordCount = 0
            val ayahs = mutableSetOf<AyahRef>()
            for (page in 1..604) {
                val layout = repository.pageLayout(page, script)
                assertEquals(
                    "$script page $page",
                    (1..layout.lines.size).toList(),
                    layout.lines.map { it.line },
                )
                for (line in layout.lines) {
                    assertTrue(
                        "$script page $page line ${line.line} is empty",
                        line.items.isNotEmpty(),
                    )
                }
                val words =
                    layout.lines
                        .flatMap { it.items }
                        .filterIsInstance<LineItem.WordItem>()
                        .map { it.word.key }
                assertEquals("$script page $page", words.sorted(), words)
                wordCount += words.size
                ayahs += repository.ayahsOnPage(page, script)
            }
            assertEquals(script.name, 77_429, wordCount)
            assertEquals(script.name, 6_236, ayahs.size)
        }
    }

    private fun glyphOf(codePoint: Int): String = codePoint.toChar().toString()

    @Test
    fun ayahEndSignsCarryTheGlyphOfTheirPageFont() = runBlocking {
        suspend fun endOf(page: Int, script: ScriptId, ayah: AyahRef): String? =
            repository
                .pageLayout(page, script)
                .lines
                .flatMap { it.items }
                .filterIsInstance<LineItem.AyahEnd>()
                .first { it.ayah == ayah }
                .glyphCode

        assertEquals(glyphOf(0xFC45), endOf(1, ScriptId.V2, AyahRef(1, 1)))
        assertEquals(glyphOf(0xFB55), endOf(1, ScriptId.V1, AyahRef(1, 1)))
        // 2:178 ends with the shared V1 glyph U+FBB1, which prints its sign as U+FBD3.
        val page = repository.pageOf(AyahRef(2, 178), ScriptId.V1)
        assertEquals(glyphOf(0xFBD3), endOf(page, ScriptId.V1, AyahRef(2, 178)))
    }

    @Test
    fun everyAyahEndSignAppearsOnceInTheLayout() = runBlocking {
        for (script in layoutScripts) {
            val endSigns = mutableListOf<AyahRef>()
            for (page in 1..604) {
                endSigns +=
                    repository
                        .pageLayout(page, script)
                        .lines
                        .flatMap { it.items }
                        .filterIsInstance<LineItem.AyahEnd>()
                        .map { it.ayah }
            }
            assertEquals(script.name, 6_236, endSigns.size)
            assertEquals(script.name, 6_236, endSigns.toSet().size)
        }
    }

    @Test
    fun pageOfFindsTheFirstWordOfAnAyah() = runBlocking {
        assertEquals(562, repository.pageOf(AyahRef(67, 1), ScriptId.V2))
        assertEquals(1, repository.pageOf(AyahRef(1, 1), ScriptId.V1))
        assertEquals(604, repository.pageOf(AyahRef(114, 6), ScriptId.V2))
    }

    @Test
    fun qpcHafsAndV4TajweedUseTheV2Layout() = runBlocking {
        val v2 = repository.pageLayout(300, ScriptId.V2).lines
        assertEquals(v2, repository.pageLayout(300, ScriptId.QpcHafs).lines)
        assertEquals(v2, repository.pageLayout(300, ScriptId.V4Tajweed).lines)
    }

    @Test
    fun v1AndV2DifferOnTheFiftySixAyahsThatMovePages() = runBlocking {
        var differing = 0
        for (surah in 1..114) {
            val chapter = repository.chapters().first { it.number == surah }
            for (ayah in 1..chapter.versesCount) {
                val ref = AyahRef(surah, ayah)
                if (repository.pageOf(ref, ScriptId.V1) != repository.pageOf(ref, ScriptId.V2)) {
                    differing += 1
                }
            }
        }
        assertEquals(56, differing)
    }

    @Test
    fun firstAyahHasFourWordsInSpokenOrder() = runBlocking {
        val words = repository.wordsOfAyah(AyahRef(1, 1))

        assertEquals(4, words.size)
        assertEquals(listOf(1, 2, 3, 4), words.map { it.key.wordPosition })
        assertTrue(words.all { it.codeV1 != null && it.codeV2 != null })
    }

    @Test
    fun unknownAyahHasNoWords() = runBlocking {
        assertEquals(emptyList<Any>(), repository.wordsOfAyah(AyahRef(1, 8)))
    }

    @Test
    fun indoPakFailsWithAClearError() {
        val error =
            assertThrows(UnsupportedOperationException::class.java) {
                runBlocking { repository.pageLayout(1, ScriptId.IndoPak) }
            }
        assertTrue(error.message.orEmpty().contains("IndoPak"))
        assertThrows(UnsupportedOperationException::class.java) {
            repository.pageCount(ScriptId.IndoPak)
        }
    }

    @Test
    fun pageOutOfRangeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.pageLayout(605, ScriptId.V2) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.pageOf(AyahRef(1, 99), ScriptId.V2) }
        }
    }
}
