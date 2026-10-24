package app.awn.quran.feature.duas.ui

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.LineItem
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.Word
import app.awn.quran.core.data.WordKey
import app.awn.quran.feature.duas.card.DuaCardData
import app.awn.quran.feature.read.quran.AyahWord
import app.awn.quran.feature.read.quran.WordGlyphs

/**
 * The position the ayah-end marker takes in its ayah. No ayah has this many words, so the marker
 * never shares a key with a real word, and the cards can tell it from one.
 */
private const val AYAH_END_POSITION = 1000

private const val ARABIC_INDIC_ZERO = '٠'

/** The end-of-ayah sign, which a Unicode font draws round the digits that follow it. */
private const val END_OF_AYAH_SIGN = '۝'

/** What the glyph prints need to draw the marker: the sign in the page font, and that page. */
internal class GlyphEndSign(val code: String, val page: Int)

internal fun ayahEndKeyOf(ayah: AyahRef): WordKey =
    WordKey(ayah.surah, ayah.ayah, AYAH_END_POSITION)

internal fun isAyahEndKey(key: WordKey): Boolean = key.wordPosition == AYAH_END_POSITION

/** Test tag of the marker after [ayah]. */
internal fun ayahEndTag(ayah: AyahRef): String = "dua-ayah-end-${ayah.surah}-${ayah.ayah}"

/** The sign and the number of [ayah] as Unicode text: U+06DD followed by Arabic-Indic digits. */
internal fun ayahEndText(ayah: Int): String {
    val digits = ayah.toString().map { digit -> ARABIC_INDIC_ZERO + (digit - '0') }
    return END_OF_AYAH_SIGN + digits.joinToString(separator = "")
}

/**
 * The marker as one more word at the end of the ayah, so the word flow draws it in the script's own
 * way. A glyph print draws [endSign] in the page font; every other script draws the Unicode text,
 * as does a glyph print whose pack has no end sign.
 */
internal fun ayahEndWordOf(ayah: AyahRef, endSign: GlyphEndSign?): AyahWord {
    val key = ayahEndKeyOf(ayah)
    val text = ayahEndText(ayah.ayah)
    val glyphs = endSign?.let { sign ->
        val signWord = Word(key, text, codeV1 = sign.code, codeV2 = sign.code)
        WordGlyphs(signWord, sign.page)
    }
    return AyahWord(
        key = key,
        arabic = text,
        meaning = null,
        transliteration = null,
        glyphs = glyphs,
    )
}

/**
 * The end sign of each ayah of [cardWords] that a glyph [script] prints, read from the layout of
 * the page that holds the ayah's last word; the layout is the one place that knows the sign's code.
 */
internal suspend fun glyphEndSignsOf(
    cardWords: List<CardWord>,
    script: ScriptId,
    glyphPages: Map<WordKey, Int>,
    quranText: QuranTextRepository,
): Map<AyahRef, GlyphEndSign> {
    val endSigns = mutableMapOf<AyahRef, GlyphEndSign>()
    cardWords
        .groupBy { AyahRef(it.word.key.surah, it.word.key.ayah) }
        .forEach { (ayah, wordsOfAyah) ->
            val lastWordKey = wordsOfAyah.last().word.key
            val page = glyphPages[lastWordKey] ?: return@forEach
            val code = endSignCodeOn(page, ayah, script, quranText)
            if (code != null) endSigns[ayah] = GlyphEndSign(code, page)
        }
    return endSigns
}

private suspend fun endSignCodeOn(
    page: Int,
    ayah: AyahRef,
    script: ScriptId,
    quranText: QuranTextRepository,
): String? =
    quranText
        .pageLayout(page, script)
        .lines
        .flatMap { line -> line.items }
        .filterIsInstance<LineItem.AyahEnd>()
        .firstOrNull { it.ayah == ayah }
        ?.glyphCode

/** The last word of each ayah on the card; the end marker follows it when it is shown. */
internal fun lastWordKeysOfAyahs(card: DuaCardData): Map<AyahRef, WordKey> =
    card.words
        .groupBy { AyahRef(it.word.key.surah, it.word.key.ayah) }
        .mapValues { (_, cardWords) -> cardWords.last().word.key }

/** The tag of a word of the card, or of the end marker that follows an ayah. */
internal fun tagOfCardWord(key: WordKey): String =
    if (isAyahEndKey(key)) ayahEndTag(AyahRef(key.surah, key.ayah)) else cardWordTag(key)
