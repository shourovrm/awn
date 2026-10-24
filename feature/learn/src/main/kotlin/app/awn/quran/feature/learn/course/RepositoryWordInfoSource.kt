package app.awn.quran.feature.learn.course

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.TranslationRepository
import app.awn.quran.core.data.WordKey
import app.awn.quran.feature.learn.session.WordInfo
import app.awn.quran.feature.learn.session.WordInfoSource
import app.awn.quran.feature.learn.words.WordFrequency
import java.util.concurrent.ConcurrentHashMap

/**
 * Looks up the meaning of a form in the installed word-by-word pack, in [language], or in English
 * when the pack of [language] is missing or has no text for the word (as the Quran screen does).
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
        return glossesOf(FALLBACK_LANGUAGE, ayah).getOrNull(position - 1)?.takeIf {
            it.isNotBlank()
        }
    }

    /**
     * The meaning that fits the word whose places are [places] best: the commonest gloss among the
     * first [SAMPLE_SIZE] places (see [CommonGloss]). The ayahs of the sample are read in one batch
     * per pack, so a word costs one query rather than up to 40.
     */
    suspend fun commonMeaning(places: List<WordKey>): String? {
        val sample = places.take(SAMPLE_SIZE)
        val ayahs = sample.map { AyahRef(it.surah, it.ayah) }.distinct()
        loadGlosses(language, ayahs)
        if (language != FALLBACK_LANGUAGE) {
            loadGlosses(FALLBACK_LANGUAGE, ayahs.filter { glosses[language to it].isNullOrEmpty() })
        }
        val glossesOfPlaces = sample.mapNotNull {
            meaning(AyahRef(it.surah, it.ayah), it.wordPosition)
        }
        return CommonGloss.of(glossesOfPlaces)
    }

    private suspend fun loadGlosses(language: String, ayahs: List<AyahRef>) {
        val unread = ayahs.filter { !glosses.containsKey(language to it) }
        if (unread.isEmpty()) return
        translations.wordByWord(language, unread).forEach { (ayah, words) ->
            glosses[language to ayah] = words
        }
    }

    private suspend fun glossesOf(language: String, ayah: AyahRef): List<String> =
        glosses[language to ayah]
            ?: translations.wordByWord(language, ayah).also { glosses[language to ayah] = it }

    companion object {
        const val FALLBACK_LANGUAGE = "en"

        /** Enough places to tell the commonest gloss; the sample reads the first ones in order. */
        private const val SAMPLE_SIZE = 40
    }
}

/** [WordInfoSource] over the Quran text and the word-by-word pack, with a cache per form. */
class RepositoryWordInfoSource(
    private val frequency: WordFrequency,
    private val arabic: LearningArabic,
    private val meanings: RepositoryWordMeanings,
) : WordInfoSource {
    private val cache = ConcurrentHashMap<Int, WordInfo>()

    @Suppress("ReturnCount") // Each missing piece means the word has no usable meaning.
    override suspend fun info(rank: Int): WordInfo? {
        cache[rank]?.let {
            return it
        }
        val key = frequency.representative(rank)
        val ayah = AyahRef(key.surah, key.ayah)
        // The representative's own gloss can be specific to its context, so it only decides when
        // the places give no meaning at all.
        val meaning =
            meanings.commonMeaning(frequency.occurrences(rank))
                ?: meanings.meaning(ayah, key.wordPosition)
                ?: return null
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
