package app.awn.quran.feature.read.page

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import app.awn.quran.core.data.PackRepository
import app.awn.quran.core.data.ScriptId
import java.io.File

/**
 * Keeps the [FontFamily] of the pages near the one on screen. Glyph scripts draw each page with its
 * own font file, so the families are created on demand and dropped once the reader is more than
 * [radius] pages away.
 */
class PageFontCache(
    private val packs: PackRepository,
    private val radius: Int = DEFAULT_RADIUS,
    private val createFamily: (File) -> FontFamily = { file -> FontFamily(Font(file)) },
) {

    private val families = mutableMapOf<Pair<ScriptId, Int>, FontFamily>()

    /** The pages that currently have a cached family; for tests. */
    val cachedPages: Set<Int>
        get() = families.keys.map { it.second }.toSet()

    /** Returns the font family for [page], or null when the script's font is not installed. */
    fun familyFor(script: ScriptId, page: Int): FontFamily? {
        val key = script to page
        val cached = families[key]
        if (cached != null) return cached
        // A missing font is not cached, because the pack may be installed a moment later.
        return packs.fontFile(script, page)?.let { file ->
            createFamily(file).also { families[key] = it }
        }
    }

    /** Drops every cached family that is more than [radius] pages from [centerPage]. */
    fun retainAround(centerPage: Int) {
        val nearby = (centerPage - radius)..(centerPage + radius)
        families.keys.retainAll { (_, page) -> page in nearby }
    }

    /** Forgets everything, for when a pack was installed or removed. */
    fun clear() = families.clear()

    companion object {
        /** Five pages on either side of the current one. */
        const val DEFAULT_RADIUS = 5
    }
}
