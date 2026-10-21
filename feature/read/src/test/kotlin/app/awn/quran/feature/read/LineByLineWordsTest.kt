package app.awn.quran.feature.read

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.ResolvedTextDirection
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.Word
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.theme.AwnField
import app.awn.quran.core.ui.theme.AwnReciteFills
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.core.ui.theme.ColourMode
import app.awn.quran.core.ui.theme.awnFieldColors
import app.awn.quran.feature.read.page.PageFontCache
import app.awn.quran.feature.read.page.wordCellFill
import app.awn.quran.feature.read.quran.AyahBlock
import app.awn.quran.feature.read.quran.AyahWord
import app.awn.quran.feature.read.quran.ListPageEnvironment
import app.awn.quran.feature.read.quran.PageContent
import app.awn.quran.feature.read.quran.PageItem
import app.awn.quran.feature.read.quran.PageListView
import app.awn.quran.feature.read.quran.WordGlyphs
import app.awn.quran.feature.read.settings.PageView
import app.awn.quran.feature.read.settings.ReadSettings
import app.awn.quran.tracker.WordState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Arabic layer of the line-by-line view: page fonts for glyph prints and recite fills. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LineByLineWordsTest {
    @get:Rule val composeRule = createComposeRule()

    @get:Rule val inlinePageWork = InlinePageWorkRule()

    private val ayah = AyahRef(2, 6)
    private val packs = RecordingPackRepository()
    private val fontCache by lazy {
        packs.install(ScriptId.V4Tajweed, File("p.ttf"))
        PageFontCache(packs, createFamily = { FontFamily.Serif })
    }

    private fun glyphWord(position: Int, page: Int, hasGlyphs: Boolean = true): AyahWord {
        val key = WordKey(ayah.surah, ayah.ayah, position)
        val source = Word(key, "text$position", null, "glyph$position", null)
        return AyahWord(
            key = key,
            arabic = "text$position",
            meaning = null,
            transliteration = null,
            glyphs = if (hasGlyphs) WordGlyphs(source, page) else null,
        )
    }

    private fun environment(
        settings: ReadSettings,
        wordStates: Map<WordKey, WordState> = emptyMap(),
        isReciting: Boolean = false,
    ) =
        ListPageEnvironment(
            settings = settings,
            arabicFamily = null,
            fontCache = fontCache,
            wordStates = wordStates,
            isReciting = isReciting,
            selection = null,
            playingAyah = null,
            playingWordPosition = mutableStateOf(null),
            isFollowing = false,
            followRequest = 0,
            isPanelOpen = false,
            savedAyahs = emptySet(),
            contentPadding = PaddingValues(),
            onTap = {},
            onAyahLongPress = { _, _ -> },
            onWordTap = { _, _, _ -> },
            onUserScrolled = {},
        )

    private fun contentOf(vararg words: AyahWord) =
        PageContent(
            page = 3,
            items =
                listOf(
                    PageItem.Ayah(
                        AyahBlock(
                            ayah,
                            words.toList(),
                            transliteration = null,
                            translations = emptyList(),
                        ),
                    ),
                ),
        )

    private val tajweedSettings =
        ReadSettings(
            script = ScriptId.V4Tajweed,
            pageView = PageView.LineByLine,
            showWordByWord = false,
            translations = emptyList(),
            showTransliteration = false,
        )

    private fun show(content: PageContent, environment: ListPageEnvironment) {
        composeRule.setContent {
            AwnTheme { PageListView(content, isCurrent = true, environment = environment) }
        }
    }

    @Test
    fun aTajweedAyahDrawsEachWordWithItsGlyphCodeInThePageFont() {
        show(
            contentOf(glyphWord(1, page = 3), glyphWord(2, page = 3)),
            environment(tajweedSettings),
        )
        composeRule.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
            nodeCountOf("glyph1") == 1
        }

        composeRule.onNodeWithText("glyph1").assertIsDisplayed()
        composeRule.onNodeWithText("glyph2").assertIsDisplayed()
    }

    @Test
    fun eachWordAsksForTheFontOfItsOwnPage() {
        show(
            contentOf(glyphWord(1, page = 3), glyphWord(2, page = 4)),
            environment(tajweedSettings),
        )
        composeRule.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
            nodeCountOf("glyph2") == 1
        }

        val pagesAsked =
            packs.fontFileRequests.filter { it.script == ScriptId.V4Tajweed }.map { it.page }
        assertEquals(setOf(3, 4), pagesAsked.toSet())
    }

    @Test
    fun aWordWithoutGlyphsKeepsItsUnicodeText() {
        show(contentOf(glyphWord(1, page = 3, hasGlyphs = false)), environment(tajweedSettings))
        composeRule.waitForIdle()

        composeRule.onNodeWithText("text1").assertIsDisplayed()
    }

    @Test
    fun recitingSplitsAPlainParagraphIntoOneCellPerWord() {
        val plain = tajweedSettings.copy(script = ScriptId.QpcHafs)
        val words = arrayOf(glyphWord(1, 3, false), glyphWord(2, 3, false))
        val states = mapOf(words[0].key to WordState.Confirmed)
        show(contentOf(*words), environment(plain, wordStates = states, isReciting = true))
        composeRule.waitForIdle()

        composeRule.onNodeWithText("text1").assertIsDisplayed()
        composeRule.onNodeWithText("text2").assertIsDisplayed()
    }

    @Test
    fun aGlossReadsLeftToRightInsideTheRightToLeftCell() {
        val word = glyphWord(1, page = 3, hasGlyphs = false).copy(meaning = "(He) who")
        val wordByWord = tajweedSettings.copy(script = ScriptId.QpcHafs, showWordByWord = true)
        show(contentOf(word), environment(wordByWord))
        composeRule.waitForIdle()

        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText("(He) who").performSemanticsAction(
            SemanticsActions.GetTextLayoutResult,
        ) { action ->
            action(layouts)
        }

        assertEquals(ResolvedTextDirection.Ltr, layouts.single().getParagraphDirection(0))
    }

    @Test
    fun aWordStateIsAFillBehindTheWordInTheSameColoursAsTheMushafPage() {
        val day = awnFieldColors(AwnField.Paper, ColourMode.Light)
        val night = awnFieldColors(AwnField.Paper, ColourMode.Dark)

        assertEquals(AwnReciteFills.dayConfirmed, wordCellFill(WordState.Confirmed, day))
        assertEquals(AwnReciteFills.dayTentative, wordCellFill(WordState.Tentative, day))
        assertEquals(AwnReciteFills.nightWrong, wordCellFill(WordState.Wrong, night))
        assertNull(wordCellFill(WordState.Neutral, day))
    }

    @Test
    fun aHiddenWordIsABlockOfTheInkColour() {
        val day = awnFieldColors(AwnField.Paper, ColourMode.Light)

        val block: Color? = wordCellFill(WordState.Hidden, day)

        assertEquals(day.content.copy(alpha = 0.3f), block)
    }

    private fun nodeCountOf(text: String): Int =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private companion object {
        const val LOAD_TIMEOUT_MS = 5_000L
    }
}
