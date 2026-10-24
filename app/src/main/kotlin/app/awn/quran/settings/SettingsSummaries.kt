package app.awn.quran.settings

import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.awn.quran.R
import app.awn.quran.core.ui.theme.ColourSettings
import app.awn.quran.feature.learn.content.LearnLanguage
import app.awn.quran.feature.read.page.pageTypeNameRes
import app.awn.quran.feature.read.settings.ReadSettings
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val SEPARATOR = " · "

/**
 * The second line of each page in the hub: the values the user has chosen, so the whole setup can
 * be read without opening a page. [totalBytes] is the space all downloads use.
 */
@Composable
fun hubSummaries(
    state: SettingsState,
    colours: ColourSettings,
    totalBytes: Long,
    versionName: String,
): Map<SettingsSection, String> {
    val context = LocalContext.current
    val audioParts = buildList {
        if (state.reciterName.isNotEmpty()) add(state.reciterName)
        if (state.wifiOnly) add(stringResource(R.string.audio_wifi_only))
    }
    return mapOf(
        SettingsSection.QuranPage to
            quranPageSummaryParts(state.readSettings).joinToString(SEPARATOR),
        SettingsSection.Audio to audioParts.joinToString(SEPARATOR),
        SettingsSection.ReciteHifz to reciteSummaryParts(state).joinToString(SEPARATOR),
        SettingsSection.Learn to learnSummaryParts(state).joinToString(SEPARATOR),
        SettingsSection.Colours to
            stringResource(
                R.string.summary_colours,
                stringResource(colours.day.label()),
                stringResource(colours.night.label()),
            ),
        SettingsSection.Storage to
            stringResource(
                R.string.summary_space_used,
                Formatter.formatShortFileSize(context, totalBytes),
            ),
        SettingsSection.Backup to stringResource(R.string.summary_backup),
        SettingsSection.About to stringResource(R.string.summary_about, versionName),
    )
}

/** The edition name first, then what is shown under the Arabic. */
@Composable
private fun quranPageSummaryParts(read: ReadSettings): List<String> = buildList {
    add(stringResource(pageTypeNameRes(read.script)))
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
    if (size == 1) add(stringResource(R.string.summary_arabic_only))
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

/** The reminder time or "No reminder", then the language the lessons are written in. */
@Composable
internal fun learnSummaryParts(state: SettingsState): List<String> {
    val reminder =
        if (state.reminderEnabled) {
            val time = LocalTime.of(state.reminderHour, state.reminderMinute)
            stringResource(
                R.string.summary_learn_reminder,
                time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
            )
        } else {
            stringResource(R.string.summary_learn_no_reminder)
        }
    val lessonLanguage =
        LearnLanguage.resolve(state.lessonLanguage, LocalConfiguration.current.locales[0])
    val languageName =
        when (lessonLanguage) {
            LearnLanguage.English -> R.string.language_own_english
            LearnLanguage.Bangla -> R.string.language_own_bangla
        }
    return listOf(
        reminder,
        stringResource(R.string.summary_learn_language, stringResource(languageName)),
    )
}
