package app.awn.quran.feature.learn.content

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class LearnLanguageTest {

    private val englishApp = Locale.forLanguageTag("en")
    private val banglaApp = Locale.forLanguageTag("bn")

    @Test
    fun followingTheAppGivesTheAppLanguage() {
        assertEquals(
            LearnLanguage.English,
            LearnLanguage.resolve(LessonLanguageChoice.FollowApp, englishApp),
        )
        assertEquals(
            LearnLanguage.Bangla,
            LearnLanguage.resolve(LessonLanguageChoice.FollowApp, banglaApp),
        )
    }

    @Test
    fun aChosenLanguageWinsOverTheAppLanguage() {
        assertEquals(
            LearnLanguage.Bangla,
            LearnLanguage.resolve(LessonLanguageChoice.Bangla, englishApp),
        )
        assertEquals(
            LearnLanguage.English,
            LearnLanguage.resolve(LessonLanguageChoice.English, banglaApp),
        )
    }

    @Test
    fun aMissingOrUnknownStoredChoiceFollowsTheApp() {
        assertEquals(LessonLanguageChoice.FollowApp, LessonLanguageChoice.fromStoredName(null))
        assertEquals(LessonLanguageChoice.FollowApp, LessonLanguageChoice.fromStoredName("french"))
    }

    @Test
    fun everyChoiceSurvivesStorage() {
        for (choice in LessonLanguageChoice.entries) {
            assertEquals(choice, LessonLanguageChoice.fromStoredName(choice.storedName))
        }
    }
}
