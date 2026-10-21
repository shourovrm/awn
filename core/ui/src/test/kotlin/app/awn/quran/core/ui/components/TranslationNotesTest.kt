package app.awn.quran.core.ui.components

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TranslationNotesTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun onlyTheQuantumTranslationHasANote() {
        assertNotNull(translationNoteFor("bn_quantum"))
        assertNull(translationNoteFor("bn_zakaria"))
    }

    @Test
    fun theNoteFollowsTheNameInParenthesesAndOtherNamesStayAlone() {
        composeRule.setContent {
            Text(translationNameWithNote("Quantum Foundation", "bn_quantum"))
            Text(translationNameWithNote("Abu Bakr Zakaria", "bn_zakaria"))
        }

        composeRule.onNodeWithText("Quantum Foundation (meaning-based)").assertExists()
        composeRule.onNodeWithText("Abu Bakr Zakaria").assertExists()
    }
}
