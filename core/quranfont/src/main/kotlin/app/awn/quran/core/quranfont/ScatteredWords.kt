package app.awn.quran.core.quranfont

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Word
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnTypography

/**
 * The glyph run of each of [words] in the glyph print of [print], read from [cache]. A word is left
 * out when the print is not a page-glyph print, when its page is unknown, or when its page font is
 * not installed; such a word is drawn in the Hafs font by the caller.
 */
suspend fun glyphRunsOf(
    words: List<Word>,
    print: QuranPrint,
    cache: ScatteredFontCache,
    isNight: Boolean,
): Map<WordKey, GlyphRun> {
    val quranText = print.quranText
    val script = print.script
    if (quranText == null || script !in pageGlyphScripts) return emptyMap()
    val runs = mutableMapOf<WordKey, GlyphRun>()
    words
        .groupBy { word -> AyahRef(word.key.surah, word.key.ayah) }
        .forEach { (ayah, ayahWords) ->
            // A layout index is read for one ayah at a time; it is not shared between ayahs.
            val pages = GlyphPageIndex(quranText, script).pagesOf(ayah, ayahWords)
            ayahWords.forEach { word ->
                val page = pages[word.key] ?: return@forEach
                val family = cache.load(script, page, isNight) ?: return@forEach
                runs[word.key] = GlyphRun(textToDraw(word, script, hasGlyphFont = true), family)
            }
        }
    return runs
}

/**
 * [glyphRunsOf] for words named only by their [keys]: each ayah's words are read from the Quran
 * text first, so a lesson that names a word by its key can draw it in the glyph print.
 */
suspend fun glyphRunsOfKeys(
    keys: Set<WordKey>,
    print: QuranPrint,
    cache: ScatteredFontCache,
    isNight: Boolean,
): Map<WordKey, GlyphRun> {
    val quranText = print.quranText ?: return emptyMap()
    val words =
        keys
            .groupBy { key -> AyahRef(key.surah, key.ayah) }
            .flatMap { (ayah, ayahKeys) ->
                quranText.wordsOfAyah(ayah).filter { word -> word.key in ayahKeys }
            }
    return glyphRunsOf(words, print, cache, isNight)
}

/**
 * The words in one line of text: each word in its glyph font when [runs] has it, otherwise its
 * Unicode text, which the [style] font draws. Words are separated by a space, as in the Hafs line.
 */
internal fun scatteredLine(words: List<Word>, runs: Map<WordKey, GlyphRun>): AnnotatedString =
    buildAnnotatedString {
        words.forEachIndexed { index, word ->
            if (index > 0) append(" ")
            val run = runs[word.key]
            if (run == null) {
                append(word.textUthmani)
            } else {
                withStyle(SpanStyle(fontFamily = run.family)) { append(run.text) }
            }
        }
    }

/**
 * The [words] of a scattered row (a search hit, a dua) drawn in the user's glyph print. Until their
 * page fonts are loaded, and whenever the print is not a glyph print, they are drawn in the Hafs
 * font exactly as a plain text of the words' Unicode would be.
 */
@Composable
fun ScatteredWordsText(
    words: List<Word>,
    modifier: Modifier = Modifier,
    style: TextStyle = AwnTypography.quran,
    color: Color = Awn.field.content,
    maxLines: Int = Int.MAX_VALUE,
) {
    val print = LocalQuranPrint.current
    val cache = LocalScatteredFontCache.current
    val isNight = !Awn.field.isLight
    val runs by
        produceState(emptyMap<WordKey, GlyphRun>(), words, print, cache, isNight) {
            value = if (cache == null) emptyMap() else glyphRunsOf(words, print, cache, isNight)
        }
    Text(
        text = scatteredLine(words, runs),
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
