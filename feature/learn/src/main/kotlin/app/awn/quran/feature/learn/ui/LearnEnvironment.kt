package app.awn.quran.feature.learn.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.TranslationId
import app.awn.quran.core.data.TranslationRepository
import app.awn.quran.core.data.WordKey
import app.awn.quran.feature.learn.course.LearningArabic
import app.awn.quran.feature.learn.course.LearningAyah

/**
 * What the learning screens draw Arabic, names and translations from. [languageCode] is "en" or
 * "bn": the language of the app, which decides lesson text, meanings and the translation shown
 * under an ayah.
 */
class LearnEnvironment(
    val arabic: LearningArabic,
    val quranText: QuranTextRepository,
    val translations: TranslationRepository,
    val languageCode: String,
    val chapters: List<Chapter>,
    private val fallbackSurahName: (Int) -> String,
) {
    fun surahName(surah: Int): String {
        val chapter = chapters.firstOrNull { it.number == surah } ?: return fallbackSurahName(surah)
        return if (languageCode == "bn") chapter.nameBangla ?: chapter.nameSimple
        else chapter.nameSimple
    }

    /** "Al-Baqarah 2:7". */
    fun ayahReference(ayah: AyahRef): String = "${surahName(ayah.surah)} $ayah"

    /** The ayah's text in the translation that goes with the app language, if it is installed. */
    suspend fun translation(ayah: AyahRef): String? {
        val id = if (languageCode == "bn") BANGLA_TRANSLATION else ENGLISH_TRANSLATION
        return translations.text(TranslationId(id), ayah)?.text
    }

    private companion object {
        const val ENGLISH_TRANSLATION = "en_saheeh"
        const val BANGLA_TRANSLATION = "bn_zakaria"
    }
}

val LocalLearnEnvironment = compositionLocalOf<LearnEnvironment?> { null }

/** The texts of [keys] in the learning script, empty until they are loaded. */
@Composable
internal fun rememberWordTexts(keys: Set<WordKey>): Map<WordKey, String> {
    val environment = LocalLearnEnvironment.current
    val texts by
        produceState(emptyMap<WordKey, String>(), keys, environment) {
            val arabic = environment?.arabic ?: return@produceState
            value = keys.mapNotNull { key -> arabic.word(key)?.let { key to it.text } }.toMap()
        }
    return texts
}

/** The words of [ayah] in the learning script, or null until they are loaded. */
@Composable
internal fun rememberAyah(ayah: AyahRef?): State<LearningAyah?> {
    val environment = LocalLearnEnvironment.current
    return produceState<LearningAyah?>(null, ayah, environment) {
        value = if (ayah == null) null else environment?.arabic?.ayah(ayah)
    }
}

/** The translation of [ayah], or null when none is installed. */
@Composable
internal fun rememberTranslation(ayah: AyahRef?): State<String?> {
    val environment = LocalLearnEnvironment.current
    return produceState<String?>(null, ayah, environment) {
        value = if (ayah == null) null else environment?.translation(ayah)
    }
}
