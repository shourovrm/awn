package app.awn.quran

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source checks for the :app wiring of three fixes whose logic is tested in :feature:recite
 * (ReciteRunScopeTest): Hifz practice no longer saves "hide words" as the user's setting, a new
 * lesson starts with the words shown, and the Today row's "Revise N" passes its cap to the run.
 */
class HifzAndReviseWiringTest {

    private fun source(path: String) = File("src/main/kotlin/app/awn/quran/$path").readText()

    @Test
    fun hifzPracticeStartsARunInsteadOfChangingTheSavedSetting() {
        val services = source("services/AwnServices.kt")

        assertFalse(services.contains("recite.setHideWords("))
        assertTrue(services.contains("practiceFrom("))
    }

    @Test
    fun aNewLessonStartsWithTheWordsShown() {
        val services = source("services/AwnServices.kt")

        assertTrue(
            Regex("""hideWords\s*=\s*request\.kind\s*!=\s*PartKind\.NewLesson""")
                .containsMatchIn(services),
        )
    }

    @Test
    fun theTodayRowPassesItsCapToTheRevisionRun() {
        val today = source("tabs/TodayProvider.kt")

        assertTrue(Regex("""reviseAt\([^)]*LEARN_TODAY_CAP""").containsMatchIn(today))
    }

    @Test
    fun theAppPassesTheLimitOnToTheReciteController() {
        val app = source("AwnApp.kt")

        assertTrue(Regex("""recite\.reviseAt\(\s*ayah\s*,\s*limit""").containsMatchIn(app))
    }
}
