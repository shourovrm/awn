package app.awn.quran.feature.learn.content

import java.util.Locale

/** The two languages of the lesson texts. */
enum class LearnLanguage {
    English,
    Bangla;

    companion object {
        fun of(locale: Locale): LearnLanguage = if (locale.language == "bn") Bangla else English

        /**
         * The language lessons are shown in: the user's [choice], or the language of the app when
         * the choice is to follow it.
         */
        fun resolve(choice: LessonLanguageChoice, appLocale: Locale): LearnLanguage =
            when (choice) {
                LessonLanguageChoice.FollowApp -> of(appLocale)
                LessonLanguageChoice.English -> English
                LessonLanguageChoice.Bangla -> Bangla
            }
    }
}

/** What the user picked for the lesson language in settings. */
enum class LessonLanguageChoice(val storedName: String) {
    FollowApp("follow_app"),
    English("english"),
    Bangla("bangla");

    companion object {
        /** The choice saved as [storedName]; an unknown or missing value means [FollowApp]. */
        fun fromStoredName(storedName: String?): LessonLanguageChoice =
            entries.firstOrNull { it.storedName == storedName } ?: FollowApp
    }
}
