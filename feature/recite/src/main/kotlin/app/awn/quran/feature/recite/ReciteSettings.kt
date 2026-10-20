package app.awn.quran.feature.recite

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.reciteDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "recite_settings")

/** The user's recitation preferences, kept in a DataStore of this module. */
class ReciteSettings(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.reciteDataStore)

    /** 0 patient to 1 eager, the value the Speed slider shows. */
    val speed: Flow<Float> =
        dataStore.data.map { preferences ->
            preferences[SPEED_KEY] ?: RecitationSession.DEFAULT_SPEED
        }

    /** Whether the words of the range are hidden until the user recites them. */
    val hifzEnabled: Flow<Boolean> = dataStore.data.map { it[HIFZ_KEY] ?: false }

    /** Whether the screen offers the first-letter hint button. */
    val hintEnabled: Flow<Boolean> = dataStore.data.map { it[HINT_KEY] ?: true }

    /** The reciter whose recording plays when the user taps a flagged word. */
    val reciterId: Flow<String> = dataStore.data.map { it[RECITER_KEY] ?: DEFAULT_RECITER_ID }

    suspend fun setReciterId(reciterId: String) {
        dataStore.edit { it[RECITER_KEY] = reciterId }
    }

    suspend fun setSpeed(speed: Float) {
        dataStore.edit { it[SPEED_KEY] = speed.coerceIn(0f, 1f) }
    }

    suspend fun setHifzEnabled(enabled: Boolean) {
        dataStore.edit { it[HIFZ_KEY] = enabled }
    }

    suspend fun setHintEnabled(enabled: Boolean) {
        dataStore.edit { it[HINT_KEY] = enabled }
    }

    companion object {
        const val DEFAULT_RECITER_ID = "husary"

        private val SPEED_KEY = floatPreferencesKey("speed")
        private val HIFZ_KEY = booleanPreferencesKey("hifz_enabled")
        private val HINT_KEY = booleanPreferencesKey("hint_enabled")
        private val RECITER_KEY = stringPreferencesKey("reciter_id")
    }
}
