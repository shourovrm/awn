package app.awn.quran.language

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.awn.quran.AppPreferences
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FirstLaunchTest {

    @get:Rule val temporaryFolder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After fun cancelScope() = scope.cancel()

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
    fun aFreshInstallationGoesFromLanguageToNoticeToTheQuran() = runBlocking {
        val preferences = AppPreferences(newDataStore())
        assertEquals(FirstLaunchStep.ChooseLanguage, stepOf(preferences))

        preferences.markLanguageChosen()
        assertEquals(FirstLaunchStep.AcceptNotice, stepOf(preferences))

        preferences.acceptNotice()
        assertEquals(FirstLaunchStep.Done, stepOf(preferences))
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
        assertEquals(FirstLaunchStep.AcceptNotice, firstLaunchStep(false, true))
        assertEquals(FirstLaunchStep.Done, firstLaunchStep(true, false))
        assertEquals(FirstLaunchStep.Done, firstLaunchStep(true, true))
    }
}
