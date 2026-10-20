package app.awn.quran.feature.learn.progress

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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

    companion object {
        const val DEFAULT_MEMORISATION_THRESHOLD = 3
        const val MIN_MEMORISATION_THRESHOLD = 1
        const val MAX_MEMORISATION_THRESHOLD = 10
        const val DATASTORE_NAME = "learn_settings"

        private val MEMORISATION_THRESHOLD_KEY = intPreferencesKey("memorisation_threshold")
    }
}
