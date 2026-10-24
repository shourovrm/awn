package app.awn.quran.feature.duas.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.quranfont.PageFontCache
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.duas.DuasFixtures
import app.awn.quran.feature.duas.DuasTestEnvironment
import app.awn.quran.feature.duas.NoPacks
import app.awn.quran.feature.duas.card.CardRequest
import app.awn.quran.feature.duas.card.DuaCardData
import app.awn.quran.feature.duas.card.DuaCardLoader
import app.awn.quran.feature.read.settings.PageView
import app.awn.quran.feature.read.settings.ReadSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The dua card's words as the Mushaf's word flow draws them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
class DuaLineViewTest {

    @get:Rule val composeRule = createComposeRule()

    private val environment = DuasTestEnvironment()
    private val outsideWord = WordKey(2, 201, 1)
    private val duaWord = WordKey(2, 201, 4)
    private val ayah = AyahRef(2, 201)

    private fun cardOfFirstDua(): DuaCardData = runBlocking {
        DuaCardLoader(environment.quranText, environment.translations)
            .load(listOf(DuasFixtures.d1), CardRequest(DuasFixtures.englishTranslation, "en"))
            .single()
    }

    private fun show(showDuaOnly: Boolean, card: DuaCardData = cardOfFirstDua()) {
        val settings = ReadSettings(script = ScriptId.QpcHafs, pageView = PageView.LineByLine)
        val resources =
            LineViewResources(
                settings = settings,
                fontCache = PageFontCache(NoPacks),
                arabicFamily = null,
                quranText = environment.quranText,
            )
        composeRule.setContent {
            AwnTheme {
                DuaLineView(
                    card = card,
                    wordTiles = emptyList(),
                    showDuaOnly = showDuaOnly,
                    highlights = CardHighlights(),
                    settings = settings,
                    resources = resources,
                    onWordTap = { _, _, _ -> },
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun duaOnlyLeavesOutTheWordsOutsideTheDua() {
        show(showDuaOnly = true)

        composeRule.onNodeWithTag(cardWordTag(outsideWord)).assertDoesNotExist()
        composeRule.onNodeWithTag(cardWordTag(duaWord)).assertExists()
    }

    @Test
    fun withoutDuaOnlyTheWholeAyahIsShown() {
        show(showDuaOnly = false)

        composeRule.onNodeWithTag(cardWordTag(outsideWord)).assertExists()
        composeRule.onNodeWithTag(cardWordTag(duaWord)).assertExists()
    }

    /** The card of the first dua with its ayah's last word taken out of the dua. */
    private fun cardWhoseLastWordIsOutsideTheDua(): DuaCardData {
        val card = cardOfFirstDua()
        val wordsWithoutDuaEnd =
            card.words.mapIndexed { index, cardWord ->
                if (index == card.words.lastIndex) cardWord.copy(isDuaWord = false) else cardWord
            }
        return card.copy(words = wordsWithoutDuaEnd)
    }

    @Test
    fun anAyahEndsWithItsMarkerWhenItsLastWordIsShown() {
        show(showDuaOnly = true)

        composeRule.onNodeWithTag(ayahEndTag(ayah)).assertExists()
    }

    @Test
    fun theMarkerIsShownWithTheWholeAyah() {
        show(showDuaOnly = false, card = cardWhoseLastWordIsOutsideTheDua())

        composeRule.onNodeWithTag(ayahEndTag(ayah)).assertExists()
    }

    @Test
    fun duaOnlyShowsNoMarkerWhenItHidesTheLastWord() {
        show(showDuaOnly = true, card = cardWhoseLastWordIsOutsideTheDua())

        composeRule.onNodeWithTag(ayahEndTag(ayah)).assertDoesNotExist()
    }

    @Test
    fun theMarkerIsTheAyahNumberInArabicIndicDigits() {
        assertEquals("\u0662\u0660\u0661", ayahEndText(201))
    }

    @Test
    fun wordsOutsideTheDuaAreTheOnesDimmed() {
        val expected = (1..3).map { position -> WordKey(2, 201, position) }.toSet()

        assertEquals(expected, wordsOutsideDua(cardOfFirstDua()))
    }

    @Test
    fun thePlayingWordFillsItsPositionInItsOwnAyahOnly() {
        val ayah = AyahRef(2, 201)

        assertEquals(5, playingPositionIn(ayah, WordKey(2, 201, 5)))
        assertNull(playingPositionIn(ayah, WordKey(2, 202, 5)))
        assertNull(playingPositionIn(ayah, null))
    }
}
