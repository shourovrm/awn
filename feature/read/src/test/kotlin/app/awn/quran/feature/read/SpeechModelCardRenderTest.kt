package app.awn.quran.feature.read

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.awn.quran.core.data.models.SpeechModelState
import app.awn.quran.core.data.packs.PackFailureReason
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.core.ui.theme.ColourMode
import app.awn.quran.core.ui.theme.LocalColourMode
import app.awn.quran.feature.read.quran.SpeechModelCard
import app.awn.quran.feature.read.quran.SpeechModelCardActions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val LARGE_FONT_SCALE = 1.3f
private const val TOTAL_BYTES = 88_000_000L
private val TOUCH_TARGET = 48.dp

/** The download card that recite shows before the speech files are on the phone. */
abstract class SpeechModelCardChecks {
    @get:Rule val composeRule = createComposeRule()

    abstract val titleLine: String
    abstract val bodyLine: String
    abstract val downloadLabel: String
    abstract val notNowLabel: String
    abstract val downloadingLine: String
    abstract val progressLine: String
    abstract val cancelLabel: String
    abstract val noInternetLine: String

    private var downloadTaps = 0
    private var notNowTaps = 0
    private var cancelTaps = 0

    private fun show(state: SpeechModelState, mode: ColourMode, fontScale: Float) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalColourMode provides mode,
                LocalDensity provides Density(density.density, fontScale = fontScale),
            ) {
                AwnTheme { Card(state) }
            }
        }
        composeRule.waitForIdle()
    }

    @Composable
    private fun Card(state: SpeechModelState) {
        SpeechModelCard(
            state = state,
            totalBytes = TOTAL_BYTES,
            actions =
                SpeechModelCardActions(
                    onDownload = { downloadTaps++ },
                    onNotNow = { notNowTaps++ },
                    onCancel = { cancelTaps++ },
                ),
        )
    }

    private fun checkAsking(mode: ColourMode, fontScale: Float) {
        show(SpeechModelState.NotInstalled, mode, fontScale)

        composeRule.onNodeWithText(titleLine).assertIsDisplayed()
        composeRule.onNodeWithText(bodyLine).assertIsDisplayed()
        composeRule
            .onNodeWithText(downloadLabel)
            .assertIsDisplayed()
            .assertHeightIsAtLeast(TOUCH_TARGET)
        composeRule
            .onNodeWithText(notNowLabel)
            .assertIsDisplayed()
            .assertHeightIsAtLeast(TOUCH_TARGET)
    }

    @Test fun askingInAwnAtNormalSize() = checkAsking(ColourMode.Awn, 1.0f)

    @Test fun askingInAwnAtLargeSize() = checkAsking(ColourMode.Awn, LARGE_FONT_SCALE)

    @Test fun askingInBlackAtLargeSize() = checkAsking(ColourMode.Black, LARGE_FONT_SCALE)

    @Test
    fun downloadAndNotNowReportTheirTaps() {
        show(SpeechModelState.NotInstalled, ColourMode.Awn, 1.0f)

        composeRule.onNodeWithText(downloadLabel).performClick()
        composeRule.onNodeWithText(notNowLabel).performClick()

        assertEquals(1, downloadTaps)
        assertEquals(1, notNowTaps)
    }

    @Test
    fun downloadingShowsProgressAndCancel() {
        show(SpeechModelState.Downloading(37_000_000L, TOTAL_BYTES), ColourMode.Awn, 1.0f)

        composeRule.onNodeWithText(downloadingLine).assertIsDisplayed()
        composeRule.onNodeWithText(progressLine).assertIsDisplayed()
        composeRule.onNodeWithText(cancelLabel).assertHeightIsAtLeast(TOUCH_TARGET).performClick()
        assertEquals(1, cancelTaps)
    }

    @Test
    fun aFailedDownloadSaysWhyAndOffersDownloadAgain() {
        show(
            SpeechModelState.Failed(PackFailureReason.NoInternet, "no route"),
            ColourMode.Awn,
            1.0f,
        )

        composeRule.onNodeWithText(noInternetLine).assertIsDisplayed()
        composeRule.onNodeWithText(downloadLabel).performClick()
        assertEquals(1, downloadTaps)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class SpeechModelCardEnglishTest : SpeechModelCardChecks() {
    override val titleLine = "Recite with Awn listening"
    override val bodyLine =
        "A one-time download of 88 MB. Awn then follows your words offline. Wi‑Fi recommended."
    override val downloadLabel = "Download 88 MB"
    override val notNowLabel = "Not now"
    override val downloadingLine = "Downloading"
    override val progressLine = "42% of 88 MB"
    override val cancelLabel = "Cancel"
    override val noInternetLine = "No connection. Check your internet."
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "bn-w411dp-h891dp")
class SpeechModelCardBanglaTest : SpeechModelCardChecks() {
    override val titleLine = "আওন শুনছে, আপনি তিলাওয়াত করুন"
    override val bodyLine =
        "একবার ৮৮ এমবি ডাউনলোড করতে হবে। তারপর আওন ইন্টারনেট ছাড়াই আপনার তিলাওয়াত অনুসরণ করবে। ওয়াই-ফাই ভালো।"
    override val downloadLabel = "ডাউনলোড ৮৮ এমবি"
    override val notNowLabel = "এখন নয়"
    override val downloadingLine = "ডাউনলোড হচ্ছে"
    override val progressLine = "৮৮ এমবির ৪২%"
    override val cancelLabel = "বাতিল"
    override val noInternetLine = "সংযোগ নেই। ইন্টারনেট দেখুন।"
}
