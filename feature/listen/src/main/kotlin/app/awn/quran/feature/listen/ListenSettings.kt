package app.awn.quran.feature.listen

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.listenDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "listen_settings")

/** The user's listening choices, kept in a DataStore of this module. */
class ListenSettings(private val dataStore: DataStore<Preferences>) {

    /** True until the user turns it off: downloads then wait for an unmetered connection. */
    val wifiOnly: Flow<Boolean> = dataStore.data.map { it[WIFI_ONLY_KEY] ?: true }

    val selectedReciterId: Flow<String> =
        dataStore.data.map { it[SELECTED_RECITER_KEY] ?: DEFAULT_RECITER_ID }

    suspend fun setWifiOnly(wifiOnly: Boolean) {
        dataStore.edit { it[WIFI_ONLY_KEY] = wifiOnly }
    }

    suspend fun selectReciter(reciterId: String) {
        dataStore.edit { it[SELECTED_RECITER_KEY] = reciterId }
    }

    companion object {
        const val DEFAULT_RECITER_ID = "alafasy"

        private val WIFI_ONLY_KEY = booleanPreferencesKey("wifi_only")
        private val SELECTED_RECITER_KEY = stringPreferencesKey("selected_reciter_id")

        fun create(context: Context): ListenSettings =
            ListenSettings(context.applicationContext.listenDataStore)
    }
}
