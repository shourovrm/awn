package app.awn.quran.feature.listen

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** A DataStore that lives in memory, so tests do not wait for file reads on another thread. */
internal class InMemoryPreferences : DataStore<Preferences> {

    private val preferences = MutableStateFlow(emptyPreferences())

    override val data: Flow<Preferences> = preferences

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val updated = transform(preferences.value)
        preferences.value = updated
        return updated
    }
}
