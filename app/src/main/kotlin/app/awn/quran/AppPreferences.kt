package app.awn.quran

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.awn.quran.core.ui.theme.ColourSettings
import app.awn.quran.core.ui.theme.DayColours
import app.awn.quran.core.ui.theme.NightColours
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.awnDataStore: DataStore<Preferences> by preferencesDataStore("awn_preferences")

/**
 * Small settings that live in DataStore: notice acceptance, whether the language screen was
 * answered, the colour choices and whether surah names show their meaning.
 */
class AppPreferences(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.awnDataStore)

    val noticeAccepted: Flow<Boolean> = dataStore.data.map { it[NOTICE_ACCEPTED_KEY] ?: false }

    /**
     * Whether the language screen has been answered. The language itself is stored by AppCompat;
     * this flag only keeps the screen from showing again.
     */
    val languageChosen: Flow<Boolean> = dataStore.data.map { it[LANGUAGE_CHOSEN_KEY] ?: false }

    /**
     * The stored colour choices; settings written by the earlier Light/Dark/System choice are
     * converted.
     */
    val colourSettings: Flow<ColourSettings> = dataStore.data.map(::readColourSettings)

    val showSurahNameMeaning: Flow<Boolean> =
        dataStore.data.map { it[SURAH_NAME_MEANING_KEY] ?: true }

    suspend fun acceptNotice() {
        dataStore.edit { it[NOTICE_ACCEPTED_KEY] = true }
    }

    /**
     * Whether the one-time welcome card (edition, translation and word by word) is still to be
     * shown. It becomes true only when the language screen is answered, which an installation from
     * before the offer never does again, so an upgrade is never asked.
     */
    val printOfferPending: Flow<Boolean> =
        dataStore.data.map { it[PRINT_OFFER_PENDING_KEY] ?: false }

    /** Records the language answer, which makes the welcome card pending. */
    suspend fun markLanguageChosen() {
        dataStore.edit {
            it[LANGUAGE_CHOSEN_KEY] = true
            it[PRINT_OFFER_PENDING_KEY] = true
        }
    }

    /** The user downloaded, said Not now or closed the welcome card; it never returns. */
    suspend fun markPrintOfferHandled() {
        dataStore.edit { it[PRINT_OFFER_PENDING_KEY] = false }
    }

    /** Applies [transform] to the stored colour choices and writes all four values. */
    suspend fun updateColourSettings(transform: (ColourSettings) -> ColourSettings) {
        dataStore.edit { preferences ->
            val updated = transform(readColourSettings(preferences))
            preferences[DAY_COLOURS_KEY] = updated.day.name
            preferences[NIGHT_COLOURS_KEY] = updated.night.name
            preferences[FOLLOW_DEVICE_KEY] = updated.followDevice
            preferences[MANUAL_NIGHT_KEY] = updated.manualNight
            // The earlier value is now represented by the four keys above.
            preferences.remove(LEGACY_THEME_KEY)
        }
    }

    suspend fun setShowSurahNameMeaning(isShown: Boolean) {
        dataStore.edit { it[SURAH_NAME_MEANING_KEY] = isShown }
    }

    private fun readColourSettings(preferences: Preferences): ColourSettings {
        val hasNewKeys =
            listOf(DAY_COLOURS_KEY, NIGHT_COLOURS_KEY, FOLLOW_DEVICE_KEY, MANUAL_NIGHT_KEY).any {
                it in preferences
            }
        if (!hasNewKeys)
            return ColourSettingsMigration.fromLegacyName(preferences[LEGACY_THEME_KEY])
        val defaults = ColourSettings()
        return ColourSettings(
            // A name written by a newer app version falls back to the default.
            day =
                DayColours.entries.firstOrNull { it.name == preferences[DAY_COLOURS_KEY] }
                    ?: defaults.day,
            night =
                NightColours.entries.firstOrNull { it.name == preferences[NIGHT_COLOURS_KEY] }
                    ?: defaults.night,
            followDevice = preferences[FOLLOW_DEVICE_KEY] ?: defaults.followDevice,
            manualNight = preferences[MANUAL_NIGHT_KEY] ?: defaults.manualNight,
        )
    }

    private companion object {
        val NOTICE_ACCEPTED_KEY = booleanPreferencesKey("notice_accepted")
        val LANGUAGE_CHOSEN_KEY = booleanPreferencesKey("language_chosen")
        val PRINT_OFFER_PENDING_KEY = booleanPreferencesKey("print_offer_pending")
        val SURAH_NAME_MEANING_KEY = booleanPreferencesKey("surah_name_meaning")
        val DAY_COLOURS_KEY = stringPreferencesKey("colours_day")
        val NIGHT_COLOURS_KEY = stringPreferencesKey("colours_night")
        val FOLLOW_DEVICE_KEY = booleanPreferencesKey("colours_follow_device")
        val MANUAL_NIGHT_KEY = booleanPreferencesKey("colours_manual_night")

        /** Written by versions before the colour modes: "Light", "Dark" or "System". */
        val LEGACY_THEME_KEY = stringPreferencesKey("theme_preference")
    }
}

/** Converts the earlier Light, Dark and System choice into the colour settings that replace it. */
object ColourSettingsMigration {

    /**
     * Light always used the cream paper, so it becomes the Awn day palette with no following. Dark
     * turned the page into the night field, so it becomes the Dark palette, fixed on. System
     * followed the device, which is also the default for a name that is missing or unknown.
     */
    fun fromLegacyName(legacyName: String?): ColourSettings =
        when (legacyName) {
            "Light" -> ColourSettings(followDevice = false, manualNight = false)
            "Dark" -> ColourSettings(followDevice = false, manualNight = true)
            else -> ColourSettings()
        }
}
