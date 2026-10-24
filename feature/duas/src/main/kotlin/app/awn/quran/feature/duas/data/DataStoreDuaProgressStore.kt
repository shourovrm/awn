package app.awn.quran.feature.duas.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.duaProgressDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "duas")

/**
 * Stores the saved dua ids as one string set and each dua's clean-recitation count under its own
 * key (`clean_<id>`), so recording one recitation never rewrites the other counts.
 */
class DataStoreDuaProgressStore(private val dataStore: DataStore<Preferences>) : DuaProgressStore {

    constructor(context: Context) : this(context.applicationContext.duaProgressDataStore)

    override val progress: Flow<DuaProgress> =
        dataStore.data.map { preferences ->
            val cleanCounts =
                preferences
                    .asMap()
                    .mapNotNull { (key, value) ->
                        val duaId = key.name.removePrefix(CLEAN_KEY_PREFIX)
                        if (key.name.startsWith(CLEAN_KEY_PREFIX) && value is Int) {
                            duaId to value
                        } else {
                            null
                        }
                    }
                    .toMap()
            DuaProgress(savedIds = preferences[SAVED_KEY].orEmpty(), cleanRecitations = cleanCounts)
        }

    override suspend fun toggleSaved(duaId: String) {
        dataStore.edit { preferences ->
            val saved = preferences[SAVED_KEY].orEmpty()
            preferences[SAVED_KEY] = if (duaId in saved) saved - duaId else saved + duaId
        }
    }

    override suspend fun recordCleanRecitation(duaId: String) {
        val key = intPreferencesKey(CLEAN_KEY_PREFIX + duaId)
        dataStore.edit { preferences -> preferences[key] = (preferences[key] ?: 0) + 1 }
    }

    private companion object {
        const val CLEAN_KEY_PREFIX = "clean_"
        val SAVED_KEY = stringSetPreferencesKey("saved_ids")
    }
}
