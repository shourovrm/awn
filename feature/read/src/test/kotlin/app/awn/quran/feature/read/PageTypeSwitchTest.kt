package app.awn.quran.feature.read

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import app.awn.quran.core.data.LineItem
import app.awn.quran.core.data.PageLayout
import app.awn.quran.core.data.PageLine
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.Word
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.data.fakes.FakePackRepository
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.read.page.MushafPage
import app.awn.quran.feature.read.page.PageFontCache
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Follows a page type from the pack repository to the text that is drawn: the font lookup, the
 * cached family, and the glyph code or Unicode text on the page.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PageTypeSwitchTest {
    @get:Rule val composeRule = createComposeRule()

    private val unicode = "بِسْمِ"
    private val v1Code = 0xFB51.toChar().toString()
    private val v2Code = 0xFC41.toChar().toString()

    private fun layout(script: ScriptId): PageLayout {
        val word = Word(WordKey(1, 1, 1), unicode, v1Code, v2Code, null)
        return PageLayout(3, script, listOf(PageLine(1, listOf(LineItem.WordItem(word)))))
    }

    private val pageTypes = listOf(ScriptId.V1, ScriptId.V2, ScriptId.V4Tajweed)

    @Test
    fun installingAPageTypeGivesItsOwnFontFamilyForEveryPage() {
        val packs = FakePackRepository()
        val cache = PageFontCache(packs, createFamily = { file -> FontFamily(Font(file)) })
        pageTypes.forEach { assertNull(it.name, cache.familyFor(it, 3)) }

        pageTypes.forEach { packs.fontFiles[it] = File("${it.packId}.ttf") }

        val families = pageTypes.map { cache.familyFor(it, 3) }
        families.forEach { assertNotNull(it) }
        assertNotSame(families[0], families[1])
        assertNotSame(families[1], families[2])
        assertNotSame(families[0], families[2])
    }

    @Test
    fun switchingThePageTypeChangesTheGlyphCodesDrawnOnTheOpenPage() {
        var script by mutableStateOf(ScriptId.V1)
        var family: FontFamily? by mutableStateOf(FontFamily.Serif)
        composeRule.setContent {
            AwnTheme {
                MushafPage(
                    layout = layout(script),
                    chapters = emptyList(),
                    fontFamily = family,
                    plainFamily = null,
                    showPageInfo = false,
                )
            }
        }
        composeRule.onNodeWithText(v1Code).assertIsDisplayed()

        script = ScriptId.V2
        composeRule.waitForIdle()
        composeRule.onNodeWithText(v2Code).assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText(v1Code).fetchSemanticsNodes().size)

        script = ScriptId.V4Tajweed
        composeRule.waitForIdle()
        composeRule.onNodeWithText(v2Code).assertIsDisplayed()

        // The font is removed: the same page falls back to the Unicode text of the word.
        family = null
        composeRule.waitForIdle()
        composeRule.onNodeWithText(unicode).assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText(v2Code).fetchSemanticsNodes().size)
    }
}
