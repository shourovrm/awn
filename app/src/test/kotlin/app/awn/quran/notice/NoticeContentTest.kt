package app.awn.quran.notice

import android.app.Application
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.awn.quran.core.ui.theme.AwnField
import app.awn.quran.core.ui.theme.AwnTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NoticeContentTest {

    @get:Rule val composeRule = createComposeRule()

    private fun showNotice() {
        composeRule.setContent { AwnTheme(field = AwnField.Paper) { NoticeContent() } }
    }

    @Test
    fun theNoticeIsShownInEnglishWhenTheInterfaceIsEnglish() {
        showNotice()

        composeRule.onNodeWithText("Notice").assertIsDisplayed()
        composeRule
            .onNodeWithText("This app is an artificial-intelligence", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("এই অ্যাপ", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("هذا المعلّم", substring = true).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "bn")
    fun theNoticeIsShownInBanglaWhenTheInterfaceIsBangla() {
        showNotice()

        composeRule.onNodeWithText("বিজ্ঞপ্তি").assertIsDisplayed()
        composeRule.onNodeWithText("এই অ্যাপ", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("This app is", substring = true).assertDoesNotExist()
    }
}
