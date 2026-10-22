package app.awn.quran.feature.read

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.read.quran.QuranScreen
import app.awn.quran.feature.read.quran.QuranScreenSlots
import app.awn.quran.feature.read.quran.QuranTarget
import app.awn.quran.feature.read.settings.PageView
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * On a narrow screen Settings moves into the More menu. Choosing it opened Settings but left the
 * menu open, so Back from Settings showed the menu again. Every other item closes the menu first.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp")
class QuranMoreMenuRenderTest {
    @get:Rule val composeRule = createComposeRule()

    @get:Rule val inlinePageWork = InlinePageWorkRule()

    private val fixture = ReadFixture()

    @Test
    fun choosingSettingsInTheMoreMenuClosesTheMenu() {
        var settingsOpened = 0
        runBlocking { fixture.store.update { it.copy(pageView = PageView.LineByLine) } }
        composeRule.setContent {
            AwnTheme {
                QuranScreen(
                    fixture.dependencies,
                    QuranScreenSlots.Empty.copy(onOpenSettings = { settingsOpened += 1 }),
                    target = QuranTarget.Page(1),
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("More").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.waitForIdle()

        assertEquals(1, settingsOpened)
        // The menu's other items are gone once it closes.
        composeRule.onAllNodesWithText("Go to page").assertCountEquals(0)
    }
}
