package app.awn.quran.core.data.packs

import app.awn.quran.core.data.CatalogueGroup
import app.awn.quran.core.data.realManifestCatalog
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CatalogueRulesTest {

    private val oldSha = "a".repeat(64)
    private val newSha = "b".repeat(64)

    @Test
    fun updateIsAvailableOnlyWhenTheRecordedChecksumDiffersFromTheManifest() {
        assertTrue(PackUpdate.isAvailable(installedSha256 = oldSha, manifestSha256 = newSha))
        assertFalse(PackUpdate.isAvailable(installedSha256 = newSha, manifestSha256 = newSha))
    }

    @Test
    fun aCopyWithNoRecordedChecksumIsNeverOfferedAnUpdate() {
        assertFalse(PackUpdate.isAvailable(installedSha256 = null, manifestSha256 = newSha))
    }

    @Test
    fun anArchiveNeedsRoomForTheZipAndTheUnpackedFonts() {
        val required = PackSpace.requiredFreeBytes(PackFileLayout.FontArchive, 137_000_000L)

        assertEquals(411_000_000L, required)
    }

    @Test
    fun aDatabaseNeedsRoomOnlyForTheDownload() {
        assertEquals(
            1_000L,
            PackSpace.requiredFreeBytes(PackFileLayout.Database, downloadBytes = 1_000L),
        )
    }

    @Test
    fun aPartialDownloadLowersWhatIsStillNeeded() {
        assertEquals(
            400L,
            PackSpace.requiredFreeBytes(
                PackFileLayout.SingleFont,
                downloadBytes = 1_000L,
                alreadyDownloadedBytes = 600L,
            ),
        )
    }

    @Test
    fun shortfallIsTheMissingBytesOrZero() {
        assertEquals(34_000_000L, PackSpace.shortfall(54_000_000L, 20_000_000L))
        assertEquals(0L, PackSpace.shortfall(20_000_000L, 54_000_000L))
        assertEquals(0L, PackSpace.shortfall(20_000_000L, 20_000_000L))
    }

    @Test
    fun lowLevelErrorsMapToTheCauseTheUserSees() {
        assertEquals(
            PackFailureReason.NoInternet,
            PackFailureReason.of(UnknownHostException("github.com")),
        )
        assertEquals(PackFailureReason.NoInternet, PackFailureReason.of(ConnectException()))
        assertEquals(PackFailureReason.NoInternet, PackFailureReason.of(SocketException("reset")))
        assertEquals(
            PackFailureReason.NoSpace,
            PackFailureReason.of(IOException("write failed: No space left on device")),
        )
        assertEquals(PackFailureReason.Other, PackFailureReason.of(IOException("odd")))
    }

    @Test
    fun theBundledManifestsFillEverySectionWithoutNamingAPack() {
        val catalog = realManifestCatalog()

        val groups =
            catalog.allPacks.associate { record ->
                // The bundled files are the Hafs font and the text, translations and word packs.
                record.entry.id to CatalogueGrouping.groupOf(record, isBundled = false)
            }

        assertEquals(CatalogueGroup.PageType, groups.getValue("v2"))
        assertEquals(CatalogueGroup.Translation, groups.getValue("en_abdul_haleem"))
        assertEquals(CatalogueGroup.WordByWord, groups.getValue("wbw_bn"))
        assertEquals(CatalogueGroup.Transliteration, groups.getValue("en_transliteration"))
        assertEquals(null, groups.getValue("quran-text"))
    }
}
