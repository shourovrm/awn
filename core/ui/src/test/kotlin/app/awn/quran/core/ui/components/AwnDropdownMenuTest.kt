package app.awn.quran.core.ui.components

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.awn.quran.core.ui.theme.AwnTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class AwnDropdownMenuTest {

    @get:Rule val composeRule = createComposeRule()

    private fun show(isExpanded: Boolean, onItemClick: () -> Unit = {}) {
        composeRule.setContent {
            AwnTheme {
                AwnDropdownMenu(expanded = isExpanded, onDismissRequest = {}) {
                    DropdownMenuItem(text = { Text("Item") }, onClick = onItemClick)
                }
            }
        }
    }

    @Test
    fun anExpandedMenuShowsItsRowsAndTheyCanBeTapped() {
        var taps = 0
        show(isExpanded = true, onItemClick = { taps += 1 })

        composeRule.onNodeWithText("Item").assertIsDisplayed()
        composeRule.onNodeWithText("Item").performClick()

        assertEquals(1, taps)
    }

    @Test
    fun aCollapsedMenuShowsNothing() {
        show(isExpanded = false)

        composeRule.onNodeWithText("Item").assertDoesNotExist()
    }
}
