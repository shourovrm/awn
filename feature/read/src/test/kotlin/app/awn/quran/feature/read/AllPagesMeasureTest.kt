package app.awn.quran.feature.read

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.quran.SqliteQuranTextRepository
import app.awn.quran.feature.read.page.PageFamilies
import app.awn.quran.feature.read.page.PagePlacement
import app.awn.quran.feature.read.page.buildPageRows
import app.awn.quran.feature.read.page.measurePage
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Measures all 604 pages of the three glyph prints with the real page fonts (native graphics, so
 * the text is really shaped) and the bundled Quran pack. The fonts are not in the repository: set
 * `AWN_DATA_DIR` to the folder that holds `fonts/v1`, `fonts/v2` and `fonts/v4-tajweed`; without it
 * the test is skipped.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class AllPagesMeasureTest {
    private val fontsDirectory = System.getenv("AWN_DATA_DIR")?.let { File(it, "fonts") }
    private val packFile = File("../../core/data/src/main/assets/packs/quran.sqlite").absoluteFile

    @Test
    fun everyPageOfEveryPrintIsPlacedInsideTheWidth() {
        assumeTrue(fontsDirectory?.isDirectory == true && packFile.isFile)
        val repository = SqliteQuranTextRepository({ packFile })
        val resolver =
            createFontFamilyResolver(
                ApplicationProvider.getApplicationContext<android.content.Context>(),
            )
        val density = Density(3f)
        val widthPx = 1080f
        val failures = mutableListOf<String>()
        val folders =
            mapOf(ScriptId.V1 to "v1", ScriptId.V2 to "v2", ScriptId.V4Tajweed to "v4-tajweed")
        for ((script, folder) in folders) {
            for (page in 1..PAGE_COUNT) {
                val problem =
                    problemOfPage(repository, script, page, folder, resolver, density, widthPx)
                if (problem != null) failures += "$script $page: $problem"
            }
        }
        assertEquals(emptyList<String>(), failures)
    }

    /** Why the page cannot be placed inside [widthPx], or null when it can. */
    @Suppress("LongParameterList") // Plain inputs of one measurement, used in one place.
    private fun problemOfPage(
        repository: SqliteQuranTextRepository,
        script: ScriptId,
        page: Int,
        folder: String,
        resolver: FontFamily.Resolver,
        density: Density,
        widthPx: Float,
    ): String? {
        val layout = runBlocking { repository.pageLayout(page, script) }
        val family = FontFamily(Font(File(fontsDirectory, "$folder/p$page.ttf")))
        val measurer = TextMeasurer(resolver, density, LayoutDirection.Ltr, 4)
        val measured =
            try {
                measurePage(
                    buildPageRows(layout, hasGlyphFont = true),
                    PageFamilies(family, null),
                    density,
                    measurer,
                    PagePlacement(widthPx, widthPx * 2f, 1f, isCentredBlock = page <= 2),
                )
            } catch (error: IllegalStateException) {
                return error.toString()
            }
        val isOutside =
            measured.geometry.textLines.any { line ->
                line.tokens.any { it.left < -1f || it.right > widthPx + 1f }
            }
        return if (isOutside) "a token leaves the page" else null
    }

    private companion object {
        const val PAGE_COUNT = 604
    }
}
