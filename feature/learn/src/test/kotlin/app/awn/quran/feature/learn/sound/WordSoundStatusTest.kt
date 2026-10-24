package app.awn.quran.feature.learn.sound

import app.awn.quran.core.data.DEFAULT_WORD_RECITER_ID
import app.awn.quran.core.data.downloads.PackTransfer
import app.awn.quran.core.data.packs.PackFailureReason
import app.awn.quran.core.data.wordReciterIdFor
import org.junit.Assert.assertEquals
import org.junit.Test

class WordSoundStatusTest {

    private fun pack(isInstalled: Boolean = false, transfer: PackTransfer? = null) =
        TimingPackState("timing-husary", isInstalled, 3_000_000L, transfer)

    @Test
    fun aWordWithTimingsPlays() {
        assertEquals(WordSoundStatus.Ready, wordSoundStatusOf(true, pack(isInstalled = true)))
    }

    @Test
    fun missingTimingsOfferTheDownloadWithItsSize() {
        val status = wordSoundStatusOf(false, pack())

        assertEquals(WordSoundStatus.Missing("timing-husary", 3_000_000L, null, false), status)
    }

    @Test
    fun aRunningDownloadReportsItsProgress() {
        val running = wordSoundStatusOf(false, pack(transfer = PackTransfer.Running(0.4f)))
        val waiting = wordSoundStatusOf(false, pack(transfer = PackTransfer.Waiting))

        assertEquals(0.4f, (running as WordSoundStatus.Missing).progress)
        assertEquals(0f, (waiting as WordSoundStatus.Missing).progress)
    }

    @Test
    fun aFailedDownloadIsOfferedAgain() {
        val failed = PackTransfer.Failed(PackFailureReason.NoSpace)

        val status = wordSoundStatusOf(false, pack(transfer = failed)) as WordSoundStatus.Missing

        assertEquals(true, status.hasFailed)
        assertEquals(null, status.progress)
    }

    @Test
    fun noWordReciterOrInstalledTimingsWithoutThisWordOfferNothing() {
        assertEquals(WordSoundStatus.Unavailable, wordSoundStatusOf(false, null))
        assertEquals(
            WordSoundStatus.Unavailable,
            wordSoundStatusOf(false, pack(isInstalled = true)),
        )
    }

    @Test
    fun theChosenWordReciterWinsAndHusaryIsTheDefault() {
        val withTimings = listOf(DEFAULT_WORD_RECITER_ID, "minshawi")

        assertEquals("minshawi", wordReciterIdFor("minshawi", "afasy", withTimings))
        assertEquals(DEFAULT_WORD_RECITER_ID, wordReciterIdFor(null, "afasy", withTimings))
        assertEquals("afasy", wordReciterIdFor("minshawi", "afasy", listOf("afasy")))
    }
}
