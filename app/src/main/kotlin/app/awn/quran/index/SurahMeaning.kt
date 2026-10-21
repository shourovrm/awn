package app.awn.quran.index

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringArrayResource
import app.awn.quran.R
import app.awn.quran.core.data.Chapter

private const val BANGLA_LANGUAGE = "bn"

/**
 * The meaning of a surah's name in [languageCode] ("bn", "en", ...). Bangla comes from the Quran
 * pack; every other language, and a Bangla surah the pack has no meaning for, falls back to the
 * English meaning from `surah_meanings_en`. Null only when neither exists.
 */
fun surahMeaningFor(
    languageCode: String,
    chapter: Chapter,
    englishMeanings: List<String>,
): String? {
    val english = englishMeanings.getOrNull(chapter.number - 1)?.takeIf { it.isNotBlank() }
    if (languageCode != BANGLA_LANGUAGE) return english
    return chapter.nameBangla?.takeIf { it.isNotBlank() } ?: english
}

/** The meanings of the surah names in the app language, for the list rows. */
class SurahMeanings(private val languageCode: String, private val englishMeanings: List<String>) {
    fun of(chapter: Chapter): String? = surahMeaningFor(languageCode, chapter, englishMeanings)
}

@Composable
internal fun rememberSurahMeanings(): SurahMeanings {
    val englishMeanings = stringArrayResource(R.array.surah_meanings_en).toList()
    val languageCode = LocalConfiguration.current.locales[0].language
    return SurahMeanings(languageCode, englishMeanings)
}
