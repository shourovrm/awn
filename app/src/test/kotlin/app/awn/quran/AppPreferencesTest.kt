package app.awn.quran

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.awn.quran.core.ui.theme.ColourSettings
import app.awn.quran.core.ui.theme.DayColours
import app.awn.quran.core.ui.theme.NightColours
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

    private fun newDataStore(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(temporaryFolder.root, "$name.preferences_pb") },
        )

    private val legacyKey = stringPreferencesKey("theme_preference")

    @Test
    fun colourSettingsDefaultToAwnByDayDarkByNightFollowingTheDevice() = runBlocking {
        val preferences = AppPreferences(newDataStore("a"))

        assertEquals(ColourSettings(), preferences.colourSettings.first())
        assertEquals(DayColours.Awn, ColourSettings().day)
        assertEquals(NightColours.Dark, ColourSettings().night)
        assertTrue(ColourSettings().followDevice)
    }

    @Test
    fun everyColourChoiceSurvivesARoundTrip() = runBlocking {
        val preferences = AppPreferences(newDataStore("b"))

        for (day in DayColours.entries) {
            for (night in NightColours.entries) {
                val chosen = ColourSettings(day, night, followDevice = false, manualNight = true)
                preferences.updateColourSettings { chosen }

                assertEquals(chosen, preferences.colourSettings.first())
            }
        }
    }

    @Test
    fun anUpdateChangesOnlyTheFieldsTheTransformChanges() = runBlocking {
        val preferences = AppPreferences(newDataStore("c"))
        preferences.updateColourSettings { it.copy(night = NightColours.Black) }
        preferences.updateColourSettings { it.copy(day = DayColours.Light) }

        assertEquals(
            ColourSettings(DayColours.Light, NightColours.Black),
            preferences.colourSettings.first(),
        )
    }

    @Test
    fun legacyLightBecomesTheAwnDayPaletteFixedOnDay() = runBlocking {
        val dataStore = newDataStore("d")
        dataStore.edit { it[legacyKey] = "Light" }

        val settings = AppPreferences(dataStore).colourSettings.first()

        assertEquals(ColourSettings(followDevice = false, manualNight = false), settings)
    }

    @Test
    fun legacyDarkBecomesTheDarkPaletteFixedOnNight() = runBlocking {
        val dataStore = newDataStore("e")
        dataStore.edit { it[legacyKey] = "Dark" }

        val settings = AppPreferences(dataStore).colourSettings.first()

        assertEquals(ColourSettings(followDevice = false, manualNight = true), settings)
    }

    @Test
    fun legacySystemAndUnknownNamesFollowTheDevice() = runBlocking {
        for ((index, name) in listOf("System", "Sepia", null).withIndex()) {
            val dataStore = newDataStore("f$index")
            if (name != null) dataStore.edit { it[legacyKey] = name }

            assertEquals(ColourSettings(), AppPreferences(dataStore).colourSettings.first())
        }
    }

    @Test
    fun theFirstUpdateWritesTheMigratedValuesAndRemovesTheLegacyKey() = runBlocking {
        val dataStore = newDataStore("g")
        dataStore.edit { it[legacyKey] = "Dark" }
        val preferences = AppPreferences(dataStore)

        preferences.updateColourSettings { it.copy(night = NightColours.Black) }

        assertEquals(
            ColourSettings(night = NightColours.Black, followDevice = false, manualNight = true),
            preferences.colourSettings.first(),
        )
        assertFalse(legacyKey in dataStore.data.first())
    }

    @Test
    fun anUnknownStoredPaletteNameFallsBackToTheDefault() = runBlocking {
        val dataStore = newDataStore("h")
        dataStore.edit { it[stringPreferencesKey("colours_day")] = "Sepia" }

        assertEquals(DayColours.Awn, AppPreferences(dataStore).colourSettings.first().day)
    }

    @Test
    fun noticeIsNotAcceptedUntilTheUserAccepts() = runBlocking {
        val preferences = AppPreferences(newDataStore("i"))
        assertFalse(preferences.noticeAccepted.first())

        preferences.acceptNotice()

        assertTrue(preferences.noticeAccepted.first())
    }

    @Test
    fun surahNameMeaningIsOffUntilTurnedOn() = runBlocking {
        val preferences = AppPreferences(newDataStore("j"))
        assertFalse(preferences.showSurahNameMeaning.first())

        preferences.setShowSurahNameMeaning(true)

        assertTrue(preferences.showSurahNameMeaning.first())
    }
}
