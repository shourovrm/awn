package app.awn.quran.feature.read

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.LineItem
import app.awn.quran.core.data.PageLayout
import app.awn.quran.core.data.PageLine
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.Word
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.read.page.ACCESSIBILITY_LAYER_DELAY_MS
import app.awn.quran.feature.read.page.MushafPage
import app.awn.quran.feature.read.page.SelectionBounds
import app.awn.quran.feature.read.page.spokenAyahsOf
import app.awn.quran.feature.read.quran.AyahActionBar
import app.awn.quran.feature.read.quran.AyahBarActions
import app.awn.quran.feature.read.quran.PlayButtonMode
import app.awn.quran.feature.read.quran.QuranTopBar
import app.awn.quran.feature.read.quran.QuranUiState
import app.awn.quran.feature.read.quran.TopBarActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val DOUBLE_FONT_SCALE = 2f

// The test window is 360 dp wide at density 1.
private const val SCREEN_WIDTH_PX = 360f

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class AccessibilityTest {
    @get:Rule val composeRule = createComposeRule()

    @get:Rule val inlinePageWork = InlinePageWorkRule()

    private val doNothing = {}
    private val topBarActions =
        TopBarActions(
            onOpenIndex = doNothing,
            onSwitchView = doNothing,
            onOpenLayers = doNothing,
            onToggleLayer = {},
            onToggleTranslation = {},
            onToggleNight = doNothing,
            onOpenTextSize = doNothing,
            onOpenOverflow = doNothing,
            onDismissPopup = doNothing,
            onTextSizeChange = { _, _ -> },
            onMushafSizeChange = {},
            onSearch = doNothing,
            onGoToPage = doNothing,
            onBookmarkPage = doNothing,
            onRotate = doNothing,
            onLearn = doNothing,
            onSettings = doNothing,
        )

    private val barDescriptions =
        listOf("Index", "Settings", "More", "Text size", "Switch to night", "Line by line")

    private fun atDoubleFontScale(content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = DOUBLE_FONT_SCALE),
            ) {
                AwnTheme { content() }
            }
        }
    }

    private fun textLayoutOf(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        composeRule
            .onNodeWithText(text)
            .fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action
            ?.invoke(results)
        return results.single()
    }

    @Test
    fun theTopBarAtTwiceTheFontSizeShowsEveryButtonAndTheWholeTitle() {
        atDoubleFontScale {
            Box(Modifier.width(360.dp)) {
                QuranTopBar(
                    state = QuranUiState(currentPage = 2, juz = 1),
                    surahName = "Al-Baqarah",
                    isNight = false,
                    actions = topBarActions,
                )
            }
        }

        for (description in barDescriptions) {
            composeRule
                .onNodeWithContentDescription(description)
                .assertIsDisplayed()
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
        }
        composeRule.onNodeWithText("Al-Baqarah").assertIsDisplayed()
        composeRule.onNodeWithText("Page 2 · Juz 1").assertIsDisplayed()

        // The buttons moved under the title, so the title has the whole bar width and no line was
        // dropped. (Robolectric measures every glyph as one pixel wide, so
        // clipping by width is checked on the phone at font scale 2.0.)
        val title = composeRule.onNodeWithText("Al-Baqarah").fetchSemanticsNode().boundsInRoot
        val more =
            composeRule.onNodeWithContentDescription("More").fetchSemanticsNode().boundsInRoot
        assertTrue("buttons are under the title", more.top >= title.bottom)
        assertTrue(more.right <= SCREEN_WIDTH_PX)
        for (text in listOf("Al-Baqarah", "Page 2 · Juz 1")) {
            val layout = textLayoutOf(text)
            assertFalse(text, layout.multiParagraph.didExceedMaxLines)
        }
    }

    @Test
    fun theTopBarKeepsOneRowAtNormalFontSize() {
        composeRule.setContent {
            AwnTheme {
                Box(Modifier.width(360.dp)) {
                    QuranTopBar(
                        state = QuranUiState(),
                        surahName = "Al-Fatihah",
                        isNight = false,
                        actions = topBarActions,
                    )
                }
            }
        }

        val indexTop = composeRule.onNodeWithContentDescription("Index").fetchSemanticsNode()
        val moreTop = composeRule.onNodeWithContentDescription("More").fetchSemanticsNode()
        assertEquals(indexTop.boundsInRoot.top, moreTop.boundsInRoot.top, 1f)
    }

    @Test
    fun theFloatingBarAtTwiceTheFontSizeKeepsSixTouchTargetsOnScreen() {
        atDoubleFontScale {
            Box(Modifier.width(360.dp).height(640.dp)) {
                AyahActionBar(
                    playMode = PlayButtonMode.Play,
                    bounds =
                        SelectionBounds(
                            firstLine = Rect(40f, 600f, 600f, 700f),
                            all = Rect(40f, 600f, 600f, 900f),
                        ),
                    isBookmarked = false,
                    actions =
                        AyahBarActions(
                            doNothing,
                            doNothing,
                            doNothing,
                            doNothing,
                            doNothing,
                            doNothing,
                        ),
                    modifier = Modifier.width(360.dp).height(640.dp),
                )
            }
        }

        val descriptions =
            listOf(
                "Play from here",
                "Recite from here",
                "Bookmark",
                "Translation",
                "Copy",
                "Share",
            )
        for (description in descriptions) {
            composeRule
                .onNodeWithContentDescription(description)
                .assertIsDisplayed()
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun everyClickableControlOfTheBarsHasADescription() {
        composeRule.setContent {
            AwnTheme {
                Box(Modifier.width(360.dp).height(640.dp)) {
                    QuranTopBar(QuranUiState(), "Al-Fatihah", false, topBarActions)
                    AyahActionBar(
                        playMode = PlayButtonMode.Play,
                        bounds =
                            SelectionBounds(
                                Rect(40f, 600f, 600f, 700f),
                                Rect(40f, 600f, 600f, 900f),
                            ),
                        isBookmarked = true,
                        actions =
                            AyahBarActions(
                                doNothing,
                                doNothing,
                                doNothing,
                                doNothing,
                                doNothing,
                                doNothing,
                            ),
                        modifier = Modifier.width(360.dp).height(640.dp),
                    )
                }
            }
        }

        val clickable = composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertTrue(clickable.isNotEmpty())
        for (node in clickable) {
            val description = node.config.getOrNull(SemanticsProperties.ContentDescription)
            assertTrue(
                "A clickable node has no content description: ${node.config}",
                !description.isNullOrEmpty() && description.all { it.isNotBlank() },
            )
        }
    }

    private fun glyphWord(ayah: Int, position: Int, glyph: String) =
        Word(WordKey(2, ayah, position), "كلمة$ayah", null, glyph)

    private fun glyphLayout() =
        PageLayout(
            3,
            ScriptId.V2,
            (1..2).map { ayah ->
                PageLine(
                    ayah,
                    listOf(
                        LineItem.WordItem(glyphWord(ayah, 1, "ﭑ")),
                        LineItem.WordItem(glyphWord(ayah, 2, "ﭒ")),
                        LineItem.AyahEnd(AyahRef(2, ayah), "ﯓ"),
                    ),
                )
            },
        )

    @Test
    fun aGlyphPageSpeaksUnicodeTextOneNodePerAyah() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            AwnTheme {
                Box(Modifier.width(300.dp).height(450.dp)) {
                    MushafPage(
                        layout = glyphLayout(),
                        chapters = ReadFixture().text.chapterList,
                        fontFamily = FontFamily.Default,
                        plainFamily = null,
                        showPageInfo = false,
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000)

        val nodes =
            composeRule.onAllNodes(hasContentDescription("Al-Baqarah, ayah", substring = true))
        assertEquals(2, nodes.fetchSemanticsNodes().size)
        composeRule.onNodeWithContentDescription("Al-Baqarah, ayah 1. كلمة1 كلمة1").assertExists()
        composeRule.onNodeWithContentDescription("Al-Baqarah, ayah 2. كلمة2 كلمة2").assertExists()

        // The private-use codes that are drawn are hidden from screen readers.
        val hiddenGlyphs =
            composeRule.onAllNodes(
                hasText("ﭑ") and SemanticsMatcher.keyIsDefined(SemanticsProperties.InvisibleToUser),
                useUnmergedTree = true,
            )
        assertEquals(2, hiddenGlyphs.fetchSemanticsNodes().size)
        assertEquals(
            0,
            composeRule
                .onAllNodesWithContentDescription("ﭑ", substring = true)
                .fetchSemanticsNodes()
                .size,
        )
    }

    @Test
    fun theScreenReaderLayerIsBuiltOnlyAfterThePageHasAppeared() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            AwnTheme {
                Box(Modifier.width(300.dp).height(450.dp)) {
                    MushafPage(
                        layout = glyphLayout(),
                        chapters = ReadFixture().text.chapterList,
                        fontFamily = FontFamily.Default,
                        plainFamily = null,
                        showPageInfo = false,
                    )
                }
            }
        }

        composeRule.mainClock.advanceTimeBy(ACCESSIBILITY_LAYER_DELAY_MS - 50)
        val before =
            composeRule
                .onAllNodes(hasContentDescription("Al-Baqarah, ayah", substring = true))
                .fetchSemanticsNodes()
        composeRule.mainClock.advanceTimeBy(100)
        val after =
            composeRule
                .onAllNodes(hasContentDescription("Al-Baqarah, ayah", substring = true))
                .fetchSemanticsNodes()

        assertEquals(0, before.size)
        assertEquals(2, after.size)
    }

    @Test
    fun theSpokenTextOfAnAyahJoinsItsWordsInReadingOrder() {
        val spoken = spokenAyahsOf(glyphLayout())

        assertEquals(listOf(AyahRef(2, 1), AyahRef(2, 2)), spoken.map { it.ayah })
        assertEquals("كلمة1 كلمة1", spoken.first().arabic)
    }
}
