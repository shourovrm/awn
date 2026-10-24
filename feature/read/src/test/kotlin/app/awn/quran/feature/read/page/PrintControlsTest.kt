package app.awn.quran.feature.read.page

import app.awn.quran.core.data.ScriptId
import org.junit.Assert.assertEquals
import org.junit.Test

class PrintControlsTest {

    private fun status(
        isInstalled: Boolean,
        progress: Float? = null,
        downloadBytes: Long? = 69_000_000L,
    ) =
        FontPackStatus(
            script = ScriptId.V4Tajweed,
            isInstalled = isInstalled,
            downloadBytes = downloadBytes,
            progress = progress,
        )

    @Test
    fun printNotOnThePhoneOffersDownloadWithItsSize() {
        val controls = printControlsOf(status(isInstalled = false), isInUse = false)

        assertEquals(PrintControls.Download(69_000_000L), controls)
    }

    @Test
    fun printWithUnknownSizeOffersDownloadWithoutSize() {
        val controls =
            printControlsOf(status(isInstalled = false, downloadBytes = null), isInUse = false)

        assertEquals(PrintControls.Download(null), controls)
    }

    @Test
    fun printInUseButNotOnThePhoneStillOffersDownload() {
        val controls = printControlsOf(status(isInstalled = false), isInUse = true)

        assertEquals(PrintControls.Download(69_000_000L), controls)
    }

    @Test
    fun runningDownloadShowsItsProgress() {
        val controls =
            printControlsOf(status(isInstalled = false, progress = 0.4f), isInUse = false)

        assertEquals(PrintControls.Downloading(0.4f), controls)
    }

    @Test
    fun printOnThePhoneNotInUseOffersDeleteAndUse() {
        val controls = printControlsOf(status(isInstalled = true), isInUse = false)

        assertEquals(PrintControls.DeleteOrUse, controls)
    }

    @Test
    fun printInUseShowsTheInUseMark() {
        val controls = printControlsOf(status(isInstalled = true), isInUse = true)

        assertEquals(PrintControls.InUse, controls)
    }
}
