package app.awn.quran

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.awn.quran.core.ui.theme.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.awnDataStore: DataStore<Preferences> by preferencesDataStore("awn_preferences")

/** Small settings that live in DataStore: notice acceptance and the theme choice. */
class AppPreferences(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.awnDataStore)

    val noticeAccepted: Flow<Boolean> = dataStore.data.map { it[NOTICE_ACCEPTED_KEY] ?: false }

    val themePreference: Flow<ThemePreference> =
        dataStore.data.map { preferences ->
            val storedName = preferences[THEME_PREFERENCE_KEY]
            // A name written by a newer app version that this one does not know falls back to the
            // default instead of locking the user out of the UI.
            ThemePreference.entries.firstOrNull { it.name == storedName } ?: ThemePreference.System
        }

    suspend fun acceptNotice() {
        dataStore.edit { it[NOTICE_ACCEPTED_KEY] = true }
    }

    suspend fun setThemePreference(themePreference: ThemePreference) {
        dataStore.edit { it[THEME_PREFERENCE_KEY] = themePreference.name }
    }

    private companion object {
        val NOTICE_ACCEPTED_KEY = booleanPreferencesKey("notice_accepted")
        val THEME_PREFERENCE_KEY = stringPreferencesKey("theme_preference")
    }
}
