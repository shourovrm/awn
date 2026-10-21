package app.awn.quran.feature.read

import android.content.Context
import android.text.format.Formatter
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.data.downloads.PackTransfer
import app.awn.quran.core.data.packs.PackFailureReason
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.read.quran.AyahWord
import app.awn.quran.feature.read.quran.WordAudioOffer
import app.awn.quran.feature.read.quran.WordPopup
import app.awn.quran.feature.read.quran.WordPopupCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What the word card shows in place of the play button while the word audio is not ready. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WordAudioOfferRenderTest {
    @get:Rule val composeRule = createComposeRule()

    private fun show(offer: WordAudioOffer, onDownload: () -> Unit = {}) {
        val popup =
            WordPopup(
                ayah = AyahRef(1, 1),
                word = AyahWord(WordKey(1, 1, 1), ReadFixture.FIRST_WORD, "In (the) name", "bismi"),
                audioOffer = offer,
            )
        composeRule.setContent {
            AwnTheme {
                WordPopupCard(
                    popup = popup,
                    anchor = null,
                    topClearancePx = 0f,
                    onDismiss = {},
                    isPlaying = false,
                    onPlay = {},
                    onStop = {},
                    onDownloadAudio = onDownload,
                )
            }
        }
    }

    @Test
    fun theDownloadIsOfferedWithItsSize() {
        var downloads = 0
        show(WordAudioOffer.Download(3_162_112L)) { downloads++ }

        val context = ApplicationProvider.getApplicationContext<Context>()
        composeRule
            .onNodeWithText(Formatter.formatShortFileSize(context, 3_162_112L))
            .assertIsDisplayed()
        composeRule.onNodeWithText("Download word audio").performClick()

        assertEquals(1, downloads)
    }

    @Test
    fun progressReplacesTheDownloadWhileItRuns() {
        show(WordAudioOffer.Downloading(0.4f))

        composeRule.onNodeWithText("Downloading 40%").assertIsDisplayed()
        composeRule.onNodeWithText("Download word audio").assertDoesNotExist()
    }

    @Test
    fun aFailureNamesTheCauseAndOffersTheDownloadAgain() {
        show(WordAudioOffer.Failed(PackTransfer.Failed(PackFailureReason.NoInternet), 3_162_112L))

        composeRule.onNodeWithText("No connection. Check your internet.").assertIsDisplayed()
        composeRule.onNodeWithText("Download word audio").assertIsDisplayed()
    }
}
