package app.awn.quran.feature.read

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.core.ui.theme.ColourMode
import app.awn.quran.core.ui.theme.LocalColourMode
import app.awn.quran.feature.read.page.FontPackStatus
import app.awn.quran.feature.read.quran.GoToPageDialog
import app.awn.quran.feature.read.quran.PrintOfferActions
import app.awn.quran.feature.read.quran.PrintOfferCard
import app.awn.quran.feature.read.quran.PrintOfferState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val LARGE_FONT_SCALE = 1.3f
private val TOUCH_TARGET = 48.dp

/** The print offer card and the Go to page card in Awn and Black, at two font sizes. */
abstract class CardsRenderChecks {
    @get:Rule val composeRule = createComposeRule()

    private val fixture = ReadFixture()

    abstract val chooseTitle: String
    abstract val downloadLabel: String
    abstract val notNowLabel: String
    abstract val keepReadingLabel: String
    abstract val progressLine: String
    abstract val goLabel: String

    private val statuses =
        mapOf(
            ScriptId.V2 to FontPackStatus(ScriptId.V2, false, downloadBytes = 137_000_000L),
            ScriptId.V1 to FontPackStatus(ScriptId.V1, false, downloadBytes = 55_000_000L),
            ScriptId.V4Tajweed to
                FontPackStatus(ScriptId.V4Tajweed, false, downloadBytes = 69_000_000L),
        )

    private val noActions = PrintOfferActions({}, {}, {}, {}, {})

    private fun show(mode: ColourMode, fontScale: Float, content: @Composable () -> Unit) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalColourMode provides mode,
                LocalDensity provides Density(density.density, fontScale = fontScale),
            ) {
                AwnTheme { content() }
            }
        }
        composeRule.waitForIdle()
    }

    private fun SemanticsNodeInteraction.assertTouchTarget() =
        assertIsDisplayed().assertHeightIsAtLeast(TOUCH_TARGET)

    private fun checkAskingCard(mode: ColourMode, fontScale: Float) {
        show(mode, fontScale) {
            PrintOfferCard(PrintOfferState(isOpen = true), statuses, noActions)
        }

        composeRule.onNodeWithText(chooseTitle).assertIsDisplayed()
        composeRule.onNodeWithText(downloadLabel).assertTouchTarget()
        composeRule.onNodeWithText(notNowLabel).assertTouchTarget()
    }

    private fun checkDownloadingCard(mode: ColourMode, fontScale: Float) {
        val running =
            statuses + (ScriptId.V2 to statuses.getValue(ScriptId.V2).copy(progress = 0.42f))
        show(mode, fontScale) {
            PrintOfferCard(
                PrintOfferState(isOpen = true, downloading = ScriptId.V2),
                running,
                noActions,
            )
        }

        composeRule.onNodeWithText(progressLine).assertIsDisplayed()
        composeRule.onNodeWithText(keepReadingLabel).assertTouchTarget()
    }

    private fun checkGoCard(mode: ColourMode, fontScale: Float) {
        show(mode, fontScale) { goCard(onGo = {}) }

        composeRule.onNodeWithText(goLabel).assertTouchTarget()
        field(0).assertIsFocused()
    }

    @Composable
    private fun goCard(onGo: (Int) -> Unit) {
        GoToPageDialog(
            currentPage = 2,
            quranText = fixture.text,
            script = ScriptId.QpcHafs,
            chapters = listOf(ReadFixture.fatiha, ReadFixture.baqarah),
            onGo = onGo,
            onDismiss = {},
        )
    }

    private fun field(index: Int) = composeRule.onAllNodes(hasSetTextAction())[index]

    @Test fun askingCardInAwnAtNormalSize() = checkAskingCard(ColourMode.Awn, 1.0f)

    @Test fun askingCardInAwnAtLargeSize() = checkAskingCard(ColourMode.Awn, LARGE_FONT_SCALE)

    @Test fun askingCardInBlackAtNormalSize() = checkAskingCard(ColourMode.Black, 1.0f)

    @Test fun askingCardInBlackAtLargeSize() = checkAskingCard(ColourMode.Black, LARGE_FONT_SCALE)

    @Test
    fun downloadingCardInAwnAtLargeSize() = checkDownloadingCard(ColourMode.Awn, LARGE_FONT_SCALE)

    @Test fun downloadingCardInBlackAtNormalSize() = checkDownloadingCard(ColourMode.Black, 1.0f)

    @Test fun goCardInAwnAtNormalSize() = checkGoCard(ColourMode.Awn, 1.0f)

    @Test fun goCardInAwnAtLargeSize() = checkGoCard(ColourMode.Awn, LARGE_FONT_SCALE)

    @Test fun goCardInBlackAtNormalSize() = checkGoCard(ColourMode.Black, 1.0f)

    @Test fun goCardInBlackAtLargeSize() = checkGoCard(ColourMode.Black, LARGE_FONT_SCALE)

    @Test
    fun goCardOpensFocusedWithTheCurrentPageAndItsFirstAyah() {
        show(ColourMode.Awn, 1.0f) { goCard(onGo = {}) }

        field(0).assertIsFocused()
        field(0).assertTextContains(localDigits(2))
    }

    @Test
    fun theKeyboardActionKeyDoesWhatGoDoes() {
        var openedPage: Int? = null
        show(ColourMode.Awn, 1.0f) { goCard(onGo = { openedPage = it }) }

        field(0).performTextReplacement("1")
        field(0).performImeAction()

        assertEquals(1, openedPage)
    }

    @Test
    fun theGoButtonOpensTheTypedPage() {
        var openedPage: Int? = null
        show(ColourMode.Awn, 1.0f) { goCard(onGo = { openedPage = it }) }

        field(0).performTextReplacement("1")
        composeRule.onNodeWithText(goLabel).performClick()

        assertEquals(1, openedPage)
    }

    @Test
    fun typingSurahAndAyahFillsThePageField() {
        fixture.text.pageByAyah[AyahRef(2, 1)] = 2
        show(ColourMode.Awn, 1.0f) { goCard(onGo = {}) }

        field(0).performTextReplacement("1")
        field(1).performTextReplacement("2")
        field(2).performTextReplacement("1")
        composeRule.waitForIdle()

        field(0).assertTextContains(localDigits(2))
    }

    @Test
    fun banglaDigitsTypedInTheFieldAreUnderstood() {
        var openedPage: Int? = null
        show(ColourMode.Awn, 1.0f) { goCard(onGo = { openedPage = it }) }

        field(0).performTextReplacement("১")
        field(0).performImeAction()

        assertEquals(1, openedPage)
    }

    private fun localDigits(number: Int): String =
        java.text.NumberFormat.getIntegerInstance().apply { isGroupingUsed = false }.format(number)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class CardsRenderEnglishTest : CardsRenderChecks() {
    override val chooseTitle = "Choose a print"
    override val downloadLabel = "Download 137 MB"
    override val notNowLabel = "Not now"
    override val keepReadingLabel = "Keep reading"
    override val progressLine = "42% of 137 MB"
    override val goLabel = "Go"
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "bn-w411dp-h891dp")
class CardsRenderBanglaTest : CardsRenderChecks() {
    override val chooseTitle = "ছাপা বেছে নিন"
    override val downloadLabel = "ডাউনলোড ১৩৭ এমবি"
    override val notNowLabel = "এখন নয়"
    override val keepReadingLabel = "পড়তে থাকুন"
    override val progressLine = "১৩৭ এমবির ৪২%"
    override val goLabel = "যান"
}
