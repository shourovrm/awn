package app.awn.quran.feature.read

import android.database.sqlite.SQLiteDatabase
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
import app.awn.quran.feature.read.page.frameSlotCountOf
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
 * Reads and measures all 610 pages of the IndoPak print with the real Nastaleeq font and the real
 * layout pack (native graphics, so the text is really shaped). Set `AWN_DATA_DIR` to the folder
 * that holds `fonts/indopak` and `published/quran`; without it the test is skipped.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class IndoPakAllPagesMeasureTest {
    private val dataDirectory = System.getenv("AWN_DATA_DIR")?.let(::File)
    private val fontFile =
        File(dataDirectory, "fonts/indopak/indopak-nastaleeq-waqf-lazim-v4.2.1.ttf")
    private val quranFile = File(dataDirectory, "published/quran/quran.sqlite")
    private val indoPakFile = File(dataDirectory, "published/quran/indopak.sqlite")

    @Test
    fun everyPageIsReadAndPlacedInsideTheWidth() {
        assumeTrue(dataDirectory != null && fontFile.isFile && indoPakFile.isFile)
        val repository =
            SqliteQuranTextRepository(
                databaseFile = { quranFile },
                indoPakDatabase = {
                    SQLiteDatabase.openDatabase(
                        indoPakFile.path,
                        null,
                        SQLiteDatabase.OPEN_READONLY,
                    )
                },
            )
        val resolver =
            createFontFamilyResolver(
                ApplicationProvider.getApplicationContext<android.content.Context>(),
            )
        val family = FontFamily(Font(fontFile))
        val density = Density(3f)
        val widthPx = 1080f
        val failures = mutableListOf<String>()
        for (page in 1..repository.pageCount(ScriptId.IndoPak15)) {
            val layout = runBlocking { repository.pageLayout(page, ScriptId.IndoPak15) }
            val placement =
                PagePlacement(
                    widthPx,
                    widthPx * 2f,
                    1f,
                    isCentredBlock = false,
                    slotCount = frameSlotCountOf(layout),
                )
            val measurer = TextMeasurer(resolver, density, LayoutDirection.Ltr, 4)
            try {
                measurePage(
                    buildPageRows(layout, hasGlyphFont = true),
                    PageFamilies(family, null),
                    density,
                    measurer,
                    placement,
                )
            } catch (error: IllegalStateException) {
                failures += "page $page: $error"
            }
        }
        assertEquals(emptyList<String>(), failures)
    }
}
