package app.awn.quran.settings

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.StringRes
import app.awn.quran.R
import app.awn.quran.core.ui.components.SettingRowKeys
import app.awn.quran.feature.learn.R as LearnR
import app.awn.quran.feature.listen.R as ListenR
import app.awn.quran.feature.read.R as ReadR
import app.awn.quran.feature.recite.R as ReciteR
import java.util.Locale

/**
 * One searchable setting. [rowKey] null means the page itself. [keywords] are the names of the
 * values the setting offers, so that typing "surah" finds "Download by".
 */
private class RegisteredSetting(
    val section: SettingsSection,
    val rowKey: String?,
    @StringRes val label: Int,
    val keywords: List<Int> = emptyList(),
)

private val ORIENTATION_KEYWORDS =
    listOf(
        ReadR.string.orientation_auto,
        ReadR.string.orientation_portrait,
        ReadR.string.orientation_landscape,
        ReadR.string.reading_group_rotation,
    )

private val SETTINGS: List<RegisteredSetting> =
    SettingsSection.entries.map { RegisteredSetting(it, null, it.label) } +
        listOf(
            RegisteredSetting(
                SettingsSection.Mushaf,
                SettingRowKeys.MUSHAF_PRINT,
                ReadR.string.mushaf_print,
                listOf(
                    ReadR.string.mushaf_type_v2,
                    ReadR.string.mushaf_type_v1,
                    ReadR.string.mushaf_type_v4,
                ),
            ),
            RegisteredSetting(
                SettingsSection.Mushaf,
                SettingRowKeys.MUSHAF_PAGE_INFO,
                ReadR.string.mushaf_show_page_info,
            ),
            RegisteredSetting(
                SettingsSection.Reading,
                SettingRowKeys.READING_TRANSLATIONS,
                ReadR.string.setting_translations,
            ),
            RegisteredSetting(
                SettingsSection.Reading,
                SettingRowKeys.READING_TRANSLITERATION,
                ReadR.string.setting_transliteration,
            ),
            RegisteredSetting(
                SettingsSection.Reading,
                SettingRowKeys.READING_WORD_BY_WORD,
                ReadR.string.setting_word_by_word,
            ),
            RegisteredSetting(
                SettingsSection.Reading,
                SettingRowKeys.READING_WORD_LANGUAGE,
                ReadR.string.setting_word_language,
                listOf(ReadR.string.word_language_app),
            ),
            RegisteredSetting(
                SettingsSection.Reading,
                SettingRowKeys.READING_SIZE_ARABIC,
                ReadR.string.layer_arabic,
                listOf(ReadR.string.reading_group_size),
            ),
            RegisteredSetting(
                SettingsSection.Reading,
                SettingRowKeys.READING_SIZE_TRANSLATION,
                ReadR.string.layer_translation,
                listOf(ReadR.string.reading_group_size),
            ),
            RegisteredSetting(
                SettingsSection.Reading,
                SettingRowKeys.READING_HIGHLIGHT_BOOKMARKS,
                ReadR.string.setting_highlight_bookmarks,
            ),
            RegisteredSetting(
                SettingsSection.Reading,
                SettingRowKeys.READING_VOLUME_KEYS,
                ReadR.string.setting_volume_keys,
            ),
            RegisteredSetting(
                SettingsSection.Reading,
                SettingRowKeys.READING_ORIENTATION_QURAN,
                ReadR.string.setting_orientation_quran,
                ORIENTATION_KEYWORDS,
            ),
            RegisteredSetting(
                SettingsSection.Reading,
                SettingRowKeys.READING_ORIENTATION_APP,
                ReadR.string.setting_orientation_app,
                ORIENTATION_KEYWORDS,
            ),
            RegisteredSetting(
                SettingsSection.Audio,
                SettingRowKeys.AUDIO_RECITER,
                ListenR.string.audio_setting_reciter,
            ),
            RegisteredSetting(
                SettingsSection.Audio,
                SettingRowKeys.AUDIO_WORD_RECITER,
                ListenR.string.audio_setting_word_audio,
            ),
            RegisteredSetting(
                SettingsSection.Audio,
                SettingRowKeys.AUDIO_DOWNLOAD_BY,
                ListenR.string.audio_setting_download_amount,
                listOf(
                    ListenR.string.amount_page,
                    ListenR.string.amount_surah,
                    ListenR.string.amount_juz,
                ),
            ),
            RegisteredSetting(
                SettingsSection.Audio,
                SettingRowKeys.AUDIO_WIFI_ONLY,
                ListenR.string.audio_setting_wifi,
            ),
            RegisteredSetting(
                SettingsSection.Audio,
                SettingRowKeys.AUDIO_STOP_ON_CLOSE,
                ListenR.string.audio_setting_stop_on_close,
            ),
            RegisteredSetting(
                SettingsSection.Audio,
                SettingRowKeys.AUDIO_DOWNLOADS,
                ListenR.string.audio_setting_downloads,
            ),
            RegisteredSetting(
                SettingsSection.Downloads,
                SettingRowKeys.DOWNLOADS_PAGE_TYPES,
                R.string.downloads_page_types,
            ),
            RegisteredSetting(
                SettingsSection.Downloads,
                SettingRowKeys.DOWNLOADS_TRANSLATIONS,
                R.string.downloads_translations,
            ),
            RegisteredSetting(
                SettingsSection.Downloads,
                SettingRowKeys.DOWNLOADS_TRANSLITERATION,
                R.string.downloads_transliteration,
            ),
            RegisteredSetting(
                SettingsSection.Downloads,
                SettingRowKeys.DOWNLOADS_WORD_BY_WORD,
                R.string.downloads_word_by_word,
            ),
            RegisteredSetting(
                SettingsSection.Downloads,
                SettingRowKeys.DOWNLOADS_WORD_AUDIO,
                R.string.downloads_word_audio,
            ),
            RegisteredSetting(
                SettingsSection.Downloads,
                SettingRowKeys.DOWNLOADS_LESSON_SOUNDS,
                R.string.downloads_lesson_sounds,
            ),
            RegisteredSetting(
                SettingsSection.Downloads,
                SettingRowKeys.DOWNLOADS_RECITERS,
                R.string.downloads_reciters,
            ),
            RegisteredSetting(
                SettingsSection.Downloads,
                SettingRowKeys.DOWNLOADS_SPACE_USED,
                R.string.downloads_space_used,
            ),
            RegisteredSetting(
                SettingsSection.Recite,
                SettingRowKeys.RECITE_SPEED,
                ReciteR.string.recite_speed,
            ),
            RegisteredSetting(
                SettingsSection.Recite,
                SettingRowKeys.RECITE_HIDE_WORDS,
                ReciteR.string.recite_settings_hide_words,
            ),
            RegisteredSetting(
                SettingsSection.Recite,
                SettingRowKeys.RECITE_HINT,
                ReciteR.string.recite_settings_hint,
            ),
            RegisteredSetting(
                SettingsSection.Recite,
                SettingRowKeys.RECITE_REMINDER,
                LearnR.string.reminder_setting,
            ),
            RegisteredSetting(
                SettingsSection.Recite,
                SettingRowKeys.RECITE_LESSON_LANGUAGE,
                LearnR.string.lesson_language,
            ),
            RegisteredSetting(
                SettingsSection.Recite,
                SettingRowKeys.RECITE_MEMORISED_AFTER,
                R.string.recite_memorised_after,
            ),
            RegisteredSetting(
                SettingsSection.Appearance,
                SettingRowKeys.APPEARANCE_DAY,
                R.string.appearance_day_colours,
                listOf(R.string.colours_awn, R.string.colours_light),
            ),
            RegisteredSetting(
                SettingsSection.Appearance,
                SettingRowKeys.APPEARANCE_NIGHT,
                R.string.appearance_night_colours,
                listOf(R.string.colours_dark, R.string.colours_black),
            ),
            RegisteredSetting(
                SettingsSection.Appearance,
                SettingRowKeys.APPEARANCE_FOLLOW_DEVICE,
                R.string.appearance_follow_device,
            ),
            RegisteredSetting(
                SettingsSection.Appearance,
                SettingRowKeys.APPEARANCE_LANGUAGE,
                R.string.appearance_language,
                listOf(R.string.language_own_english, R.string.language_own_bangla),
            ),
            RegisteredSetting(
                SettingsSection.Appearance,
                SettingRowKeys.APPEARANCE_SURAH_MEANING,
                R.string.appearance_surah_name_meaning,
            ),
            RegisteredSetting(
                SettingsSection.Backup,
                SettingRowKeys.BACKUP_EXPORT,
                R.string.backup_export,
            ),
            RegisteredSetting(
                SettingsSection.Backup,
                SettingRowKeys.BACKUP_IMPORT,
                R.string.backup_import,
            ),
            RegisteredSetting(
                SettingsSection.About,
                SettingRowKeys.ABOUT_NOTICE,
                R.string.about_notice,
            ),
            RegisteredSetting(
                SettingsSection.About,
                SettingRowKeys.ABOUT_CREDITS,
                R.string.about_credits,
            ),
            RegisteredSetting(
                SettingsSection.About,
                SettingRowKeys.ABOUT_VERSION,
                R.string.about_version,
            ),
        )

/**
 * Builds the searchable entries in the app's current language. Each entry also carries its label
 * and value names in English and in Bangla, so either language finds it.
 */
fun buildSettingsSearchEntries(context: Context): List<SettingsSearchEntry> {
    val english = resourcesFor(context, Locale.forLanguageTag("en"))
    val bangla = resourcesFor(context, Locale.forLanguageTag("bn"))
    return SETTINGS.map { setting ->
        val textResources = listOf(setting.label) + setting.keywords + setting.section.label
        SettingsSearchEntry(
            route = setting.section.route,
            rowKey = setting.rowKey,
            label = context.getString(setting.label),
            pageLabel = context.getString(setting.section.label),
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
