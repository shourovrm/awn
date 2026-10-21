package app.awn.quran.feature.read.quran

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.TranslationId
import app.awn.quran.core.data.TranslationRepository
import app.awn.quran.core.data.WordKey
import app.awn.quran.feature.read.page.normalizeFallbackText

/** One Arabic word with what the word by word view and its popup show. */
data class AyahWord(
    val key: WordKey,
    val arabic: String,
    /** Null when the word-by-word pack is not installed. */
    val meaning: String?,
    val transliteration: String?,
)

/** One translation of an ayah. [rangeLabel] is set when a publisher merged several ayahs. */
data class AyahTranslation(val name: String, val rangeLabel: String?, val text: String)

/** Everything the Translation and Word by word views show for one ayah. */
data class AyahBlock(
    val ayah: AyahRef,
    val words: List<AyahWord>,
    val transliteration: String?,
    val translations: List<AyahTranslation>,
) {
    /** The Arabic words joined with spaces, for copy and share. */
    val arabic: String
        get() = words.joinToString(separator = " ") { it.arabic }
}

/** One entry of the vertical list that the Translation and Word by word views draw. */
sealed interface PageItem {
    /** The band with the surah name that starts each surah. */
    data class SurahBand(val chapter: Chapter) : PageItem

    /**
     * The centred Basmalah before [surah]. It is not an ayah: it has no reference, cannot be
     * selected and has no translation.
     */
    data class Basmalah(val surah: Int) : PageItem

    data class Ayah(val block: AyahBlock) : PageItem
}

/** The list of one page for the two list views. */
data class PageContent(val page: Int, val items: List<PageItem>) {
    /** Position in [items] of [ayah], or -1 when the page does not list it. */
    fun indexOf(ayah: AyahRef): Int = items.indexOfFirst {
        it is PageItem.Ayah && it.block.ayah == ayah
    }

    val ayahs: List<AyahRef>
        get() = items.filterIsInstance<PageItem.Ayah>().map { it.block.ayah }
}

/** Builds [PageContent] from the text and translation repositories. */
internal class PageContentLoader(
    private val quranText: QuranTextRepository,
    private val translations: TranslationRepository,
    /** "en" or "bn": picks the word-by-word pack. */
    private val wordLanguage: String,
) {

    suspend fun load(
        page: Int,
        script: ScriptId,
        enabledTranslations: List<TranslationId>,
        chapters: List<Chapter>,
    ): PageContent {
        val names = translations.available().associate { it.id to it.name }
        val items = mutableListOf<PageItem>()
        for (ayah in quranText.ayahsOnPage(page, script)) {
            if (ayah.ayah == 1) items += surahStartItems(chapters.chapterNumbered(ayah.surah))
            items += PageItem.Ayah(loadBlock(ayah, enabledTranslations, names))
        }
        return PageContent(page, items)
    }

    /** The band, and the Basmalah unless the surah is 1 (its ayah 1) or 9 (it has none). */
    private fun surahStartItems(chapter: Chapter?): List<PageItem> =
        when {
            chapter == null -> emptyList()
            chapter.bismillahPre ->
                listOf(PageItem.SurahBand(chapter), PageItem.Basmalah(chapter.number))
            else -> listOf(PageItem.SurahBand(chapter))
        }

    /** The block of one ayah, also used by the ayah panel. */
    suspend fun loadBlock(
        ayah: AyahRef,
        enabledTranslations: List<TranslationId>,
        names: Map<TranslationId, String>? = null,
    ): AyahBlock {
        val translationNames = names ?: translations.available().associate { it.id to it.name }
        val words = quranText.wordsOfAyah(ayah)
        val meanings = translations.wordByWord(wordLanguage, ayah)
        val ayahTranslations = enabledTranslations.mapNotNull { id ->
            val passage = translations.text(id, ayah) ?: return@mapNotNull null
            val isRange = passage.ayahFrom != passage.ayahTo
            // A merged passage is shown once, at its first ayah (docs/packs.md).
            if (isRange && ayah.ayah != passage.ayahFrom) return@mapNotNull null
            AyahTranslation(
                name = translationNames[id] ?: id.id,
                rangeLabel = if (isRange) "${passage.ayahFrom}-${passage.ayahTo}" else null,
                text = passage.text,
            )
        }
        return AyahBlock(
            ayah = ayah,
            words =
                words.map { word ->
                    AyahWord(
                        key = word.key,
                        arabic = normalizeFallbackText(word.textUthmani),
                        meaning = meaningAtPosition(meanings, word.key.wordPosition),
                        transliteration = word.transliteration,
                    )
                },
            transliteration =
                words
                    .mapNotNull { it.transliteration }
                    .joinToString(separator = " ")
                    .ifBlank { null },
            translations = ayahTranslations,
        )
    }
}

/**
 * The meaning of the word at [position] (counted from 1). The pack lists one meaning per word
 * position, so the position of a word, not its place in the word list, picks the entry; a list that
 * holds another token, or starts later, cannot shift the meanings. A blank entry (the pack empties
 * repeated phrases) shows nothing.
 */
internal fun meaningAtPosition(meanings: List<String>, position: Int): String? =
    meanings.getOrNull(position - 1)?.takeIf { it.isNotBlank() }
