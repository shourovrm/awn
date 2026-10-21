package app.awn.quran.feature.read.page

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.Word
import app.awn.quran.core.ui.theme.Awn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One Quran [word] drawn as the printed page [page] of [script] draws it: its glyph code in the
 * page font, so a tajweed (V4) word keeps its colours. With [isNight] a colour font is loaded in
 * its night palette, where the letters are white; the default follows the current field.
 *
 * The font family comes from [fontCache] (the same cache the Mushaf page uses, so a page that is on
 * screen costs nothing extra) and is read off the main thread when it is not cached yet; until it
 * is ready nothing is drawn. When the script has no page font, or the font is not installed, the
 * Unicode text is drawn in the QPC Hafs family instead, as on the Mushaf page.
 *
 * [color] is the ink of fonts without colour layers; colour glyph layers ignore it.
 */
@Composable
fun PageFontWord(
    word: Word,
    script: ScriptId,
    page: Int,
    fontCache: PageFontCache,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
    color: Color = Awn.field.content,
    isNight: Boolean = !Awn.field.isLight,
) {
    val fonts by
        produceState<WordFonts?>(
            initialValue = cachedWordFonts(fontCache, script, page, isNight),
            script,
            page,
            isNight,
            fontCache,
        ) {
            if (value == null) {
                value =
                    withContext(Dispatchers.IO) { loadWordFonts(fontCache, script, page, isNight) }
            }
        }
    val loaded = fonts ?: return
    val glyphFamily = loaded.glyphFamily
    Text(
        text = textToDraw(word, script, hasGlyphFont = glyphFamily != null),
        style = TextStyle(fontFamily = glyphFamily ?: loaded.plainFamily, fontSize = fontSize),
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Visible,
        modifier = modifier,
    )
}

/**
 * The families a word can be drawn with: [glyphFamily] is the page font of a glyph script, or null
 * when the script has none or its font is not installed; [plainFamily] then draws the Unicode text.
 */
private class WordFonts(val glyphFamily: FontFamily?, val plainFamily: FontFamily?)

private fun cachedWordFonts(
    fontCache: PageFontCache,
    script: ScriptId,
    page: Int,
    isNight: Boolean,
): WordFonts? =
    if (script in glyphScripts) {
        fontCache.cachedFamily(script, page, isNight)?.let { WordFonts(it, plainFamily = null) }
    } else {
        fontCache.cachedFamily(ScriptId.QpcHafs, page)?.let { WordFonts(null, it) }
    }

private fun loadWordFonts(
    fontCache: PageFontCache,
    script: ScriptId,
    page: Int,
    isNight: Boolean,
): WordFonts {
    val glyphFamily =
        if (script in glyphScripts) fontCache.familyFor(script, page, isNight) else null
    val plainFamily = if (glyphFamily == null) fontCache.familyFor(ScriptId.QpcHafs, page) else null
    return WordFonts(glyphFamily, plainFamily)
}
