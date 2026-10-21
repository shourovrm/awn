package app.awn.quran.feature.learn.course

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.TranslationRepository
import app.awn.quran.feature.learn.session.WordInfo
import app.awn.quran.feature.learn.session.WordInfoSource
import app.awn.quran.feature.learn.words.WordFrequency
import java.util.concurrent.ConcurrentHashMap

/**
 * Looks up the meaning of a form in the installed word-by-word pack: the meaning of its
 * representative word, in [language], or in English when the pack of [language] is missing or
 * has no text for the word (as the Quran screen does).
 */
class RepositoryWordMeanings(
    private val translations: TranslationRepository,
    private val language: String,
) {
    private val glosses = ConcurrentHashMap<Pair<String, AyahRef>, List<String>>()

    /** The meaning of word [position] of [ayah], or null when neither pack has one. */
    suspend fun meaning(ayah: AyahRef, position: Int): String? {
        val wanted = glossesOf(language, ayah).getOrNull(position - 1)?.takeIf { it.isNotBlank() }
        if (wanted != null || language == FALLBACK_LANGUAGE) return wanted
        return glossesOf(FALLBACK_LANGUAGE, ayah).getOrNull(position - 1)?.takeIf { it.isNotBlank() }
    }

    private suspend fun glossesOf(language: String, ayah: AyahRef): List<String> =
        glosses[language to ayah]
            ?: translations.wordByWord(language, ayah).also { glosses[language to ayah] = it }

    companion object {
        const val FALLBACK_LANGUAGE = "en"
    }
}

/** [WordInfoSource] over the Quran text and the word-by-word pack, with a cache per form. */
class RepositoryWordInfoSource(
    private val frequency: WordFrequency,
    private val arabic: LearningArabic,
    private val meanings: RepositoryWordMeanings,
) : WordInfoSource {
    private val cache = ConcurrentHashMap<Int, WordInfo>()

    override suspend fun info(rank: Int): WordInfo? {
        cache[rank]?.let {
            return it
        }
        val key = frequency.representative(rank)
        val ayah = AyahRef(key.surah, key.ayah)
        val meaning = meanings.meaning(ayah, key.wordPosition) ?: return null
        val words = arabic.ayah(ayah).words
        val text = words.firstOrNull { it.key == key }?.text ?: return null
        return WordInfo(
                rank = rank,
                representative = key,
                meaning = meaning,
                letterCount = text.count { Character.isLetter(it) },
                ayahWordCount = words.size,
            )
            .also { cache[rank] = it }
    }
}
