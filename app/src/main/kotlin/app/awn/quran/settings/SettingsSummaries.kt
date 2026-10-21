package app.awn.quran.settings

import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.awn.quran.R
import app.awn.quran.core.ui.theme.ColourSettings
import app.awn.quran.feature.read.page.pageTypeNameRes
import app.awn.quran.feature.read.settings.ReadSettings

private const val SUMMARY_SEPARATOR = " · "

/**
 * The second line of each section in the hub: the values the user has chosen, so the whole setup
 * can be read without opening a page. [totalBytes] is the space all downloads use.
 */
@Composable
fun hubSummaries(
    state: SettingsState,
    colours: ColourSettings,
    totalBytes: Long,
    versionName: String,
): Map<SettingsSection, String> {
    val context = LocalContext.current
    val read = state.readSettings
    val pageDetails =
        stringResource(
            if (read.showPageInfo) R.string.summary_page_details_shown
            else R.string.summary_page_details_hidden,
        )
    val readingParts = readingSummaryParts(read)
    val audioParts = buildList {
        if (state.reciterName.isNotEmpty()) add(state.reciterName)
        if (state.wifiOnly) add(stringResource(R.string.audio_wifi_only))
    }
    val reciteParts = reciteSummaryParts(state)
    val language = LocalConfiguration.current.locales[0].let { it.getDisplayLanguage(it) }
    return mapOf(
        SettingsSection.Mushaf to
            listOf(stringResource(pageTypeNameRes(read.script)), pageDetails)
                .joinToString(SUMMARY_SEPARATOR),
        SettingsSection.Reading to readingParts.joinToString(SUMMARY_SEPARATOR),
        SettingsSection.Audio to audioParts.joinToString(SUMMARY_SEPARATOR),
        SettingsSection.Downloads to
            stringResource(
                R.string.summary_space_used,
                Formatter.formatShortFileSize(context, totalBytes),
            ),
        SettingsSection.Recite to reciteParts.joinToString(SUMMARY_SEPARATOR),
        SettingsSection.Appearance to
            stringResource(
                R.string.summary_appearance,
                stringResource(colours.day.label()),
                stringResource(colours.night.label()),
                language,
            ),
        SettingsSection.Backup to stringResource(R.string.summary_backup),
        SettingsSection.About to stringResource(R.string.summary_about, versionName),
    )
}

@Composable
private fun readingSummaryParts(read: ReadSettings): List<String> = buildList {
    if (read.translations.isNotEmpty()) {
        add(
            pluralStringResource(
                R.plurals.summary_translations,
                read.translations.size,
                read.translations.size,
            ),
        )
    }
    if (read.showTransliteration) add(stringResource(R.string.summary_transliteration))
    if (read.showWordByWord) add(stringResource(R.string.summary_word_by_word))
    if (isEmpty()) add(stringResource(R.string.summary_arabic_only))
}

@Composable
private fun reciteSummaryParts(state: SettingsState): List<String> = buildList {
    if (state.hifzEnabled) add(stringResource(R.string.summary_words_hidden))
    if (state.hintEnabled) add(stringResource(R.string.summary_hint_on))
    val days =
        pluralStringResource(
            R.plurals.recite_days,
            state.memorisationThreshold,
            state.memorisationThreshold,
        )
    add(stringResource(R.string.summary_memorised_after, days))
}
