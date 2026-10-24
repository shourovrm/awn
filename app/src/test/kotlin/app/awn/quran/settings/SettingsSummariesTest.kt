package app.awn.quran.settings

import android.app.Application
import android.content.ComponentName
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.awn.quran.core.data.TranslationId
import app.awn.quran.core.ui.theme.ColourSettings
import app.awn.quran.feature.learn.content.LessonLanguageChoice
import app.awn.quran.feature.read.settings.ReadSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/** The second lines of the hub, built from the stored values. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en", application = Application::class)
class SettingsSummariesTest {

    @get:Rule val composeRule = createComposeRule()

    init {
        // The app's own manifest does not declare the activity that Compose tests launch.
        val application = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(application.packageManager)
            .addActivityIfNotPresent(
                ComponentName(application.packageName, "androidx.activity.ComponentActivity"),
            )
    }

    private fun summariesOf(state: SettingsState): Map<SettingsSection, String> {
        var summaries: Map<SettingsSection, String> = emptyMap()
        composeRule.setContent {
            summaries =
                hubSummaries(state, ColourSettings(), totalBytes = 0L, versionName = "0.3.1")
        }
        composeRule.waitForIdle()
        return summaries
    }

    @Test
    fun learnSaysWhenTheReminderRingsAndWhichLanguageTheLessonsUse() {
        val summary =
            summariesOf(
                SettingsState(
                    reminderEnabled = true,
                    reminderHour = 19,
                    reminderMinute = 0,
                    lessonLanguage = LessonLanguageChoice.English,
                ),
            )

        val learn = summary.getValue(SettingsSection.Learn)
        assertTrue(learn, learn.startsWith("Reminder 7:00"))
        assertTrue(learn, learn.endsWith(" · Lessons in English"))
    }

    @Test
    fun learnSaysNoReminderWhenTheReminderIsOffAndFollowsTheAppLanguage() {
        val summary = summariesOf(SettingsState(reminderEnabled = false))

        assertEquals(
            "No reminder · Lessons in English",
            summary.getValue(SettingsSection.Learn),
        )
    }

    @Test
    fun theQuranPageNamesTheEditionThenWhatIsShownUnderTheArabic() {
        val read =
            ReadSettings(translations = listOf(TranslationId("sahih")), showWordByWord = true)

        val summary = summariesOf(SettingsState(readSettings = read))

        assertEquals(
            "Madinah Mushaf · 1421 · 1 translation · word by word",
            summary.getValue(SettingsSection.QuranPage),
        )
    }

    @Test
    fun reciteHifzListsHiddenWordsTheHintAndTheMemorisedAfterDays() {
        val summary =
            summariesOf(
                SettingsState(hifzEnabled = true, hintEnabled = true, memorisationThreshold = 3),
            )

        assertEquals(
            "Words hidden · First-letter hint · Memorised after 3 days",
            summary.getValue(SettingsSection.ReciteHifz),
        )
    }

    @Test
    fun coloursAndBackupUseTheirNewWording() {
        val summary = summariesOf(SettingsState())

        assertEquals("Cream by day · Dark at night", summary.getValue(SettingsSection.Colours))
        assertEquals(
            "Bookmarks, saved ayahs, recent pages",
            summary.getValue(SettingsSection.Backup),
        )
        assertEquals("Version 0.3.1", summary.getValue(SettingsSection.About))
    }
}
