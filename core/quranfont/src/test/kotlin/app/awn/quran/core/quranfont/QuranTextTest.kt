package app.awn.quran.core.quranfont

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.font.FontFamily
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.LineItem
import app.awn.quran.core.data.PackRepository
import app.awn.quran.core.data.PageLayout
import app.awn.quran.core.data.PageLine
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.Word
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.data.fakes.FakePackRepository
import app.awn.quran.core.data.fakes.FakeQuranTextRepository
import app.awn.quran.core.ui.theme.AwnTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The two text composables draw in the print of [LocalQuranPrint], word by word. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuranTextTest {
    @get:Rule val composeRule = createComposeRule()

    private val ayah = AyahRef(2, 6)
    private val fontRequests = mutableListOf<Pair<ScriptId, Int>>()
    private val fakePacks =
        FakePackRepository().apply { fontFiles[ScriptId.V4Tajweed] = File("p.ttf") }
    private val packs =
        object : PackRepository by fakePacks {
            override fun fontFile(script: ScriptId, page: Int): File? {
                fontRequests += script to page
                return fakePacks.fontFile(script, page)
            }
        }
    private val fontCache = PageFontCache(packs, createFamily = { FontFamily.Serif })

    private fun wordAt(position: Int) =
        Word(WordKey(ayah.surah, ayah.ayah, position), "text$position", null, "glyph$position")

    /** Word 1 is printed on page 3, word 2 and the end sign on page 4. */
    private val quranText =
        FakeQuranTextRepository(
            wordsByAyah = mutableMapOf(ayah to listOf(wordAt(1), wordAt(2))),
            pages =
                mutableMapOf(
                    3 to layout(3, LineItem.WordItem(wordAt(1))),
                    4 to layout(4, LineItem.WordItem(wordAt(2)), LineItem.AyahEnd(ayah, "endsign")),
                ),
            pageByAyah = mutableMapOf(ayah to 3),
            totalPages = 4,
        )

    private fun layout(page: Int, vararg items: LineItem) =
        PageLayout(page, ScriptId.V4Tajweed, listOf(PageLine(1, items.toList())))

    private val glyphPrint =
        QuranPrint(ScriptId.V4Tajweed, fontCache = fontCache, quranText = quranText)
    private val unicodePrint = QuranPrint(ScriptId.QpcHafs, quranText = quranText)

    private fun showAyah(
        print: QuranPrint,
        wordRange: IntRange? = null,
        showEndMarker: Boolean = true,
    ) {
        composeRule.setContent {
            AwnTheme {
                CompositionLocalProvider(LocalQuranPrint provides print) {
                    QuranAyahText(ayah, wordRange = wordRange, showEndMarker = showEndMarker)
                }
            }
        }
    }

    private fun nodeCountOf(text: String): Int =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) { nodeCountOf(text) == 1 }
    }

    @Test
    fun aGlyphPrintDrawsGlyphCodesInTheFontOfEachWordsOwnPage() {
        showAyah(glyphPrint)
        waitForText("glyph2")

        composeRule.onNodeWithText("glyph1").assertIsDisplayed()
        val pagesAsked = fontRequests.filter { it.first == ScriptId.V4Tajweed }.map { it.second }
        assertEquals(setOf(3, 4), pagesAsked.toSet())
    }

    @Test
    fun aGlyphPrintEndsWithTheSignOfThePageLayout() {
        showAyah(glyphPrint)
        waitForText("endsign")
    }

    @Test
    fun aUnicodePrintDrawsTheTextAndTheNumberAloneAsEndMarker() {
        showAyah(unicodePrint)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("text1").assertIsDisplayed()
        composeRule.onNodeWithText("text2").assertIsDisplayed()
        composeRule.onNodeWithText("٦").assertIsDisplayed()
    }

    @Test
    fun theEndMarkerCanBeLeftOut() {
        showAyah(unicodePrint, showEndMarker = false)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("text2").assertIsDisplayed()
        assertEquals(0, nodeCountOf("٦"))
    }

    @Test
    fun aWordRangeDrawsOnlyThoseWordsAndNoMarkerBeforeTheAyahEnd() {
        showAyah(unicodePrint, wordRange = 1..1)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("text1").assertIsDisplayed()
        assertEquals(0, nodeCountOf("text2"))
        assertEquals(0, nodeCountOf("٦"))
    }

    @Test
    fun aWordRangeThatReachesTheLastWordKeepsTheMarker() {
        showAyah(unicodePrint, wordRange = 2..2)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("text2").assertIsDisplayed()
        composeRule.onNodeWithText("٦").assertIsDisplayed()
        assertEquals(0, nodeCountOf("text1"))
    }

    @Test
    fun theBlankPositionIsDrawnAsAGapInPlaceOfItsWord() {
        composeRule.setContent {
            AwnTheme {
                CompositionLocalProvider(LocalQuranPrint provides unicodePrint) {
                    QuranAyahText(ayah, blankPosition = 1, showEndMarker = false)
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("……").assertIsDisplayed()
        assertEquals(0, nodeCountOf("text1"))
        composeRule.onNodeWithText("text2").assertIsDisplayed()
    }

    @Test
    fun aGlyphPrintFallsBackToTextForAWordWhosePageIsUnknown() {
        quranText.pages[3] = layout(3)
        showAyah(glyphPrint, wordRange = 1..1)
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) { nodeCountOf("text1") == 1 }
    }

    @Test
    fun oneWordIsDrawnInTheFontOfItsPage() {
        composeRule.setContent {
            AwnTheme {
                CompositionLocalProvider(LocalQuranPrint provides glyphPrint) {
                    QuranWordText(WordKey(ayah.surah, ayah.ayah, 2))
                }
            }
        }
        waitForText("glyph2")

        assertEquals(0, nodeCountOf("glyph1"))
        assertEquals(
            setOf(4),
            fontRequests.filter { it.first == ScriptId.V4Tajweed }.map { it.second }.toSet(),
        )
    }

    @Test
    fun withoutAProviderTheBundledHafsFontDrawsNothingForWantOfText() {
        composeRule.setContent { AwnTheme { QuranWordText(WordKey(ayah.surah, ayah.ayah, 1)) } }
        composeRule.waitForIdle()

        assertEquals(0, nodeCountOf("text1"))
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 5_000L
    }
}
