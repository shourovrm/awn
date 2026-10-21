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
            preferences[SHOW_ARABIC_KEY] = updated.showArabic
            preferences[TRANSLATIONS_KEY] = updated.translations.joinToString(SEPARATOR) { it.id }
            preferences[TRANSLITERATION_KEY] = updated.showTransliteration
            preferences[WORD_BY_WORD_KEY] = updated.showWordByWord
            // Absent means "follow the app language", so a cleared choice removes the key.
            if (updated.wordByWordLanguage == null) {
                preferences.remove(WORD_BY_WORD_LANGUAGE_KEY)
            } else {
                preferences[WORD_BY_WORD_LANGUAGE_KEY] = updated.wordByWordLanguage
            }
            preferences[ARABIC_STEP_KEY] = updated.arabicSizeStep
            preferences[TRANSLATION_STEP_KEY] = updated.translationSizeStep
            preferences[TRANSLITERATION_STEP_KEY] = updated.transliterationSizeStep
            preferences[WORD_BY_WORD_STEP_KEY] = updated.wordByWordSizeStep
            preferences[MUSHAF_STEP_KEY] = updated.mushafSizeStep
            preferences[QURAN_ORIENTATION_KEY] = updated.quranOrientation.name
            preferences[APP_ORIENTATION_KEY] = updated.appOrientation.name
            preferences[HIGHLIGHT_BOOKMARKS_KEY] = updated.highlightBookmarks
            preferences[VOLUME_KEYS_KEY] = updated.volumeKeysTurnPages
            // The 3-step size is replaced by the two step keys written above.
            preferences.remove(LEGACY_TEXT_SIZE_KEY)
            preferences[SHOW_PAGE_INFO_KEY] = updated.showPageInfo
        }
    }

    private fun readSettings(preferences: Preferences): ReadSettings {
        val defaults = ReadSettings()
        val script = ScriptId.entries.firstOrNull { it.name == preferences[SCRIPT_KEY] }
        val storedViewName = preferences[PAGE_VIEW_KEY]
        val steps = sizeStepsOf(preferences, defaults)
        return ReadSettings(
            script = script ?: defaults.script,
            pageView = pageViewOf(storedViewName) ?: defaults.pageView,
            showArabic = preferences[SHOW_ARABIC_KEY] ?: defaults.showArabic,
            translations = translationsOf(preferences, defaults),
            showTransliteration = preferences[TRANSLITERATION_KEY] ?: defaults.showTransliteration,
            showWordByWord =
                wordByWordLayerOf(storedViewName)
                    ?: preferences[WORD_BY_WORD_KEY]
                    ?: defaults.showWordByWord,
            // Before the language was chosen the one flag above was the only word-by-word setting,
            // so an absent key (follow the app language) keeps those installations as they were.
            wordByWordLanguage = preferences[WORD_BY_WORD_LANGUAGE_KEY]?.takeIf { it.isNotBlank() },
            arabicSizeStep = steps.arabic,
            translationSizeStep = steps.translation,
            transliterationSizeStep = steps.transliteration,
            wordByWordSizeStep = steps.wordByWord,
            mushafSizeStep = steps.mushaf,
            quranOrientation =
                orientationOf(preferences[QURAN_ORIENTATION_KEY]) ?: defaults.quranOrientation,
            appOrientation =
                orientationOf(preferences[APP_ORIENTATION_KEY]) ?: defaults.appOrientation,
            highlightBookmarks =
                preferences[HIGHLIGHT_BOOKMARKS_KEY] ?: defaults.highlightBookmarks,
            volumeKeysTurnPages = preferences[VOLUME_KEYS_KEY] ?: defaults.volumeKeysTurnPages,
            showPageInfo = preferences[SHOW_PAGE_INFO_KEY] ?: defaults.showPageInfo,
        )
    }

    private fun translationsOf(
        preferences: Preferences,
        defaults: ReadSettings,
    ): List<TranslationId> {
        val storedTranslations = preferences[TRANSLATIONS_KEY] ?: return defaults.translations
        return storedTranslations.split(SEPARATOR).filter { it.isNotBlank() }.map(::TranslationId)
    }

    /** The stored size steps, inside their ranges, with the older keys as fallbacks. */
    private fun sizeStepsOf(preferences: Preferences, defaults: ReadSettings): SizeSteps {
        val legacyStep = TextScale.stepForLegacyName(preferences[LEGACY_TEXT_SIZE_KEY])
        val arabic = preferences[ARABIC_STEP_KEY] ?: legacyStep ?: defaults.arabicSizeStep
        val translation =
            preferences[TRANSLATION_STEP_KEY] ?: legacyStep ?: defaults.translationSizeStep
        return SizeSteps(
            arabic = TextScale.clampStep(arabic),
            translation = TextScale.clampStep(translation),
            // Before these layers had their own size they followed the translation size.
            transliteration =
                TextScale.clampStep(preferences[TRANSLITERATION_STEP_KEY] ?: translation),
            wordByWord = TextScale.clampStep(preferences[WORD_BY_WORD_STEP_KEY] ?: translation),
            mushaf =
                TextScale.clampMushafStep(preferences[MUSHAF_STEP_KEY] ?: defaults.mushafSizeStep),
        )
    }

    private class SizeSteps(
        val arabic: Int,
        val translation: Int,
        val transliteration: Int,
        val wordByWord: Int,
        val mushaf: Int,
    )

    /**
     * The Translation and Word by word views of the three-view design are both the line-by-line
     * view now; "AyahList" is the name Translation had before that.
     */
    private fun pageViewOf(storedName: String?): PageView? =
        when (storedName) {
            LEGACY_AYAH_LIST_VIEW,
            LEGACY_TRANSLATION_VIEW,
            LEGACY_WORD_BY_WORD_VIEW -> PageView.LineByLine
            else -> PageView.entries.firstOrNull { it.name == storedName }
        }

    /**
     * The word-by-word layer an old saved view stands for: the Word by word view had meanings under
     * the words and the Translation view had none. Null for a view saved in the new form, whose own
     * stored tick is used.
     */
    private fun wordByWordLayerOf(storedViewName: String?): Boolean? =
        when (storedViewName) {
            LEGACY_WORD_BY_WORD_VIEW -> true
            LEGACY_AYAH_LIST_VIEW,
            LEGACY_TRANSLATION_VIEW -> false
            else -> null
        }

    private fun orientationOf(storedName: String?): ScreenOrientation? =
        ScreenOrientation.entries.firstOrNull { it.name == storedName }

    private companion object {
        const val SEPARATOR = ","
        const val LEGACY_AYAH_LIST_VIEW = "AyahList"
        const val LEGACY_TRANSLATION_VIEW = "Translation"
        const val LEGACY_WORD_BY_WORD_VIEW = "WordByWord"
        val SHOW_ARABIC_KEY = booleanPreferencesKey("show_arabic")
        val TRANSLITERATION_STEP_KEY = intPreferencesKey("transliteration_size_step")
        val WORD_BY_WORD_STEP_KEY = intPreferencesKey("word_by_word_size_step")
        val MUSHAF_STEP_KEY = intPreferencesKey("mushaf_size_step")
        val QURAN_ORIENTATION_KEY = stringPreferencesKey("quran_orientation")
        val APP_ORIENTATION_KEY = stringPreferencesKey("app_orientation")
        val SCRIPT_KEY = stringPreferencesKey("script")
        val PAGE_VIEW_KEY = stringPreferencesKey("page_view")
        val TRANSLATIONS_KEY = stringPreferencesKey("translations")
        val TRANSLITERATION_KEY = booleanPreferencesKey("transliteration")
        val WORD_BY_WORD_KEY = booleanPreferencesKey("word_by_word")
        val WORD_BY_WORD_LANGUAGE_KEY = stringPreferencesKey("word_by_word_language")
        val LEGACY_TEXT_SIZE_KEY = stringPreferencesKey("text_size")
        val ARABIC_STEP_KEY = intPreferencesKey("arabic_size_step")
        val TRANSLATION_STEP_KEY = intPreferencesKey("translation_size_step")
        val HIGHLIGHT_BOOKMARKS_KEY = booleanPreferencesKey("highlight_bookmarks")
        val VOLUME_KEYS_KEY = booleanPreferencesKey("volume_keys_turn_pages")
        val SHOW_PAGE_INFO_KEY = booleanPreferencesKey("show_page_info")
    }
}
