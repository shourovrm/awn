package app.awn.quran

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.awn.quran.core.ui.theme.ThemePreference
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppPreferencesTest {

    @get:Rule val temporaryFolder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After fun cancelScope() = scope.cancel()

    private fun newDataStore(file: File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    @Test
    fun themeDefaultsToSystem() = runBlocking {
        val preferences =
            AppPreferences(newDataStore(File(temporaryFolder.root, "a.preferences_pb")))

        assertEquals(ThemePreference.System, preferences.themePreference.first())
    }

    @Test
    fun everyThemeValueSurvivesARoundTrip() = runBlocking {
        val preferences =
            AppPreferences(newDataStore(File(temporaryFolder.root, "b.preferences_pb")))

        for (choice in ThemePreference.entries) {
            preferences.setThemePreference(choice)

            assertEquals(choice, preferences.themePreference.first())
        }
    }

    @Test
    fun noticeIsNotAcceptedUntilTheUserAccepts() = runBlocking {
        val preferences =
            AppPreferences(newDataStore(File(temporaryFolder.root, "c.preferences_pb")))
        assertFalse(preferences.noticeAccepted.first())

        preferences.acceptNotice()

        assertTrue(preferences.noticeAccepted.first())
    }

    @Test
    fun unknownStoredThemeNameFallsBackToSystem() = runBlocking {
        val dataStore = newDataStore(File(temporaryFolder.root, "d.preferences_pb"))
        dataStore.edit { it[stringPreferencesKey("theme_preference")] = "Sepia" }

        assertEquals(ThemePreference.System, AppPreferences(dataStore).themePreference.first())
    }
}
