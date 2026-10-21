package app.awn.quran.feature.read

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.font.FontFamily
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.Word
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.read.page.PageFontCache
import app.awn.quran.feature.read.page.PageFontWord
import java.io.File
import java.util.Collections
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A word on screen when the reader switches to night must change to the night palette. It kept the
 * day font, so tajweed words in the line-by-line view stayed black on the dark background.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PageFontWordNightTest {
    @get:Rule val composeRule = createComposeRule()

    private val familiesCreatedFrom: MutableList<File> =
        Collections.synchronizedList(mutableListOf())
    private val fontCache by lazy {
        val packs = RecordingPackRepository()
        packs.install(ScriptId.V4Tajweed, File("p3.ttf"))
        PageFontCache(
            packs,
            createFamily = { file ->
                familiesCreatedFrom += file
                FontFamily.Serif
            },
            nightFileFor = { file -> File(file.path + ".night") },
        )
    }

    @Test
    fun switchingToNightLoadsTheNightFontOfAWordAlreadyShown() {
        val isNight = mutableStateOf(false)
        val word = Word(WordKey(2, 6, 1), "text", null, "glyph")
        composeRule.setContent {
            AwnTheme {
                PageFontWord(
                    word = word,
                    script = ScriptId.V4Tajweed,
                    page = 3,
                    fontCache = fontCache,
                    isNight = isNight.value,
                )
            }
        }
        composeRule.waitUntil(LOAD_TIMEOUT_MS) { familiesCreatedFrom.isNotEmpty() }

        isNight.value = true
        composeRule.waitForIdle()

        composeRule.waitUntil(LOAD_TIMEOUT_MS) {
            familiesCreatedFrom.any { it.name.endsWith(".night") }
        }
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 5_000L
    }
}
