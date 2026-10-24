package app.awn.quran

import org.junit.Assert.assertEquals
import org.junit.Test

class ReciteEntryStepTest {

    @Test
    fun theNoticeComesFirstWhetherOrNotTheSpeechFilesAreOnThePhone() {
        assertEquals(ReciteEntryStep.Notice, reciteEntryStep(false, isModelInstalled = false))
        assertEquals(ReciteEntryStep.Notice, reciteEntryStep(false, isModelInstalled = true))
    }

    @Test
    fun afterTheNoticeTheDownloadIsOfferedUntilTheFilesAreInstalled() {
        assertEquals(ReciteEntryStep.Download, reciteEntryStep(true, isModelInstalled = false))
    }

    @Test
    fun withTheNoticeAcceptedAndTheFilesInstalledTheBarShows() {
        assertEquals(ReciteEntryStep.Bar, reciteEntryStep(true, isModelInstalled = true))
    }
}
