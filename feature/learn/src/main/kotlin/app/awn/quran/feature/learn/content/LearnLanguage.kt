package app.awn.quran.feature.learn.content

import java.util.Locale

/** The two languages of the lesson texts. */
enum class LearnLanguage {
    English,
    Bangla;

    companion object {
        fun of(locale: Locale): LearnLanguage = if (locale.language == "bn") Bangla else English
    }
}
