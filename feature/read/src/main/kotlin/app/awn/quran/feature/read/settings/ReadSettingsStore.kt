package app.awn.quran.feature.read.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
            preferences[ARABIC_STEP_KEY] = updated.arabicSizeStep
            preferences[TRANSLATION_STEP_KEY] = updated.translationSizeStep
            preferences[HIGHLIGHT_BOOKMARKS_KEY] = updated.highlightBookmarks
            preferences[VOLUME_KEYS_KEY] = updated.volumeKeysTurnPages
            // The 3-step size is replaced by the two step keys written above.
            preferences.remove(LEGACY_TEXT_SIZE_KEY)
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
        val pageView = pageViewOf(preferences[PAGE_VIEW_KEY])
        val legacyStep = TextScale.stepForLegacyName(preferences[LEGACY_TEXT_SIZE_KEY])
        val arabicStep = preferences[ARABIC_STEP_KEY] ?: legacyStep ?: defaults.arabicSizeStep
        val translationStep =
            preferences[TRANSLATION_STEP_KEY] ?: legacyStep ?: defaults.translationSizeStep
        return ReadSettings(
            script = script ?: defaults.script,
            pageView = pageView ?: defaults.pageView,
            translations = translations,
            showTransliteration = preferences[TRANSLITERATION_KEY] ?: defaults.showTransliteration,
            showWordByWord = preferences[WORD_BY_WORD_KEY] ?: defaults.showWordByWord,
            arabicSizeStep = TextScale.clampStep(arabicStep),
            translationSizeStep = TextScale.clampStep(translationStep),
            highlightBookmarks =
                preferences[HIGHLIGHT_BOOKMARKS_KEY] ?: defaults.highlightBookmarks,
            volumeKeysTurnPages = preferences[VOLUME_KEYS_KEY] ?: defaults.volumeKeysTurnPages,
        )
    }

    /** "AyahList" is the name the Translation view had before the three-view redesign. */
    private fun pageViewOf(storedName: String?): PageView? =
        if (storedName == LEGACY_AYAH_LIST_VIEW) {
            PageView.Translation
        } else {
            PageView.entries.firstOrNull { it.name == storedName }
        }

    private companion object {
        const val SEPARATOR = ","
        const val LEGACY_AYAH_LIST_VIEW = "AyahList"
        val SCRIPT_KEY = stringPreferencesKey("script")
        val PAGE_VIEW_KEY = stringPreferencesKey("page_view")
        val TRANSLATIONS_KEY = stringPreferencesKey("translations")
        val TRANSLITERATION_KEY = booleanPreferencesKey("transliteration")
        val WORD_BY_WORD_KEY = booleanPreferencesKey("word_by_word")
        val LEGACY_TEXT_SIZE_KEY = stringPreferencesKey("text_size")
        val ARABIC_STEP_KEY = intPreferencesKey("arabic_size_step")
        val TRANSLATION_STEP_KEY = intPreferencesKey("translation_size_step")
        val HIGHLIGHT_BOOKMARKS_KEY = booleanPreferencesKey("highlight_bookmarks")
        val VOLUME_KEYS_KEY = booleanPreferencesKey("volume_keys_turn_pages")
    }
}
