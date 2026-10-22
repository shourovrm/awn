package app.awn.quran.feature.read.page

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import app.awn.quran.core.data.PackRepository
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.fonts.NightFontFiles
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What a cached font family was made for: one page of one script, in day or night colours. */
internal data class PageFontKey(val script: ScriptId, val page: Int, val isNight: Boolean)

/**
 * Keeps the [FontFamily] of the pages near the one on screen. Glyph scripts draw each page with its
 * own font file, so the families are created on demand and dropped once the reader is more than
 * [radius] pages away: the page on screen and two on each side by default.
 *
 * A family belongs to a font file, not to a page. A script that draws every page with one font file
 * shares one family, and so one loaded typeface, between all its pages; a family is released when
 * no page within [radius] uses its file any more.
 *
 * Colour (tajweed) fonts are cached per day and per night: at night the family is made from a copy
 * of the font whose palette 0 is the night palette (see [NightFontFiles]).
 *
 * [retainAround] also prepares the [prefetchRadius] pages on either side of the new page on
 * [prefetchDispatcher], for the script and mode last asked for, so that a swipe finds the family
 * (and the night copy on disk) ready instead of creating them while the page slides in.
 */
class PageFontCache(
    private val packs: PackRepository,
    private val radius: Int = DEFAULT_RADIUS,
    private val createFamily: (File) -> FontFamily = { file -> FontFamily(Font(file)) },
    private val nightFileFor: (File) -> File = NightFontFiles::nightFileFor,
    private val prefetchRadius: Int = DEFAULT_PREFETCH_RADIUS,
    prefetchDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val loadLock = Any()
    private val families = ConcurrentHashMap<PageFontKey, FontFamily>()

    /** One family per font file that a cached page uses; guarded by [loadLock]. */
    private val familiesByFile = HashMap<File, FontFamily>()
    private val prefetchScope = CoroutineScope(SupervisorJob() + prefetchDispatcher)
    private var prefetchJob: Job? = null

    /** The script and mode of the latest request for a page font; what the next swipe will need. */
    @Volatile private var latestRequest: Pair<ScriptId, Boolean>? = null

    /** The pages that currently have a cached family; for tests. */
    val cachedPages: Set<Int>
        get() = families.keys.map { it.page }.toSet()

    /**
     * Returns the font family for [page], or null when the script's font is not installed. With
     * [isNight] a colour font is loaded in its night palette; other fonts are the same at night.
     */
    @Suppress("ReturnCount") // Cache hit, missing font, loaded font: three plain exits.
    fun familyFor(script: ScriptId, page: Int, isNight: Boolean = false): FontFamily? {
        val key = PageFontKey(script, page, isNight)
        families[key]?.let {
            return it
        }
        if (script in glyphScripts) latestRequest = script to isNight
        // One load at a time: many words ask for the same page font at once, and two writers of
        // the same night copy would corrupt it.
        synchronized(loadLock) {
            families[key]?.let {
                return it
            }
            // A missing font is not cached, because the pack may be installed a moment later.
            val file = packs.fontFile(script, page) ?: return null
            val fileToLoad = if (isNight && hasColourPages(script)) nightCopyOrSame(file) else file
            val family = familiesByFile.getOrPut(fileToLoad) { createFamily(fileToLoad) }
            families[key] = family
            return family
        }
    }

    /**
     * [familyFor] on the IO dispatcher. Screens call this, never [familyFor], so that finding, and
     * for a night page writing, the font file never happens on the main thread.
     */
    suspend fun load(script: ScriptId, page: Int, isNight: Boolean = false): FontFamily? =
        withContext(Dispatchers.IO) { familyFor(script, page, isNight) }

    /**
     * The family already cached for [page], or null; never reads a file, so it is safe anywhere.
     */
    fun cachedFamily(script: ScriptId, page: Int, isNight: Boolean = false): FontFamily? =
        families[PageFontKey(script, page, isNight)]

    /**
     * Drops every cached family that is more than [radius] pages from [centerPage], and starts
     * preparing the pages next to it.
     */
    fun retainAround(centerPage: Int) {
        val nearby = (centerPage - radius)..(centerPage + radius)
        synchronized(loadLock) {
            families.keys.retainAll { key -> key.page in nearby }
            releaseFamiliesNoPageUses()
        }
        prepareAround(centerPage)
    }

    /** The number of font files with a family held, so a test can see that old ones are dropped. */
    val loadedFileCount: Int
        get() = synchronized(loadLock) { familiesByFile.size }

    private fun releaseFamiliesNoPageUses() {
        val inUse = families.values.toSet()
        familiesByFile.values.retainAll(inUse)
    }

    /** Forgets everything, for when a pack was installed or removed. */
    fun clear() {
        prefetchJob?.cancel()
        synchronized(loadLock) {
            families.clear()
            familiesByFile.clear()
        }
    }

    private fun prepareAround(centerPage: Int) {
        val (script, isNight) = latestRequest ?: return
        prefetchJob?.cancel()
        prefetchJob = prefetchScope.launch {
            for (page in pagesToPrepare(centerPage, prefetchRadius)) {
                familyFor(script, page, isNight)
                // The Unicode family is the fallback of a glyph page; a swipe needs it as well.
                if (script in glyphScripts) familyFor(ScriptId.QpcHafs, page)
            }
        }
    }

    private fun nightCopyOrSame(file: File): File =
        try {
            nightFileFor(file)
        } catch (error: IOException) {
            // Day colours on a dark page are hard to read but better than no tajweed page at all.
            android.util.Log.w(TAG, "No night copy of ${file.name}; using the day palette", error)
            file
        }

    private fun hasColourPages(script: ScriptId): Boolean = script == ScriptId.V4Tajweed

    companion object {
        private const val TAG = "PageFontCache"

        /** Two pages on either side of the current one; the same pages are prepared ahead. */
        const val DEFAULT_RADIUS = 2

        /** Two pages on either side of the current one are prepared ahead of a swipe. */
        const val DEFAULT_PREFETCH_RADIUS = 2

        private const val FIRST_PAGE = 1
        private const val LAST_PAGE = 604

        /**
         * The pages to prepare around [center], nearest first and the next page before the previous
         * one, since most readers turn forward; [center] itself is skipped because it is on screen.
         */
        fun pagesToPrepare(center: Int, radius: Int): List<Int> =
            (1..radius)
                .flatMap { distance -> listOf(center + distance, center - distance) }
                .filter { page -> page in FIRST_PAGE..LAST_PAGE }
    }
}
