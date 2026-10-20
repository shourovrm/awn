package app.awn.quran.feature.read.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.TranslationId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Where [ReadSettings] are kept. */
interface ReadSettingsStore {

    /** Emits the stored settings, then every change. */
    val settings: Flow<ReadSettings>

    /** Applies [transform] to the stored settings and stores the result. */
    suspend fun update(transform: (ReadSettings) -> ReadSettings)
}

private val Context.readSettingsDataStore by preferencesDataStore(name = "read_settings")

/** Keeps the settings in a Preferences DataStore; unknown stored values fall back to defaults. */
class DataStoreReadSettingsStore(private val dataStore: DataStore<Preferences>) :
    ReadSettingsStore {

    constructor(context: Context) : this(context.applicationContext.readSettingsDataStore)

    override val settings: Flow<ReadSettings> = dataStore.data.map(::readSettings)

    override suspend fun update(transform: (ReadSettings) -> ReadSettings) {
        dataStore.edit { preferences ->
            val updated = transform(readSettings(preferences))
            preferences[SCRIPT_KEY] = updated.script.name
            preferences[PAGE_VIEW_KEY] = updated.pageView.name
            preferences[TRANSLATIONS_KEY] = updated.translations.joinToString(SEPARATOR) { it.id }
            preferences[TRANSLITERATION_KEY] = updated.showTransliteration
            preferences[WORD_BY_WORD_KEY] = updated.showWordByWord
            preferences[TEXT_SIZE_KEY] = updated.textSize.name
        }
    }

    private fun readSettings(preferences: Preferences): ReadSettings {
        val defaults = ReadSettings()
        val storedTranslations = preferences[TRANSLATIONS_KEY]
        val translations =
            if (storedTranslations == null) {
                defaults.translations
            } else {
                storedTranslations.split(SEPARATOR).filter { it.isNotBlank() }.map(::TranslationId)
            }
        val script = ScriptId.entries.firstOrNull { it.name == preferences[SCRIPT_KEY] }
        val pageView = PageView.entries.firstOrNull { it.name == preferences[PAGE_VIEW_KEY] }
        val textSize = TextSize.entries.firstOrNull { it.name == preferences[TEXT_SIZE_KEY] }
        return ReadSettings(
            script = script ?: defaults.script,
            pageView = pageView ?: defaults.pageView,
            translations = translations,
            showTransliteration = preferences[TRANSLITERATION_KEY] ?: defaults.showTransliteration,
            showWordByWord = preferences[WORD_BY_WORD_KEY] ?: defaults.showWordByWord,
            textSize = textSize ?: defaults.textSize,
        )
    }

    private companion object {
        const val SEPARATOR = ","
        val SCRIPT_KEY = stringPreferencesKey("script")
        val PAGE_VIEW_KEY = stringPreferencesKey("page_view")
        val TRANSLATIONS_KEY = stringPreferencesKey("translations")
        val TRANSLITERATION_KEY = booleanPreferencesKey("transliteration")
        val WORD_BY_WORD_KEY = booleanPreferencesKey("word_by_word")
        val TEXT_SIZE_KEY = stringPreferencesKey("text_size")
    }
}
