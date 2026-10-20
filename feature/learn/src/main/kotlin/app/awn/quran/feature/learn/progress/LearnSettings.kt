package app.awn.quran.feature.learn.progress

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The user's choices for Learn, kept in a DataStore of their own. */
class LearnSettings(private val dataStore: DataStore<Preferences>) {

    /** How many skip-free hifz completions, on that many different days, make an ayah memorised. */
    val memorisationThreshold: Flow<Int> =
        dataStore.data.map { preferences ->
            preferences[MEMORISATION_THRESHOLD_KEY] ?: DEFAULT_MEMORISATION_THRESHOLD
        }

    suspend fun setMemorisationThreshold(threshold: Int) {
        dataStore.edit { preferences ->
            preferences[MEMORISATION_THRESHOLD_KEY] =
                threshold.coerceIn(MIN_MEMORISATION_THRESHOLD, MAX_MEMORISATION_THRESHOLD)
        }
    }

    /** Whether the daily "N ayahs to revise" notification is on; off until the user turns it on. */
    val reminderEnabled: Flow<Boolean> = dataStore.data.map { it[REMINDER_ENABLED_KEY] ?: false }

    val reminderHour: Flow<Int> =
        dataStore.data.map { it[REMINDER_HOUR_KEY] ?: DEFAULT_REMINDER_HOUR }

    val reminderMinute: Flow<Int> = dataStore.data.map { it[REMINDER_MINUTE_KEY] ?: 0 }

    suspend fun setReminderEnabled(enabled: Boolean) {
        dataStore.edit { it[REMINDER_ENABLED_KEY] = enabled }
    }

    suspend fun setReminderTime(hour: Int, minute: Int) {
        dataStore.edit {
            it[REMINDER_HOUR_KEY] = hour.coerceIn(0, MAX_HOUR)
            it[REMINDER_MINUTE_KEY] = minute.coerceIn(0, MAX_MINUTE)
        }
    }

    companion object {
        const val DEFAULT_REMINDER_HOUR = 20
        private const val MAX_HOUR = 23
        private const val MAX_MINUTE = 59
        private val REMINDER_ENABLED_KEY = booleanPreferencesKey("reminder_enabled")
        private val REMINDER_HOUR_KEY = intPreferencesKey("reminder_hour")
        private val REMINDER_MINUTE_KEY = intPreferencesKey("reminder_minute")

        const val DEFAULT_MEMORISATION_THRESHOLD = 3
        const val MIN_MEMORISATION_THRESHOLD = 1
        const val MAX_MEMORISATION_THRESHOLD = 10
        const val DATASTORE_NAME = "learn_settings"

        private val MEMORISATION_THRESHOLD_KEY = intPreferencesKey("memorisation_threshold")
    }
}
