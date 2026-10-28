package app.awn.quran.feature.learn.course

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.quranfont.GlyphRun

/**
 * Lesson text with its Quran words by key: `{{2:3:7}}` stands for a word, `[[هُمْ]]` for a typed
 * fragment, and any other Arabic (a letter in a title) is drawn in the Arabic font as it stands.
 * [build] returns a string whose Arabic runs use the learning font, so a line of English with a
 * Quran word in it keeps both scripts right.
 */
object LessonMarkup {
    private val TOKEN = Regex("""\{\{(\d{1,3}:\d{1,3}:\d{1,3})\}\}""")
    private val FRAGMENT = Regex("""\[\[([^\]]*)\]\]""")

    /** The word keys that [text] names, so a screen can load them before it draws. */
    fun wordKeys(text: String): Set<WordKey> =
        TOKEN.findAll(text).map { WordKey.parse(it.groupValues[1]) }.toSet()

    /** [text] with every word key and fragment replaced and the Arabic runs styled. */
    @Suppress("LongParameterList") // Each argument is a different input of the styled string.
    fun build(
        text: String,
        wordTexts: Map<WordKey, String>,
        arabicFont: FontFamily?,
        arabicSize: TextUnit,
        highlight: WordKey? = null,
        highlightStyle: SpanStyle = SpanStyle(),
        glyphRuns: Map<WordKey, GlyphRun> = emptyMap(),
    ): AnnotatedString {
        val arabicStyle = SpanStyle(fontFamily = arabicFont, fontSize = arabicSize)
        return buildAnnotatedString {
            val plain = FRAGMENT.replace(text) { it.groupValues[1] }
            var cursor = 0
            for (match in TOKEN.findAll(plain)) {
                appendStyledRuns(plain.substring(cursor, match.range.first), arabicStyle)
                val key = WordKey.parse(match.groupValues[1])
                val word = wordTexts[key].orEmpty()
                val glyphRun = glyphRuns[key]
                // A word with a glyph run uses its page font; the others keep the learning font.
                val wordStyle =
                    if (glyphRun == null) {
                        arabicStyle
                    } else {
                        arabicStyle.copy(fontFamily = glyphRun.family)
                    }
                val style = if (key == highlight) wordStyle.merge(highlightStyle) else wordStyle
                withStyle(style) { append(glyphRun?.text ?: word) }
                cursor = match.range.last + 1
            }
            appendStyledRuns(plain.substring(cursor), arabicStyle)
        }
    }

    /** True when [text] holds a character of the Arabic script. */
    fun hasArabic(text: String): Boolean = text.any(::isArabic)

    private fun AnnotatedString.Builder.appendStyledRuns(text: String, arabicStyle: SpanStyle) {
        var runStart = 0
        while (runStart < text.length) {
            val isArabicRun = isArabic(text[runStart])
            var runEnd = runStart
            while (runEnd < text.length && isArabic(text[runEnd]) == isArabicRun) runEnd++
            val run = text.substring(runStart, runEnd)
            if (isArabicRun) withStyle(arabicStyle) { append(run) } else append(run)
            runStart = runEnd
        }
    }

    private fun isArabic(char: Char): Boolean =
        char.code in ARABIC_RANGE ||
            char.code in ARABIC_SUPPLEMENT ||
            char.code in PRESENTATION_FORMS

    private val ARABIC_RANGE = 0x0600..0x06FF
    private val ARABIC_SUPPLEMENT = 0x0750..0x077F
    private val PRESENTATION_FORMS = 0xFB50..0xFEFF
}
