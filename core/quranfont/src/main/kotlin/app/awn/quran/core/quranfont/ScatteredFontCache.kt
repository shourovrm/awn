package app.awn.quran.core.quranfont

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import app.awn.quran.core.data.PackRepository
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.fonts.NightFontFiles
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The page font families for screens whose words sit on many pages at once, such as search results
 * and Dua search hits. [PageFontCache] keeps only the pages around the reader; these rows would
 * otherwise add a family per page and never release one.
 *
 * At most [capacity] families are held. The least recently used family is dropped to make room, so
 * a row that is scrolled away costs nothing once newer rows need the room.
 *
 * Colour (tajweed) fonts are kept per day and per night, as in [PageFontCache].
 */
class ScatteredFontCache(
    private val packs: PackRepository,
    private val capacity: Int = DEFAULT_CAPACITY,
    private val createFamily: (File) -> FontFamily = { file -> FontFamily(Font(file)) },
    private val nightFileFor: (File) -> File = NightFontFiles::nightFileFor,
) {

    private val lock = Any()
    private val families = LeastRecentlyUsedCache<PageFontKey, FontFamily>(capacity)

    /** The number of families held; for tests and for checking the memory bound. */
    internal val cachedPageCount: Int
        get() = synchronized(lock) { families.size }

    /**
     * The family of [page] in [script], or null when the script's font is not installed. A missing
     * font is not cached, because the pack may be installed a moment later.
     */
    @Suppress("ReturnCount") // Cache hit, missing font, loaded font: three plain exits.
    fun familyFor(script: ScriptId, page: Int, isNight: Boolean = false): FontFamily? {
        val key = PageFontKey(script, page, isNight)
        // Loads happen under the lock: two words asking for one page must not both create its font.
        synchronized(lock) {
            families.get(key)?.let {
                return it
            }
            val file = packs.fontFile(script, page) ?: return null
            val needsNightCopy = isNight && script == ScriptId.V4Tajweed
            val fileToLoad = if (needsNightCopy) nightCopyOrSame(file) else file
            val family = createFamily(fileToLoad)
            families.put(key, family)
            return family
        }
    }

    /** [familyFor] on the IO dispatcher, so that a font file is never read on the main thread. */
    suspend fun load(script: ScriptId, page: Int, isNight: Boolean = false): FontFamily? =
        withContext(Dispatchers.IO) { familyFor(script, page, isNight) }

    /** The family already held for [page], or null; never reads a file, so it is safe anywhere. */
    fun cachedFamily(script: ScriptId, page: Int, isNight: Boolean = false): FontFamily? =
        synchronized(lock) { families.get(PageFontKey(script, page, isNight)) }

    private fun nightCopyOrSame(file: File): File =
        try {
            nightFileFor(file)
        } catch (error: IOException) {
            // Day colours on a dark page are hard to read but better than no tajweed text at all.
            android.util.Log.w(TAG, "No night copy of ${file.name}; using the day palette", error)
            file
        }

    companion object {
        private const val TAG = "ScatteredFontCache"

        /**
         * Families held at once: about the pages of a screen's rows. A page font is 160 to 340 KB,
         * so twelve keep the scattered rows near the memory of the reader's own pages.
         */
        const val DEFAULT_CAPACITY = 12
    }
}

/**
 * The bounded cache of the app, kept while the pack set does not change. A pack installed later
 * makes a new one, so a font that was missing before shows.
 */
object SharedScatteredFontCache {
    private var current: Triple<PackRepository, Int, ScatteredFontCache>? = null

    /** The cache for [packs] at [revision]; a different pack set or revision gets a new one. */
    @Synchronized
    fun obtain(packs: PackRepository, revision: Int): ScatteredFontCache {
        val kept = current
        if (kept != null && kept.first === packs && kept.second == revision) return kept.third
        return ScatteredFontCache(packs).also { current = Triple(packs, revision, it) }
    }
}

/**
 * The scattered font cache of the app. Without a provider (a test, a preview) it is null, and the
 * words of the scattered rows are drawn in the bundled Hafs font.
 */
val LocalScatteredFontCache = staticCompositionLocalOf<ScatteredFontCache?> { null }
