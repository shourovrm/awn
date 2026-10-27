package app.awn.quran.settings

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.StringRes
import app.awn.quran.R
import app.awn.quran.Routes
import app.awn.quran.core.ui.components.SettingRowKeys
import app.awn.quran.feature.learn.R as LearnR
import app.awn.quran.feature.listen.R as ListenR
import app.awn.quran.feature.read.R as ReadR
import app.awn.quran.feature.recite.R as ReciteR
import java.util.Locale

/**
 * One searchable setting. [rowKey] null means the page itself. [keywords] are the names of the
 * values the setting offers, so that typing "surah" finds "Download ahead". [pageLabel] is the
 * second line of the result: the page the setting is on.
 */
private class RegisteredSetting(
    val route: String,
    @StringRes val pageLabel: Int,
    val rowKey: String?,
    @StringRes val label: Int,
    val keywords: List<Int> = emptyList(),
)

private fun onPage(
    section: SettingsSection,
    rowKey: String,
    @StringRes label: Int,
    keywords: List<Int> = emptyList(),
) = RegisteredSetting(section.route, section.label, rowKey, label, keywords)

private val QURAN_PAGE_SETTINGS =
    listOf(
        onPage(
            SettingsSection.QuranPage,
            SettingRowKeys.QURAN_MUSHAF,
            ReadR.string.mushaf_print,
            listOf(
                ReadR.string.mushaf_type_v2,
                ReadR.string.mushaf_type_v1,
                ReadR.string.mushaf_type_v4,
                ReadR.string.mushaf_type_indopak,
            ),
        ),
        onPage(
            SettingsSection.QuranPage,
            SettingRowKeys.QURAN_PAGE_INFO,
            ReadR.string.mushaf_show_page_info,
        ),
        onPage(
            SettingsSection.QuranPage,
            SettingRowKeys.QURAN_TRANSLATIONS,
            ReadR.string.setting_translations,
        ),
        onPage(
            SettingsSection.QuranPage,
            SettingRowKeys.QURAN_TRANSLITERATION,
            ReadR.string.setting_transliteration,
        ),
        onPage(
            SettingsSection.QuranPage,
            SettingRowKeys.QURAN_WORD_BY_WORD,
            ReadR.string.setting_word_by_word,
        ),
        onPage(
            SettingsSection.QuranPage,
            SettingRowKeys.QURAN_SIZE_ARABIC,
            ReadR.string.layer_arabic,
            listOf(ReadR.string.reading_group_size),
        ),
        onPage(
            SettingsSection.QuranPage,
            SettingRowKeys.QURAN_SIZE_TRANSLATION,
            ReadR.string.layer_translation,
            listOf(ReadR.string.reading_group_size),
        ),
        onPage(
            SettingsSection.QuranPage,
            SettingRowKeys.QURAN_SIZE_TRANSLITERATION,
            ReadR.string.setting_transliteration,
            listOf(ReadR.string.reading_group_size),
        ),
        onPage(
            SettingsSection.QuranPage,
            SettingRowKeys.QURAN_SIZE_WORD_BY_WORD,
            ReadR.string.setting_word_by_word,
            listOf(ReadR.string.reading_group_size),
        ),
        onPage(
            SettingsSection.QuranPage,
            SettingRowKeys.QURAN_HIGHLIGHT_BOOKMARKS,
            ReadR.string.setting_highlight_bookmarks,
        ),
        onPage(
            SettingsSection.QuranPage,
            SettingRowKeys.QURAN_VOLUME_KEYS,
            ReadR.string.setting_volume_keys,
        ),
        onPage(
            SettingsSection.QuranPage,
            SettingRowKeys.QURAN_KEEP_SCREEN_ON,
            ReadR.string.setting_keep_screen_on,
        ),
        onPage(
            SettingsSection.QuranPage,
            SettingRowKeys.QURAN_ROTATION,
            ReadR.string.reading_group_rotation,
            listOf(
                ReadR.string.orientation_auto,
                ReadR.string.orientation_portrait,
                ReadR.string.orientation_landscape,
            ),
        ),
        onPage(
            SettingsSection.QuranPage,
            SettingRowKeys.QURAN_SURAH_MEANING,
            ReadR.string.setting_surah_name_meaning,
        ),
    )

private val AUDIO_SETTINGS =
    listOf(
        onPage(
            SettingsSection.Audio,
            SettingRowKeys.AUDIO_RECITER,
            ListenR.string.audio_setting_reciter,
        ),
        onPage(
            SettingsSection.Audio,
            SettingRowKeys.AUDIO_WORD_RECITER,
            ListenR.string.audio_setting_word_audio,
        ),
        onPage(
            SettingsSection.Audio,
            SettingRowKeys.AUDIO_STOP_ON_CLOSE,
            ListenR.string.audio_setting_stop_on_close,
        ),
        onPage(
            SettingsSection.Audio,
            SettingRowKeys.AUDIO_DOWNLOAD_BY,
            ListenR.string.audio_setting_download_amount,
            listOf(
                ListenR.string.amount_page,
                ListenR.string.amount_surah,
                ListenR.string.amount_juz,
            ),
        ),
        onPage(
            SettingsSection.Audio,
            SettingRowKeys.AUDIO_WIFI_ONLY,
            ListenR.string.audio_setting_wifi,
        ),
        onPage(
            SettingsSection.Audio,
            SettingRowKeys.AUDIO_DOWNLOADS,
            ListenR.string.audio_setting_downloads,
        ),
    )

private val STORAGE_SETTINGS =
    listOf(
        onPage(
            SettingsSection.Storage,
            SettingRowKeys.STORAGE_SPACE_USED,
            R.string.storage_space_used,
        ),
        onPage(SettingsSection.Storage, SettingRowKeys.STORAGE_MUSHAF, R.string.storage_mushaf),
        onPage(
            SettingsSection.Storage,
            SettingRowKeys.STORAGE_TRANSLATIONS,
            R.string.storage_translations,
        ),
        onPage(
            SettingsSection.Storage,
            SettingRowKeys.STORAGE_TRANSLITERATION,
            R.string.storage_transliteration,
        ),
        onPage(
            SettingsSection.Storage,
            SettingRowKeys.STORAGE_WORD_BY_WORD,
            R.string.storage_word_by_word,
        ),
        onPage(
            SettingsSection.Storage,
            SettingRowKeys.STORAGE_WORD_AUDIO,
            R.string.storage_word_audio,
        ),
        onPage(
            SettingsSection.Storage,
            SettingRowKeys.STORAGE_LESSON_SOUNDS,
            R.string.storage_lesson_sounds,
        ),
        onPage(SettingsSection.Storage, SettingRowKeys.STORAGE_RECITERS, R.string.storage_reciters),
        onPage(
            SettingsSection.Storage,
            SettingRowKeys.STORAGE_RECITATION_CHECKER,
            ReadR.string.speech_row_title,
        ),
    )

private val PRACTICE_SETTINGS =
    listOf(
        onPage(
            SettingsSection.ReciteHifz,
            SettingRowKeys.RECITE_HIDE_WORDS,
            ReciteR.string.recite_settings_hide_words,
        ),
        onPage(
            SettingsSection.ReciteHifz,
            SettingRowKeys.RECITE_HINT,
            ReciteR.string.recite_settings_hint,
        ),
        onPage(
            SettingsSection.ReciteHifz,
            SettingRowKeys.RECITE_MEMORISED_AFTER,
            R.string.recite_memorised_after,
        ),
        onPage(
            SettingsSection.ReciteHifz,
            SettingRowKeys.RECITE_FOLLOW_PACE,
            ReciteR.string.recite_follow_pace,
            listOf(ReciteR.string.recite_pace_patient, ReciteR.string.recite_pace_eager),
        ),
        onPage(
            SettingsSection.Learn,
            SettingRowKeys.LEARN_REMINDER,
            LearnR.string.reminder_setting,
        ),
        onPage(
            SettingsSection.Learn,
            SettingRowKeys.LEARN_REMINDER_TIME,
            LearnR.string.reminder_time,
        ),
        onPage(
            SettingsSection.Learn,
            SettingRowKeys.LEARN_LESSON_LANGUAGE,
            LearnR.string.lesson_language,
            listOf(
                LearnR.string.lesson_language_follow_app,
                R.string.language_own_english,
                R.string.language_own_bangla,
            ),
        ),
    )

private val APP_SETTINGS =
    listOf(
        RegisteredSetting(
            Routes.SETTINGS,
            R.string.settings_title,
            SettingRowKeys.HUB_LANGUAGE,
            R.string.settings_language,
            listOf(R.string.language_own_english, R.string.language_own_bangla),
        ),
        onPage(
            SettingsSection.Colours,
            SettingRowKeys.COLOURS_DAY,
            R.string.colours_day,
            listOf(R.string.colours_cream, R.string.colours_white),
        ),
        onPage(
            SettingsSection.Colours,
            SettingRowKeys.COLOURS_NIGHT,
            R.string.colours_night,
            listOf(R.string.colours_dark, R.string.colours_black),
        ),
        onPage(
            SettingsSection.Colours,
            SettingRowKeys.COLOURS_FOLLOW_PHONE,
            R.string.colours_follow_phone,
        ),
        onPage(SettingsSection.Backup, SettingRowKeys.BACKUP_SAVE, R.string.backup_save),
        onPage(SettingsSection.Backup, SettingRowKeys.BACKUP_RESTORE, R.string.backup_restore),
        onPage(SettingsSection.About, SettingRowKeys.ABOUT_NOTICE, R.string.notice_learn_title),
        onPage(SettingsSection.About, SettingRowKeys.ABOUT_CREDITS, R.string.about_credits),
        onPage(SettingsSection.About, SettingRowKeys.ABOUT_DEVELOPER, R.string.about_developer),
        onPage(SettingsSection.About, SettingRowKeys.ABOUT_VERSION, R.string.about_version),
    )

/**
 * Extra words that find a page by its old or alternative name. Storage's title is now Downloads,
 * and people still type "storage", so that word is kept as a keyword.
 */
private fun pageKeywords(section: SettingsSection): List<Int> =
    if (section == SettingsSection.Storage) {
        listOf(R.string.settings_storage_search_keyword)
    } else {
        emptyList()
    }

private val SETTINGS: List<RegisteredSetting> =
    SettingsSection.entries.map { section ->
        RegisteredSetting(section.route, section.label, null, section.label, pageKeywords(section))
    } + QURAN_PAGE_SETTINGS + AUDIO_SETTINGS + PRACTICE_SETTINGS + STORAGE_SETTINGS + APP_SETTINGS

/**
 * Builds the searchable entries in the app's current language. Each entry also carries its label
 * and value names in English and in Bangla, so either language finds it.
 */
fun buildSettingsSearchEntries(context: Context): List<SettingsSearchEntry> {
    val english = resourcesFor(context, Locale.forLanguageTag("en"))
    val bangla = resourcesFor(context, Locale.forLanguageTag("bn"))
    return SETTINGS.map { setting ->
        val textResources = listOf(setting.label) + setting.keywords + setting.pageLabel
        SettingsSearchEntry(
            route = setting.route,
            rowKey = setting.rowKey,
            label = context.getString(setting.label),
            pageLabel = context.getString(setting.pageLabel),
            searchTexts =
                textResources.flatMap { listOf(english.getString(it), bangla.getString(it)) },
        )
    }
}

private fun resourcesFor(context: Context, locale: Locale): Resources {
    val configuration = Configuration(context.resources.configuration)
    configuration.setLocale(locale)
    return context.createConfigurationContext(configuration).resources
}

/** The keys of every registered row, for the test that checks each page defines its rows. */
internal fun registeredRowKeys(): List<String> = SETTINGS.mapNotNull { it.rowKey }

/** The route and page label of every registered page row, for the test of the hub's sections. */
internal fun registeredPageRoutes(): List<String> =
    SETTINGS.filter { it.rowKey == null }.map { it.route }
