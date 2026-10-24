package app.awn.quran.core.quranfont

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.LineItem
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.Word
import app.awn.quran.core.data.WordKey

/**
 * A word ready for [PageFontWord]: the [script] to draw it in (the Hafs Unicode print when the word
 * has no glyph on a known page, whatever print the user chose) and the font [page].
 */
internal class PrintedWord(val word: Word, val script: ScriptId, val page: Int)

/**
 * The position the ayah-end marker takes in its ayah. No ayah has this many words, so the marker
 * never shares a key with a real word.
 */
private const val AYAH_END_POSITION = 1000

/** The font page of a print that draws every page with one font. */
private const val SHARED_FONT_PAGE = 1

private const val BLANK_TEXT = "……"

private const val ARABIC_INDIC_ZERO = '٠'

/**
 * The number of [ayah] in Arabic-Indic digits. The Hafs font draws the end-of-ayah ornament around
 * the number by itself; adding the U+06DD sign as well would draw a second ring.
 */
internal fun ayahEndText(ayah: Int): String =
    ayah.toString().map { digit -> ARABIC_INDIC_ZERO + (digit - '0') }.joinToString(separator = "")

/**
 * The words of [ayah] to draw in [print]: those whose position is in [wordRange] (all of them when
 * null), then the ayah-end marker when [showEndMarker] is set and the range reaches the last word.
 */
internal suspend fun printedWordsOf(
    print: QuranPrint,
    ayah: AyahRef,
    wordRange: IntRange?,
    showEndMarker: Boolean,
    blankPosition: Int? = null,
): List<PrintedWord> {
    val quranText = print.quranText ?: return emptyList()
    val allWords = quranText.wordsOfAyah(ayah)
    val shownWords =
        if (wordRange == null) allWords else allWords.filter { it.key.wordPosition in wordRange }
    val pages = pagesOfWords(quranText, print.script, ayah, shownWords)
    val printedWords = shownWords.map { word ->
        if (word.key.wordPosition == blankPosition) {
            blankOf(word)
        } else {
            printedWordOf(word, print.script, pages[word.key])
        }
    }
    val reachesAyahEnd = shownWords.isNotEmpty() && shownWords.last() == allWords.last()
    val endMarker =
        if (showEndMarker && reachesAyahEnd) {
            endMarkerOf(quranText, print.script, ayah, pages[shownWords.last().key])
        } else {
            null
        }
    return printedWords + listOfNotNull(endMarker)
}

private suspend fun pagesOfWords(
    quranText: QuranTextRepository,
    script: ScriptId,
    ayah: AyahRef,
    words: List<Word>,
): Map<WordKey, Int> =
    if (script in pageGlyphScripts) {
        GlyphPageIndex(quranText, script).pagesOf(ayah, words)
    } else {
        emptyMap()
    }

/** A gap with the width of a short word, in the place of a word the learner has to supply. */
private fun blankOf(word: Word): PrintedWord =
    PrintedWord(Word(word.key, BLANK_TEXT, null, null), ScriptId.QpcHafs, SHARED_FONT_PAGE)

private fun printedWordOf(word: Word, script: ScriptId, glyphPage: Int?): PrintedWord =
    when {
        script in pageGlyphScripts && glyphPage != null -> PrintedWord(word, script, glyphPage)
        script in indoPakScripts -> PrintedWord(word, script, SHARED_FONT_PAGE)
        else -> PrintedWord(word, ScriptId.QpcHafs, SHARED_FONT_PAGE)
    }

/**
 * The end marker as one more word. A glyph print draws the sign the page layout holds, in the font
 * of [lastWordPage]; every other print, and a glyph print whose layout has no sign, draws the
 * number.
 */
private suspend fun endMarkerOf(
    quranText: QuranTextRepository,
    script: ScriptId,
    ayah: AyahRef,
    lastWordPage: Int?,
): PrintedWord {
    val key = WordKey(ayah.surah, ayah.ayah, AYAH_END_POSITION)
    val number = ayahEndText(ayah.ayah)
    val signCode = lastWordPage?.let { page -> endSignCodeOn(quranText, script, ayah, page) }
    return if (lastWordPage != null && signCode != null) {
        PrintedWord(Word(key, number, signCode, signCode), script, lastWordPage)
    } else {
        PrintedWord(Word(key, number, null, null), ScriptId.QpcHafs, SHARED_FONT_PAGE)
    }
}

private suspend fun endSignCodeOn(
    quranText: QuranTextRepository,
    script: ScriptId,
    ayah: AyahRef,
    page: Int,
): String? =
    quranText
        .pageLayout(page, script)
        .lines
        .flatMap { line -> line.items }
        .filterIsInstance<LineItem.AyahEnd>()
        .firstOrNull { item -> item.ayah == ayah }
        ?.glyphCode
