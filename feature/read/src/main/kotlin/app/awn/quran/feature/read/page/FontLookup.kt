package app.awn.quran.feature.read.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.text.font.FontFamily
import app.awn.quran.core.data.PackRepository
import app.awn.quran.core.data.ScriptId

/**
 * The answer to "which font family draws this page": [family] is null when the font is not
 * installed. A [FontLookup] that does not exist yet means the answer is still being fetched.
 */
internal class FontLookup(val family: FontFamily?)

/**
 * The font family of [page] in [script], or null while it is still being found.
 *
 * A cached family is returned in the first composition, so a page the cache prepared ahead of a
 * swipe costs nothing. Otherwise the family is loaded on the IO dispatcher and the caller
 * recomposes when it is ready; no font file is read on the main thread.
 */
@Composable
internal fun rememberFontLookup(
    fontCache: PageFontCache,
    script: ScriptId,
    page: Int,
    isNight: Boolean,
): FontLookup? {
    val lookup by
        produceState<FontLookup?>(
            initialValue = cachedLookup(fontCache, script, page, isNight),
            fontCache,
            script,
            page,
            isNight,
        ) {
            // The state outlives a change of keys: a switch to night must replace the day family,
            // not keep it because a value is already there.
            value =
                cachedLookup(fontCache, script, page, isNight)
                    ?: FontLookup(fontCache.load(script, page, isNight))
        }
    return lookup
}

private fun cachedLookup(
    fontCache: PageFontCache,
    script: ScriptId,
    page: Int,
    isNight: Boolean,
): FontLookup? = fontCache.cachedFamily(script, page, isNight)?.let(::FontLookup)

/**
 * True once the page fonts of [pages] are in the cache (or known to be missing). A list of words
 * must wait for this: a word draws nothing until its font is there, so words that appear one by one
 * would make the lines jump. With no glyph pages it is true at once, and so it is when the cache
 * prepared the pages ahead of the swipe.
 *
 * A missing glyph font also loads the Unicode family of the page, which the words then draw with.
 */
@Composable
internal fun rememberFontsLoaded(
    fontCache: PageFontCache,
    script: ScriptId,
    pages: Set<Int>,
    isNight: Boolean,
): Boolean {
    val alreadyCached = pages.all { page -> fontCache.cachedFamily(script, page, isNight) != null }
    val isLoaded by
        produceState(initialValue = alreadyCached, fontCache, script, pages, isNight) {
            // Runs again on every change of keys (a switch to night, say): the list keeps showing
            // meanwhile, and each cached page returns at once.
            pages.forEach { page -> loadGlyphOrFallbackFont(fontCache, script, page, isNight) }
            value = true
        }
    return isLoaded
}

private suspend fun loadGlyphOrFallbackFont(
    fontCache: PageFontCache,
    script: ScriptId,
    page: Int,
    isNight: Boolean,
) {
    val glyphFamily = fontCache.load(script, page, isNight)
    if (glyphFamily == null) fontCache.load(ScriptId.QpcHafs, page)
}

/**
 * The page font cache of the screen, kept when the screen is recreated. Coming back from Settings
 * builds the Quran screen again; a new empty cache would make every page wait for its font before
 * its first frame, although the page was on screen a moment ago.
 */
internal object SharedPageFontCache {
    private var current: Triple<PackRepository, Int, PageFontCache>? = null

    /** The cache for [packs] at [revision]; a different pack set or revision gets a new one. */
    @Synchronized
    fun obtain(packs: PackRepository, revision: Int): PageFontCache {
        val kept = current
        if (kept != null && kept.first === packs && kept.second == revision) return kept.third
        return PageFontCache(packs).also { current = Triple(packs, revision, it) }
    }
}
