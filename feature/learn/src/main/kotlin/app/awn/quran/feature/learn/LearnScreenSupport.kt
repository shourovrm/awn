package app.awn.quran.feature.learn

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import app.awn.quran.core.data.Chapter
import app.awn.quran.feature.learn.content.LearnLanguage
import app.awn.quran.feature.learn.content.LessonLanguageChoice
import java.util.Locale

/** Names a surah in the lesson language, or by its number while the chapters are loading. */
@Composable
internal fun rememberSurahName(chapters: List<Chapter>, language: LearnLanguage): (Int) -> String {
    val resources = LocalResources.current
    return remember(chapters, language, resources) {
        { surah: Int ->
            surahNameOf(chapters, surah, language) {
                resources.getString(R.string.surah_number, surah)
            }
        }
    }
}

/** The lesson language the user chose, or the app language while they follow it. */
@Composable
internal fun rememberLessonLanguage(services: LearnServices, locale: Locale): LearnLanguage {
    val lessonLanguageChoice by
        services.settings.lessonLanguage.collectAsState(initial = LessonLanguageChoice.FollowApp)
    return LearnLanguage.resolve(lessonLanguageChoice, locale)
}

private fun surahNameOf(
    chapters: List<Chapter>,
    surah: Int,
    language: LearnLanguage,
    fallback: () -> String,
): String {
    val chapter = chapters.firstOrNull { it.number == surah } ?: return fallback()
    return if (language == LearnLanguage.Bangla) chapter.nameBangla ?: chapter.nameSimple
    else chapter.nameSimple
}
