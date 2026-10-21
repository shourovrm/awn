package app.awn.quran.feature.recite

import android.Manifest
import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.core.ui.theme.ColourMode
import app.awn.quran.core.ui.theme.LocalColourMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private const val PHONE_WIDTH_DP = 360
private const val TOUCH_TARGET_DP = 48
private const val LARGEST_FONT_SCALE = 2f
private const val FRAME_TAG = "bar-frame"

/** Checks that the bar fits a 360 dp wide phone at the largest font scale, in every colour mode. */
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
@RunWith(RobolectricTestRunner::class)
class ReciteBarLayoutTest {

    @get:Rule val composeRule = createComposeRule()
    @get:Rule val folder = TemporaryFolder()

    private lateinit var fixture: ControllerFixture

    @Before
    fun setUp() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.RECORD_AUDIO)
        fixture = ControllerFixture(folder.root)
        fixture.controller.reciteFrom(AyahRef(1, 1))
        runBlocking { fixture.controller.joinPendingWork() }
    }

    private var colourMode by mutableStateOf(ColourMode.Awn)

    private fun show(fontScale: Float) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalColourMode provides colourMode,
                LocalDensity provides Density(density.density, fontScale),
            ) {
                AwnTheme {
                    Box(Modifier.width(PHONE_WIDTH_DP.dp).testTag(FRAME_TAG)) {
                        ReciteBar(fixture.controller, onExit = {})
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun revealTheAyahToShowEveryRow() {
        composeRule.onNodeWithTag(RECITE_BAR_HIDE_TAG).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            fixture.controller.session.hifzEnabled.value
        }
        composeRule.onNodeWithTag(RECITE_BAR_REVEAL_AYAH_TAG).performClick()
        composeRule.waitForIdle()
    }

    private fun assertEveryButtonFitsTheScreen() {
        val tags =
            listOf(
                RECITE_BAR_CLOSE_TAG,
                RECITE_BAR_SPEED_TAG,
                RECITE_BAR_HIDE_TAG,
                RECITE_BAR_HINT_TAG,
                RECITE_BAR_LISTEN_TAG,
                RECITE_BAR_MIC_TAG,
                RECITE_BAR_REVEAL_WORD_TAG,
                RECITE_BAR_REVEAL_AYAH_TAG,
            ) + listOf("easy", "good", "hard", "forgot").map { RECITE_BAR_GRADE_TAG_PREFIX + it }
        for (tag in tags) {
            val bounds = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertTrue("$tag left ${bounds.left}", bounds.left >= 0.dp)
            assertTrue("$tag right ${bounds.right}", bounds.right <= PHONE_WIDTH_DP.dp)
            assertTrue("$tag width ${bounds.width}", bounds.width >= TOUCH_TARGET_DP.dp)
            assertTrue("$tag height ${bounds.height}", bounds.height >= TOUCH_TARGET_DP.dp)
        }
        val frame = composeRule.onNodeWithTag(FRAME_TAG).getUnclippedBoundsInRoot()
        assertEquals(PHONE_WIDTH_DP.dp, frame.width)
    }

    @Test
    fun everyButtonFitsTheScreenAtTheLargestFontScaleInEveryMode() {
        show(LARGEST_FONT_SCALE)
        revealTheAyahToShowEveryRow()

        for (mode in ColourMode.entries) {
            colourMode = mode
            composeRule.waitForIdle()

            assertEveryButtonFitsTheScreen()
        }
    }
}
