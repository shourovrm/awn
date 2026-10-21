package app.awn.quran.feature.read.quran

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.TranslationId
import app.awn.quran.core.data.TranslationRepository
import app.awn.quran.core.data.Word
import app.awn.quran.core.data.WordKey
import app.awn.quran.feature.read.page.isGlyphScript
import app.awn.quran.feature.read.page.normalizeFallbackText
import app.awn.quran.feature.read.settings.ENGLISH_WORD_LANGUAGE

/**
 * What draws a word in its page font: the glyph [word] and the Mushaf [page] whose font its glyph
 * code belongs to. A code is valid only with the font of its own page, and an ayah that runs over a
 * page break holds words of two pages.
 */
data class WordGlyphs(val word: Word, val page: Int)

/** One Arabic word with what the word by word view and its popup show. */
data class AyahWord(
    val key: WordKey,
    val arabic: String,
    /** Null when the word-by-word pack is not installed. */
    val meaning: String?,
    val transliteration: String?,
    /** Set for the glyph prints (V1, V2, tajweed); null when [arabic] is drawn as plain text. */
    val glyphs: WordGlyphs? = null,
)

/** One translation of an ayah. [rangeLabel] is set when a publisher merged several ayahs. */
data class AyahTranslation(
    val name: String,
    val rangeLabel: String?,
    val text: String,
    val packId: String = "",
)

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

    /** The Mushaf pages whose fonts draw the words of this list; empty for Unicode prints. */
    val fontPages: Set<Int>
        get() =
            items
                .filterIsInstance<PageItem.Ayah>()
                .flatMap { item -> item.block.words }
                .mapNotNullTo(mutableSetOf()) { word -> word.glyphs?.page }
}

/** Builds [PageContent] from the text and translation repositories. */
internal class PageContentLoader(
    private val quranText: QuranTextRepository,
    private val translations: TranslationRepository,
    /**
     * The language of the word-by-word meanings to read now, such as "en" or "bn". It is asked at
     * every load because the user can change it, or the app language it follows, while the screen
     * is open.
     */
    private val wordLanguage: () -> String,
) {
    constructor(
        quranText: QuranTextRepository,
        translations: TranslationRepository,
        wordLanguage: String,
    ) : this(quranText, translations, { wordLanguage })

    suspend fun load(
        page: Int,
        script: ScriptId,
        enabledTranslations: List<TranslationId>,
        chapters: List<Chapter>,
    ): PageContent {
        val names = translations.available().associate { it.id to it.name }
        val fontPages = if (isGlyphScript(script)) GlyphPageIndex(quranText, script) else null
        val items = mutableListOf<PageItem>()
        for (ayah in quranText.ayahsOnPage(page, script)) {
            if (ayah.ayah == 1) items += surahStartItems(chapters.chapterNumbered(ayah.surah))
            items += PageItem.Ayah(loadBlock(ayah, enabledTranslations, names, script, fontPages))
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

    /**
     * The block of one ayah, also used by the ayah panel. With a glyph [script] and [fontPages],
     * each word also carries the page font that draws it.
     */
    suspend fun loadBlock(
        ayah: AyahRef,
        enabledTranslations: List<TranslationId>,
        names: Map<TranslationId, String>? = null,
        script: ScriptId? = null,
        fontPages: GlyphPageIndex? = null,
    ): AyahBlock {
        val translationNames = names ?: translations.available().associate { it.id to it.name }
        val words = quranText.wordsOfAyah(ayah)
        val pageOfWord = if (script != null) fontPages?.pagesOf(ayah, words).orEmpty() else null
        val meanings = meaningsOf(ayah)
        val wordTransliterations = translations.wordTransliteration(ayah)
        val ayahTranslations = enabledTranslations.mapNotNull { id ->
            val passage = translations.text(id, ayah) ?: return@mapNotNull null
            val isRange = passage.ayahFrom != passage.ayahTo
            // A merged passage is shown once, at its first ayah (docs/packs.md).
            if (isRange && ayah.ayah != passage.ayahFrom) return@mapNotNull null
            AyahTranslation(
                name = translationNames[id] ?: id.id,
                rangeLabel = if (isRange) "${passage.ayahFrom}-${passage.ayahTo}" else null,
                text = passage.text,
                packId = id.id,
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
                        transliteration =
                            meaningAtPosition(wordTransliterations, word.key.wordPosition),
                        glyphs = pageOfWord?.let { glyphsOf(word, script, it) },
                    )
                },
            transliteration = translations.ayahTransliterationLine(ayah, wordTransliterations),
            translations = ayahTranslations,
        )
    }

    /**
     * The meanings of [ayah] in the wanted language. A language with no pack on the phone, and a
     * word the pack leaves blank (packs store '' where the source had English only), show the
     * English meaning instead.
     */
    private suspend fun meaningsOf(ayah: AyahRef): List<String> {
        val language = wordLanguage()
        val wanted = translations.wordByWord(language, ayah)
        if (language == ENGLISH_WORD_LANGUAGE) return wanted
        return meaningsWithEnglishFallback(
            wanted,
            translations.wordByWord(ENGLISH_WORD_LANGUAGE, ayah),
        )
    }
}

/**
 * Joins the [wanted] meanings with the [english] ones by position: a blank or missing wanted
 * meaning is replaced by the English one, and a missing English one stays blank.
 */
internal fun meaningsWithEnglishFallback(
    wanted: List<String>,
    english: List<String>,
): List<String> =
    List(maxOf(wanted.size, english.size)) { index ->
        wanted.getOrNull(index)?.takeIf { it.isNotBlank() } ?: english.getOrNull(index).orEmpty()
    }

/**
 * The transliteration line of an ayah: its words joined, or, when the word transliteration pack is
 * not installed, the whole-ayah English transliteration pack if that one is.
 */
private suspend fun TranslationRepository.ayahTransliterationLine(
    ayah: AyahRef,
    wordTransliterations: List<String>,
): String? {
    val fromWords = wordTransliterations.joinToString(separator = " ")
    if (fromWords.isNotBlank()) return fromWords
    return text(TranslationId(AYAH_TRANSLITERATION_PACK_ID), ayah)?.text?.takeIf {
        it.isNotBlank()
    }
}

private const val AYAH_TRANSLITERATION_PACK_ID = "en_transliteration"

/**
 * The meaning of the word at [position] (counted from 1). The pack lists one meaning per word
 * position, so the position of a word, not its place in the word list, picks the entry; a list that
 * holds another token, or starts later, cannot shift the meanings. A blank entry (the pack empties
 * repeated phrases) shows nothing.
 */
internal fun meaningAtPosition(meanings: List<String>, position: Int): String? =
    meanings.getOrNull(position - 1)?.takeIf { it.isNotBlank() }

/**
 * The glyphs of [word] when it has a glyph code in [script] and its page is known in [pageOfWord];
 * null otherwise, which draws the Unicode text.
 */
private fun glyphsOf(word: Word, script: ScriptId?, pageOfWord: Map<WordKey, Int>): WordGlyphs? {
    val page = pageOfWord[word.key] ?: return null
    val hasCode =
        when (script) {
            ScriptId.V1 -> word.codeV1 != null
            ScriptId.V2,
            ScriptId.V4Tajweed -> word.codeV2 != null
            else -> false
        }
    return if (hasCode) WordGlyphs(word, page) else null
}
