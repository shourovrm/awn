package app.awn.quran.feature.recite

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.theme.AwnTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The mistake card has the play and save-word buttons of the word card. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReviewCardWordActionsTest {
    @get:Rule val composeRule = createComposeRule()

    private val key = WordKey(2, 137, 14)
    private val listened = mutableListOf<WordKey>()
    private val saved = mutableListOf<WordKey>()
    private val removed = mutableListOf<WordKey>()

    private fun showCard(isSaved: Boolean?) {
        composeRule.setContent {
            AwnTheme {
                ReciteReviewCard(
                    reviews = listOf(WordReview(key, listOf(ReviewFinding.WrongWord))),
                    focusedKey = key,
                    onFocus = {},
                    onListen = { listened += it },
                    onDismiss = {},
                    isSaved = isSaved,
                    onSave = { saved += it },
                    onRemove = { removed += it },
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun thePlayButtonPlaysTheWord() {
        showCard(isSaved = false)

        composeRule.onNodeWithTag(REVIEW_LISTEN_TAG).performClick()

        assertEquals(listOf(key), listened)
    }

    @Test
    fun aTapOnTheBookmarkSavesAWordThatIsNotSaved() {
        showCard(isSaved = false)

        composeRule.onNodeWithContentDescription("Add to my words").performClick()

        assertEquals(listOf(key), saved)
        assertEquals(emptyList<WordKey>(), removed)
    }

    @Test
    fun aTapOnTheFilledBookmarkRemovesTheWord() {
        showCard(isSaved = true)

        composeRule.onNodeWithContentDescription("Added to my words").performClick()

        assertEquals(listOf(key), removed)
        assertEquals(emptyList<WordKey>(), saved)
    }

    @Test
    fun withoutAWordListThereIsNoBookmark() {
        showCard(isSaved = null)

        composeRule.onNodeWithTag(REVIEW_LISTEN_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(REVIEW_SAVE_TAG).assertDoesNotExist()
    }
}
