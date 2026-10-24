package app.awn.quran.feature.duas.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.font.FontFamily
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.quranfont.AyahWord
import app.awn.quran.core.quranfont.GlyphPageIndex
import app.awn.quran.core.quranfont.PageFontCache
import app.awn.quran.core.quranfont.SharedPageFontCache
import app.awn.quran.core.quranfont.glyphsOf
import app.awn.quran.core.quranfont.isGlyphScript
import app.awn.quran.core.quranfont.pageGlyphScripts
import app.awn.quran.core.quranfont.rememberFontLookup
import app.awn.quran.core.ui.text.normalizeFallbackText
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.duas.DuasDependencies
import app.awn.quran.feature.duas.card.DuaCardData
import app.awn.quran.feature.duas.card.DuaWordTile
import app.awn.quran.feature.read.quran.AyahBlock
import app.awn.quran.feature.read.quran.WordFlowInput
import app.awn.quran.feature.read.quran.WordsFlow
import app.awn.quran.feature.read.quran.readerFills
import app.awn.quran.feature.read.settings.ReadSettings
import app.awn.quran.tracker.WordState
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.runningFold

/** The page that holds the Arabic font of a non-glyph script, as the Mushaf's line view asks. */
private const val ARABIC_FONT_PAGE = 1

/**
 * What the cards need to draw words as the Mushaf's line view does: the user's Mushaf settings and
 * the page fonts they chose. [arabicFamily] draws a word that has no glyph code of its own.
 */
internal class LineViewResources(
    val settings: ReadSettings,
    val fontCache: PageFontCache,
    val arabicFamily: FontFamily?,
    val quranText: QuranTextRepository,
)

/** The resources of the line view, or null until the settings and the Arabic font are read. */
@Composable
internal fun rememberLineViewResources(dependencies: DuasDependencies): LineViewResources? {
    val settings by dependencies.readSettings.collectAsState(initial = null)
    // A new cache for each pack change, as the Quran screen does, so a font installed later shows.
    val packRevision by
        remember(dependencies.packs) {
                dependencies.packs.installed().drop(1).runningFold(0) { count, _ -> count + 1 }
            }
            .collectAsState(initial = 0)
    val fontCache =
        remember(dependencies.packs, packRevision) {
            SharedPageFontCache.obtain(dependencies.packs, packRevision)
        }
    val script = settings?.script ?: ScriptId.QpcHafs
    val arabicScript = script.takeUnless { isGlyphScript(it) } ?: ScriptId.QpcHafs
    val arabicLookup =
        rememberFontLookup(fontCache, arabicScript, ARABIC_FONT_PAGE, isNight = false)
    val loadedSettings = settings
    return remember(loadedSettings, fontCache, arabicLookup, dependencies.quranText) {
        if (loadedSettings == null || arabicLookup == null) {
            null
        } else {
            LineViewResources(
                settings = loadedSettings,
                fontCache = fontCache,
                arabicFamily = arabicLookup.family,
                quranText = dependencies.quranText,
            )
        }
    }
}

/** What a tap on a word of the card hands over; the Mushaf's line view hands the same. */
internal typealias WordTapHandler = (AyahRef, AyahWord, Rect?) -> Unit

/**
 * The words of one dua card drawn by the Mushaf's own word flow, one flow for each ayah. [settings]
 * is the user's Mushaf settings with this card's layers applied.
 */
@Composable
internal fun DuaLineView(
    card: DuaCardData,
    wordTiles: List<DuaWordTile>,
    showDuaOnly: Boolean,
    highlights: CardHighlights,
    settings: ReadSettings,
    resources: LineViewResources,
    onWordTap: WordTapHandler,
    modifier: Modifier = Modifier,
) {
    val cardGlyphs = rememberCardGlyphs(card, settings.script, resources.quranText)
    // A glyph word draws nothing before its page is known, so the lines wait for it.
    val glyphs = cardGlyphs ?: return
    val blocks =
        remember(card, wordTiles, showDuaOnly, glyphs, settings.script) {
            ayahBlocksOf(card, wordTiles, showDuaOnly, settings.script, glyphs)
        }
    val input =
        WordFlowInput(
            settings = settings,
            fontCache = resources.fontCache,
            wordStates = highlights.wordStates,
            focusedWord = highlights.focusedWord,
            dimmedWords = if (showDuaOnly) emptySet() else dimmedWordsOf(card),
            tappableWords = tappableWordsOf(blocks, highlights),
            wordTag = ::tagOfCardWord,
            onWordTap = onWordTap,
        )
    val arabicStyle =
        AwnTypography.quran.copy(
            fontSize = AwnTypography.quran.fontSize * settings.arabicScale,
            fontFamily = resources.arabicFamily ?: AwnTypography.quran.fontFamily,
        )
    val fills = readerFills()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
        blocks.forEach { block ->
            WordsFlow(
                block = block,
                input = input,
                arabicStyle = arabicStyle,
                fills = fills,
                playingWord = playingPositionIn(block.ayah, highlights.playingWord),
                onAyahLongPress = {},
            )
        }
        val transliteration = blocks.mapNotNull { it.transliteration }.joinToString(separator = " ")
        if (settings.showTransliteration && transliteration.isNotBlank()) {
            Text(
                text = transliteration,
                style =
                    AwnTypography.body.copy(
                        fontSize = AwnTypography.body.fontSize * settings.transliterationScale,
                    ),
                color = Awn.field.buttonFill,
                modifier = Modifier.padding(top = AwnSpacing.small),
            )
        }
    }
}

/**
 * What a glyph print needs to draw [DuaCardData.words]: the page of each word and the end sign of
 * each ayah. Both are empty for a script that has no glyphs.
 */
internal class CardGlyphs(
    val pages: Map<WordKey, Int>,
    val endSigns: Map<AyahRef, GlyphEndSign>,
) {
    companion object {
        val None = CardGlyphs(emptyMap(), emptyMap())
    }
}

/** The glyphs of [card] in [script]; null while the pages are being looked up. */
@Composable
private fun rememberCardGlyphs(
    card: DuaCardData,
    script: ScriptId,
    quranText: QuranTextRepository,
): CardGlyphs? {
    val usesGlyphs = script in pageGlyphScripts
    val glyphs by
        produceState<CardGlyphs?>(
            initialValue = if (usesGlyphs) null else CardGlyphs.None,
            card.dua.id,
            script,
        ) {
            value = if (usesGlyphs) cardGlyphsOf(card, script, quranText) else CardGlyphs.None
        }
    return glyphs
}

private suspend fun cardGlyphsOf(
    card: DuaCardData,
    script: ScriptId,
    quranText: QuranTextRepository,
): CardGlyphs {
    val pages = glyphPagesOf(card, script, quranText)
    val endSigns = glyphEndSignsOf(card.words, script, pages, quranText)
    return CardGlyphs(pages, endSigns)
}

private suspend fun glyphPagesOf(
    card: DuaCardData,
    script: ScriptId,
    quranText: QuranTextRepository,
): Map<WordKey, Int> {
    val index = GlyphPageIndex(quranText, script)
    val pages = mutableMapOf<WordKey, Int>()
    card.words
        .groupBy { AyahRef(it.word.key.surah, it.word.key.ayah) }
        .forEach { (ayah, cardWords) -> pages += index.pagesOf(ayah, cardWords.map { it.word }) }
    return pages
}

/**
 * The ayahs of [card] as the word flow wants them. A word outside the dua is left out when
 * [showDuaOnly] is set. A word has its meaning and sound from [wordTiles], which hold the dua's own
 * words only, so a word outside the dua has none. An ayah ends with its end marker unless its last
 * word is left out, so the marker never stands after a word that is not shown.
 */
internal fun ayahBlocksOf(
    card: DuaCardData,
    wordTiles: List<DuaWordTile>,
    showDuaOnly: Boolean,
    script: ScriptId,
    glyphs: CardGlyphs,
): List<AyahBlock> {
    val tilesByKey = wordTiles.associateBy { it.key }
    val shownWords = if (showDuaOnly) card.words.filter { it.isDuaWord } else card.words
    val lastWordKeys = lastWordKeysOfAyahs(card)
    return shownWords
        .groupBy { AyahRef(it.word.key.surah, it.word.key.ayah) }
        .map { (ayah, cardWords) ->
            val spokenWords = cardWords.map { cardWord ->
                val tile = tilesByKey[cardWord.word.key]
                AyahWord(
                    key = cardWord.word.key,
                    arabic = normalizeFallbackText(cardWord.word.textUthmani),
                    meaning = tile?.meaning?.takeIf { it.isNotBlank() },
                    transliteration = tile?.transliteration?.takeIf { it.isNotBlank() },
                    glyphs = glyphsOf(cardWord.word, script, glyphs.pages),
                )
            }
            val endsWithMarker = cardWords.any { it.word.key == lastWordKeys[ayah] }
            val words =
                if (endsWithMarker) {
                    spokenWords + ayahEndWordOf(ayah, glyphs.endSigns[ayah])
                } else {
                    spokenWords
                }
            AyahBlock(
                ayah = ayah,
                words = words,
                transliteration =
                    spokenWords
                        .mapNotNull { it.transliteration }
                        .joinToString(separator = " ")
                        .takeIf { it.isNotBlank() },
                translations = emptyList(),
            )
        }
}

/** The words faint on a card of whole ayahs: those outside the dua and the markers after them. */
private fun dimmedWordsOf(card: DuaCardData): Set<WordKey> {
    val markersAfterOutsideWords =
        lastWordKeysOfAyahs(card)
            .filterValues { lastWordKey -> lastWordKey in wordsOutsideDua(card) }
            .keys
            .map { ayah -> ayahEndKeyOf(ayah) }
    return wordsOutsideDua(card) + markersAfterOutsideWords
}

/** The words of the ayah that are not part of the dua; they are drawn faint. */
internal fun wordsOutsideDua(card: DuaCardData): Set<WordKey> =
    card.words.filterNot { it.isDuaWord }.mapTo(mutableSetOf()) { it.word.key }

/** The position the word flow of [ayah] fills as the playing word, or null when it is elsewhere. */
internal fun playingPositionIn(ayah: AyahRef, playingWord: WordKey?): Int? =
    playingWord?.takeIf { it.surah == ayah.surah && it.ayah == ayah.ayah }?.wordPosition

/**
 * Every word on a card opens the word card, with or without the word-by-word layer, except a word
 * hidden for recitation: its card would give the word away.
 */
private fun tappableWordsOf(blocks: List<AyahBlock>, highlights: CardHighlights): Set<WordKey> {
    val shownWords =
        blocks
            .flatMap { block -> block.words }
            .map { word -> word.key }
            .filterNot { key -> isAyahEndKey(key) }
            .filter { key -> highlights.wordStates[key] != WordState.Hidden }
    return shownWords.toSet() + highlights.flaggedWords
}
