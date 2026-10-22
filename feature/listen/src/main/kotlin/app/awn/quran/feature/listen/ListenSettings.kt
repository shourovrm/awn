package app.awn.quran.feature.listen

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.awn.quran.player.DownloadAmount
import app.awn.quran.player.PauseAfterAyah
import app.awn.quran.player.PlaybackOptions
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

    /** The reciter chosen for single words on the word card; null until the user chooses. */
    val wordReciterId: Flow<String?> = dataStore.data.map { it[WORD_RECITER_KEY] }

    /** How much audio is fetched when the audio at the start of a range is missing. */
    val downloadAmount: Flow<DownloadAmount> =
        dataStore.data.map { preferences ->
            val stored = preferences[DOWNLOAD_AMOUNT_KEY]
            DownloadAmount.entries.firstOrNull { it.name == stored } ?: DownloadAmount.Page
        }

    /** The repeat, speed and memorisation choices, kept between launches and surahs. */
    val playbackOptions: Flow<PlaybackOptions> =
        dataStore.data.map { preferences ->
            val defaults = PlaybackOptions()
            val storedPause = preferences[PAUSE_AFTER_KEY]
            PlaybackOptions(
                    repeatEachAyah = preferences[REPEAT_EACH_KEY] ?: defaults.repeatEachAyah,
                    repeatRange = preferences[REPEAT_RANGE_KEY] ?: defaults.repeatRange,
                    speed = preferences[SPEED_KEY] ?: defaults.speed,
                    pauseAfterAyah =
                        PauseAfterAyah.entries.firstOrNull { it.name == storedPause }
                            ?: defaults.pauseAfterAyah,
                    cumulative = preferences[CUMULATIVE_KEY] ?: defaults.cumulative,
                    stopOnTaskRemoved =
                        preferences[STOP_ON_TASK_REMOVED_KEY] ?: defaults.stopOnTaskRemoved,
                )
                .coerced()
        }

    suspend fun savePlaybackOptions(options: PlaybackOptions) {
        dataStore.edit {
            it[REPEAT_EACH_KEY] = options.repeatEachAyah
            it[REPEAT_RANGE_KEY] = options.repeatRange
            it[SPEED_KEY] = options.speed
            it[PAUSE_AFTER_KEY] = options.pauseAfterAyah.name
            it[CUMULATIVE_KEY] = options.cumulative
            it[STOP_ON_TASK_REMOVED_KEY] = options.stopOnTaskRemoved
        }
    }

    suspend fun setWifiOnly(wifiOnly: Boolean) {
        dataStore.edit { it[WIFI_ONLY_KEY] = wifiOnly }
    }

    suspend fun selectReciter(reciterId: String) {
        dataStore.edit { it[SELECTED_RECITER_KEY] = reciterId }
    }

    suspend fun setWordReciter(reciterId: String) {
        dataStore.edit { it[WORD_RECITER_KEY] = reciterId }
    }

    suspend fun setDownloadAmount(amount: DownloadAmount) {
        dataStore.edit { it[DOWNLOAD_AMOUNT_KEY] = amount.name }
    }

    companion object {
        const val DEFAULT_RECITER_ID = "alafasy"

        private val WIFI_ONLY_KEY = booleanPreferencesKey("wifi_only")
        private val SELECTED_RECITER_KEY = stringPreferencesKey("selected_reciter_id")
        private val WORD_RECITER_KEY = stringPreferencesKey("word_reciter_id")
        private val DOWNLOAD_AMOUNT_KEY = stringPreferencesKey("download_amount")
        private val REPEAT_EACH_KEY = intPreferencesKey("repeat_each_ayah")
        private val REPEAT_RANGE_KEY = intPreferencesKey("repeat_range")
        private val SPEED_KEY = floatPreferencesKey("speed")
        private val PAUSE_AFTER_KEY = stringPreferencesKey("pause_after_ayah")
        private val CUMULATIVE_KEY = booleanPreferencesKey("cumulative")
        private val STOP_ON_TASK_REMOVED_KEY = booleanPreferencesKey("stop_on_task_removed")

        fun create(context: Context): ListenSettings =
            ListenSettings(context.applicationContext.listenDataStore)
    }
}
