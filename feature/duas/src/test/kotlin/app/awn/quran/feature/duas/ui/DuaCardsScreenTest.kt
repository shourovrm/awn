package app.awn.quran.feature.duas.ui

import android.Manifest
import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.TranslationId
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.duas.DuasFixtures
import app.awn.quran.feature.duas.DuasTestEnvironment
import app.awn.quran.feature.duas.RECITER_PICKER_TEXT
import app.awn.quran.feature.duas.card.CardRequest
import app.awn.quran.feature.duas.card.DuaCardLoader
import app.awn.quran.feature.duas.content.Dua
import app.awn.quran.feature.duas.data.DuaProgress
import app.awn.quran.feature.duas.state.DuasState
import app.awn.quran.feature.recite.RecitationPhase
import app.awn.quran.player.PlaybackState
import app.awn.quran.tracker.WordProblem
import app.awn.quran.tracker.WordState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/** The card screen: card content, the three modes, saving, searching and swiping. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
class DuaCardsScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private var environment = DuasTestEnvironment()
    private var wentBack = 0
    private var searched = 0
    private val toggledSaved = mutableListOf<Dua>()
    private val openedInMushaf = mutableListOf<Dua>()
    private val cleanRecitations = mutableListOf<Dua>()
    private val requestedTiles = mutableListOf<Dua>()

    private val d1Words = (4..6).map { WordKey(2, 201, it) }

    private fun stateOf(
        translationId: TranslationId? = DuasFixtures.englishTranslation,
        progress: DuaProgress = DuaProgress(),
    ): DuasState {
        val loader = DuaCardLoader(environment.quranText, environment.translations)
        val cards = runBlocking {
            loader.load(DuasFixtures.catalogue.duas, CardRequest(translationId, "en"))
        }
        val tiles = runBlocking { loader.wordTiles(cards.first(), "en") }
        return DuasState(
            catalogue = DuasFixtures.catalogue,
            progress = progress,
            cards = cards.associateBy { it.dua.id },
            wordTiles = mapOf("d1" to tiles),
        )
    }

    private fun show(startIndex: Int = 0, state: DuasState = stateOf()) {
        val application = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(application).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val dependencies = environment.dependencies()
        composeRule.setContent {
            AwnTheme(barBehindStatusBar = true) {
                DuaCardsScreen(
                    title = "Good in this world and the next",
                    duas = listOf(DuasFixtures.d1, DuasFixtures.d2),
                    startIndex = startIndex,
                    state = state,
                    dependencies = dependencies,
                    actions =
                        DuaCardsActions(
                            onBack = { wentBack++ },
                            onSearch = { searched++ },
                            onToggleSaved = { toggledSaved += it },
                            onOpenMushaf = { openedInMushaf += it },
                            onCleanRecitation = { cleanRecitations += it },
                            onRequestWordTiles = { requestedTiles += it },
                        ),
                )
            }
        }
    }

    private fun chooseMode(mode: DuaCardMode) {
        composeRule.onNodeWithTag(modeTag(mode)).performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun theBarNamesTheGroupAndThePosition() {
        show()

        composeRule.onNodeWithText("Good in this world and the next").assertIsDisplayed()
        composeRule.onNodeWithText("1 of 2").assertIsDisplayed()
    }

    @Test
    fun theCardShowsTheReferenceTheTagTheArabicAndTheTranslation() {
        show()

        composeRule.onNodeWithText("Al-Baqarah 2:201").assertIsDisplayed()
        composeRule.onNodeWithText("Rabbana").assertIsDisplayed()
        composeRule.onNodeWithText("Our Lord, give us good in this world.").assertIsDisplayed()
        (1..6).forEach { position ->
            composeRule.onNodeWithTag(cardWordTag(WordKey(2, 201, position))).assertExists()
        }
    }

    @Test
    fun withNoTranslationTickedTheCardShowsNoTranslationAndNoError() {
        environment = DuasTestEnvironment(translationsTicked = emptyList())

        show(state = stateOf(translationId = null))

        composeRule.onNodeWithText("Al-Baqarah 2:201").assertIsDisplayed()
        composeRule.onNodeWithText("Our Lord, give us good in this world.").assertDoesNotExist()
    }

    @Test
    fun openInTheMushafReportsTheDua() {
        show()

        composeRule.onNodeWithText("Open in the Mushaf").performClick()

        assertEquals(listOf(DuasFixtures.d1), openedInMushaf)
    }

    @Test
    fun showDuaOnlyHidesTheWordsBeforeTheDuaAndCanBeUndone() {
        show()
        val outsideWord = cardWordTag(WordKey(2, 201, 1))
        val duaWord = cardWordTag(WordKey(2, 201, 4))

        composeRule.onNodeWithText("Show dua only").performClick()

        composeRule.onNodeWithTag(outsideWord).assertDoesNotExist()
        composeRule.onNodeWithTag(duaWord).assertExists()
        composeRule.onNodeWithText("Show whole ayah").performClick()
        composeRule.onNodeWithTag(outsideWord).assertExists()
    }

    @Test
    fun aDuaThatFillsItsAyahsHasNoShowDuaOnlyLink() {
        show(startIndex = 1)

        composeRule.onNodeWithText("2 of 2").assertIsDisplayed()
        composeRule.onNodeWithText("Show dua only").assertDoesNotExist()
        composeRule.onNodeWithText("Open in the Mushaf").assertIsDisplayed()
    }

    @Test
    fun theSaveButtonReportsTheDua() {
        show()

        composeRule.onNodeWithContentDescription("Save dua").performClick()

        assertEquals(listOf(DuasFixtures.d1), toggledSaved)
    }

    @Test
    fun aSavedDuaOffersToRemoveIt() {
        show(state = stateOf(progress = DuaProgress(savedIds = setOf("d1"))))

        composeRule.onNodeWithContentDescription("Remove from saved").assertIsDisplayed()
    }

    @Test
    fun theSearchButtonAsksForTheSearch() {
        show()

        composeRule.onNodeWithContentDescription("Search duas").performClick()

        assertEquals(1, searched)
    }

    @Test
    fun backIsReported() {
        show()

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertEquals(1, wentBack)
    }

    @Test
    fun listenShowsTheReciterAndPlaysTheWholeAyahsOfTheDua() {
        show()
        composeRule.waitUntil {
            composeRule.onAllNodesWithText("Mishary Alafasy").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag(LISTEN_PLAY_TAG).performClick()
        composeRule.waitForIdle()

        assertEquals(
            listOf(Triple("alafasy", AyahRef(2, 201), AyahRef(2, 201))),
            environment.audioPlayer.playCalls,
        )
    }

    @Test
    fun aDuaOverTwoAyahsPlaysFromTheFirstToTheLast() {
        show(startIndex = 1)

        composeRule.onNodeWithTag(LISTEN_PLAY_TAG).performClick()
        composeRule.waitForIdle()

        assertEquals(
            listOf(Triple("alafasy", AyahRef(20, 25), AyahRef(20, 26))),
            environment.audioPlayer.playCalls,
        )
    }

    @Test
    fun whileItPlaysThePanelNamesTheWordAndStops() {
        show()
        composeRule.runOnIdle {
            environment.audioPlayer.currentState.value =
                PlaybackState.Playing("alafasy", AyahRef(2, 201), 5, 0L, null)
        }

        composeRule.onNodeWithText("Word 5 of 6").assertIsDisplayed()
        composeRule.onNodeWithTag(LISTEN_PLAY_TAG).performClick()

        assertEquals(1, environment.audioPlayer.stopCalls)
    }

    @Test
    fun repeatCyclesThroughThreeAndFiveTimes() {
        show()

        composeRule.onNodeWithTag(LISTEN_REPEAT_TAG).performClick()
        composeRule.waitForIdle()
        assertEquals(2, environment.audioPlayer.repeatTimes)
        composeRule.onNodeWithText("3×").assertIsDisplayed()

        composeRule.onNodeWithTag(LISTEN_REPEAT_TAG).performClick()
        composeRule.waitForIdle()
        assertEquals(4, environment.audioPlayer.repeatTimes)
    }

    @Test
    fun theReciterNameOpensTheReciterSheetAndThereIsNoSpeedButton() {
        show()

        composeRule.onNodeWithTag(LISTEN_RECITER_TAG).performClick()

        composeRule.onNodeWithText(RECITER_PICKER_TEXT).assertIsDisplayed()
        composeRule.onNodeWithText("1.25×").assertDoesNotExist()
    }

    @Test
    fun swipingToTheNextCardStopsTheAudioOfTheLastOne() {
        show()
        composeRule.runOnIdle {
            environment.audioPlayer.currentState.value =
                PlaybackState.Playing("alafasy", AyahRef(2, 201), 2, 0L, null)
        }

        composeRule.onRoot().performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("2 of 2").assertIsDisplayed()
        assertTrue(environment.audioPlayer.stopCalls >= 1)
    }

    @Test
    fun reciteLoadsExactlyTheDuasAyahsAndWaitsForTheMicrophone() {
        show()

        chooseMode(DuaCardMode.Recite)

        assertEquals(listOf(listOf(AyahRef(2, 201)) to false), environment.recitation.begun)
        composeRule.onNodeWithText("Say the dua from the start").assertIsDisplayed()
        composeRule.onNodeWithTag(RECITE_MICROPHONE_TAG).performClick()
        composeRule.waitForIdle()
        assertEquals(1, environment.recitation.startCount)
        composeRule.onNodeWithText("Listening").assertIsDisplayed()
    }

    @Test
    fun hidingTheWordsHidesTheTranslationAndTellsTheChecker() {
        show()
        chooseMode(DuaCardMode.Recite)

        composeRule.onNodeWithTag(RECITE_HIDE_TAG).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(true), environment.recitation.hideCalls)
        composeRule.onNodeWithText("Translation hidden while you recite").assertIsDisplayed()
        composeRule.onNodeWithText("Our Lord, give us good in this world.").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Show words").assertIsDisplayed()
    }

    @Test
    fun everyDuaWordConfirmedCountsAsACleanRecitation() {
        show()
        chooseMode(DuaCardMode.Recite)

        composeRule.runOnIdle {
            environment.recitation.wordStates.value = d1Words.associateWith { WordState.Confirmed }
        }
        composeRule.waitForIdle()

        assertEquals(listOf(DuasFixtures.d1), cleanRecitations)
        composeRule.onNodeWithText("Clean recitation").assertIsDisplayed()
        assertTrue(environment.recitation.stopCount >= 1)
    }

    @Test
    fun aTentativeWordIsNotACleanRecitation() {
        show()
        chooseMode(DuaCardMode.Recite)

        composeRule.runOnIdle {
            environment.recitation.wordStates.value =
                d1Words.associateWith { WordState.Confirmed } +
                    (d1Words.last() to WordState.Tentative)
        }
        composeRule.waitForIdle()

        assertTrue(cleanRecitations.isEmpty())
    }

    @Test
    fun theCountOfCleanRecitationsIsShown() {
        show(state = stateOf(progress = DuaProgress(cleanRecitations = mapOf("d1" to 2))))

        chooseMode(DuaCardMode.Recite)

        composeRule.onNodeWithText("2 of 3 clean recitations").assertIsDisplayed()
    }

    @Test
    fun aWrongWordOpensTheReviewCardAndTappingItPlaysTheWord() {
        show()
        chooseMode(DuaCardMode.Recite)
        val wrongWord = WordKey(2, 201, 5)

        composeRule.runOnIdle {
            environment.recitation.wordStates.value = mapOf(wrongWord to WordState.Wrong)
            environment.recitation.wordProblems.value =
                mapOf(wrongWord to WordProblem.DifferentWord)
            environment.recitation.phase.value = RecitationPhase.Stopped
        }
        composeRule.waitForIdle()

        composeRule
            .onNodeWithText("This word did not sound right. Tap to hear it, then say it again.")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(REVIEW_WORD_TAG).performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(wrongWord), environment.playedWords)

        composeRule.onNodeWithTag(REVIEW_CLOSE_TAG).performClick()
        composeRule.onNodeWithTag(REVIEW_WORD_TAG).assertDoesNotExist()
    }

    @Test
    fun tappingAFlaggedWordOnTheCardOpensItsReview() {
        show()
        chooseMode(DuaCardMode.Recite)
        val skipped = WordKey(2, 201, 6)

        composeRule.runOnIdle {
            environment.recitation.wordProblems.value = mapOf(skipped to WordProblem.Skipped)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(cardWordTag(skipped)).performClick()

        composeRule
            .onNodeWithText("This word was not heard. Tap to hear it, then say it again.")
            .assertIsDisplayed()
    }

    @Test
    fun leavingReciteEndsTheRecitation() {
        show()
        chooseMode(DuaCardMode.Recite)

        chooseMode(DuaCardMode.Listen)

        assertEquals(1, environment.recitation.endCount)
    }

    @Test
    fun theDownloadOfferReplacesTheControlsAndLeavingItGoesBackToListen() {
        environment.reciteGate = { onCancel, _ ->
            Text(text = "Get speech files", modifier = Modifier.clickable(onClick = onCancel))
        }
        show()

        chooseMode(DuaCardMode.Recite)

        composeRule.onNodeWithText("Get speech files").assertIsDisplayed()
        assertTrue(environment.recitation.begun.isEmpty())
        composeRule.onNodeWithText("Get speech files").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(LISTEN_PLAY_TAG).assertIsDisplayed()
    }

    @Test
    fun wordByWordShowsTilesWithMeaningAndSoundForTheDuaWordsOnly() {
        show()

        chooseMode(DuaCardMode.WordByWord)

        d1Words.forEach { composeRule.onNodeWithTag(wordTileTag(it)).assertIsDisplayed() }
        composeRule.onNodeWithTag(wordTileTag(WordKey(2, 201, 1))).assertDoesNotExist()
        composeRule.onNodeWithText("Our Lord").assertIsDisplayed()
        composeRule.onNodeWithText("rabbana").assertIsDisplayed()
        assertTrue(DuasFixtures.d1 in requestedTiles)
    }

    @Test
    fun tappingATilePlaysTheWordAndOffersToAddIt() {
        show()
        chooseMode(DuaCardMode.WordByWord)
        val key = WordKey(2, 201, 5)

        composeRule.onNodeWithTag(wordTileTag(key)).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(key), environment.playedWords)
        composeRule.onNodeWithTag(ADD_WORD_TAG).performClick()
        composeRule.waitForIdle()
        assertEquals(setOf(key), environment.myWords.addedWords)
        composeRule.onNodeWithText("In my words").assertIsDisplayed()
    }
}
