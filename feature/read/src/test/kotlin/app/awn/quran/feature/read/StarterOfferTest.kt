package app.awn.quran.feature.read

import app.awn.quran.core.data.CatalogueGroup
import app.awn.quran.core.data.downloads.CatalogueRow
import app.awn.quran.core.data.downloads.PackTransfer
import app.awn.quran.core.data.fakes.fakeCatalogueItem
import app.awn.quran.core.data.packs.PackFailureReason
import app.awn.quran.feature.read.quran.starterPacksFor
import app.awn.quran.feature.read.quran.starterProgressOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StarterOfferTest {

    private fun translationRow(
        installed: Boolean = false,
        transfer: PackTransfer? = null,
        bytes: Long = 1_000_000L,
    ) =
        CatalogueRow(
            fakeCatalogueItem(
                "en_saheeh",
                downloadBytes = bytes,
                isInstalled = installed,
                group = CatalogueGroup.Translation,
            ),
            transfer,
        )

    private fun wordRow(
        installed: Boolean = false,
        transfer: PackTransfer? = null,
        bytes: Long = 3_000_000L,
    ) =
        CatalogueRow(
            fakeCatalogueItem(
                "wbw_en",
                group = CatalogueGroup.WordByWord,
                downloadBytes = bytes,
                isInstalled = installed,
            ),
            transfer,
        )

    @Test
    fun englishGetsSaheehAndEnglishWords() {
        val packs = starterPacksFor("en")!!

        assertEquals(listOf("en_saheeh", "wbw_en"), packs.ids)
    }

    @Test
    fun banglaGetsZakariaAndBanglaWords() {
        val packs = starterPacksFor("bn")!!

        assertEquals(listOf("bn_zakaria", "wbw_bn"), packs.ids)
    }

    @Test
    fun otherLanguagesGetNoStarterDownload() {
        assertNull(starterPacksFor("ar"))
    }

    @Test
    fun theOfferedSizeIsTheSumOfTheDownloadsNotYetOnThePhone() {
        val progress = starterProgressOf(listOf(translationRow(installed = true), wordRow()))

        assertEquals(3_000_000L, progress.remainingBytes)
        assertFalse(progress.isFinished)
    }

    @Test
    fun theOfferIsFinishedWhenBothAreInstalled() {
        val progress =
            starterProgressOf(listOf(translationRow(installed = true), wordRow(installed = true)))

        assertTrue(progress.isFinished)
    }

    @Test
    fun progressIsWeightedBySize() {
        val progress =
            starterProgressOf(
                listOf(
                    translationRow(transfer = PackTransfer.Running(1.0f)),
                    wordRow(transfer = PackTransfer.Running(0.0f)),
                ),
            )

        // 1 MB of 4 MB has arrived.
        assertEquals(0.25f, progress.fractionOf(totalBytes = 4_000_000L), 0.001f)
    }

    @Test
    fun aDownloadThatFinishedKeepsCountingTowardsTheStartingTotal() {
        val progress =
            starterProgressOf(
                listOf(
                    translationRow(installed = true),
                    wordRow(transfer = PackTransfer.Running(0.5f)),
                ),
            )

        // 1 MB installed plus 1.5 MB of the 3 MB word pack, out of the 4 MB at the start.
        assertEquals(0.625f, progress.fractionOf(totalBytes = 4_000_000L), 0.001f)
    }

    @Test
    fun aFailureOfEitherDownloadIsReported() {
        val failure = PackTransfer.Failed(PackFailureReason.NoInternet)

        val progress = starterProgressOf(listOf(translationRow(), wordRow(transfer = failure)))

        assertEquals(failure, progress.failure)
    }
}
