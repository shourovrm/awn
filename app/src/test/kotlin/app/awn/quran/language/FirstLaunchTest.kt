package app.awn.quran.language

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.awn.quran.AppPreferences
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FirstLaunchTest {

    @get:Rule val temporaryFolder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Joined, not just cancelled: DataStore keeps writing until its job completes, and a failure
    // after
    // the test ends is reported against whichever test runs next in the same JVM.
    @After fun cancelScope() = runBlocking { scope.coroutineContext.job.cancelAndJoin() }

    private fun newDataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(temporaryFolder.root, "first_launch.preferences_pb") },
        )

    private suspend fun stepOf(preferences: AppPreferences) =
        firstLaunchStep(
            noticeAccepted = preferences.noticeAccepted.first(),
            languageChosen = preferences.languageChosen.first(),
        )

    @Test
    fun aFreshInstallationGoesFromLanguageStraightToTheQuran() = runBlocking {
        // The notice now waits for the first visit to Learn, so it is no longer a launch step.
        val preferences = AppPreferences(newDataStore())
        assertEquals(FirstLaunchStep.ChooseLanguage, stepOf(preferences))

        preferences.markLanguageChosen()
        assertEquals(FirstLaunchStep.Done, stepOf(preferences))
        assertEquals(false, preferences.noticeAccepted.first())
    }

    @Test
    fun anUpgradingUserWhoAcceptedTheNoticeNeverSeesTheLanguageScreen() = runBlocking {
        // The earlier version stored only the notice flag; the language flag does not exist yet.
        val preferences = AppPreferences(newDataStore())
        preferences.acceptNotice()

        assertEquals(false, preferences.languageChosen.first())
        assertEquals(FirstLaunchStep.Done, stepOf(preferences))
    }

    @Test
    fun theRoutingTableCoversEveryCombination() {
        assertEquals(FirstLaunchStep.ChooseLanguage, firstLaunchStep(false, false))
        assertEquals(FirstLaunchStep.Done, firstLaunchStep(false, true))
        assertEquals(FirstLaunchStep.Done, firstLaunchStep(true, false))
        assertEquals(FirstLaunchStep.Done, firstLaunchStep(true, true))
    }

    @Test
    fun theFirstQuranScreenOfAFreshInstallationHasThePrintOfferPending() = runBlocking {
        val preferences = AppPreferences(newDataStore())
        assertEquals(false, preferences.printOfferPending.first())

        preferences.markLanguageChosen()
        preferences.acceptNotice()

        assertEquals(true, preferences.printOfferPending.first())
    }

    @Test
    fun anUpgradingUserNeverHasThePrintOfferPending() = runBlocking {
        val preferences = AppPreferences(newDataStore())
        preferences.acceptNotice()

        assertEquals(false, preferences.printOfferPending.first())
    }

    @Test
    fun handlingThePrintOfferOnceEndsItForGood() = runBlocking {
        val preferences = AppPreferences(newDataStore())
        preferences.markLanguageChosen()

        preferences.markPrintOfferHandled()

        assertEquals(false, preferences.printOfferPending.first())
    }

    @Test
    fun theLanguageAnswerOfAFreshInstallationNamesTheStarterDownloadToOffer() = runBlocking {
        val preferences = AppPreferences(newDataStore())
        assertEquals(null, preferences.starterOfferLanguage.first())

        preferences.markLanguageChosen("bn")

        assertEquals("bn", preferences.starterOfferLanguage.first())
    }

    @Test
    fun anUpgradingUserIsNeverOfferedTheStarterDownload() = runBlocking {
        val preferences = AppPreferences(newDataStore())
        preferences.acceptNotice()

        assertEquals(null, preferences.starterOfferLanguage.first())
    }

    @Test
    fun handlingTheStarterOfferOnceEndsItForGood() = runBlocking {
        val preferences = AppPreferences(newDataStore())
        preferences.markLanguageChosen("en")

        preferences.markStarterOfferHandled()

        assertEquals(null, preferences.starterOfferLanguage.first())
    }
}
